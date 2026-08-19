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
