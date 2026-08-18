/**
 * ScottsTechX — Google Sign-In verification tests.
 *
 * POST /api/v1/auth/google must only ever trust a token that Google really
 * signed, for *our* OAuth client. Google's endpoints are unreachable from CI,
 * so this suite stands up a local JWKS server and mints its own RS256 tokens.
 * That is strictly better than mocking the verifier: the production code path
 * (jose signature check, iss/aud/exp validation, the upsert) runs untouched —
 * only the URL of the key set changes.
 *
 * Usage:
 *   node tests/google-auth.mjs                # boots its own API on :3902
 *   API_URL=http://127.0.0.1:3002 node tests/google-auth.mjs   # reuse a server
 *                                             # (must have GOOGLE_JWKS_URL set)
 */
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawn } from 'node:child_process';
import { SignJWT, exportJWK, generateKeyPair, importJWK } from 'jose';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(HERE, '..');

const JWKS_PORT = Number(process.env.TEST_JWKS_PORT || 4599);
const JWKS_URL = `http://127.0.0.1:${JWKS_PORT}/certs`;
const KID = 'stx-test-key';
const ISSUER = 'https://accounts.google.com';
/** Must match a real client id in google.route.ts DEFAULT_AUDIENCES. */
const AUD = '911393008938-f0an8p59rlkhimcnn9rdqbtbi1aa9hbk.apps.googleusercontent.com';

const RUN = `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 7)}`;
const bold = (s) => `\x1b[1m${s}\x1b[0m`;

let pass = 0;
let fail = 0;
const check = (name, ok, detail = '') => {
  if (ok) {
    pass++;
    console.log(`  \x1b[32m✓\x1b[0m ${name}`);
  } else {
    fail++;
    console.log(`  \x1b[31m✗\x1b[0m ${name}${detail ? `  — ${detail}` : ''}`);
  }
};

/* ── local Google ─────────────────────────────────────────────────── */

// A stable keypair keeps jose's JWKS cache valid if the API is reused.
const KEY_CACHE = '/tmp/stx-google-test-key.json';
async function loadKeys() {
  if (fs.existsSync(KEY_CACHE)) {
    const j = JSON.parse(fs.readFileSync(KEY_CACHE, 'utf8'));
    return { privateKey: await importJWK({ ...j.priv, alg: 'RS256' }, 'RS256'), pub: j.pub };
  }
  const { publicKey, privateKey } = await generateKeyPair('RS256', { extractable: true });
  const j = { priv: await exportJWK(privateKey), pub: await exportJWK(publicKey) };
  fs.writeFileSync(KEY_CACHE, JSON.stringify(j));
  return { privateKey, pub: j.pub };
}

const { privateKey, pub } = await loadKeys();
const publicJwk = { ...pub, kid: KID, alg: 'RS256', use: 'sig' };

async function mint(claims = {}, opts = {}) {
  const now = Math.floor(Date.now() / 1000);
  return new SignJWT({ email_verified: true, ...claims })
    .setProtectedHeader({ alg: 'RS256', kid: opts.kid || KID })
    .setIssuedAt(now)
    .setIssuer(opts.iss || ISSUER)
    .setAudience(opts.aud || AUD)
    .setExpirationTime(opts.exp ?? now + 3600)
    .setSubject(claims.sub || `sub-${RUN}`)
    .sign(opts.key || privateKey);
}

const jwksServer = await new Promise((resolve) => {
  const s = http.createServer((_req, res) => {
    res.setHeader('content-type', 'application/json');
    res.end(JSON.stringify({ keys: [publicJwk] }));
  });
  s.listen(JWKS_PORT, '127.0.0.1', () => resolve(s));
});

/* ── API under test ───────────────────────────────────────────────── */

let api = null;
let shutdownApi = () => {};
const API = process.env.API_URL || 'http://127.0.0.1:3902';

