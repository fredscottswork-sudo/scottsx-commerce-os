/**
 * ScottsTechX — seller image uploads.
 *
 *   POST   /api/v1/uploads/images   (auth, multipart, field "image")
 *   GET    /api/v1/uploads/images/:id            → the bytes (public, cached)
 *   GET    /api/v1/me/uploads                    → my recent uploads
 *   DELETE /api/v1/uploads/images/:id            → remove my own upload
 *
 * Sellers list from a phone, where the photo is in the camera roll and there is
 * no URL to paste, so uploading has to work with no configuration at all.
 *
 * Storage: Firebase Storage when it is configured, otherwise Postgres. The DB
 * fallback is deliberate — the deploy targets have an ephemeral filesystem, so
 * anything written next to the process vanishes on the next deploy and every
 * listing photo silently 404s.
 *
 * The declared Content-Type is never trusted: the magic bytes are checked, and
 * the real pixel dimensions are read from the file header, so a renamed .exe or
 * a zip bomb cannot be stored as an "image".
 */
import type { FastifyInstance } from 'fastify';
import { createHash } from 'node:crypto';
import { getPool } from '../../db.js';
import { requireAuth, authedUser } from '../../auth.js';
import { NotFoundError } from '../../errors.js';
import { getFirebaseApp, firebaseReady } from '../../firebase/admin.js';
import { getStorage } from 'firebase-admin/storage';

/** Hard ceiling on what a phone may send us. */
const MAX_BYTES = 3 * 1024 * 1024; // 3 MB
/** Beyond this a listing photo is wasteful; the client downscales first. */
const WARN_BYTES = 900 * 1024;
const MAX_PER_PRODUCT = 8;

type Sniffed = { mime: string; ext: string; width: number | null; height: number | null };

/**
 * Identify an image from its magic bytes and read its real dimensions.
 * Returns null when the bytes are not an image we accept.
 */
export function sniffImage(b: Buffer): Sniffed | null {
  // ── PNG: 89 50 4E 47 0D 0A 1A 0A, IHDR width/height at bytes 16..24
  if (b.length > 24 && b[0] === 0x89 && b[1] === 0x50 && b[2] === 0x4e && b[3] === 0x47) {
    return { mime: 'image/png', ext: 'png', width: b.readUInt32BE(16), height: b.readUInt32BE(20) };
  }
  // ── GIF: "GIF87a" / "GIF89a", little-endian dimensions at 6..10
  if (b.length > 10 && b.toString('latin1', 0, 3) === 'GIF') {
    return { mime: 'image/gif', ext: 'gif', width: b.readUInt16LE(6), height: b.readUInt16LE(8) };
  }
  // ── WEBP: "RIFF"…"WEBP"
  if (b.length > 30 && b.toString('latin1', 0, 4) === 'RIFF' && b.toString('latin1', 8, 12) === 'WEBP') {
    const fmt = b.toString('latin1', 12, 16);
    let width: number | null = null;
    let height: number | null = null;
    if (fmt === 'VP8X') {
      width = 1 + ((b[24] | (b[25] << 8) | (b[26] << 16)) & 0xffffff);
      height = 1 + ((b[27] | (b[28] << 8) | (b[29] << 16)) & 0xffffff);
    } else if (fmt === 'VP8L' && b.length > 25) {
      const bits = b.readUInt32LE(21);
      width = (bits & 0x3fff) + 1;
      height = ((bits >> 14) & 0x3fff) + 1;
    } else if (fmt === 'VP8 ' && b.length > 30) {
      width = b.readUInt16LE(26) & 0x3fff;
      height = b.readUInt16LE(28) & 0x3fff;
    }
    return { mime: 'image/webp', ext: 'webp', width, height };
  }
  // ── JPEG: FF D8, walk the segment chain to the SOFn frame header.
  if (b.length > 4 && b[0] === 0xff && b[1] === 0xd8) {
    let o = 2;
    while (o + 9 < b.length) {
      if (b[o] !== 0xff) { o++; continue; }
      const marker = b[o + 1];
      // Standalone markers carry no length.
      if (marker === 0xd8 || marker === 0x01 || (marker >= 0xd0 && marker <= 0xd7)) { o += 2; continue; }
      const len = b.readUInt16BE(o + 2);
      // SOF0-SOF15 except the DHT/JPG/DAC markers = the frame header.
      const isSof = marker >= 0xc0 && marker <= 0xcf && marker !== 0xc4 && marker !== 0xc8 && marker !== 0xcc;
      if (isSof) {
        return { mime: 'image/jpeg', ext: 'jpg', height: b.readUInt16BE(o + 5), width: b.readUInt16BE(o + 7) };
      }
      if (len <= 0) break;
      o += 2 + len;
    }
    return { mime: 'image/jpeg', ext: 'jpg', width: null, height: null };
  }
  return null;
}

