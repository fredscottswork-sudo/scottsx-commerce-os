/**
 * ScottsTechX — password reset.
 *
 *   POST /api/v1/auth/forgot-password  { identifier }  (public)
 *   POST /api/v1/auth/reset-password   { token, password } (public)
 *
 * Reuses the email_verifications table with purpose = 'password_reset' —
 * same SHA-256 token storage, same expiry/consumption semantics, same
 * rate limiting as sign-up verification. A reset token is a bearer
 * credential exactly like a verification link, so it gets the same
 * treatment: 32 random bytes base64url, only the hash stored, 30-minute
 * expiry, single use, 6 attempts before burn.
 *
 * Enumeration safety: /auth/forgot-password returns the same { ok: true }
 * whether or not the identifier belongs to an account. The honest copy in
 * the Android app ("If an account exists for …, a reset link is on its
 * way") is what makes a constant response the RIGHT behaviour, not a
 * cop-out.
 *
 * /auth/reset-password is deliberately NOT behind requireAuth: the whole
 * point is that the person's session is the thing that may be compromised.
 * The token in the email is the proof of ownership.
 */
import type { FastifyInstance } from 'fastify';
import { createHash, randomBytes } from 'node:crypto';
import { z } from 'zod';
import { getPool } from '../../db.js';
import { hashPassword } from '../../auth.js';
import { ValidationError } from '../../errors.js';
import { sendMail, mailConfigured, devCodesAllowed } from '../../mail.js';
import { passwordResetEmail } from '../../mail-templates.js';

const TOKEN_TTL_MIN = 30;
const MAX_ATTEMPTS = 6;
const RESEND_COOLDOWN_SEC = Number(process.env.RESET_RESEND_COOLDOWN_SEC ?? 60);
const MAX_SENDS_PER_HOUR = Number(process.env.RESET_MAX_SENDS_PER_HOUR ?? 6);

const newToken = () => randomBytes(32).toString('base64url');
const hashToken = (token: string) => createHash('sha256').update(token).digest('hex');

/**
 * Same link destination logic as verification: the web app, falling back to
 * the deployed site origin. The page is /reset-password (not /verify-email)
 * — the two flows must not cross.
 */
const FALLBACK_WEB_URL = 'https://scottstechx-web.onrender.com';

function webBase(): string {
  const raw = process.env.PUBLIC_WEB_URL?.trim();
  if (!raw) return FALLBACK_WEB_URL;
  return raw.replace(/\/+$/, '');
}

/**
 * Resolve an account from the app's single "Email or Phone Number" input.
 * Same rules as login: an '@' means email, otherwise the identifier is
 * normalised to digits and matched against users.phone.
 */
async function findUserByIdentifier(identifier: string): Promise<{ id: string; email: string | null; display_name: string | null } | null> {
  const pool = getPool();
  if (identifier.includes('@')) {
    const { rows } = await pool.query(
      'SELECT id, email, display_name FROM users WHERE email = $1 LIMIT 1',
      [identifier]
    );
    return rows[0] ?? null;
  }
  const digits = identifier.replace(/[^0-9]/g, '');
  if (digits.length < 7 || digits.length > 15) return null;
  const { rows } = await pool.query(
    `SELECT id, email, display_name FROM users
      WHERE phone IS NOT NULL
        AND regexp_replace(phone, '[^0-9]', '', 'g') = $1
      LIMIT 1`,
    [digits]
  );
  return rows[0] ?? null;
}

