/**
 * ScottsTechX — Firebase Authentication verification tests.
 *
 * POST /api/v1/auth/firebase/sign-in must only ever trust a token that Google
 * really signed, for *our* Firebase project. Google's endpoints are
 * unreachable from CI, so this suite stands up a local JWKS server and mints
 * its own RS256 tokens. That is strictly better than mocking the verifier: the
 * production code path (jose signature check, iss/aud/exp validation, the
 * upsert) runs untouched — only the URL of the key set changes.
 *
 * The security property under test is the one that makes Firebase worth using:
 * `email_verified` is a signed claim. The client cannot assert it, so a user
 * who has not clicked Firebase's verification link cannot obtain a verified
 * account — which is exactly the fake-email hole we are closing.
 *
 * Usage:
 *   node tests/firebase-auth.mjs                # boots its own API on :3903
 */
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawn } from 'node:child_process';
import { SignJWT, exportJWK, generateKeyPair, importJWK } from 'jose';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(HERE, '..');

const JWKS_PORT = Number(process.env.TEST_FB_JWKS_PORT || 4598);
const JWKS_URL = `http://127.0.0.1:${JWKS_PORT}/certs`;
const KID = 'stx-fb-test-key';
const PROJECT = 'scottstechx-52bab';
const ISSUER = `https://securetoken.google.com/${PROJECT}`;

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

/* ── local Firebase ───────────────────────────────────────────────── */

const KEY_CACHE = '/tmp/stx-firebase-test-key.json';
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