export default async function registerImageUploadRoute(app: FastifyInstance) {
  const pool = getPool();

  app.post('/api/v1/uploads/images', { preHandler: requireAuth }, async (request, reply) => {
    const me = authedUser(request);

    let file;
    try {
      file = await request.file();
    } catch (err) {
      // @fastify/multipart throws when the part exceeds the configured limit.
      const msg = err instanceof Error ? err.message : String(err);
      if (/file too large|request file too large/i.test(msg)) {
        return reply.code(413).send({ error: 'Image too large — maximum 3 MB' });
      }
      return reply.code(400).send({ error: 'Expected a multipart upload with an "image" field' });
    }
    if (!file) return reply.code(400).send({ error: 'No file uploaded (field "image")' });

    let buffer: Buffer;
    try {
      buffer = await file.toBuffer();
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err);
      if (/file too large|reached/i.test(msg)) {
        return reply.code(413).send({ error: 'Image too large — maximum 3 MB' });
      }
      throw err;
    }

    if (buffer.length === 0) return reply.code(400).send({ error: 'The uploaded file is empty' });
    if (buffer.length > MAX_BYTES) return reply.code(413).send({ error: 'Image too large — maximum 3 MB' });

    // Trust the bytes, not the declared type.
    const sniffed = sniffImage(buffer);
    if (!sniffed) {
      return reply.code(400).send({
        error: 'That file is not a JPEG, PNG, WEBP or GIF image',
      });
    }
    if (sniffed.width !== null && (sniffed.width < 16 || sniffed.height! < 16)) {
      return reply.code(400).send({ error: 'That image is too small to use as a product photo' });
    }

    const sha = createHash('sha256').update(buffer).digest('hex');
    const purpose = typeof (file.fields?.purpose as { value?: string } | undefined)?.value === 'string'
      ? String((file.fields!.purpose as { value: string }).value).slice(0, 32)
      : 'product';

    // ── Firebase Storage, when it is configured ──────────────────────────────
    if (firebaseReady()) {
      try {
        const bucket = getStorage(getFirebaseApp()).bucket();
        const name = `product-images/${me.id}/${sha.slice(0, 32)}.${sniffed.ext}`;
        const blob = bucket.file(name);
        await blob.save(buffer, { contentType: sniffed.mime, metadata: { metadata: { uploader: me.id } } });
        await blob.makePublic().catch(() => undefined);
        return {
          url: `https://storage.googleapis.com/${bucket.name}/${name}`,
          width: sniffed.width,
          height: sniffed.height,
          bytes: buffer.length,
          storage: 'firebase',
        };
      } catch {
        // Fall through to the database — an upload must never just fail.
      }
    }

    // ── Postgres fallback (always available) ────────────────────────────────
    const saved = await pool.query<{ id: string }>(
      `INSERT INTO uploaded_images (owner_id, sha256, mime_type, byte_size, width, height, data, purpose)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8)
       ON CONFLICT (owner_id, sha256) DO UPDATE SET created_at = now()
       RETURNING id`,
      [me.id, sha, sniffed.mime, buffer.length, sniffed.width, sniffed.height, buffer, purpose]
    );

    return {
      url: `/api/v1/uploads/images/${saved.rows[0].id}`,
      id: saved.rows[0].id,
      width: sniffed.width,
      height: sniffed.height,
      bytes: buffer.length,
      oversized: buffer.length > WARN_BYTES,
      storage: 'database',
    };
  });

  // Public: a product photo has to load for buyers who are not signed in.
  app.get('/api/v1/uploads/images/:id', async (request, reply) => {
    const { id } = request.params as { id: string };
    if (!/^[0-9a-f-]{36}$/i.test(id)) throw new NotFoundError('Image not found');

    const r = await pool.query<{ data: Buffer; mime_type: string; sha256: string }>(
      'SELECT data, mime_type, sha256 FROM uploaded_images WHERE id = $1',
      [id]
    );
    const row = r.rows[0];
    if (!row) throw new NotFoundError('Image not found');

    // Content-addressed: the bytes for an id never change.
    const etag = `"${row.sha256.slice(0, 32)}"`;
    if (request.headers['if-none-match'] === etag) return reply.code(304).send();

    return reply
      .header('content-type', row.mime_type)
      .header('cache-control', 'public, max-age=31536000, immutable')
      .header('etag', etag)
      .header('content-length', String(row.data.length))
      .send(row.data);
  });

  app.get('/api/v1/me/uploads', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const r = await pool.query(
      `SELECT id, mime_type AS "mimeType", byte_size AS "byteSize", width, height,
              purpose, created_at AS "createdAt"
         FROM uploaded_images
        WHERE owner_id = $1
        ORDER BY created_at DESC
        LIMIT 60`,
      [me.id]
    );
    return {
      images: r.rows.map((row) => ({ ...row, url: `/api/v1/uploads/images/${row.id}` })),
      maxPerProduct: MAX_PER_PRODUCT,
    };
  });

  app.delete('/api/v1/uploads/images/:id', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const { id } = request.params as { id: string };
    if (!/^[0-9a-f-]{36}$/i.test(id)) throw new NotFoundError('Image not found');
    const r = await pool.query(
      'DELETE FROM uploaded_images WHERE id = $1 AND owner_id = $2 RETURNING id',
      [id, me.id]
    );
    if (!r.rows[0]) throw new NotFoundError('Image not found');
    return { deleted: true, id };
  });
}
