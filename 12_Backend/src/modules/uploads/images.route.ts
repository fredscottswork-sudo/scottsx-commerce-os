/**
 * ScottsTechX — product image upload + serving (merged).
 *
 * This file merges two generations of the upload flow:
 *   - Generation 1 (master): stored_images table, key = {uuid}-{epoch}-{random}.{ext}
 *   - Generation 2 (stranded): uploaded_images table, UUID primary key, magic-byte sniffing,
 *     dimension reading, Firebase-first with Postgres fallback, dedup.
 *
 * Both tables are kept (migrations 0015 and 0018) and both serving paths work,
 * so a deployment that already has photos in either table keeps serving them.
 *
 *   POST /api/v1/uploads/images        (auth, multipart, field "image")
 *   GET  /api/v1/uploads/images/:idOrKey (public)
 *   GET  /api/v1/me/uploads
 *   DELETE /api/v1/uploads/images/:id
 */

import type { FastifyInstance } from 'fastify';
import { createHash, randomBytes } from 'node:crypto';
import { getPool } from '../../db.js';
import { requireAuth, authedUser } from '../../auth.js';
import { NotFoundError, ServiceUnavailableError } from '../../errors.js';
import { getFirebaseApp, firebaseReady } from '../../firebase/admin.js';
import { getStorage } from 'firebase-admin/storage';

const MAX_BYTES_NEW = 3 * 1024 * 1024; // 3 MB for the new sniffing path
const MAX_BYTES_OLD = 8 * 1024 * 1024; // 8 MB headroom for legacy path
const WARN_BYTES = 900 * 1024;
const MAX_PER_PRODUCT = 8;
const ALLOWED_OLD = new Set(['image/jpeg', 'image/png', 'image/webp']);

const KEY_RE_OLD = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}-\d+-[A-Za-z0-9_-]{10,40}\.(jpg|png|webp)$/;
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

type Sniffed = { mime: string; ext: string; width: number | null; height: number | null };

