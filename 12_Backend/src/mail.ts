/**
 * ScottsTechX — outbound email.
 *
 * Deliberately dependency-free: a small, correct SMTP client rather than
 * nodemailer, because the only messages this app sends are short codes.
 *
 * Two transports, tried in this order:
 *
 *   1. HTTP email API — works everywhere, including hosts that block SMTP
 *      ports (Render's free tier does). Set ONE of:
 *        BREVO_API_KEY     https://app.brevo.com/settings/keys/api  (300/day free)
 *        RESEND_API_KEY    https://resend.com/api-keys              (100/day free)
 *        SENDGRID_API_KEY  https://app.sendgrid.com/settings/api_keys
 *      plus MAIL_FROM — an address that provider has verified for you.
 *
 *   2. SMTP — classic. Configure with:
 *   SMTP_HOST            e.g. smtp.gmail.com, smtp-relay.brevo.com, smtp.zoho.com
 *   SMTP_PORT            465 (implicit TLS) or 587 / 25 (STARTTLS). Default 465.
 *   SMTP_SECURE          optional override: "true" = implicit TLS, "false" = STARTTLS
 *   SMTP_USER, SMTP_PASS login (AUTH PLAIN, falling back to AUTH LOGIN)
 *   MAIL_FROM            "ScottsTechX <no-reply@yourdomain.com>" or a bare address
 *
 * With no SMTP_HOST the mailer runs in "log" mode: the message is written to
 * the server log and reported as not-delivered. In development the code is
 * then returned to the caller; in production sign-in by email is refused.
 *
 * Every failure is recorded in `lastMailError` (no secrets) and exposed via
 * GET /api/v1/mail/status so an operator can see *why* a send failed without
 * digging through logs.
 */
import { connect as netConnect, type Socket } from 'node:net';
import { connect as tlsConnect, type TLSSocket } from 'node:tls';

export interface MailResult {
  delivered: boolean;
  /** Why it was not delivered, when it was not. */
  reason?: string;
}

function smtpConfigured(): boolean {
  return Boolean(process.env.SMTP_HOST && process.env.SMTP_USER && process.env.SMTP_PASS);
}

type HttpProvider = 'brevo' | 'resend' | 'sendgrid';
function httpProvider(): HttpProvider | null {
  if (process.env.BREVO_API_KEY) return 'brevo';
  if (process.env.RESEND_API_KEY) return 'resend';
  if (process.env.SENDGRID_API_KEY) return 'sendgrid';
  return null;
}

export function mailConfigured(): boolean {
  return httpProvider() !== null || smtpConfigured();
}

/**
 * May the API hand a verification code back in its own response?
 * Only as a local-development convenience — never when a mailer is configured
 * and never in production (unless ALLOW_DEV_VERIFICATION_CODES=true).
 */
export function devCodesAllowed(): boolean {
  if (mailConfigured()) return false;
  if (process.env.ALLOW_DEV_VERIFICATION_CODES === 'true') return true;
  return process.env.NODE_ENV !== 'production';
}

/** True when the server cannot deliver mail AND may not fall back to codes. */
export function verificationUndeliverable(): boolean {
  return !mailConfigured() && !devCodesAllowed();
}

/** Last failure, for /mail/status. Never contains credentials. */
export let lastMailError: { at: string; reason: string; to: string } | null = null;
export let lastMailSuccess: { at: string; to: string } | null = null;

function parseFrom(): { address: string; display: string } {
  const raw = (process.env.MAIL_FROM || process.env.SMTP_USER || 'no-reply@scottstechx.ug').trim();
  const m = raw.match(/^(.*?)\s*<\s*([^<>\s]+@[^<>\s]+)\s*>$/);
  if (m) return { display: m[1].replace(/^"|"$/g, '').trim() || 'ScottsTechX', address: m[2] };
  return { display: 'ScottsTechX', address: raw.replace(/[<>]/g, '') };
}

export function mailSummary() {
  const { address } = parseFrom();
  const port = Number(process.env.SMTP_PORT || 465);
  const api = httpProvider();
  return {
    configured: mailConfigured(),
    transport: api ? `api:${api}` : smtpConfigured() ? 'smtp' : 'log',
    host: api ? null : process.env.SMTP_HOST || null,
    port: api ? null : port,
    mode: api ? api : !process.env.SMTP_HOST ? 'log' : secureMode(port) ? 'tls' : 'starttls',
    from: address,
    devCodes: devCodesAllowed(),
    lastError: lastMailError,
    lastSuccess: lastMailSuccess,
  };
}

