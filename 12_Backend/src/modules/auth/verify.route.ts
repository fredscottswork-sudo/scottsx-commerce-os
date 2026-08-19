/**
 * ScottsTechX — email verification.
 *
 *   POST /api/v1/auth/verify/request  { }            (auth)  -> resend a code
 *   POST /api/v1/auth/verify/confirm  { code }       (auth)  -> mark verified
 *
 * Registration used to insert users with email_verified = true, so any string
 * that merely looked like an address became a real account. Accounts now start
 * unverified and a six-digit code is emailed.
 *
 * Hardening:
 *   - only the SHA-256 of the code is stored, so a leaked table is useless
 *   - codes expire (15 min) and are single-use
 *   - 6 wrong attempts burns the code, defeating a 1-in-a-million brute force
 *   - a fresh request invalidates older outstanding codes
 *   - the code is only ever returned in the response on a NON-production
 *     server that has no mailer (local dev). In production it is never
 *     exposed, even when SMTP is missing - otherwise anyone could verify an
 *     address they do not own by simply reading the API response.
 */
import type { FastifyInstance } from 'fastify';
import { createHash, randomBytes, randomInt } from 'node:crypto';
import { z } from 'zod';
import { getPool } from '../../db.js';
import { requireAuth, markVerified, tokenForUser } from '../../auth.js';
import { publicUser } from './login.route.js';
import { ValidationError, TooManyRequestsError } from '../../errors.js';
import { sendMail, mailConfigured, devCodesAllowed } from '../../mail.js';

const CODE_TTL_MIN = 15;
const MAX_ATTEMPTS = 6;

/**
 * Minimum gap between two verification emails for one account, and the cap on
 * how many may be issued in an hour.
 *
 * Without these, /auth/verify/request is a mail cannon: one session could fire
 * it in a loop, and every send costs someone something. On Firebase's free
 * Spark plan the whole project gets 1,000 verification emails a day, so a
 * single account could exhaust the quota for every real user in seconds. It is
 * also an abuse vector pointed at the address itself - sign up as
 * someone@example.com and hammer resend to flood their inbox.
 *
 * 60s is long enough to stop that and short enough that a genuine "it did not
 * arrive" retry is not annoying.
 */
/**
 * Overridable so tests can exercise the supersede-and-confirm path without
 * sleeping a real minute. Production never sets these; the defaults are the
 * product behaviour.
 */
const RESEND_COOLDOWN_SEC = Number(process.env.VERIFY_RESEND_COOLDOWN_SEC ?? 60);
const MAX_SENDS_PER_HOUR = Number(process.env.VERIFY_MAX_SENDS_PER_HOUR ?? 6);

const hashCode = (code: string) => createHash('sha256').update(code).digest('hex');
const newCode = () => String(randomInt(0, 1_000_000)).padStart(6, '0');

/**
 * The link token is a bearer credential: whoever holds it can verify the
 * address, without signing in. So it must be long enough that guessing is
 * hopeless - 32 random bytes, base64url - rather than reusing the six-digit
 * code, which is short on purpose so a human can retype it.
 */
const newLinkToken = () => randomBytes(32).toString('base64url');
const hashToken = (token: string) => createHash('sha256').update(token).digest('hex');

/**
 * Where the verification link should point: the WEB app, not this API.
 *
 * The link has to land on a real page that can show success or failure and
 * then move the user on. PUBLIC_WEB_URL is the same variable the sitemap uses
 * for the canonical site origin.
 *
 * Verification is link-only, so there must ALWAYS be a link. Returning null
 * here used to mean the email silently degraded to a six-digit code, which is
 * precisely the failure this flow exists to remove - and it happened whenever
 * someone forgot one environment variable. The deployed site is the fallback
 * instead, mirroring how the web client falls back to a known API origin.
 *
 * Set PUBLIC_WEB_URL anyway: it is what makes links point at a custom domain.
 */
const FALLBACK_WEB_URL = 'https://scottstechx-web.onrender.com';

function verifyLinkBase(): string {
  const raw = process.env.PUBLIC_WEB_URL?.trim();
  if (!raw) return FALLBACK_WEB_URL;
  return raw.replace(/\/+$/, '');
}

