/**
 * Passwordless sign-in with an emailed code + onboarding.
 *
 *   POST /api/v1/auth/otp/start    { email }            → mails a 6-digit code
 *   POST /api/v1/auth/otp/verify   { email, code }      → session (creates the
 *                                                          account on first use)
 *   POST /api/v1/auth/onboarding   { role, storeName?, storeLogoUrl?, displayName? }
 *
 * One code per address at a time; 15-minute expiry; 6 attempts; 60 s resend
 * cool-down and 6 sends/hour per address (same limits as sign-up codes).
 * Delivery is through the operator's SMTP (SMTP_HOST/USER/PASS, MAIL_FROM).
 * With no mailer, the code is returned only where devCodesAllowed() says so.
 */
import type { FastifyInstance } from 'fastify';
import { createHash, randomInt } from 'node:crypto';
import { z } from 'zod';
import { getPool } from '../../db.js';
import { requireAuth, authedUser, markVerified, tokenForUser } from '../../auth.js';
import { publicUser } from './login.route.js';
import { ValidationError, TooManyRequestsError, ServiceUnavailableError, UnauthorizedError } from '../../errors.js';
import { sendMail, devCodesAllowed, verificationUndeliverable } from '../../mail.js';
import { signInCodeEmail } from '../../mail-templates.js';

const CODE_TTL_MIN = 15;
const MAX_ATTEMPTS = 6;
const RESEND_COOLDOWN_SEC = Number(process.env.VERIFY_RESEND_COOLDOWN_SEC ?? 60);
const MAX_SENDS_PER_HOUR = Number(process.env.VERIFY_MAX_SENDS_PER_HOUR ?? 6);

const hash = (v: string) => createHash('sha256').update(v).digest('hex');
const newCode = () => String(randomInt(0, 1_000_000)).padStart(6, '0');
const normEmail = (e: string) => e.trim().toLowerCase();

const startSchema = z.object({ email: z.string().trim().email().max(254) });
const verifySchema = z.object({
  email: z.string().trim().email().max(254),
  code: z.string().trim().regex(/^\d{6}$/, 'Enter the 6-digit code'),
});
const onboardingSchema = z.object({
  role: z.enum(['buyer', 'seller']),
  displayName: z.string().trim().max(80).optional(),
  storeName: z.string().trim().min(2).max(80).optional(),
  storeLogoUrl: z.string().trim().max(2048).optional().nullable(),
  storeDescription: z.string().trim().max(600).optional(),
  city: z.string().trim().max(120).optional(),
});