async function waitForApi(ms = 90_000) {
  const deadline = Date.now() + ms;
  while (Date.now() < deadline) {
    try {
      const r = await fetch(`${API}/healthz`);
      if (r.ok) return true;
    } catch {
      /* not up yet */
    }
    await new Promise((r) => setTimeout(r, 500));
  }
  return false;
}

// `npx tsx` spawns a grandchild that survives a plain child.kill(), and a
// leaked server keeps serving the *old* code — which then looks like a source
// bug on the next run. Own the whole process group and tear it down for real.
shutdownApi = function shutdownApi() {
  if (!api?.pid) return;
  try {
    process.kill(-api.pid, 'SIGKILL');
  } catch {
    try {
      api.kill('SIGKILL');
    } catch {
      /* already gone */
    }
  }
  api = null;
};

if (!process.env.API_URL) {
  // Refuse to run against a stale listener we did not start.
  const stale = await fetch(`${API}/healthz`).then((r) => r.ok).catch(() => false);
  if (stale) {
    console.error(
      `\nSomething is already listening on ${API}. It may be serving older code.\n` +
        `Stop it first, or set API_URL to target it deliberately.\n`
    );
    jwksServer.close();
    process.exit(1);
  }
  api = spawn('npx', ['tsx', 'src/server.ts'], {
    cwd: ROOT,
    env: { ...process.env, PORT: '3902', GOOGLE_JWKS_URL: JWKS_URL },
    stdio: 'ignore',
    detached: true,
  });
  for (const sig of ['SIGINT', 'SIGTERM', 'uncaughtException']) {
    process.once(sig, () => {
      shutdownApi();
      process.exit(1);
    });
  }
}
if (!(await waitForApi())) {
  console.error('API did not become healthy');
  jwksServer.close();
  shutdownApi();
  process.exit(1);
}

// Guard against the subtle failure where API_URL points at a server whose
// GOOGLE_JWKS_URL is different (or cached from an older key): every signature
// check would fail and look like a code bug.
if (process.env.API_URL && !process.env.TEST_SKIP_JWKS_GUARD) {
  const probe = await fetch(`${API}/api/v1/auth/google`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ idToken: await mint({ email: `probe-${RUN}@gmail.com`, sub: `probe-${RUN}` }) }),
  });
  if (probe.status !== 200) {
    console.error(
      `\nThe API at ${API} did not accept a token from this test IdP ` +
        `(HTTP ${probe.status}).\nStart it with GOOGLE_JWKS_URL=${JWKS_URL}, or run this file ` +
        `without API_URL so it boots its own server.\n`
    );
    jwksServer.close();
    process.exit(1);
  }
  await fetch(`${API}/api/v1/__test_noop`).catch(() => {});
}

const post = async (body) => {
  const r = await fetch(`${API}/api/v1/auth/google`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(body),
  });
  return { status: r.status, body: await r.json().catch(() => ({})) };
};

/* ── tests ────────────────────────────────────────────────────────── */

console.log(bold('\nGoogle Sign-In — happy path'));

const email = `googler-${RUN}@gmail.com`;
const sub = `sub-${RUN}`;

let r = await post({
  idToken: await mint({ email, sub, name: 'Google Tester', picture: 'https://example.com/p.png' }),
});
check('a valid Google token signs in', r.status === 200 && !!r.body.token, JSON.stringify(r.body).slice(0, 120));
check('the account is created already email-verified', r.body.user?.emailVerified === true);
check('the display name comes from the Google profile', r.body.user?.displayName === 'Google Tester');
check('the profile photo is stored', !!r.body.user?.profilePhotoUrl);
check('a new Google user is a buyer', r.body.user?.role === 'buyer');
const googleUserId = r.body.user?.id;

r = await post({ idToken: await mint({ email, sub, name: 'Renamed Later' }) });
check('signing in twice reuses one account', r.status === 200 && r.body.user?.id === googleUserId, `got ${r.body.user?.id}`);
check('an existing display name is not overwritten', r.body.user?.displayName === 'Google Tester');

console.log(bold('\nGoogle Sign-In — linking to a password account'));