function secureMode(port: number): boolean {
  const o = process.env.SMTP_SECURE;
  if (o === 'true') return true;
  if (o === 'false') return false;
  return port === 465;
}

/** Encode a header value with non-ASCII characters (RFC 2047). */
function headerValue(v: string): string {
  return /^[\x20-\x7e]*$/.test(v) ? v : `=?utf-8?B?${Buffer.from(v, 'utf8').toString('base64')}?=`;
}

class SmtpError extends Error {
  constructor(public code: number, message: string) { super(message); }
}

/**
 * Line-oriented SMTP conversation. Reads complete (possibly multi-line)
 * replies, upgrades to TLS via STARTTLS when needed, authenticates with
 * PLAIN or LOGIN, and returns a precise reason on any failure.
 */
async function smtpSend(to: string, subject: string, text: string, html?: string): Promise<MailResult> {
  const port = Number(process.env.SMTP_PORT || 465);
  const secure = secureMode(port);
  const first = await smtpAttempt(to, subject, text, secure, html);
  // Operators frequently set a non-standard port that is actually implicit
  // TLS (e.g. 2465, 8465). If plaintext got no greeting, try TLS once.
  if (!first.delivered && !secure && process.env.SMTP_SECURE !== 'false' && /no SMTP greeting/.test(first.reason || '')) {
    const second = await smtpAttempt(to, subject, text, true, html);
    if (second.delivered) console.warn(`[mail] port ${port} is implicit TLS — set SMTP_SECURE=true to skip the plaintext attempt`);
    // The TLS attempt got a real conversation going (or a real refusal) —
    // its reason is the informative one.
    if (second.delivered || !/wrong version|handshake|no SMTP greeting/i.test(second.reason || '')) return second;
  }
  return first;
}