export default async function registerOtpRoutes(app: FastifyInstance) {
  const pool = getPool();

  app.post('/api/v1/auth/otp/start', async (request) => {
    const email = normEmail(startSchema.parse(request.body).email);

    if (verificationUndeliverable()) {
      throw new ServiceUnavailableError(
        'Email sign-in is temporarily unavailable: this server cannot send emails yet. Please continue with Google.'
      );
    }

    // Rate limits are per address so an attacker cannot flood one inbox.
    const recent = await pool.query(
      `SELECT COUNT(*)::int AS hourly, MAX(created_at) AS last_at
         FROM email_verifications
        WHERE email = $1 AND purpose = 'login' AND created_at > now() - interval '1 hour'`,
      [email]
    );
    const { hourly, last_at: lastAt } = recent.rows[0] ?? { hourly: 0, last_at: null };
    if (lastAt) {
      const waited = (Date.now() - new Date(lastAt).getTime()) / 1000;
      if (waited < RESEND_COOLDOWN_SEC) {
        const retryIn = Math.ceil(RESEND_COOLDOWN_SEC - waited);
        throw new TooManyRequestsError(`Please wait ${retryIn}s before requesting another code.`, retryIn);
      }
    }
    if (hourly >= MAX_SENDS_PER_HOUR) {
      throw new TooManyRequestsError('Too many codes requested for this address. Try again in an hour or continue with Google.', 3600);
    }

    const existing = await pool.query('SELECT id FROM users WHERE email = $1', [email]);
    const userId: string | null = existing.rows[0]?.id ?? null;
    const isNew = !userId;

    await pool.query(
      `UPDATE email_verifications SET consumed_at = now()
        WHERE email = $1 AND purpose = 'login' AND consumed_at IS NULL`,
      [email]
    );
    const code = newCode();
    // user_id is NOT NULL in the table; for a brand-new address we park the
    // row on a sentinel-free path by creating the (unverified) user row now.
    // It has no password and no role chosen; it becomes real only when the
    // code is entered. Unused rows are harmless and de-duplicated by email.
    let uid = userId;
    if (!uid) {
      const ins = await pool.query(
        `INSERT INTO users (email, display_name, role, email_verified, role_chosen)
         VALUES ($1, $2, 'buyer', false, false)
         ON CONFLICT (email) DO UPDATE SET updated_at = now()
         RETURNING id`,
        [email, email.split('@')[0]]
      );
      uid = ins.rows[0].id;
    }
    await pool.query(
      `INSERT INTO email_verifications (user_id, email, code_hash, purpose, expires_at)
       VALUES ($1, $2, $3, 'login', now() + ($4 || ' minutes')::interval)`,
      [uid, email, hash(code), String(CODE_TTL_MIN)]
    );

    let delivered = false;
    let reason = '';
    try {
      const { subject, text, html } = signInCodeEmail(code, { isNew, ttlMin: CODE_TTL_MIN, email });
      const res = await sendMail(email, subject, text, html);
      delivered = res.delivered;
      reason = res.reason || '';
    } catch (e: any) {
      reason = e?.message || 'mailer threw';
      console.error('[otp] mailer threw', reason);
    }
    if (!delivered && !devCodesAllowed()) {
      // Do not burn the user's cool-down on a failure that was ours.
      await pool.query('DELETE FROM email_verifications WHERE email = $1 AND purpose = $2 AND code_hash = $3', [email, 'login', hash(code)]);
      const hint = /535|auth|credential|password/i.test(reason) ? ' (mail server rejected the login)'
        : /timed out|ECONNREFUSED|ENOTFOUND|EAI_AGAIN/i.test(reason) ? ' (mail server unreachable)'
        : /STARTTLS|wrong version|handshake|SSL/i.test(reason) ? ' (TLS/port mismatch)'
        : '';
      throw new ServiceUnavailableError(
        `We could not send the email right now${hint}. Please try again in a moment or continue with Google.`
      );
    }

    return {
      sent: delivered,
      isNew,
      expiresInMin: CODE_TTL_MIN,
      resendInSec: RESEND_COOLDOWN_SEC,
      // Local development only — never when a mailer is configured.
      devCode: devCodesAllowed() ? code : undefined,
    };
  });

  app.post('/api/v1/auth/otp/verify', async (request) => {
    const body = verifySchema.parse(request.body);
    const email = normEmail(body.email);

    const { rows } = await pool.query(
      `SELECT * FROM email_verifications
        WHERE email = $1 AND purpose = 'login' AND consumed_at IS NULL
        ORDER BY created_at DESC LIMIT 1`,
      [email]
    );
    const rec = rows[0];
    if (!rec) throw new ValidationError('No code is pending for this address. Request a new one.');
    if (new Date(rec.expires_at).getTime() < Date.now()) throw new ValidationError('That code has expired. Request a new one.');
    if (rec.attempts >= MAX_ATTEMPTS) {
      await pool.query('UPDATE email_verifications SET consumed_at = now() WHERE id = $1', [rec.id]);
      throw new ValidationError('Too many attempts. Request a new code.');
    }
    if (rec.code_hash !== hash(body.code)) {
      await pool.query('UPDATE email_verifications SET attempts = attempts + 1 WHERE id = $1', [rec.id]);
      const left = MAX_ATTEMPTS - rec.attempts - 1;
      throw new ValidationError(left > 0 ? `That code is not correct. ${left} attempt${left === 1 ? '' : 's'} left.` : 'That code is not correct.');
    }
    await pool.query('UPDATE email_verifications SET consumed_at = now() WHERE id = $1', [rec.id]);

    const upd = await pool.query(
      `UPDATE users SET email_verified = true, updated_at = now() WHERE id = $1 RETURNING *`,
      [rec.user_id]
    );
    const user = upd.rows[0];
    if (!user) throw new ValidationError('That account no longer exists.');
    markVerified(user.id);
    return {
      token: await tokenForUser(user),
      user: publicUser(user),
      needsOnboarding: !user.role_chosen,
    };
  });

  app.post('/api/v1/auth/onboarding', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const body = onboardingSchema.parse(request.body);
    const { rows } = await pool.query('SELECT * FROM users WHERE id = $1', [me.id]);
    const user = rows[0];
    if (!user) throw new UnauthorizedError('Account not found');
    if (user.role === 'admin') throw new ValidationError('Admins do not go through onboarding');
    if (!user.email_verified) throw new UnauthorizedError('Verify your email first');

    if (body.role === 'seller' && !body.storeName) throw new ValidationError('Choose a store name');

    const upd = await pool.query(
      `UPDATE users
          SET role = $2,
              role_chosen = true,
              display_name = CASE WHEN $3 <> '' THEN $3 ELSE display_name END,
              city = CASE WHEN $4 <> '' THEN $4 ELSE city END,
              updated_at = now()
        WHERE id = $1 RETURNING *`,
      [me.id, body.role, body.displayName ?? '', body.city ?? '']
    );
    if (body.role === 'seller') {
      await pool.query(
        `INSERT INTO store_settings (user_id, store_name, store_logo_url, store_description, city, address)
         VALUES ($1, $2, $3, $4, $5, '')
         ON CONFLICT (user_id) DO UPDATE
           SET store_name = EXCLUDED.store_name,
               store_logo_url = COALESCE(EXCLUDED.store_logo_url, store_settings.store_logo_url),
               store_description = CASE WHEN EXCLUDED.store_description <> '' THEN EXCLUDED.store_description ELSE store_settings.store_description END,
               city = CASE WHEN EXCLUDED.city <> '' THEN EXCLUDED.city ELSE store_settings.city END`,
        [me.id, body.storeName, body.storeLogoUrl ?? null, body.storeDescription ?? '', body.city ?? '']
      );
    }
    const u = upd.rows[0];
    return { token: await tokenForUser(u), user: publicUser(u) };
  });
}