/** Mint a token shaped exactly like one Firebase Authentication issues. */
async function mint(claims = {}, opts = {}) {
  const now = Math.floor(Date.now() / 1000);
  const {
    email_verified = true,
    provider = 'password',
    sub = `fbuid-${RUN}`,
    ...rest
  } = claims;
  return new SignJWT({
    email_verified,
    auth_time: opts.authTime ?? now,
    firebase: { sign_in_provider: provider, identities: {} },
    ...rest,
  })
    .setProtectedHeader({ alg: 'RS256', kid: opts.kid || KID })
    .setIssuedAt(now)
    .setIssuer(opts.iss || ISSUER)
    .setAudience(opts.aud || PROJECT)
    .setExpirationTime(opts.exp ?? now + 3600)
    .setSubject(sub)
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
const API = process.env.API_URL || 'http://127.0.0.1:3903';

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
// leaked server keeps serving the *old* code. Own the process group.
function shutdownApi() {
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
}

if (!process.env.API_URL) {
  const stale = await fetch(`${API}/healthz`).then((r) => r.ok).catch(() => false);
  if (stale) {
    console.error(`\nSomething is already listening on ${API}. Stop it first.\n`);
    jwksServer.close();
    process.exit(1);
  }
  api = spawn('npx', ['tsx', 'src/server.ts'], {
    cwd: ROOT,
    env: {
      ...process.env,
      PORT: '3903',
      FIREBASE_JWKS_URL: JWKS_URL,
      FIREBASE_ISSUER: ISSUER,
      FIREBASE_PROJECT_ID: PROJECT,
    },
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

const signIn = async (idToken) => {
  const r = await fetch(`${API}/api/v1/auth/firebase/sign-in`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ idToken }),
  });
  return { status: r.status, body: await r.json().catch(() => ({})) };
};

const created = [];

/* ── tests ────────────────────────────────────────────────────────── */

console.log(bold('\nFirebase sign-in — happy path'));
{
  const email = `fb-${RUN}@gmail.com`;
  const res = await signIn(await mint({ email, sub: `uid-happy-${RUN}`, name: 'Firebase User', provider: 'google.com' }));
  check('a valid Firebase token is accepted', res.status === 200, JSON.stringify(res.body).slice(0, 140));
  check('it returns a ScottsTechX session', typeof res.body.token === 'string' && res.body.token.length > 20);
  check('the account carries the right address', res.body.user?.email === email);
  check('a Google-provider account is verified', res.body.user?.emailVerified === true);
  check('new Firebase users default to buyer', res.body.user?.role === 'buyer');
  if (res.body.user?.id) created.push(res.body.user.id);

  // Signing in again must reuse the same row, not create a second one.
  const again = await signIn(await mint({ email, sub: `uid-happy-${RUN}`, provider: 'google.com' }));
  check('signing in again reuses the same account', again.body.user?.id === res.body.user?.id);
}

console.log(bold('\nemail_verified is attested by Firebase, not asserted by the client'));
{
  // This is the fake-email hole. An unverified password sign-up must NOT
  // arrive as a verified account.
  const email = `fbunverified-${RUN}@example.com`;
  const res = await signIn(await mint({ email, sub: `uid-unver-${RUN}`, email_verified: false, provider: 'password' }));
  check('an unverified password account is still allowed to sign in', res.status === 200,
    JSON.stringify(res.body).slice(0, 140));
  check('…but it is NOT marked verified', res.body.user?.emailVerified === false,
    `got ${res.body.user?.emailVerified}`);
  if (res.body.user?.id) created.push(res.body.user.id);

  // And the gate that depends on it must hold.
  const upgrade = await fetch(`${API}/api/v1/auth/firebase/upgrade-to-seller`, {
    method: 'POST',
    headers: { authorization: `Bearer ${res.body.token}` },
  });
  check('an unverified account cannot become a seller', upgrade.status >= 400, `got ${upgrade.status}`);

  // Once Firebase says verified, the flag flips.
  const verified = await signIn(await mint({ email, sub: `uid-unver-${RUN}`, email_verified: true, provider: 'password' }));
  check('verifying in Firebase promotes the account', verified.body.user?.emailVerified === true);
}

console.log(bold('\nVerification never moves backwards'));
{
  const email = `fbforward-${RUN}@example.com`;
  const v = await signIn(await mint({ email, sub: `uid-fwd-${RUN}`, email_verified: true, provider: 'password' }));
  check('account starts verified', v.body.user?.emailVerified === true);
  if (v.body.user?.id) created.push(v.body.user.id);

  // A stale token (issued before the user verified) must not un-verify them.
  const stale = await signIn(await mint({ email, sub: `uid-fwd-${RUN}`, email_verified: false, provider: 'password' }));
  check('a stale unverified token does not un-verify the account',
    stale.body.user?.emailVerified === true, `got ${stale.body.user?.emailVerified}`);
}

console.log(bold('\nForged and mis-issued tokens are rejected'));
{
  const email = `fbevil-${RUN}@example.com`;
  const evilSub = `uid-evil-${RUN}`;

  const otherKey = await generateKeyPair('RS256', { extractable: true });
  check('a token signed by someone else is rejected',
    (await signIn(await mint({ email }, { key: otherKey.privateKey }))).status === 401);

  check('a token for another Firebase project is rejected',
    (await signIn(await mint({ email }, { aud: 'someone-elses-project' }))).status === 401);

  check('a token from the wrong issuer is rejected',
    (await signIn(await mint({ email }, { iss: 'https://securetoken.google.com/evil' }))).status === 401);

  const now = Math.floor(Date.now() / 1000);
  check('an expired token is rejected',
    (await signIn(await mint({ email }, { exp: now - 120 }))).status === 401);

  check('a token with an unknown signing key is rejected',
    (await signIn(await mint({ email }, { kid: 'no-such-key' }))).status === 401);

  check('a future auth_time is rejected',
    (await signIn(await mint({ email }, { authTime: now + 3600 }))).status === 401);

  check('a plain Google id_token is not accepted here',
    (await signIn(await mint({ email }, { iss: 'https://accounts.google.com', aud: 'x.apps.googleusercontent.com' }))).status === 401);

  check('garbage is rejected', (await signIn('not-a-jwt-at-all')).status >= 400);

  check('an identity with no email is rejected',
    (await signIn(await mint({ sub: `noemail-${RUN}` }))).status >= 400);
}

console.log(bold('\nAccount linking'));
{
  // A Firebase login for an address that already exists must adopt that row
  // rather than fail on the UNIQUE(email) constraint.
  const email = `fblink-${RUN}@example.com`;
  const reg = await fetch(`${API}/api/v1/auth/register`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ email, password: 'Test123!', displayName: 'Password First' }),
  });
  const regBody = await reg.json().catch(() => ({}));
  check('a password account can be created first', reg.status === 201, `got ${reg.status}`);
  if (regBody.user?.id) created.push(regBody.user.id);

  const linked = await signIn(await mint({ email, sub: `uid-link-${RUN}`, provider: 'google.com' }));
  check('a Firebase login adopts the existing account', linked.status === 200,
    JSON.stringify(linked.body).slice(0, 140));
  check('…and it is the same user row', linked.body.user?.id === regBody.user?.id,
    `${linked.body.user?.id} vs ${regBody.user?.id}`);
  check('…and signing in through Google verified it',
    linked.body.user?.emailVerified === true);
}