async function smtpAttempt(to: string, subject: string, text: string, secure: boolean, html?: string): Promise<MailResult> {
  const host = process.env.SMTP_HOST!;
  const port = Number(process.env.SMTP_PORT || 465);
  const user = process.env.SMTP_USER!;
  const pass = process.env.SMTP_PASS!;
  const from = parseFrom();
  const isIp = /^[\d.]+$|:/.test(host);
  const TIMEOUT = 15000;

  let socket: Socket | TLSSocket | undefined;
  let buffer = '';
  let waiters: Array<{ resolve: (r: { code: number; lines: string[] }) => void; reject: (e: Error) => void }> = [];

  const attach = (s: Socket | TLSSocket) => {
    socket = s;
    s.setTimeout(TIMEOUT, () => failAll(new Error(`timed out talking to ${host}:${port}`)));
    s.on('error', (e) => failAll(new Error(`socket error: ${e.message}`)));
    s.on('close', () => failAll(new Error('connection closed by server')));
    s.on('data', (chunk) => {
      buffer += chunk.toString('utf8');
      // A reply is complete when a line "NNN<space>text" arrives (not "NNN-").
      let idx: number;
      while ((idx = buffer.indexOf('\r\n')) !== -1 || (idx = buffer.indexOf('\n')) !== -1) {
        const eol = buffer[idx] === '\r' ? 2 : 1;
        const line = buffer.slice(0, idx);
        buffer = buffer.slice(idx + eol);
        pending.push(line);
        if (/^\d{3}( |$)/.test(line)) {
          const lines = pending; pending = [];
          const reply = { code: Number(lines[lines.length - 1].slice(0, 3)), lines };
          const w = waiters.shift();
          if (w) w.resolve(reply);
          else replies.push(reply); // arrived before anyone asked (e.g. the greeting)
        }
      }
    });
  };
  let pending: string[] = [];
  const replies: Array<{ code: number; lines: string[] }> = [];
  const failAll = (e: Error) => { const ws = waiters; waiters = []; ws.forEach((w) => w.reject(e)); };

  const read = () => new Promise<{ code: number; lines: string[] }>((resolve, reject) => {
    const queued = replies.shift();
    if (queued) resolve(queued);
    else waiters.push({ resolve, reject });
  });
  const cmd = async (line: string, ok: number[] = [250]) => {
    socket!.write(line + "\r\n");
    const r = await read();
    if (!ok.includes(r.code)) throw new SmtpError(r.code, r.lines.join(' | ').slice(0, 160));
    return r;
  };

  // Listeners are attached BEFORE the connect callback fires so the 220
  // greeting can never slip past us on a fast server.
  const connectPlain = () => new Promise<void>((resolve, reject) => {
    const s = netConnect({ host, port }, () => resolve());
    attach(s);
    s.once('error', reject);
  });
  const connectTls = (opts: { socket?: Socket } = {}) => new Promise<void>((resolve, reject) => {
    const s = tlsConnect({ host, port, ...(isIp ? {} : { servername: host }), ...opts }, () => resolve());
    attach(s);
    s.once('error', reject);
  });

  try {
    if (secure) await connectTls();
    else await connectPlain();
    // A TLS-only port answers a plaintext client with silence; do not wait
    // the full timeout to say so.
    const greet = await Promise.race([
      read(),
      new Promise<never>((_, rej) => setTimeout(() => rej(new Error(
        secure ? `no SMTP greeting from ${host}:${port}` : `no SMTP greeting from ${host}:${port} — if this port expects TLS set SMTP_PORT=465 or SMTP_SECURE=true`
      )), 6000)),
    ]);
    if (greet.code !== 220) throw new SmtpError(greet.code, `greeting: ${greet.lines.join(' ')}`);

    let ehlo = await cmd('EHLO scottstechx.ug');
    const caps = () => ehlo.lines.join('\n').toUpperCase();

    if (!secure) {
      if (!caps().includes('STARTTLS')) throw new Error(`server on port ${port} does not offer STARTTLS — use port 465 (SMTP_SECURE=true)`);
      await cmd('STARTTLS', [220]);
      const plain = socket as Socket;
      plain.removeAllListeners('data'); plain.removeAllListeners('close'); plain.removeAllListeners('error'); plain.setTimeout(0);
      buffer = ''; pending = [];
      await connectTls({ socket: plain });
      ehlo = await cmd('EHLO scottstechx.ug');
    }

    // Authenticate: PLAIN if offered, else LOGIN.
    const authLine = ehlo.lines.find((l) => /AUTH/i.test(l)) || '';
    if (/PLAIN/i.test(authLine)) {
      const token = Buffer.from(`\0${user}\0${pass}`, 'utf8').toString('base64');
      await cmd(`AUTH PLAIN ${token}`, [235]);
    } else {
      await cmd('AUTH LOGIN', [334]);
      await cmd(Buffer.from(user, 'utf8').toString('base64'), [334]);
      await cmd(Buffer.from(pass, 'utf8').toString('base64'), [235]);
    }

    await cmd(`MAIL FROM:<${from.address}>`);
    await cmd(`RCPT TO:<${to}>`, [250, 251]);
    await cmd('DATA', [354]);

    const crlf = (v: string) => v.replace(/\r?\n/g, '\r\n').replace(/^\./gm, '..');
    const head = [
      `From: ${headerValue(from.display)} <${from.address}>`,
      `To: <${to}>`,
      `Subject: ${headerValue(subject)}`,
      `Date: ${new Date().toUTCString()}`,
      `Message-ID: <${Date.now()}.${Math.random().toString(36).slice(2)}@${from.address.split('@')[1] || 'scottstechx.ug'}>`,
      'MIME-Version: 1.0',
    ];
    let msg: string;
    if (html) {
      const b = `stx_${Date.now().toString(36)}`;
      msg = [
        ...head,
        `Content-Type: multipart/alternative; boundary="${b}"`,
        '',
        `--${b}`,
        'Content-Type: text/plain; charset=utf-8',
        'Content-Transfer-Encoding: 8bit',
        '',
        crlf(text),
        `--${b}`,
        'Content-Type: text/html; charset=utf-8',
        'Content-Transfer-Encoding: 8bit',
        '',
        crlf(html),
        `--${b}--`,
      ].join('\r\n');
    } else {
      msg = [...head, 'Content-Type: text/plain; charset=utf-8', 'Content-Transfer-Encoding: 8bit', '', crlf(text)].join('\r\n');
    }
    await cmd(`${msg}\r\n.`, [250]);
    try { await cmd('QUIT', [221]); } catch { /* some servers just close */ }
    return { delivered: true };
  } catch (e: any) {
    const reason = e instanceof SmtpError
      ? `smtp ${e.code}: ${e.message}`
      : e?.code === 'ECONNREFUSED' || e?.name === 'AggregateError'
        ? `connection refused by ${host}:${port}`
        : (e?.message || String(e)).split('\n')[0].slice(0, 200);
    return { delivered: false, reason };
  } finally {
    try { socket?.end(); socket?.destroy(); } catch { /* closed */ }
  }
}

