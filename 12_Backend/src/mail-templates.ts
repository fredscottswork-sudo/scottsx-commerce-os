/**
 * Branded HTML emails.
 *
 * Table-based, inline-styled, no external CSS — the only thing that renders
 * consistently across Gmail, Outlook, Apple Mail and Yahoo. The logo is the
 * PNG the web app already serves at /brand/, so no attachment is needed.
 */

const FALLBACK_WEB_URL = 'https://scottstechx-web.onrender.com';

export function webUrl(): string {
  return (process.env.PUBLIC_WEB_URL?.trim() || FALLBACK_WEB_URL).replace(/\/+$/, '');
}

function esc(s: string): string {
  return s.replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]!));
}

interface Layout {
  preheader: string;
  title: string;
  intro: string;
  /** Big centred block: the code, or a button. */
  focal: string;
  outro: string[];
  footnote: string;
}

function layout(o: Layout): string {
  const base = webUrl();
  const logo = `${base}/brand/scottstechx-logo-transparent.png`;
  const year = new Date().getFullYear();
  return `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="color-scheme" content="light dark">
<title>${esc(o.title)}</title>
</head>
<body style="margin:0;padding:0;background:#0b1220;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;">
<div style="display:none;max-height:0;overflow:hidden;opacity:0;color:transparent;">${esc(o.preheader)}&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;</div>
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:#0b1220;">
<tr><td align="center" style="padding:32px 16px;">
  <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="max-width:560px;">
    <tr><td align="center" style="padding:0 0 22px;">
      <a href="${base}" style="text-decoration:none;"><img src="${logo}" width="220" alt="ScottsTechX" style="display:block;width:220px;max-width:70%;height:auto;border:0;"></a>
    </td></tr>
    <tr><td style="background:#ffffff;border-radius:18px;padding:36px 32px 28px;">
      <h1 style="margin:0 0 10px;font-size:22px;line-height:1.3;color:#0f172a;letter-spacing:-0.01em;">${esc(o.title)}</h1>
      <p style="margin:0 0 22px;font-size:15px;line-height:1.6;color:#475569;">${o.intro}</p>
      ${o.focal}
      ${o.outro.map((p) => `<p style="margin:0 0 12px;font-size:14px;line-height:1.6;color:#475569;">${p}</p>`).join('')}
      <hr style="border:0;border-top:1px solid #e2e8f0;margin:22px 0 16px;">
      <p style="margin:0;font-size:12.5px;line-height:1.6;color:#94a3b8;">${o.footnote}</p>
    </td></tr>
    <tr><td align="center" style="padding:22px 12px 0;font-size:12px;line-height:1.7;color:#64748b;">
      ScottsTechX Enterprises (U) Ltd &middot; Kampala, Uganda<br>
      <a href="${base}" style="color:#5b9bff;text-decoration:none;">${esc(base.replace(/^https?:\/\//, ''))}</a>
      &nbsp;&middot;&nbsp; Innovate. Integrate. Elevate.<br>
      <span style="color:#475569;">&copy; ${year} ScottsTechX. This is an automated message; replies are not monitored.</span>
    </td></tr>
  </table>
</td></tr>
</table>
</body></html>`;
}

function codeBlock(code: string): string {
  const spaced = code.split('').join('&nbsp;&nbsp;');
  return `<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="margin:0 0 22px;">
  <tr><td align="center" style="background:#f1f5f9;border:1px solid #e2e8f0;border-radius:14px;padding:22px 16px;">
    <div style="font-size:11px;letter-spacing:.18em;text-transform:uppercase;color:#64748b;margin-bottom:8px;">Your code</div>
    <div style="font-family:'SF Mono',Menlo,Consolas,'Liberation Mono',monospace;font-size:36px;font-weight:800;letter-spacing:.12em;color:#0f172a;">${spaced}</div>
  </td></tr></table>`;
}

