/**
 * ScottsTechX — profile photo upload.
 *
 *   POST /api/v1/me/photo   (auth, multipart, field "photo")
 *
 * Stores the image in Firebase Storage under profile-photos/{userId}-{ts}.jpg
 * and updates users.profile_photo_url. Falls back to a clear 503 when
 * Firebase storage isn't configured (the web app also offers a URL input,
 * which is what the mobile app uses).
 */
import type { FastifyInstance } from 'fastify';
import { getPool } from '../../db.js';
import { requireAuth, authedUser } from '../../auth.js';
import { ServiceUnavailableError } from '../../errors.js';
import { getFirebaseApp, firebaseReady } from '../../firebase/admin.js';
import { getStorage } from 'firebase-admin/storage';

const MAX_BYTES = 2 * 1024 * 1024; // 2 MB
const ALLOWED = new Set(['image/jpeg', 'image/png', 'image/webp', 'image/gif']);

export default async function registerUploadsRoute(app: FastifyInstance) {
  const pool = getPool();

  app.post('/api/v1/me/photo', { preHandler: requireAuth }, async (request, reply) => {
    const me = authedUser(request);
    if (!firebaseReady()) {
      throw new ServiceUnavailableError(
        'Photo upload needs Firebase — configure the admin key (local) or deploy to Cloud Functions.'
      );
    }
    const file = await request.file();
    if (!file) return reply.code(400).send({ error: 'No file uploaded (field "photo")' });
    if (!ALLOWED.has(file.mimetype)) {
      return reply.code(400).send({ error: `Unsupported type ${file.mimetype} — use JPEG, PNG, WEBP or GIF` });
    }
    const buffer = await file.toBuffer();
    if (buffer.length > MAX_BYTES) {
      return reply.code(400).send({ error: 'Photo too large — max 2 MB' });
    }

    const bucket = getStorage(getFirebaseApp()).bucket();
    const ext = file.mimetype === 'image/jpeg' ? 'jpg' : file.mimetype === 'image/png' ? 'png' : file.mimetype === 'image/webp' ? 'webp' : 'gif';
    const name = `profile-photos/${me.id}-${Date.now()}.${ext}`;
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
          'Firebase Storage bucket is not provisioned — enable Cloud Storage in the Firebase console (or set FIREBASE_STORAGE_BUCKET). Until then, use the "paste image URL" option.'
        );
      }
      throw new ServiceUnavailableError(`Photo upload failed: ${msg.slice(0, 160)}`);
    }
    await blob.makePublic().catch(() => undefined);
    const url = `https://storage.googleapis.com/${bucket.name}/${name}`;

    await pool.query(
      `UPDATE users SET profile_photo_url = $2, updated_at = now() WHERE id = $1`,
      [me.id, url]
    );
    return { profilePhotoUrl: url };
  });
}