const linkEmail = `linkme-${RUN}@gmail.com`;
const reg = await fetch(`${API}/api/v1/auth/register`, {
  method: 'POST',
  headers: { 'content-type': 'application/json' },
  body: JSON.stringify({ email: linkEmail, password: 'Password123!', displayName: 'Password Person' }),
});
const regBody = await reg.json().catch(() => ({}));
check('a password account exists to link against', reg.status === 200 || reg.status === 201, `register -> ${reg.status}`);

r = await post({ idToken: await mint({ email: linkEmail, sub: `sub-link-${RUN}`, name: 'Google Name' }) });
check('Google sign-in adopts the existing password account', r.status === 200 && r.body.user?.id === regBody.user?.id, `${r.body.user?.id} vs ${regBody.user?.id}`);
check('linking marks the address verified', r.body.user?.emailVerified === true);
check('linking keeps the original display name', r.body.user?.displayName === 'Password Person');

const stillPassword = await fetch(`${API}/api/v1/auth/login`, {
  method: 'POST',
  headers: { 'content-type': 'application/json' },
  body: JSON.stringify({ email: linkEmail, password: 'Password123!' }),
});
check('the password still works after linking', stillPassword.status === 200);

console.log(bold('\nGoogle Sign-In — rejected tokens'));

// Scoped so a regression leaves a cleanable row, not a stray 'a@b.c'.
const rejectEmail = `reject-${RUN}@gmail.com`;

r = await post({ idToken: await mint({ email: rejectEmail, sub: 's' }, { key: (await generateKeyPair('RS256', { extractable: true })).privateKey }) });
check('a token signed by a foreign key is refused', r.status === 401, `got ${r.status}`);

r = await post({ idToken: await mint({ email: rejectEmail, sub: 's' }, { aud: '000-attacker.apps.googleusercontent.com' }) });
check('a token minted for another app is refused', r.status === 401, `got ${r.status}`);

r = await post({ idToken: await mint({ email: rejectEmail, sub: 's' }, { iss: 'https://evil.example.com' }) });
check('a token from a foreign issuer is refused', r.status === 401, `got ${r.status}`);

r = await post({ idToken: await mint({ email: rejectEmail, sub: 's' }, { exp: Math.floor(Date.now() / 1000) - 3600 }) });
check('an expired token is refused', r.status === 401, `got ${r.status}`);

r = await post({ idToken: await mint({ email: rejectEmail, sub: 's', email_verified: false }) });
check('an unverified Google address is refused', r.status === 401, `got ${r.status}`);

r = await post({ idToken: await mint({ sub: `s-no-email-${RUN}` }) });
check('a token with no email claim is refused', r.status === 401, `got ${r.status}`);

r = await post({ idToken: await mint({ email: rejectEmail, sub: 's' }, { kid: 'unknown-kid' }) });
check('a token naming an unknown signing key is refused', r.status === 401, `got ${r.status}`);

r = await post({ idToken: 'garbage.not.a.jwt' });
check('a malformed token is a 401, never a 500', r.status === 401, `got ${r.status}`);

r = await post({});
check('a missing idToken is a 400', r.status === 400, `got ${r.status}`);

r = await post({ idToken: 'short' });
check('a too-short idToken is a 400', r.status === 400, `got ${r.status}`);

/* ── cleanup ──────────────────────────────────────────────────────── */

console.log(bold('\nCleanup'));
try {
  const { Client } = await import('pg');
  const client = new Client(process.env.DATABASE_URL || 'postgres://app:app@127.0.0.1:5433/scottstechx');
  await client.connect();
  const del = await client.query('DELETE FROM users WHERE email LIKE $1', [`%-${RUN}@gmail.com`]);
  check('test accounts removed', del.rowCount >= 2, `deleted ${del.rowCount}`);
  await client.end();
} catch (err) {
  check('test accounts removed', false, String(err).slice(0, 120));
}

console.log(`\n${bold(`Result: ${pass} passed, ${fail} failed`)}\n`);
jwksServer.close();
shutdownApi();
process.exit(fail ? 1 : 0);
