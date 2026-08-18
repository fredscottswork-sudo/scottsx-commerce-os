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
 *   - the code is only returned in the response when SMTP is NOT configured
 *     (local dev); with a real mailer it is never exposed over the API
 */
import type { FastifyInstance } from 'fastify';
import { createHash, randomInt } from 'node:crypto';
import { z } from 'zod';
import { getPool } from '../../db.js';
import { requireAuth } from '../../auth.js';
import { publicUser } from './login.route.js';
import { ValidationError } from '../../errors.js';
import { sendMail, mailConfigured } from '../../mail.js';

const CODE_TTL_MIN = 15;
const MAX_ATTEMPTS = 6;

const hashCode = (code: string) => createHash('sha256').update(code).digest('hex');
const newCode = () => String(randomInt(0, 1_000_000)).padStart(6, '0');

/** Issue a code, email it, and return it only when there is no mailer. */
export async function issueVerification(userId: string, email: string, displayName?: string) {
  const pool = getPool();
  // A new code supersedes any outstanding one.
  await pool.query(
    `UPDATE email_verifications SET consumed_at = now()
      WHERE user_id = $1 AND purpose = 'signup' AND consumed_at IS NULL`,
    [userId]
  );
  const code = newCode();
  await pool.query(
    `INSERT INTO email_verifications (user_id, code_hash, purpose, expires_at)
     VALUES ($1, $2, 'signup', now() + ($3 || ' minutes')::interval)`,
    [userId, hashCode(code), String(CODE_TTL_MIN)]
  );

  // Delivery must never decide whether the account exists. A dead or slow
  // mail server would otherwise turn "sign up" into a 15-second hang or a 500
  // on an account that has already been created. The row is written first, the
  // send is best-effort, and the user can always ask for another code.
  let delivered = false;
  try {
    const res = await sendMail(
      email,
      'Your ScottsTechX verification code',
      `Hello${displayName ? ` ${displayName}` : ''},\n\n`
        + `Your ScottsTechX verification code is ${code}\n`
        + `It expires in ${CODE_TTL_MIN} minutes.\n\n`
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
    // Never leak the code once a real mailer is wired up.
    devCode: mailConfigured() ? undefined : code,
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
    return { alreadyVerified: false, sent: true, ...out };
  });

  const confirmSchema = z.object({ code: z.string().trim().regex(/^\d{6}$/, 'Enter the 6-digit code') });

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
    return { verified: true, user: publicUser(upd.rows[0]) };
  });
}
