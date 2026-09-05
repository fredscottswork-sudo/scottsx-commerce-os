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
import { tokenForUser, requireAuth, authedUser, markVerified } from '../../auth.js';
import { verifyIdToken, sendVerificationEmailLink, firebaseEmailVerified } from '../../firebase/admin.js';
import { ConflictError, ForbiddenError, NotFoundError, UnauthorizedError } from '../../errors.js';
import { publicUser } from './login.route.js';

const signInSchema = z.object({
  idToken: z.string().min(10),
  // Optional profile, supplied by the registration form. These are applied
  // only when the row is first created — a later sign-in can never use them
  // to overwrite an existing profile, and `role` is deliberately constrained
  // so nobody can register straight into 'admin'.
  displayName: z.string().trim().max(120).optional(),
  phone: z.string().trim().max(40).optional(),
  role: z.enum(['buyer', 'seller']).optional(),
  storeName: z.string().trim().max(160).optional(),
});
type SignInBody = z.infer<typeof signInSchema>;

/** Wrap Firebase verification failures in a clean 401 for the client. */
function verificationError(err: unknown): never {
  const message = err instanceof Error ? err.message : String(err);
  throw new UnauthorizedError(`Firebase token invalid: ${message.slice(0, 160)}`);
}

export default async function registerFirebaseAuthRoute(app: FastifyInstance) {
  const pool = getPool();

  /** Upsert a user row from a verified Firebase token. */
  async function upsertFromFirebase(decoded: Record<string, any>, profile: Partial<SignInBody> = {}) {
    const uid = String(decoded.uid);
    const email = String(decoded.email ?? '').toLowerCase();
    // `email` is UNIQUE, so an identity with no address would insert '' and
    // collide with the next one. Every provider we enable supplies an email.
    if (!email) throw new UnauthorizedError('That sign-in method did not provide an email address');
    // Only what Firebase actually told us. Deriving a name from the email
    // prefix here would defeat the COALESCE(NULLIF(...)) guards below: the
    // derived value is never empty, so it would overwrite the stored display
    // name on every single sign-in. The prefix is used only as a last resort
    // when creating a brand-new row.
    const name = String(decoded.name ?? '');
    const fallbackName = email.split('@')[0] || 'ScottsTechX user';
    const photo = decoded.picture ? String(decoded.picture) : null;

    const existing = await pool.query('SELECT * FROM users WHERE firebase_uid = $1', [uid]);
    let user = existing.rows[0];
    // The ID token carries the authoritative email_verified claim.
    const emailVerified = decoded.email_verified === true;

    if (user) {
      if (user.email !== email) {
        // The address on this Firebase identity changed. Overwriting blindly
        // hits the UNIQUE(email) index and surfaces as a raw 500 "duplicate
        // key" — refuse cleanly when the address now belongs to someone else.
        const taken = await pool.query('SELECT 1 FROM users WHERE email = $1 AND id <> $2', [email, user.id]);
        if (taken.rowCount) {
          throw new ConflictError('That email address already belongs to another ScottsTechX account');
        }
      }
      const { rows } = await pool.query(
        `UPDATE users
         SET email = COALESCE(NULLIF($2,''), email),
             display_name = COALESCE(NULLIF($3,''), display_name),
             profile_photo_url = COALESCE($4, profile_photo_url),
             -- Verification only ever moves forward. Assigning the claim
             -- directly would let a stale token un-verify someone who already
             -- confirmed their address through our own code flow.
             email_verified = users.email_verified OR $5,
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
               profile_photo_url = COALESCE($4, profile_photo_url),
               email_verified = users.email_verified OR $5,
               updated_at = now()
           WHERE id = $1 RETURNING *`,
          [byEmail.rows[0].id, uid, name, photo, emailVerified]
        );
        user = rows[0];
      } else {
        const { rows } = await pool.query(
          `INSERT INTO users (email, display_name, profile_photo_url, firebase_uid, email_verified, role, phone, role_chosen)
           VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
           RETURNING *`,
          [
            email,
            profile.displayName || name || fallbackName,
            photo,
            uid,
            emailVerified,
            profile.role === 'seller' ? 'seller' : 'buyer',
            profile.phone || '',
            // Google popup with no role in the profile → onboarding picks it.
            !!profile.role,
          ]
        );
        user = rows[0];

        // A seller needs a storefront row to exist before their dashboard works.
        if (rows[0]?.role === 'seller') {
          await pool.query(
            `INSERT INTO store_settings (user_id, store_name, city, address)
             VALUES ($1, $2, '', '')
             ON CONFLICT (user_id) DO NOTHING`,
            [rows[0].id, profile.storeName || profile.displayName || 'My Store']
          );
        }
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
    const user = await upsertFromFirebase(decoded, body);
    // A user who verified via the emailed link arrives here with the claim
    // already true; record it so the gate opens on the next request.
    if (user.email_verified === true) markVerified(user.id);
    const token = await tokenForUser(user);
    return reply.code(200).send({
      token,
      user: publicUser(user),
      // Lets the client decide whether to show "check your inbox" without
      // having to re-read the token itself.
      emailVerified: user.email_verified === true,
      needsOnboarding: !user.role_chosen,
    });
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
