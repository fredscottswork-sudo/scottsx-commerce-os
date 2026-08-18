/**
 * ScottsTechX — Google Sign-In bridge.
 *
 *   POST /api/v1/auth/google   { idToken }  ->  { token, user }
 *
 * The idToken is a Google-issued JWT. We verify it *cryptographically* against
 * Google's published JWKS rather than calling the deprecated /tokeninfo
 * endpoint: tokeninfo costs a network round-trip per login, is rate-limited,
 * and returns 200 with string-typed claims that are easy to mis-compare.
 *
 * Checks performed (all mandatory):
 *   - RS256 signature against https://www.googleapis.com/oauth2/v3/certs
 *   - iss  is accounts.google.com (with or without the https:// prefix)
 *   - aud  is one of our own OAuth client IDs  <- stops "token from another app"
 *   - exp / iat validity, with a small clock skew allowance
 *   - email present and email_verified true
 *
 * Env overrides (all optional):
 *   GOOGLE_CLIENT_ID   comma-separated list of accepted audiences
 *   GOOGLE_JWKS_URL    alternate JWKS endpoint (used by the offline test suite)
 *   GOOGLE_ISSUER      alternate issuer     (used by the offline test suite)
 */
import type { FastifyInstance } from 'fastify';
import { z } from 'zod';
import { createRemoteJWKSet, jwtVerify, type JWTPayload } from 'jose';
import { getPool } from '../../db.js';
import { tokenForUser } from '../../auth.js';
import { publicUser } from './login.route.js';
import { ConflictError, ServiceUnavailableError, UnauthorizedError } from '../../errors.js';

const googleSchema = z.object({ idToken: z.string().min(10) });

/** OAuth client IDs from scottsx-android/app/google-services.json (project scottstechx-52bab). */
const DEFAULT_AUDIENCES = [
  '911393008938-f0an8p59rlkhimcnn9rdqbtbi1aa9hbk.apps.googleusercontent.com', // web / "other"
  '911393008938-391gln1bdhc8bhte02gmoegr6vgkicsm.apps.googleusercontent.com', // android
];

function acceptedAudiences(): string[] {
  const fromEnv = (process.env.GOOGLE_CLIENT_ID || '')
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean);
  return fromEnv.length ? fromEnv : DEFAULT_AUDIENCES;
}

function acceptedIssuers(): string[] {
  const override = (process.env.GOOGLE_ISSUER || '').trim();
  if (override) return [override];
  return ['https://accounts.google.com', 'accounts.google.com'];
}

const JWKS_URL = () => process.env.GOOGLE_JWKS_URL || 'https://www.googleapis.com/oauth2/v3/certs';

/**
 * Cached JWKS. jose handles the cache-control/refresh dance and will re-fetch
 * when it sees an unknown `kid`, so Google's key rotation is transparent.
 * Keyed by URL so a test can point at a local JWKS without poisoning the cache.
 */
const jwksCache = new Map<string, ReturnType<typeof createRemoteJWKSet>>();
function jwks() {
  const url = JWKS_URL();
  let set = jwksCache.get(url);
  if (!set) {
    set = createRemoteJWKSet(new URL(url), { cooldownDuration: 30_000, timeoutDuration: 8_000 });
    jwksCache.set(url, set);
  }
  return set;
}

/** True when the failure is "we could not reach Google", not "the token is bad". */
function isNetworkFailure(err: unknown): boolean {
  const e = err as { code?: string; name?: string; message?: string };
  if (e?.code === 'ERR_JWKS_TIMEOUT' || e?.name === 'JWKSTimeout') return true;
  const msg = String(e?.message || '');
  return /fetch failed|ENOTFOUND|ECONNREFUSED|EAI_AGAIN|network|socket hang up|timed? ?out/i.test(msg);
}

export async function verifyGoogleIdToken(idToken: string): Promise<JWTPayload> {
  let payload: JWTPayload;
  try {
    ({ payload } = await jwtVerify(idToken, jwks(), {
      issuer: acceptedIssuers(),
      audience: acceptedAudiences(),
      algorithms: ['RS256'],
      clockTolerance: 60,
    }));
  } catch (err) {
    if (isNetworkFailure(err)) {
      throw new ServiceUnavailableError('Could not reach Google to verify the sign-in. Try again.');
    }
    const reason = String((err as Error)?.message || 'invalid token');
    throw new UnauthorizedError(`Google sign-in rejected: ${reason.slice(0, 160)}`);
  }

  if (!payload.email) throw new UnauthorizedError('Google account has no email address');
  // Google sends this as a real boolean, but be liberal about the string form.
  const verified = payload.email_verified;
  if (verified !== true && verified !== 'true') {
    throw new UnauthorizedError('Google email address is not verified');
  }
  if (!payload.sub) throw new UnauthorizedError('Google token has no subject');
  return payload;
}

export default async function registerGoogleRoute(app: FastifyInstance) {
  const pool = getPool();

  app.post('/api/v1/auth/google', async (request) => {
    const body = googleSchema.parse(request.body);
    const info = await verifyGoogleIdToken(body.idToken);

    const email = String(info.email).toLowerCase();
    const name = String((info.name as string) || email.split('@')[0] || '');
    const photo = info.picture ? String(info.picture) : null;
    const gid = String(info.sub);

    // `email` and `google_uid` are both UNIQUE, so the order matters.
    // 1. Known Google account -> that row wins, even if the address changed.
    const byUid = await pool.query('SELECT * FROM users WHERE google_uid = $1', [gid]);
    let user = byUid.rows[0];

    if (user) {
      if (user.email !== email) {
        // The address moved. Refuse only if it now belongs to someone else.
        const taken = await pool.query('SELECT 1 FROM users WHERE email = $1 AND id <> $2', [email, user.id]);
        if (taken.rowCount) {
          throw new ConflictError('That email address already belongs to another ScottsTechX account');
        }
      }
      const { rows } = await pool.query(
        `UPDATE users
            SET email             = $2,
                display_name      = COALESCE(NULLIF(display_name, ''), $3),
                profile_photo_url = COALESCE(profile_photo_url, $4),
                email_verified    = true,
                updated_at        = now()
          WHERE id = $1 RETURNING *`,
        [user.id, email, name, photo]
      );
      user = rows[0];
    } else {
      // 2. New Google account. Insert, or adopt an existing password account
      //    with the same address so the two sign-in methods share one profile.
      const { rows } = await pool.query(
        `INSERT INTO users (email, display_name, profile_photo_url, google_uid, email_verified, role)
              VALUES ($1, $2, $3, $4, true, 'buyer')
         ON CONFLICT (email) DO UPDATE
              SET google_uid        = EXCLUDED.google_uid,
                  display_name      = COALESCE(NULLIF(users.display_name, ''), EXCLUDED.display_name),
                  profile_photo_url = COALESCE(users.profile_photo_url, EXCLUDED.profile_photo_url),
                  email_verified    = true,
                  updated_at        = now()
         RETURNING *`,
        [email, name, photo, gid]
      );
      user = rows[0];
    }

    const token = await tokenForUser(user);
    return { token, user: publicUser(user) };
  });
}
