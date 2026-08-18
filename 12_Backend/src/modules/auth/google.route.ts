/**
 * ScottsTechX — Google OAuth bridge.
 *
 *   POST /api/v1/auth/google   { idToken }
 *
 * Validates the Google idToken against Google's tokeninfo endpoint (no extra
 * dependency), then upserts the user and returns our own JWT.
 */
import type { FastifyInstance } from 'fastify';
import { z } from 'zod';
import { getPool } from '../../db.js';
import { tokenForUser } from '../../auth.js';
import { publicUser } from './login.route.js';

const googleSchema = z.object({ idToken: z.string().min(10) });

async function exchangeGoogleToken(idToken: string): Promise<Record<string, any>> {
  const url = `https://oauth2.googleapis.com/tokeninfo?id_token=${encodeURIComponent(idToken)}`;
  const res = await fetch(url);
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`Google token validation failed: ${text.slice(0, 200)}`);
  }
  return (await res.json()) as Record<string, any>;
}

export default async function registerGoogleRoute(app: FastifyInstance) {
  const pool = getPool();

  app.post('/api/v1/auth/google', async (request, reply) => {
    const body = googleSchema.parse(request.body);
    const info = await exchangeGoogleToken(body.idToken);

    const expectedAud = process.env.GOOGLE_CLIENT_ID;
    if (expectedAud && info.aud && info.aud !== expectedAud) {
      return reply.code(401).send({ error: 'Google token audience mismatch' });
    }
    if (!info.email) return reply.code(401).send({ error: 'Google token has no email' });

    const email = String(info.email);
    const name = String(info.name ?? info.email.split('@')[0] ?? '');
    const photo = info.picture ? String(info.picture) : null;
    const gid = String(info.sub);

    const existing = await pool.query('SELECT * FROM users WHERE google_uid = $1', [gid]);
    let user = existing.rows[0];
    if (user) {
      const { rows } = await pool.query(
        `UPDATE users SET email = COALESCE(NULLIF($2,''), email), display_name = COALESCE(NULLIF($3,''), display_name),
                profile_photo_url = COALESCE($4, profile_photo_url), email_verified = true, updated_at = now()
         WHERE id = $1 RETURNING *`,
        [user.id, email, name, photo]
      );
      user = rows[0];
    } else {
      const byEmail = await pool.query('SELECT * FROM users WHERE email = $1', [email]);
      if (byEmail.rows[0]) {
        const { rows } = await pool.query(
          `UPDATE users SET google_uid = $2, display_name = COALESCE(NULLIF($3,''), display_name),
                  profile_photo_url = COALESCE($4, profile_photo_url), email_verified = true, updated_at = now()
           WHERE id = $1 RETURNING *`,
          [byEmail.rows[0].id, gid, name, photo]
        );
        user = rows[0];
      } else {
        const { rows } = await pool.query(
          `INSERT INTO users (email, display_name, profile_photo_url, google_uid, email_verified, role)
           VALUES ($1, $2, $3, $4, true, 'buyer') RETURNING *`,
          [email, name, photo, gid]
        );
        user = rows[0];
      }
    }

    const token = await tokenForUser(user);
    return { token, user: publicUser(user) };
  });
}
