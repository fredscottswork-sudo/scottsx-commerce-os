/**
 * ScottsTechX — Firebase Auth bridge.
 *
 *   POST /api/v1/auth/firebase/sign-in                exchange idToken -> our JWT
 *   POST /api/v1/auth/firebase/send-verification-email
 *   GET  /api/v1/auth/firebase/me
 *   POST /api/v1/auth/firebase/upgrade-to-seller
 */
import type { FastifyInstance } from 'fastify';
import { z } from 'zod';
import { getPool } from '../../db.js';
import { tokenForUser, requireAuth, authedUser } from '../../auth.js';
import { verifyIdToken, sendVerificationEmailLink, firebaseEmailVerified } from '../../firebase/admin.js';
import { ForbiddenError, NotFoundError, UnauthorizedError } from '../../errors.js';
import { publicUser } from './login.route.js';

const signInSchema = z.object({ idToken: z.string().min(10) });

/** Wrap Firebase verification failures in a clean 401 for the client. */
function verificationError(err: unknown): never {
  const message = err instanceof Error ? err.message : String(err);
  throw new UnauthorizedError(`Firebase token invalid: ${message.slice(0, 160)}`);
}

export default async function registerFirebaseAuthRoute(app: FastifyInstance) {
  const pool = getPool();

  /** Upsert a user row from a verified Firebase token. */
  async function upsertFromFirebase(decoded: Record<string, any>) {
    const uid = String(decoded.uid);
    const email = String(decoded.email ?? '');
    const name = String(decoded.name ?? decoded.email?.split('@')[0] ?? '');
    const photo = decoded.picture ? String(decoded.picture) : null;

    const existing = await pool.query('SELECT * FROM users WHERE firebase_uid = $1', [uid]);
    let user = existing.rows[0];
    // The ID token carries the authoritative email_verified claim.
    const emailVerified = decoded.email_verified === true;

    if (user) {
      const { rows } = await pool.query(
        `UPDATE users
         SET email = COALESCE(NULLIF($2,''), email),
             display_name = COALESCE(NULLIF($3,''), display_name),
             profile_photo_url = COALESCE($4, profile_photo_url),
             email_verified = $5,
             updated_at = now()
         WHERE id = $1
         RETURNING *`,
        [user.id, email, name, photo, emailVerified]
      );
      user = rows[0];
    } else {
      const byEmail = await pool.query('SELECT * FROM users WHERE email = $1', [email]);
      if (byEmail.rows[0]) {
        const { rows } = await pool.query(
          `UPDATE users
           SET firebase_uid = $2, display_name = COALESCE(NULLIF($3,''), display_name),
               profile_photo_url = COALESCE($4, profile_photo_url), email_verified = $5,
               updated_at = now()
           WHERE id = $1 RETURNING *`,
          [byEmail.rows[0].id, uid, name, photo, emailVerified]
        );
        user = rows[0];
      } else {
        const { rows } = await pool.query(
          `INSERT INTO users (email, display_name, profile_photo_url, firebase_uid, email_verified, role)
           VALUES ($1, $2, $3, $4, $5, 'buyer')
           RETURNING *`,
          [email, name, photo, uid, emailVerified]
        );
        user = rows[0];
      }
    }
    return user;
  }

  app.post('/api/v1/auth/firebase/sign-in', async (request, reply) => {
    const body = signInSchema.parse(request.body);
    let decoded: Record<string, any>;
    try {
      decoded = await verifyIdToken(body.idToken);
    } catch (err) {
      verificationError(err);
    }
    const user = await upsertFromFirebase(decoded);
    const token = await tokenForUser(user);
    return reply.code(200).send({ token, user: publicUser(user) });
  });

  app.post('/api/v1/auth/firebase/send-verification-email', async (request) => {
    const body = signInSchema.parse(request.body);
    let result: { email: string; link: string };
    try {
      result = await sendVerificationEmailLink(body.idToken);
    } catch (err) {
      verificationError(err);
    }
    return { ok: true, email: result.email, message: 'Verification email sent (or link generated)', link: result.link };
  });

  app.get('/api/v1/auth/firebase/me', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const { rows } = await pool.query('SELECT * FROM users WHERE id = $1', [me.id]);
    if (!rows[0]) throw new NotFoundError('User not found');
    return { id: rows[0].id, email: rows[0].email, displayName: rows[0].display_name, role: rows[0].role, emailVerified: !!rows[0].email_verified, firebaseUid: rows[0].firebase_uid };
  });

  app.post('/api/v1/auth/firebase/upgrade-to-seller', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const { rows } = await pool.query('SELECT * FROM users WHERE id = $1', [me.id]);
    const user = rows[0];
    if (!user) throw new NotFoundError('User not found');

    // Authoritative check: for Firebase accounts, ask Firebase (email may have
    // been verified in the client after our last sign-in). Fall back to the
    // stored flag for local accounts.
    let emailVerified = !!user.email_verified;
    if (user.firebase_uid) {
      const fbVerified = await firebaseEmailVerified(user.firebase_uid);
      if (fbVerified !== null) emailVerified = fbVerified;
    }
    if (!emailVerified) throw new ForbiddenError('Email must be verified before becoming a seller');
    const updated = await pool.query(
      `UPDATE users SET role = 'seller', updated_at = now() WHERE id = $1 RETURNING *`,
      [me.id]
    );
    // Ensure a store_settings row exists.
    await pool.query(
      `INSERT INTO store_settings (user_id, store_name)
       VALUES ($1, $2)
       ON CONFLICT (user_id) DO NOTHING`,
      [me.id, user.display_name || 'My Store']
    );
    const token = await tokenForUser(updated.rows[0]);
    return { token, user: publicUser(updated.rows[0]) };
  });
}
