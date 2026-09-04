/**
 * ScottsTechX — local email/password auth (legacy path, kept alongside
 * the Firebase-first flow).
 *
 *   POST /api/v1/auth/register
 *   POST /api/v1/auth/login
 *   GET  /api/v1/auth/me
 *   PATCH /api/v1/auth/me
 *   PATCH /api/v1/me/location
 */
import type { FastifyInstance } from 'fastify';
import { z } from 'zod';
import { getPool } from '../../db.js';
import {
  hashPassword,
  comparePassword,
  tokenForUser,
  requireAuth,
  authedUser,
} from '../../auth.js';
import {
  UnauthorizedError,
  ConflictError,
  NotFoundError,
  ServiceUnavailableError,
} from '../../errors.js';
import { issueVerification } from './verify.route.js';
import { verificationUndeliverable } from '../../mail.js';

const registerSchema = z.object({
  email: z.string().email(),
  password: z.string().min(6),
  displayName: z.string().optional().default(''),
  phone: z.string().optional().default(''),
  role: z.enum(['buyer', 'seller']).optional().default('buyer'),
  storeName: z.string().optional().default(''),
  storeDescription: z.string().optional().default(''),
  city: z.string().optional().default(''),
});

// The identifier field accepts an email address OR a phone number: many of
// our shoppers know their number but not their address by heart.
const loginSchema = z.object({
  email: z.string().min(1),
  password: z.string().min(1),
});

const updateMeSchema = z.object({
  displayName: z.string().optional(),
  phone: z.string().optional(),
  profilePhotoUrl: z.string().url().optional().nullable(),
  city: z.string().max(120).optional(),
});

const locationSchema = z.object({
  lat: z.number().min(-90).max(90).optional(),
  lng: z.number().min(-180).max(180).optional(),
  city: z.string().optional(),
});

export function publicUser(row: any) {
  return {
    id: row.id,
    email: row.email,
    displayName: row.display_name ?? '',
    phone: row.phone ?? '',
    role: row.role,
    emailVerified: !!row.email_verified,
    firebaseUid: row.firebase_uid ?? null,
    profilePhotoUrl: row.profile_photo_url ?? null,
    city: row.city ?? '',
    createdAt: row.created_at,
  };
}

