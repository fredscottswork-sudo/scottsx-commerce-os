#!/usr/bin/env node
/**
 * ScottsTechX — production-mode safety checks.
 *
 * Every other suite runs the server in development mode, which is exactly how
 * a production-only flaw stays invisible. This one boots the API with
 * NODE_ENV=production against the same database and asserts the rules that
 * only apply there.
 *
 * The flaw that prompted it: with no SMTP configured, /auth/register returned
 * the six-digit verification code in its own response body. That is a
 * deliberate local-development convenience, but the condition was "no mailer",
 * not "no mailer AND not deployed" — and the deployed backend has no mailer.
 * So anyone could register a fake address, read the code out of the HTTP
 * response, verify it, and get a fully privileged account. Email verification
 * was, in production, decorative.
 *
 * Usage:  node tests/production-safety.mjs      (from 12_Backend/)
 */
import { spawn } from 'node:child_process';
import { setTimeout as sleep } from 'node:timers/promises';

const PORT = Number(process.env.TEST_PROD_PORT || 3011);
const API = `http://127.0.0.1:${PORT}/api/v1`;
const DB = process.env.DATABASE_URL || 'postgres://app:app@127.0.0.1:5433/scottstechx';

let pass = 0;
let fail = 0;
const failures = [];
const ok = (n) => { pass++; console.log(`  \x1b[32m✓\x1b[0m ${n}`); };
const bad = (n, d = '') => {
  fail++;
  failures.push(`${n}${d ? ` — ${d}` : ''}`);
  console.log(`  \x1b[31m✗\x1b[0m ${n}${d ? ` — ${d}` : ''}`);
};
const check = (n, c, d = '') => (c ? ok(n) : bad(n, d));

async function call(path, { method = 'GET', body, token } = {}) {
  const headers = { 'content-type': 'application/json' };
  if (token) headers.authorization = `Bearer ${token}`;
  const res = await fetch(API + path, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  });
  let data = null;
  try { data = await res.json(); } catch { /* empty body */ }
  return { status: res.status, data };
}

/** Boot an API with the given extra env and wait for it to answer. */
async function boot(env, label) {
  const child = spawn('npx', ['tsx', 'src/server.ts'], {
    env: { ...process.env, DATABASE_URL: DB, PORT: String(PORT), NODE_ENV: 'production', ...env },
    stdio: ['ignore', 'pipe', 'pipe'],
    detached: true,
  });
  let log = '';
  child.stdout.on('data', (d) => { log += d.toString(); });
  child.stderr.on('data', (d) => { log += d.toString(); });

  for (let i = 0; i < 60; i++) {
    await sleep(500);
    try {
      const r = await fetch(`http://127.0.0.1:${PORT}/api/v1/geo/status`);
      if (r.ok) return { child, log: () => log };
    } catch { /* not up yet */ }
  }
  console.error(`[production-safety] ${label} did not start in 30s:\n${log}`);
  try { process.kill(-child.pid, 'SIGKILL'); } catch { /* already gone */ }
  process.exit(1);
}

function stop(handle) {
  try { process.kill(-handle.child.pid, 'SIGKILL'); } catch { /* already gone */ }
}

const stamp = Date.now();
const created = [];

console.log('\n\x1b[1mProduction-mode safety\x1b[0m');

// ── 1. No mailer in production: sign-up must refuse, not leak a code ────────
console.log('\n\x1b[1m1. A production server with no mailer cannot mint accounts\x1b[0m');
{
  const srv = await boot({}, 'production/no-SMTP');

  const email = `prodleak_${stamp}@totally-not-real.invalid`;
  const reg = await call('/auth/register', {
    method: 'POST',
    body: { email, password: 'Test123!', displayName: 'Fake', role: 'seller', storeName: 'F' },
  });

  check('email/password sign-up is refused (503)', reg.status === 503, `got ${reg.status}`);
  check('the refusal explains why', /verification email/i.test(reg.data?.error || ''),
    reg.data?.error);
  check('no session token is issued', !reg.data?.token);
  check('no verification code is leaked',
    !JSON.stringify(reg.data || {}).match(/\b\d{6}\b/), JSON.stringify(reg.data));

  // The account must not exist at all — a half-created account that can never
  // be verified is worse than a clean refusal.
  const login = await call('/auth/login', { method: 'POST', body: { email, password: 'Test123!' } });
  check('no account was created', login.status === 401, `got ${login.status}`);

  // Everything that does not depend on our mailer must keep working.
  const seeded = await call('/auth/login', {
    method: 'POST', body: { email: 'techhub@scottstechx.ug', password: 'Seller123!' },
  });
  check('existing verified users can still sign in', seeded.status === 200, `got ${seeded.status}`);
  check('their session still reaches private routes',
    (await call('/me/cart', { token: seeded.data?.token })).status === 200);

  // Google proves the address itself, so it must not be collateral damage.
  const google = await call('/auth/google', { method: 'POST', body: { idToken: 'bogus' } });
  check('Google sign-in is unaffected (rejects a bad token, not the route)',
    google.status === 400 || google.status === 401, `got ${google.status}`);

  const startup = srv.log();
  check('the server says loudly that sign-up is disabled',
    /NO MAILER in production/.test(startup), startup.slice(-200));

  stop(srv);
}