export function sniffImage(b: Buffer): Sniffed | null {
  if (b.length > 24 && b[0] === 0x89 && b[1] === 0x50 && b[2] === 0x4e && b[3] === 0x47) {
    return { mime: 'image/png', ext: 'png', width: b.readUInt32BE(16), height: b.readUInt32BE(20) };
  }
  if (b.length > 10 && b.toString('latin1', 0, 3) === 'GIF') {
    return { mime: 'image/gif', ext: 'gif', width: b.readUInt16LE(6), height: b.readUInt16LE(8) };
  }
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
  if (b.length > 4 && b[0] === 0xff && b[1] === 0xd8) {
    let o = 2;
    while (o + 9 < b.length) {
      if (b[o] !== 0xff) { o++; continue; }
      const marker = b[o + 1];
      if (marker === 0xd8 || marker === 0x01 || (marker >= 0xd0 && marker <= 0xd7)) { o += 2; continue; }
      const len = b.readUInt16BE(o + 2);
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

export default async function registerImagesRoute(app: FastifyInstance) {
  const pool = getPool();

  app.post('/api/v1/uploads/images', { preHandler: requireAuth }, async (request, reply) => {
    const me = authedUser(request);
    let file;
    try {
      file = await request.file();
    } catch (err) {
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
    if (buffer.length > MAX_BYTES_NEW && buffer.length > MAX_BYTES_OLD) {
      return reply.code(413).send({ error: 'Image too large — maximum 8 MB' });
    }

    const sniffed = sniffImage(buffer);
    if (!sniffed) {
      // Fall back to declared mimetype for legacy clients that send valid JPEG/PNG/WEBP
      // but our sniff missed (e.g. progressive JPEG with unusual markers).
      if (file.mimetype && ALLOWED_OLD.has(file.mimetype) && buffer.length <= MAX_BYTES_OLD) {
        const ext = file.mimetype === 'image/jpeg' ? 'jpg' : file.mimetype === 'image/png' ? 'png' : 'webp';
        const key = `${me.id}-${Date.now()}-${randomBytes(8).toString('base64url')}.${ext}`;
        if (firebaseReady()) {
          const bucket = getStorage(getFirebaseApp()).bucket();
          const name = `product-images/${key}`;
          const blob = bucket.file(name);
          try {
            await blob.save(buffer, { contentType: file.mimetype, metadata: { metadata: { uploader: me.id } } });
            await blob.makePublic().catch(() => undefined);
            return { url: `https://storage.googleapis.com/${bucket.name}/${name}` };
          } catch (err) {
            const msg = err instanceof Error ? err.message : String(err);
            if (/bucket does not exist/i.test(msg)) {
              throw new ServiceUnavailableError('Image storage is not provisioned yet — retry once enabled.');
            }
            throw new ServiceUnavailableError(`Image upload failed: ${msg.slice(0, 160)}`);
          }
        }
        await pool.query(
          `INSERT INTO stored_images (key, data, content_type, uploader_id) VALUES ($1, $2, $3, $4)`,
          [key, buffer, file.mimetype, me.id]
        );
        return { url: `/api/v1/uploads/images/${key}` };
      }
      return reply.code(400).send({ error: 'That file is not a JPEG, PNG, WEBP or GIF image' });
    }

    if (sniffed.width !== null && (sniffed.width < 16 || sniffed.height! < 16)) {
      return reply.code(400).send({ error: 'That image is too small to use as a product photo' });
    }

    const sha = createHash('sha256').update(buffer).digest('hex');
    const purpose = typeof (file.fields?.purpose as { value?: string } | undefined)?.value === 'string'
      ? String((file.fields!.purpose as { value: string }).value).slice(0, 32)
      : 'product';

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
        // fall through to Postgres
      }
    }

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

  // Public: serves from either table depending on the param shape.
  app.get('/api/v1/uploads/images/:id', async (request, reply) => {
    const raw = String((request.params as any).id);

    // New path: UUID -> uploaded_images
    if (UUID_RE.test(raw)) {
      const r = await pool.query<{ data: Buffer; mime_type: string; sha256: string }>(
        'SELECT data, mime_type, sha256 FROM uploaded_images WHERE id = $1',
        [raw]
      );
      const row = r.rows[0];
      if (!row) throw new NotFoundError('Image not found');
      const etag = `"${row.sha256.slice(0, 32)}"`;
      if (request.headers['if-none-match'] === etag) return reply.code(304).send();
      return reply
        .header('content-type', row.mime_type)
        .header('cache-control', 'public, max-age=31536000, immutable')
        .header('etag', etag)
        .header('content-length', String(row.data.length))
        .send(row.data);
    }

    // Old path: key -> stored_images
    if (KEY_RE_OLD.test(raw)) {
      const { rows } = await pool.query(
        'SELECT data, content_type FROM stored_images WHERE key = $1',
        [raw]
      );
      if (!rows[0]) return reply.code(404).send({ error: 'Not found' });
      reply.header('cache-control', 'public, max-age=31536000, immutable');
      return reply.type(rows[0].content_type).send(rows[0].data);
    }

    throw new NotFoundError('Image not found');
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
      images: r.rows.map((row: any) => ({ ...row, url: `/api/v1/uploads/images/${row.id}` })),
      maxPerProduct: MAX_PER_PRODUCT,
    };
  });

  app.delete('/api/v1/uploads/images/:id', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const { id } = request.params as { id: string };
    if (!UUID_RE.test(id)) throw new NotFoundError('Image not found');
    const r = await pool.query(
      'DELETE FROM uploaded_images WHERE id = $1 AND owner_id = $2 RETURNING id',
      [id, me.id]
    );
    if (!r.rows[0]) throw new NotFoundError('Image not found');
    return { deleted: true, id };
  });
}