/** Send through a transactional-email HTTP API. */
async function apiSend(provider: HttpProvider, to: string, subject: string, text: string, html?: string): Promise<MailResult> {
  const from = parseFrom();
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), 15000);
  let url: string;
  let headers: Record<string, string>;
  let body: unknown;
  if (provider === 'brevo') {
    url = (process.env.MAIL_API_BASE || 'https://api.brevo.com') + '/v3/smtp/email';
    headers = { 'api-key': process.env.BREVO_API_KEY!, 'content-type': 'application/json', accept: 'application/json' };
    body = { sender: { name: from.display, email: from.address }, to: [{ email: to }], subject, textContent: text, ...(html ? { htmlContent: html } : {}) };
  } else if (provider === 'resend') {
    url = (process.env.MAIL_API_BASE || 'https://api.resend.com') + '/emails';
    headers = { authorization: `Bearer ${process.env.RESEND_API_KEY}`, 'content-type': 'application/json' };
    body = { from: `${from.display} <${from.address}>`, to: [to], subject, text, ...(html ? { html } : {}) };
  } else {
    url = (process.env.MAIL_API_BASE || 'https://api.sendgrid.com') + '/v3/mail/send';
    headers = { authorization: `Bearer ${process.env.SENDGRID_API_KEY}`, 'content-type': 'application/json' };
    body = {
      personalizations: [{ to: [{ email: to }] }],
      from: { email: from.address, name: from.display },
      subject,
      content: [{ type: 'text/plain', value: text }, ...(html ? [{ type: 'text/html', value: html }] : [])],
    };
  }
  try {
    const res = await fetch(url, { method: 'POST', headers, body: JSON.stringify(body), signal: ctrl.signal });
    if (res.ok) return { delivered: true };
    const detail = (await res.text().catch(() => '')).replace(/\s+/g, ' ').slice(0, 200);
    return { delivered: false, reason: `${provider} api ${res.status}: ${detail || res.statusText}` };
  } catch (e: any) {
    return { delivered: false, reason: `${provider} api: ${e?.name === 'AbortError' ? 'timed out' : e?.message || String(e)}` };
  } finally {
    clearTimeout(timer);
  }
}

export async function sendMail(to: string, subject: string, text: string, html?: string): Promise<MailResult> {
  if (!mailConfigured()) {
    console.log(`[mail] (no SMTP configured) to=${to} subject="${subject}"\n${text}`);
    return { delivered: false, reason: 'smtp-not-configured' };
  }
  let res: MailResult;
  const api = httpProvider();
  try {
    res = api ? await apiSend(api, to, subject, text, html) : await smtpSend(to, subject, text, html);
    // API configured but failing and SMTP is also available → try SMTP too.
    if (!res.delivered && api && smtpConfigured()) {
      const viaSmtp = await smtpSend(to, subject, text, html);
      if (viaSmtp.delivered) res = viaSmtp;
      else res = { delivered: false, reason: `${res.reason}; smtp: ${viaSmtp.reason}` };
    }
  } catch (e: any) {
    res = { delivered: false, reason: e?.message || 'send failed' };
  }
  if (res.delivered) {
    lastMailSuccess = { at: new Date().toISOString(), to };
    console.log(`[mail] delivered to=${to} subject="${subject}"`);
  } else {
    lastMailError = { at: new Date().toISOString(), reason: res.reason || 'unknown', to };
    console.error(`[mail] FAILED to=${to} via ${api ? `${api} api` : `${process.env.SMTP_HOST}:${process.env.SMTP_PORT || 465}`} — ${res.reason}`);
  }
  return res;
}