/** Issue a code, email it, and return it only when there is no mailer. */
export async function issueVerification(userId: string, email: string, displayName?: string) {
  const pool = getPool();

  // Rate limit BEFORE issuing anything. The window is measured against rows
  // actually written, so it survives a restart - an in-memory counter would
  // reset every deploy and on Render's free tier that is every 15 idle minutes.
  const recent = await pool.query(
    `SELECT
       COUNT(*)::int AS hourly,
       MAX(created_at) AS last_at
     FROM email_verifications
     WHERE user_id = $1 AND purpose = 'signup'
       AND created_at > now() - interval '1 hour'`,
    [userId]
  );
  const { hourly, last_at: lastAt } = recent.rows[0] ?? { hourly: 0, last_at: null };

  if (lastAt) {
    const waited = (Date.now() - new Date(lastAt).getTime()) / 1000;
    if (waited < RESEND_COOLDOWN_SEC) {
      const retryIn = Math.ceil(RESEND_COOLDOWN_SEC - waited);
      throw new TooManyRequestsError(
        `Please wait ${retryIn} second${retryIn === 1 ? '' : 's'} before requesting another code.`,
        retryIn
      );
    }
  }
  if (hourly >= MAX_SENDS_PER_HOUR) {
    throw new TooManyRequestsError(
      'Too many verification emails requested. Please try again in an hour, ' +
        'or continue with Google.',
      3600
    );
  }

  // A new code supersedes any outstanding one.
  await pool.query(
    `UPDATE email_verifications SET consumed_at = now()
      WHERE user_id = $1 AND purpose = 'signup' AND consumed_at IS NULL`,
    [userId]
  );
  const code = newCode();
  const linkToken = newLinkToken();
  await pool.query(
    `INSERT INTO email_verifications (user_id, code_hash, token_hash, purpose, expires_at)
     VALUES ($1, $2, $3, 'signup', now() + ($4 || ' minutes')::interval)`,
    [userId, hashCode(code), hashToken(linkToken), String(CODE_TTL_MIN)]
  );

  // Verification is by link, always.
  const base = verifyLinkBase();
  const link = `${base}/verify-email?token=${encodeURIComponent(linkToken)}`;
  if (!process.env.PUBLIC_WEB_URL?.trim()) {
    console.warn(
      `[verify] PUBLIC_WEB_URL is not set - verification links point at ` +
      `${FALLBACK_WEB_URL}. Set it to this site's public origin.`
    );
  }

  // Delivery must never decide whether the account exists. A dead or slow
  // mail server would otherwise turn "sign up" into a 15-second hang or a 500
  // on an account that has already been created. The row is written first, the
  // send is best-effort, and the user can always ask for another code.
  let delivered = false;
  try {
    // Link only. No code is printed here: the website has no way to enter one,
    // so including it would invite people to try something that cannot work.
    const res = await sendMail(
      email,
      'Confirm your ScottsTechX email address',
      `Hello${displayName ? ` ${displayName}` : ''},\n\n`
        + `Confirm your email address to finish setting up your ScottsTechX account:\n\n`
        + `${link}\n\n`
        + `This link expires in ${CODE_TTL_MIN} minutes and can only be used once.\n\n`
        + `If the link does not open, copy it into your browser's address bar.\n\n`
        + `If you did not create this account you can ignore this email.\n`
    );
    delivered = res.delivered;
    if (!res.delivered && mailConfigured()) {
      console.error('[verify] could not send code to', email, '-', res.reason);
    }
  } catch (e: any) {
    console.error('[verify] mailer threw', e?.message);
  }

  return {
    delivered,
    // Whether a clickable link could actually be built. The UI needs to know:
    // telling someone to "click the link in your email" when the server could
    // not put one there is the same class of lie as claiming an email was sent.
    linkSent: true,
    // Dev only, on the same terms as devCode - it is a bearer credential.
    devLink: devCodesAllowed() ? link ?? undefined : undefined,
    // Never leak the code once a real mailer is wired up - and never in
    // production even without one. Handing the code to the caller would let
    // anyone verify an address they cannot read, which is exactly the "no fake
    // emails" rule this whole flow exists to enforce.
    devCode: devCodesAllowed() ? code : undefined,
  };
}