console.log(bold('Address collisions fail cleanly, not with a 500'));
{
  // A Firebase identity whose address changes to one that already belongs to
  // a different account used to hit the UNIQUE(email) index and surface as a
  // raw "duplicate key" 500. It must be a clean 409 instead.
  const mine = `fbmine-${RUN}@example.com`;
  const theirs = `fbtheirs-${RUN}@example.com`;

  const a = await signIn(await mint({ email: mine, sub: `uid-a-${RUN}`, provider: 'google.com' }));
  const b = await signIn(await mint({ email: theirs, sub: `uid-b-${RUN}`, provider: 'google.com' }));
  check('two separate Firebase accounts exist', a.status === 200 && b.status === 200);
  check('they are different rows', a.body.user?.id !== b.body.user?.id);
  if (a.body.user?.id) created.push(a.body.user.id);
  if (b.body.user?.id) created.push(b.body.user.id);

  // Now uid-a claims uid-b's address.
  const clash = await signIn(await mint({ email: theirs, sub: `uid-a-${RUN}`, provider: 'google.com' }));
  check('claiming another account\'s address is refused', clash.status === 409,
    `got ${clash.status}: ${JSON.stringify(clash.body).slice(0, 120)}`);
  check('the refusal explains itself', /already belongs/i.test(clash.body?.error || ''),
    clash.body?.error);
  check('it is not a raw database error', !/duplicate key|constraint/i.test(clash.body?.error || ''),
    clash.body?.error);
}

console.log(bold('Registration profile is applied at creation only'));
{
  // The registration form sends displayName/phone/role alongside the token.
  const email = `fbprofile-${RUN}@example.com`;
  const token = await mint({ email, sub: `uid-prof-${RUN}`, email_verified: false, provider: 'password' });

  const r = await fetch(`${API}/api/v1/auth/firebase/sign-in`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({
      idToken: token, displayName: 'Kato Fred', phone: '+256700111222',
      role: 'seller', storeName: 'Kato Electronics',
    }),
  });
  const body = await r.json().catch(() => ({}));
  check('a profile can be supplied at sign-up', r.status === 200, JSON.stringify(body).slice(0, 140));
  check('the display name is kept', body.user?.displayName === 'Kato Fred', body.user?.displayName);
  check('the requested role is applied', body.user?.role === 'seller', body.user?.role);
  check('an unverified sign-up is still unverified', body.user?.emailVerified === false);
  check('the response reports verification state', body.emailVerified === false);
  if (body.user?.id) created.push(body.user.id);

  // A later sign-in must NOT let a client overwrite the stored profile.
  const hijack = await fetch(`${API}/api/v1/auth/firebase/sign-in`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({
      idToken: await mint({ email, sub: `uid-prof-${RUN}`, email_verified: false, provider: 'password' }),
      displayName: 'Someone Else', role: 'buyer',
    }),
  });
  const hb = await hijack.json().catch(() => ({}));
  check('a later sign-in cannot rewrite the display name',
    hb.user?.displayName === 'Kato Fred', hb.user?.displayName);
  check('a later sign-in cannot demote or change the role',
    hb.user?.role === 'seller', hb.user?.role);

  // Nobody may register straight into admin.
  const evil = await fetch(`${API}/api/v1/auth/firebase/sign-in`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({
      idToken: await mint({ email: `fbadmin-${RUN}@example.com`, sub: `uid-admin-${RUN}` }),
      role: 'admin',
    }),
  });
  check('registering as admin is refused', evil.status === 400, `got ${evil.status}`);
}

/* ── cleanup ──────────────────────────────────────────────────────── */

console.log(bold('\nCleanup'));
{
  const admin = await fetch(`${API}/api/v1/auth/login`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({
      email: process.env.ADMIN_EMAIL || 'admin@scottstechx.ug',
      password: process.env.ADMIN_PASSWORD || 'Admin123!',
    }),
  }).then((r) => r.json()).catch(() => ({}));

  let removed = 0;
  for (const id of created) {
    const r = await fetch(`${API}/api/v1/admin/users/${id}`, {
      method: 'DELETE',
      headers: { authorization: `Bearer ${admin.token}` },
    });
    if (r.ok) removed++;
  }
  check('test accounts removed', removed === created.length, `${removed}/${created.length}`);
}

console.log(`\n${bold('Result:')} ${pass} passed, ${fail} failed\n`);
jwksServer.close();
shutdownApi();
process.exit(fail ? 1 : 0);