export default async function registerAuthRoute(app: FastifyInstance) {
  const pool = getPool();

  app.post('/api/v1/auth/register', async (request, reply) => {
    const body = registerSchema.parse(request.body);
    const existing = await pool.query('SELECT id FROM users WHERE email = $1', [body.email]);
    if ((existing.rowCount ?? 0) > 0) throw new ConflictError('Email already registered');

    // Refuse to create an account we could never verify. Previously a server
    // with no mailer answered by returning the code in the response body,
    // which meant anyone could "verify" an address they cannot read - the
    // exact opposite of what sign-up is for. Failing here is the honest
    // outcome: it tells the operator to configure SMTP instead of silently
    // handing out verified accounts.
    if (verificationUndeliverable()) {
      throw new ServiceUnavailableError(
        'Sign-up is temporarily unavailable: this server cannot send verification emails yet. ' +
          'Please try again later, or continue with Google.'
      );
    }

    const hash = await hashPassword(body.password);
    const { rows } = await pool.query(
      // email_verified is FALSE on purpose. It used to be hardcoded true, so
      // any string that merely parsed as an address became a full account and
      // nothing stopped fake@nowhere.invalid from signing up. A code is mailed
      // immediately after this insert and the flag is set only once the user
      // proves they can read that inbox.
      `INSERT INTO users (email, password_hash, display_name, phone, role, city, email_verified)
       VALUES ($1, $2, $3, $4, $5, $6, false)
       RETURNING *`,
      [body.email, hash, body.displayName, body.phone, body.role, body.city]
    );
    const user = rows[0];

    if (body.role === 'seller') {
      await pool.query(
        `INSERT INTO store_settings (user_id, store_name, store_description, city, address)
         VALUES ($1, $2, $3, $4, $5)
         ON CONFLICT (user_id) DO NOTHING`,
        [user.id, body.storeName || body.displayName || 'My Store', body.storeDescription, body.city, '']
      );
    }

    // Mail the verification code. The account exists and is usable for
    // browsing, but email_verified stays false until the code is confirmed —
    // and selling already requires a verified address.
    const issued = await issueVerification(user.id, user.email, user.display_name);

    const token = await tokenForUser(user);
    return reply.code(201).send({
      token,
      user: publicUser(user),
      verification: {
        required: true,
        sent: issued.delivered,
        // Whether the email actually contained a clickable link. False means
        // PUBLIC_WEB_URL is unset and only a code could be sent.
        linkSent: issued.linkSent,
        // Only present when no SMTP is configured (local/dev), so the flow can
        // still be completed. With a real mailer these are undefined.
        devCode: issued.devCode,
        devLink: issued.devLink,
      },
    });
  });

  app.post('/api/v1/auth/login', async (request, reply) => {
    const body = loginSchema.parse(request.body);
    // Email match first; if the identifier looks like a phone (digits, with
    // optional + and separators), match on the store's phone as well, so
    // "+256 700 000 000" finds a user registered as "+256700000000".
    const phoneDigits = (body.email.match(/\+?\d[\d\s\-()]{5,}/) || [null])[0];
    const { rows } = await pool.query(
      'SELECT * FROM users WHERE email = $1 OR ($2::text IS NOT NULL AND regexp_replace(phone, \'[^0-9]\', \'\', \'g\') = $2)',
      [body.email, phoneDigits ? phoneDigits.replace(/\D/g, '') : null]
    );
    const user = rows[0];
    if (!user || !user.password_hash) throw new UnauthorizedError('Invalid email or password');
    const ok = await comparePassword(body.password, user.password_hash);
    if (!ok) throw new UnauthorizedError('Invalid email or password');
    const token = await tokenForUser(user);
    return { token, user: publicUser(user) };
  });

  app.get('/api/v1/auth/me', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const { rows } = await pool.query('SELECT * FROM users WHERE id = $1', [me.id]);
    if (!rows[0]) throw new NotFoundError('User not found');
    return { user: publicUser(rows[0]) };
  });

  app.patch('/api/v1/auth/me', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const body = updateMeSchema.parse(request.body);
    const { rows } = await pool.query(
      `UPDATE users
       SET display_name = COALESCE($2, display_name),
           phone = COALESCE($3, phone),
           profile_photo_url = COALESCE($4, profile_photo_url),
           city = COALESCE($5, city),
           updated_at = now()
       WHERE id = $1
       RETURNING *`,
      [me.id, body.displayName ?? null, body.phone ?? null, body.profilePhotoUrl ?? null, body.city ?? null]
    );
    return { user: publicUser(rows[0]) };
  });

  /**
   * Local upgrade path (no Firebase needed): any authenticated, email-verified
   * account may become a seller. Re-mints a JWT with role=seller.
   */
  app.post('/api/v1/auth/upgrade-to-seller', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const { rows } = await pool.query('SELECT * FROM users WHERE id = $1', [me.id]);
    const user = rows[0];
    if (!user) throw new NotFoundError('User not found');
    if (!user.email_verified) throw new UnauthorizedError('Email must be verified to become a seller');
    const updated = await pool.query(
      `UPDATE users SET role = 'seller', updated_at = now() WHERE id = $1 RETURNING *`,
      [me.id]
    );
    await pool.query(
      `INSERT INTO store_settings (user_id, store_name)
       VALUES ($1, $2) ON CONFLICT (user_id) DO NOTHING`,
      [me.id, user.display_name || 'My Store']
    );
    const token = await tokenForUser(updated.rows[0]);
    return { token, user: publicUser(updated.rows[0]) };
  });

  app.patch('/api/v1/me/location', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const body = locationSchema.parse(request.body);
    const { rows } = await pool.query(
      `UPDATE users
       SET lat = COALESCE($2, lat),
           lng = COALESCE($3, lng),
           city = COALESCE($4, city),
           updated_at = now()
       WHERE id = $1
       RETURNING *`,
      [me.id, body.lat ?? null, body.lng ?? null, body.city ?? null]
    );
    return { user: publicUser(rows[0]) };
  });
}
