/**
 * ScottsTechX — product image upload + serving.
 *
 *   POST /api/v1/uploads/images        (auth, multipart, field "image")
 *   GET  /api/v1/uploads/images/:key   (public — buyers see photos signed-out)
 *
 * The POST is the endpoint the Android Add-product flow has always called;
 * until this file existed that call 404'd and sellers could not publish a
 * photo, so "Add product" was a dead end on a phone.
 *
 * Storage:
 *   - Firebase Storage when the admin SDK is configured (same bucket as
 *     profile photos), returned as an absolute public URL;
 *   - otherwise the Postgres `stored_images` table, returned as an
 *     API-relative path ("/api/v1/uploads/images/<key>") so the same row
 *     works on localhost, a preview host and production without baking an
 *     origin into the database.
 *
 * The GET is public by design: product photos are catalogue content. What
 * keeps it from leaking anything else is the key — user id + timestamp +
 * 8 random bytes — which is unguessable, and the key regex, which accepts
 * nothing else.
 */
import type { FastifyInstance } from 'fastify';
import { randomBytes } from 'node:crypto';
import { getPool } from '../../db.js';
import { requireAuth, authedUser } from '../../auth.js';
import { ServiceUnavailableError } from '../../errors.js';
import { getFirebaseApp, firebaseReady } from '../../firebase/admin.js';
import { getStorage } from 'firebase-admin/storage';

const MAX_BYTES = 8 * 1024 * 1024; // 8 MB — the client compresses to ~3 MB, this is headroom
const ALLOWED = new Set(['image/jpeg', 'image/png', 'image/webp']);

// Key shape: {uuid}-{epoch}-{base64url}.jpg|png|webp
const KEY_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}-\d+-[A-Za-z0-9_-]{10,40}\.(jpg|png|webp)$/;

export default async function registerImagesRoute(app: FastifyInstance) {
  const pool = getPool();

  app.post('/api/v1/uploads/images', { preHandler: requireAuth }, async (request, reply) => {
    const me = authedUser(request);
    const file = await request.file();
    if (!file) return reply.code(400).send({ error: 'No file uploaded (field "image")' });
    if (!ALLOWED.has(file.mimetype)) {
      return reply.code(400).send({ error: `Unsupported type ${file.mimetype} — use JPEG, PNG or WEBP` });
    }
    const buffer = await file.toBuffer();
    if (buffer.length === 0) return reply.code(400).send({ error: 'The file is empty' });
    if (buffer.length > MAX_BYTES) {
      return reply.code(400).send({ error: 'Image too large — max 8 MB (the app compresses before upload; a raw photo larger than that was not compressed)' });
    }

    const ext = file.mimetype === 'image/jpeg' ? 'jpg' : file.mimetype === 'image/png' ? 'png' : 'webp';
    const key = `${me.id}-${Date.now()}-${randomBytes(8).toString('base64url')}.${ext}`;

    if (firebaseReady()) {
      const bucket = getStorage(getFirebaseApp()).bucket();
      const name = `product-images/${key}`;
      const blob = bucket.file(name);
      try {
        await blob.save(buffer, {
          contentType: file.mimetype,
          metadata: { metadata: { uploader: me.id } },
        });
      } catch (err) {
        const msg = err instanceof Error ? err.message : String(err);
        if (/bucket does not exist/i.test(msg)) {
          throw new ServiceUnavailableError(
            'Image storage is not provisioned on this deployment yet — retry once it is enabled.'
          );
        }
        throw new ServiceUnavailableError(`Image upload failed: ${msg.slice(0, 160)}`);
      }
      await blob.makePublic().catch(() => undefined);
      return { url: `https://storage.googleapis.com/${bucket.name}/${name}` };
    }

    await pool.query(
      `INSERT INTO stored_images (key, data, content_type, uploader_id) VALUES ($1, $2, $3, $4)`,
      [key, buffer, file.mimetype, me.id]
    );
    return { url: `/api/v1/uploads/images/${key}` };
  });

  app.get('/api/v1/uploads/images/:key', async (request, reply) => {
    const key = String((request.params as any).key);
    if (!KEY_RE.test(key)) return reply.code(404).send({ error: 'Not found' });

    const { rows } = await pool.query(
      'SELECT data, content_type FROM stored_images WHERE key = $1',
      [key]
    );
    if (!rows[0]) return reply.code(404).send({ error: 'Not found' });

    reply.header('cache-control', 'public, max-age=31536000, immutable');
    return reply.type(rows[0].content_type).send(rows[0].data);
  });
}