// ── 2. With SMTP configured, production sign-up works and stays quiet ───────
console.log('\n\x1b[1m2. With a mailer, production sign-up works and never returns the code\x1b[0m');
{
  const srv = await boot(
    { SMTP_HOST: 'smtp.invalid.test', SMTP_USER: 'u', SMTP_PASS: 'p', MAIL_FROM: 'no-reply@x.test' },
    'production/with-SMTP'
  );

  const email = `prodok_${stamp}@example.test`;
  const reg = await call('/auth/register', {
    method: 'POST',
    body: { email, password: 'Test123!', displayName: 'Prod OK', role: 'buyer' },
  });
  check('sign-up succeeds', reg.status === 201, `got ${reg.status}`);
  if (reg.data?.user?.id) created.push({ id: reg.data.user.id, token: reg.data.token });

  check('the account starts unverified', reg.data?.user?.emailVerified === false);
  check('the code is NOT in the response',
    !JSON.stringify(reg.data || {}).match(/\b\d{6}\b/), JSON.stringify(reg.data));
  check('no devCode field at all', reg.data?.verification?.devCode === undefined);

  // Asking again must not reveal it either.
  const again = await call('/auth/verify/request', { method: 'POST', token: reg.data?.token });
  check('resending does not reveal the code', again.data?.devCode === undefined,
    JSON.stringify(again.data));

  // And the gate still applies to this unverified account.
  const cart = await call('/me/cart', { token: reg.data?.token });
  check('the unverified account is still gated', cart.status === 403, `got ${cart.status}`);

  const startup = srv.log();
  check('the server reports SMTP is configured',
    /SMTP configured/.test(startup), startup.slice(-200));

  stop(srv);
}

// ── 2b. An account stranded before this rule can still get out ──────────────
console.log('\n\x1b[1m2b. A pre-existing unverified account is told the truth\x1b[0m');
{
  // Accounts created before verification was enforceable are unverified and,
  // on a mailerless server, cannot be emailed. The API used to answer their
  // resend request with a flat `sent: true`, so the user was told to check an
  // inbox that would never receive anything - with no code shown either,
  // because production does not leak codes. That is a dead end presented as
  // progress.
  //
  // Create the stranded account while codes are permitted, then observe how a
  // strict production server treats it.
  const optIn = await boot({ ALLOW_DEV_VERIFICATION_CODES: 'true' }, 'seed-stranded');
  const email = `stranded_${stamp}@example.test`;
  const reg = await call('/auth/register', {
    method: 'POST',
    body: { email, password: 'Test123!', displayName: 'Stranded', role: 'buyer' },
  });
  const token = reg.data?.token;
  const userId = reg.data?.user?.id;
  if (userId) created.push({ id: userId, token });
  check('a stranded account exists and is unverified',
    reg.status === 201 && reg.data?.user?.emailVerified === false, `got ${reg.status}`);
  stop(optIn);

  // A real stranded account registered months ago, so its resend is not in a
  // cooldown window. This one was created seconds ago purely to set the scene,
  // so the cooldown is switched off here to keep that seeding artifact out of
  // the way - what is under test is what the server SAYS about delivery. The
  // rate limit itself is tested at its production defaults in 2c.
  const srv = await boot({ VERIFY_RESEND_COOLDOWN_SEC: '0' }, 'production/stranded');

  // It is gated, as it should be.
  check('the stranded account is gated',
    (await call('/me/cart', { token })).status === 403);

  const resend = await call('/auth/verify/request', { method: 'POST', token });
  check('resending is accepted', resend.status === 200, `got ${resend.status}`);
  // The heart of it: do not claim an email is on its way when none is.
  check('the server does NOT claim the email was sent',
    resend.data?.sent === false, JSON.stringify(resend.data));
  check('the server says plainly that it cannot deliver',
    resend.data?.undeliverable === true, JSON.stringify(resend.data));
  check('and still does not leak the code',
    resend.data?.devCode === undefined && !JSON.stringify(resend.data).match(/\b\d{6}\b/),
    JSON.stringify(resend.data));

  // The way out is Google, which proves the address without any mailer. The
  // adoption is by email, so the SAME account is upgraded rather than a second
  // one created - orders, cart and messages survive.
  check('Google sign-in remains available as the way out',
    [400, 401].includes((await call('/auth/google', { method: 'POST', body: { idToken: 'bogus' } })).status));

  stop(srv);
}