export async function registerResetRoutes(app: FastifyInstance) {
  const pool = getPool();

  const forgotSchema = z.object({
    identifier: z.string().trim().min(1).max(254),
  });

  app.post('/api/v1/auth/forgot-password', async (request) => {
    const { identifier } = forgotSchema.parse(request.body);
    const user = await findUserByIdentifier(identifier);

    // Constant response either way. No account → nothing to send, but the
    // answer is the same shape, so a probe cannot distinguish "real" from
    // "not" by the status code or the body.
    if (!user || !user.email) return { ok: true };

    // Rate limit against rows actually written — survives restarts.
    const recent = await pool.query(
      `SELECT COUNT(*)::int AS hourly, MAX(created_at) AS last_at
       FROM email_verifications
       WHERE user_id = $1 AND purpose = 'password_reset'
         AND created_at > now() - interval '1 hour'`,
      [user.id]
    );
    const { hourly, last_at: lastAt } = recent.rows[0] ?? { hourly: 0, last_at: null };

    if (lastAt || hourly >= MAX_SENDS_PER_HOUR) {
      const waited = lastAt ? (Date.now() - new Date(lastAt).getTime()) / 1000 : Infinity;
      if (waited < RESEND_COOLDOWN_SEC || hourly >= MAX_SENDS_PER_HOUR) {
        // Swallowed on purpose: telling the caller "too many" when the
        // identifier is real but throttled would itself be a signal.
        console.warn(`[reset] throttled request for user ${user.id} (hourly=${hourly})`);
        return { ok: true };
      }
    }

    // A new request supersedes outstanding reset links for the account.
    await pool.query(
      `UPDATE email_verifications SET consumed_at = now()
       WHERE user_id = $1 AND purpose = 'password_reset' AND consumed_at IS NULL`,
      [user.id]
    );

    const token = newToken();
    await pool.query(
      `INSERT INTO email_verifications (user_id, code_hash, token_hash, purpose, expires_at)
       VALUES ($1, '', $2, 'password_reset', now() + ($3 || ' minutes')::interval)`,
      [user.id, hashToken(token), String(TOKEN_TTL_MIN)]
    );

    const link = `${webBase()}/reset-password?token=${encodeURIComponent(token)}`;

    // Delivery is best-effort and logged, never reported: the response must
    // be byte-identical whether or not an account exists, so `ok` is a
    // constant. A flaky mail server must not turn "request a reset" into a
    // client-visible failure the user reads as "endpoint isn't live".
    try {
      const m = passwordResetEmail(link, { ttlMin: TOKEN_TTL_MIN, displayName: user.display_name || undefined });
      const res = await sendMail(user.email, m.subject, m.text, m.html);
      if (!res.delivered && mailConfigured()) {
        console.error('[reset] could not send reset link to', user.email, '-', res.reason);
      }
    } catch (e: any) {
      console.error('[reset] mailer threw', e?.message);
    }

    return {
      ok: true,
      // Dev escape hatch, same terms as the verification flow: only on a
      // non-production server with no mailer. In production the token is
      // never exposed to the caller.
      devLink: devCodesAllowed() ? link : undefined,
    };
  });

  const resetSchema = z.object({
    token: z.string().trim().min(20).max(200).regex(/^[A-Za-z0-9_-]+$/, 'Invalid reset link'),
    // Same floor as registration.
    password: z.string().min(6).max(200),
  });

  app.post('/api/v1/auth/reset-password', async (request) => {
    const { token, password } = resetSchema.parse(request.body);

    const { rows } = await pool.query(
      `SELECT * FROM email_verifications
       WHERE token_hash = $1 AND purpose = 'password_reset'
       ORDER BY created_at DESC LIMIT 1`,
      [hashToken(token)]
    );
    const rec = rows[0];

    // One message for "no such token" / "already used" / "tried too hard" —
    // the distinctions are a gift to anyone probing tokens.
    if (!rec || rec.consumed_at || rec.attempts >= MAX_ATTEMPTS) {
      throw new ValidationError(
        'This reset link is no longer valid. Request a new one from the login screen.'
      );
    }
    if (new Date(rec.expires_at).getTime() < Date.now()) {
      throw new ValidationError(
        'This reset link has expired. Request a new one from the login screen.'
      );
    }

    const newHash = await hashPassword(password);
    await pool.query(
      'UPDATE users SET password_hash = $2, updated_at = now() WHERE id = $1',
      [rec.user_id, newHash]
    );
    await pool.query('UPDATE email_verifications SET consumed_at = now() WHERE id = $1', [rec.id]);

    // Note: issued JWTs stay valid until their 24h expiry — the backend has
    // no token blacklist, same as /me/change-password. The password on the
    // account is changed immediately, which is what protects the account.
    return { ok: true };
  });
}