function button(href: string, label: string): string {
  return `<table role="presentation" cellpadding="0" cellspacing="0" align="center" style="margin:0 auto 22px;">
  <tr><td style="border-radius:12px;background:#1e6fff;">
    <a href="${href}" style="display:inline-block;padding:14px 28px;font-size:15px;font-weight:700;color:#ffffff;text-decoration:none;border-radius:12px;">${esc(label)}</a>
  </td></tr></table>
  <p style="margin:-10px 0 18px;text-align:center;font-size:12px;color:#94a3b8;word-break:break-all;">Or paste this link: <a href="${href}" style="color:#5b9bff;">${esc(href)}</a></p>`;
}

/** Sign-in / sign-up code (passwordless). */
export function signInCodeEmail(code: string, opts: { isNew: boolean; ttlMin: number; email: string }) {
  const subject = `${code} is your ScottsTechX ${opts.isNew ? 'sign-up' : 'sign-in'} code`;
  const text =
    `Your ScottsTechX ${opts.isNew ? 'sign-up' : 'sign-in'} code is:\n\n    ${code}\n\n` +
    `Enter it on the page where you typed your email (${opts.email}). It expires in ${opts.ttlMin} minutes and works once.\n\n` +
    `If you did not request this, you can ignore this email — nobody can sign in without the code.\n\n— ScottsTechX`;
  const html = layout({
    preheader: `${code} — your ${opts.isNew ? 'sign-up' : 'sign-in'} code, valid ${opts.ttlMin} minutes`,
    title: opts.isNew ? 'Welcome to ScottsTechX' : 'Your sign-in code',
    intro: opts.isNew
      ? `You're one step from your new account. Enter this code on the page where you typed <strong style="color:#0f172a;">${esc(opts.email)}</strong>:`
      : `Enter this code on the page where you typed <strong style="color:#0f172a;">${esc(opts.email)}</strong>:`,
    focal: codeBlock(code),
    outro: [
      `The code expires in <strong>${opts.ttlMin} minutes</strong> and can only be used once. Never share it — ScottsTechX staff will never ask for it.`,
    ],
    footnote: `Didn't try to sign in? You can safely ignore this email; nobody can access your account without the code.`,
  });
  return { subject, text, html };
}

/** Email-address verification for classic sign-up (code + optional link). */
export function verifyEmailEmail(code: string, opts: { ttlMin: number; link?: string; displayName?: string }) {
  const subject = `${code} is your ScottsTechX verification code`;
  const hi = opts.displayName ? `Hi ${opts.displayName},` : 'Hi,';
  const text =
    `${hi}\n\nYour ScottsTechX verification code is:\n\n    ${code}\n\n` +
    (opts.link ? `Or confirm with one click:\n${opts.link}\n\n` : '') +
    `The code expires in ${opts.ttlMin} minutes.\n\nIf you did not create an account, ignore this email.\n\n— ScottsTechX`;
  const html = layout({
    preheader: `${code} — verify your email address`,
    title: 'Verify your email address',
    intro: `${esc(hi)} confirm this address to finish setting up your ScottsTechX account.`,
    focal: codeBlock(code) + (opts.link ? button(opts.link, 'Verify my email') : ''),
    outro: [`The code expires in <strong>${opts.ttlMin} minutes</strong>.`],
    footnote: `If you did not create an account, you can ignore this email.`,
  });
  return { subject, text, html };
}

/** Password reset link. */
export function passwordResetEmail(link: string, opts: { ttlMin: number; displayName?: string }) {
  const subject = 'Reset your ScottsTechX password';
  const hi = opts.displayName ? `Hi ${opts.displayName},` : 'Hi,';
  const text = `${hi}\n\nReset your password with this link (valid ${opts.ttlMin} minutes):\n${link}\n\nIf you did not ask for this, ignore this email — your password is unchanged.\n\n— ScottsTechX`;
  const html = layout({
    preheader: 'Reset your ScottsTechX password',
    title: 'Reset your password',
    intro: `${esc(hi)} we received a request to reset the password for your account. Click below to choose a new one.`,
    focal: button(link, 'Choose a new password'),
    outro: [`This link is valid for <strong>${opts.ttlMin} minutes</strong>.`],
    footnote: `If you did not request a reset, you can ignore this email — your password stays the same.`,
  });
  return { subject, text, html };
}