// ── 3. The escape hatch is opt-in and explicit ──────────────────────────────
console.log('\n\x1b[1m3. ALLOW_DEV_VERIFICATION_CODES is an explicit opt-in\x1b[0m');
{
  const srv = await boot({ ALLOW_DEV_VERIFICATION_CODES: 'true' }, 'production/opt-in');

  const email = `prodoptin_${stamp}@example.test`;
  const reg = await call('/auth/register', {
    method: 'POST',
    body: { email, password: 'Test123!', displayName: 'Opt In', role: 'buyer' },
  });
  check('sign-up is allowed again', reg.status === 201, `got ${reg.status}`);
  if (reg.data?.user?.id) created.push({ id: reg.data.user.id, token: reg.data.token });
  check('and the code is returned, as asked for',
    /^\d{6}$/.test(String(reg.data?.verification?.devCode)),
    String(reg.data?.verification?.devCode));

  stop(srv);
}

// ── 2c. Verification emails are rate limited ────────────────────────────────
// Firebase's free tier sends 1,000 verification emails per DAY for the entire
// project. An unthrottled resend endpoint means a single account can burn the
// whole day's quota, and every other user silently stops receiving mail. The
// same endpoint is also a way to flood a stranger's inbox: sign up with their
// address and hold down resend.
//
// This suite boots its own server, so the limit is tested at its real
// production defaults - no cooldown override.
console.log('\n\x1b[1m2c. Verification emails are rate limited\x1b[0m');
{
  const srv = await boot({ ALLOW_DEV_VERIFICATION_CODES: 'true' }, 'rate limit');
  const email = `ratelimit_${stamp}@example.test`;
  const reg = await call('/auth/register', {
    method: 'POST',
    body: { email, password: 'Rate123!', displayName: 'Rate Limit Probe' },
  });
  check('the account registers', reg.status === 201, `got ${reg.status}`);
  if (reg.data?.user?.id) created.push({ id: reg.data.user.id });
  const token = reg.data?.token;

  // Registration already sent one email. The immediate resend is the abuse.
  const first = await call('/auth/verify/request', { method: 'POST', token });
  check('an immediate resend is refused with 429', first.status === 429, `got ${first.status}`);
  check('the refusal explains the wait in seconds',
    typeof first.data?.retryAfterSec === 'number' && first.data.retryAfterSec > 0,
    JSON.stringify(first.data));
  check('the refusal is readable, not a raw error code',
    typeof first.data?.error === 'string' && /wait/i.test(first.data.error),
    JSON.stringify(first.data?.error));
  check('a throttled resend does not leak a code', !('devCode' in (first.data ?? {})));

  // The real damage is volume, so prove the flood is actually stopped.
  let accepted = 0;
  let limited = 0;
  for (let i = 0; i < 15; i++) {
    const r = await call('/auth/verify/request', { method: 'POST', token });
    if (r.status === 200) accepted++;
    else if (r.status === 429) limited++;
  }
  check('a 15-request flood sends no further emails', accepted === 0, `${accepted} accepted`);
  check('every flood request was refused', limited === 15, `${limited}/15 limited`);

  // Being throttled must not lock the user out of their own account: the code
  // registration already issued has to keep working.
  const confirmed = await call('/auth/verify/confirm', {
    method: 'POST', token, body: { code: reg.data?.verification?.devCode },
  });
  check('the original code still verifies the account', confirmed.status === 200,
    `got ${confirmed.status}`);

  // And once verified, resending is answered politely rather than throttled.
  const after = await call('/auth/verify/request', { method: 'POST', token });
  check('a verified account is told it is already verified',
    after.status === 200 && after.data?.alreadyVerified === true, `got ${after.status}`);

  stop(srv);
}