export async function registerVerifyRoutes(app: FastifyInstance) {
  const pool = getPool();

  app.post('/api/v1/auth/verify/request', { preHandler: requireAuth }, async (request) => {
    const { id, email, display_name } = (request as any).user;
    const { rows } = await pool.query('SELECT email, display_name, email_verified FROM users WHERE id = $1', [id]);
    const row = rows[0];
    if (!row) throw new ValidationError('Account not found');
    if (row.email_verified) return { alreadyVerified: true, sent: false };

    const out = await issueVerification(id, row.email ?? email, row.display_name ?? display_name);
    // Report what actually happened, not what we attempted. `sent: true` used
    // to be hardcoded, so a server with no mailer cheerfully told the user to
    // check an inbox that would never receive anything - and on a production
    // server, where no devCode is returned either, that left them stuck with
    // no way forward and no explanation.
    return {
      alreadyVerified: false,
      sent: out.delivered,
      // When we could not deliver and cannot show the code, say so plainly so
      // the client can offer a real alternative instead of "check your email".
      undeliverable: !out.delivered && out.devCode === undefined,
      ...out,
    };
  });

  const confirmSchema = z.object({ code: z.string().trim().regex(/^\d{6}$/, 'Enter the 6-digit code') });

  // base64url of 32 bytes is 43 chars; allow a range so a future length change
  // does not silently start rejecting valid links.
  const linkSchema = z.object({
    token: z.string().trim().min(20).max(200).regex(/^[A-Za-z0-9_-]+$/, 'Invalid verification link'),
  });

  /**
   * Confirm by LINK.
   *
   * Deliberately NOT behind requireAuth. The user clicks this from their email
   * client, quite possibly on a different device or a browser with no session,
   * and demanding a login first would defeat the entire point of a link - it
   * is the case where someone signed up on their phone and opens the mail on a
   * laptop. The token itself is the proof: 32 random bytes that only reached
   * the person who can read that inbox.
   *
   * Returns the same shape as the code path, plus a token so the browser that
   * clicked can be signed straight in rather than bounced to a login form.
   */
  app.post('/api/v1/auth/verify/link', async (request) => {
    const { token } = linkSchema.parse(request.body);

    const { rows } = await pool.query(
      `SELECT * FROM email_verifications
        WHERE token_hash = $1 AND purpose = 'signup'
        ORDER BY created_at DESC LIMIT 1`,
      [hashToken(token)]
    );
    const rec = rows[0];

    // One message for "no such token" and "already used" - distinguishing them
    // tells an attacker probing tokens which guesses were once real.
    if (!rec || rec.consumed_at) {
      throw new ValidationError(
        'This verification link is no longer valid. It may have already been ' +
        'used or replaced by a newer one. Sign in to send a fresh link.'
      );
    }
    if (new Date(rec.expires_at).getTime() < Date.now()) {
      throw new ValidationError(
        'This verification link has expired. Sign in to send a fresh one.'
      );
    }

    await pool.query('UPDATE email_verifications SET consumed_at = now() WHERE id = $1', [rec.id]);
    const upd = await pool.query(
      'UPDATE users SET email_verified = true WHERE id = $1 RETURNING *',
      [rec.user_id]
    );
    const user = upd.rows[0];
    if (!user) throw new ValidationError('That account no longer exists.');

    markVerified(user.id);

    // Sign them in on the spot. They have just proved they control the
    // address, and making them retype a password now is friction for nothing.
    return {
      verified: true,
      token: await tokenForUser(user),
      user: publicUser(user),
    };
  });

  app.post('/api/v1/auth/verify/confirm', { preHandler: requireAuth }, async (request) => {
    const { code } = confirmSchema.parse(request.body);
    const { id } = (request as any).user;

    const { rows } = await pool.query(
      `SELECT * FROM email_verifications
        WHERE user_id = $1 AND purpose = 'signup' AND consumed_at IS NULL
        ORDER BY created_at DESC LIMIT 1`,
      [id]
    );
    const rec = rows[0];
    if (!rec) throw new ValidationError('No verification is pending. Request a new code.');
    if (new Date(rec.expires_at).getTime() < Date.now()) {
      throw new ValidationError('That code has expired. Request a new one.');
    }
    if (rec.attempts >= MAX_ATTEMPTS) {
      await pool.query('UPDATE email_verifications SET consumed_at = now() WHERE id = $1', [rec.id]);
      throw new ValidationError('Too many attempts. Request a new code.');
    }

    if (rec.code_hash !== hashCode(code)) {
      await pool.query('UPDATE email_verifications SET attempts = attempts + 1 WHERE id = $1', [rec.id]);
      throw new ValidationError('That code is not correct.');
    }

    await pool.query('UPDATE email_verifications SET consumed_at = now() WHERE id = $1', [rec.id]);
    const upd = await pool.query(
      'UPDATE users SET email_verified = true WHERE id = $1 RETURNING *',
      [id]
    );
    // Let the very next request through without waiting on a re-read.
    markVerified(id);
    return { verified: true, user: publicUser(upd.rows[0]) };
  });
}
