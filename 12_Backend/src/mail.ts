/**
 * ScottsTechX — outbound email.
 *
 * Deliberately dependency-free: it speaks SMTP over a TLS socket rather than
 * pulling in nodemailer, because the only message this app sends is a short
 * verification code.
 *
 * Configure with:
 *   SMTP_HOST, SMTP_PORT (default 465), SMTP_USER, SMTP_PASS, MAIL_FROM
 *
 * With no SMTP_HOST the mailer runs in "log" mode: the message is written to
 * the server log and reported as not-delivered. Verification still works —
 * useful for local development — but the caller can tell the difference, and
 * in production a missing SMTP config is surfaced rather than silently
 * dropping mail.
 */
import { connect as tlsConnect } from 'node:tls';

export interface MailResult {
  delivered: boolean;
  /** Why it was not delivered, when it was not. */
  reason?: string;
}

export function mailConfigured(): boolean {
  return Boolean(process.env.SMTP_HOST && process.env.SMTP_USER && process.env.SMTP_PASS);
}

/**
 * May the API hand a verification code back in its own response?
 *
 * Only ever as a local development convenience. Returning the code to the
 * caller defeats the entire point of email verification: whoever asks for it
 * receives it, so any string shaped like an address becomes a verified
 * account without anyone ever opening a mailbox.
 *
 * That is tolerable on a laptop with no mailer. It is not tolerable on a
 * deployed server, so in production the code is never exposed - even when
 * SMTP is missing. A sign-up that cannot be delivered must fail loudly rather
 * than quietly issue credentials to a fake address.
 *
 * ALLOW_DEV_VERIFICATION_CODES=true is the deliberate escape hatch for staging
 * environments that genuinely have no mailer and are not open to the public.
 */
export function devCodesAllowed(): boolean {
  if (mailConfigured()) return false;
  if (process.env.ALLOW_DEV_VERIFICATION_CODES === 'true') return true;
  return process.env.NODE_ENV !== 'production';
}

/**
 * True when the server cannot deliver mail AND is not allowed to fall back to
 * returning codes - i.e. verification is impossible and sign-up must say so.
 */
export function verificationUndeliverable(): boolean {
  return !mailConfigured() && !devCodesAllowed();
}

function mailFrom(): string {
  return process.env.MAIL_FROM || process.env.SMTP_USER || 'no-reply@scottstechx.ug';
}

/** Minimal SMTP-over-TLS conversation. Resolves false on any protocol error. */
async function smtpSend(to: string, subject: string, text: string): Promise<MailResult> {
  const host = process.env.SMTP_HOST!;
  const port = Number(process.env.SMTP_PORT || 465);
  const user = process.env.SMTP_USER!;
  const pass = process.env.SMTP_PASS!;
  const from = mailFrom();

  return new Promise<MailResult>((resolve) => {
    // servername is the SNI hint and must be a hostname; passing an IP is
    // rejected by RFC 6066 and warns on modern Node.
    const isIp = /^[\d.]+$|:/.test(host);
    const socket = tlsConnect(isIp ? { host, port } : { host, port, servername: host }, () => void 0);
    let stage = 0;
    let settled = false;
    const done = (r: MailResult) => {
      if (settled) return;
      settled = true;
      try { socket.end(); } catch { /* already closed */ }
      resolve(r);
    };

    const steps = [
      `EHLO scottstechx\r\n`,
      `AUTH LOGIN\r\n`,
      `${Buffer.from(user).toString('base64')}\r\n`,
      `${Buffer.from(pass).toString('base64')}\r\n`,
      `MAIL FROM:<${from}>\r\n`,
      `RCPT TO:<${to}>\r\n`,
      `DATA\r\n`,
      [
        `From: ScottsTechX <${from}>`,
        `To: <${to}>`,
        `Subject: ${subject}`,
        'MIME-Version: 1.0',
        'Content-Type: text/plain; charset=utf-8',
        '',
        text,
        '.',
        '',
      ].join('\r\n'),
      `QUIT\r\n`,
    ];

    socket.setTimeout(10000, () => done({ delivered: false, reason: "smtp timeout" }));
    socket.on('error', (e) => done({ delivered: false, reason: `smtp error: ${e.message}` }));

    socket.on('data', (buf) => {
      const line = buf.toString();
      const code = Number(line.slice(0, 3));
      if (code >= 400) return done({ delivered: false, reason: `smtp ${line.trim().slice(0, 90)}` });
      if (stage >= steps.length) return done({ delivered: true });
      socket.write(steps[stage++]);
      if (stage === steps.length) done({ delivered: true });
    });
  });
}

export async function sendMail(to: string, subject: string, text: string): Promise<MailResult> {
  if (!mailConfigured()) {
    // Not an error in development; the code is still returned to the caller
    // through the API so the flow can be completed and tested.
    console.log(`[mail] (no SMTP configured) to=${to} subject="${subject}"\n${text}`);
    return { delivered: false, reason: 'smtp-not-configured' };
  }
  try {
    return await smtpSend(to, subject, text);
  } catch (e: any) {
    console.error('[mail] send failed', e?.message);
    return { delivered: false, reason: e?.message || 'send failed' };
  }
}