// ── 2d. A resent code supersedes the one before it ──────────────────────────
// If old codes stayed valid, every resend would widen the window an attacker
// has to guess in, and a code read over someone's shoulder would never expire
// by being replaced. e2e cannot rely on testing this (its server may be
// running with production cooldowns, which throttle the second request), so it
// is pinned here where the cooldown can be turned off deliberately.
console.log('\n\x1b[1m2d. A resent verification code replaces the old one\x1b[0m');
{
  const srv = await boot(
    { ALLOW_DEV_VERIFICATION_CODES: 'true', VERIFY_RESEND_COOLDOWN_SEC: '0' },
    'supersede',
  );
  const reg = await call('/auth/register', {
    method: 'POST',
    body: {
      email: `supersede_${stamp}@example.test`,
      password: 'Super123!',
      displayName: 'Supersede Probe',
    },
  });
  if (reg.data?.user?.id) created.push({ id: reg.data.user.id });
  const token = reg.data?.token;
  const original = reg.data?.verification?.devCode;
  check('the first code is issued', /^\d{6}$/.test(String(original)));

  const resent = await call('/auth/verify/request', { method: 'POST', token });
  const fresh = resent.data?.devCode;
  check('a resend issues a different code',
    resent.status === 200 && /^\d{6}$/.test(String(fresh)) && fresh !== original,
    `got ${resent.status}`);

  const stale = await call('/auth/verify/confirm', {
    method: 'POST', token, body: { code: original },
  });
  check('the superseded code is rejected', stale.status >= 400, `got ${stale.status}`);

  const ok = await call('/auth/verify/confirm', { method: 'POST', token, body: { code: fresh } });
  check('the newest code verifies the account',
    ok.status === 200 && ok.data?.verified === true, `got ${ok.status}`);

  const replay = await call('/auth/verify/confirm', { method: 'POST', token, body: { code: fresh } });
  check('a spent code cannot be replayed', replay.status >= 400, `got ${replay.status}`);
  stop(srv);
}

// ── 2e. The verification LINK token is never leaked in production ───────────
// The link token is a bearer credential: whoever holds it can verify that
// address and receive a working session, without a password. It travels in a
// URL, so it is longer-lived in logs and history than a typed code - which
// makes leaking it in an API response strictly worse than leaking the code.
//
// devLink exists for mailerless local development, on exactly the same terms
// as devCode. This proves the production gate covers the new field too: a
// guard written for one secret does not automatically cover the next one added
// beside it.
console.log('\n\x1b[1m2e. Verification link tokens stay secret in production\x1b[0m');
{
  const srv = await boot({ SMTP_HOST: 'localhost', SMTP_USER: 'u', SMTP_PASS: 'p',
    PUBLIC_WEB_URL: 'https://example.test' }, 'production/link');
  const email = `prodlink_${stamp}@example.test`;
  const reg = await call('/auth/register', {
    method: 'POST',
    body: { email, password: 'Link123!', displayName: 'Prod Link Probe' },
  });
  check('registration is accepted when a mailer is configured', reg.status === 201,
    `got ${reg.status}`);
  if (reg.data?.user?.id) created.push({ id: reg.data.user.id });

  const v = reg.data?.verification ?? {};
  check('the response does NOT contain the link', v.devLink === undefined, JSON.stringify(v));
  check('the response does NOT contain the code', v.devCode === undefined, JSON.stringify(v));

  // Nothing resembling a token or a verify URL anywhere in the payload.
  const raw = JSON.stringify(reg.data ?? {});
  check('no verification URL appears anywhere in the response',
    !/verify-email\?token=/.test(raw), raw.slice(0, 160));
  check('no 6-digit code appears anywhere in the response',
    !/\b\d{6}\b/.test(raw), raw.slice(0, 160));

  // The server should still report that a link WAS built and sent, because
  // that is what the UI tells the user to go and click.
  check('but it still reports that a link was sent', v.linkSent === true, JSON.stringify(v));

  // A guessed token must not verify anyone.
  const guess = await call('/auth/verify/link', {
    method: 'POST', body: { token: 'Z'.repeat(43) },
  });
  check('a guessed link token is refused', guess.status >= 400, `got ${guess.status}`);
  check('and the refusal returns no session',
    guess.data?.token === undefined, JSON.stringify(guess.data));

  stop(srv);
}

// ── Cleanup ─────────────────────────────────────────────────────────────────
console.log('\n\x1b[1mCleanup\x1b[0m');
{
  const srv = await boot({ ALLOW_DEV_VERIFICATION_CODES: 'true' }, 'cleanup');
  const admin = await call('/auth/login', {
    method: 'POST', body: { email: 'admin@scottstechx.ug', password: 'Admin123!' },
  });
  let removed = 0;
  for (const u of created) {
    const r = await call(`/admin/users/${u.id}`, { method: 'DELETE', token: admin.data?.token });
    if (r.status === 200) removed++;
  }
  check('production test accounts removed', removed === created.length,
    `${removed}/${created.length}`);
  stop(srv);
}

console.log(`\n\x1b[1mResult: ${pass} passed, ${fail} failed\x1b[0m`);
if (failures.length) {
  console.log('\n\x1b[31mFailures:\x1b[0m');
  failures.forEach((f) => console.log(`  • ${f}`));
}
process.exit(fail ? 1 : 0);
