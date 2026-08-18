/**
 * One-off E2E test of the REAL Firebase path (run with: node firebase_e2e_test.mjs)
 *
 * 1. Create a Firebase user via Identity Toolkit REST (uses GOOGLE_API_KEY from .env)
 * 2. POST /api/v1/auth/firebase/sign-in with the real idToken -> our JWT
 * 3. POST /api/v1/auth/firebase/send-verification-email with the real idToken
 * 4. POST /api/v1/auth/firebase/upgrade-to-seller -> should 403 (email unverified)
 * 5. Verify the email via the Admin SDK, retry upgrade -> should succeed
 * 6. Clean up: delete the Firebase user (admin) + PG rows (cascade)
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import dotenv from 'dotenv';
import { initializeApp, cert } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';
import pg from 'pg';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
dotenv.config({ path: path.join(__dirname, '.env') });

// The Firebase project's Android API key (from the app's google-services.json) is the
// right key for Identity Toolkit REST calls. GOOGLE_API_KEY in .env may belong to
// a different GCP project.
function firebaseApiKey() {
  const gsPath = path.join(__dirname, '..', 'scottsx-android', 'app', 'google-services.json');
  try {
    const gs = JSON.parse(fs.readFileSync(gsPath, 'utf-8'));
    return gs.client[0].api_key[0].current_key;
  } catch {
    return process.env.GOOGLE_API_KEY;
  }
}
const API_KEY = firebaseApiKey();
const BASE = 'http://127.0.0.1:3001/api/v1';
const email = `e2e${Date.now()}@scottstechx.ug`;
const password = 'FirebaseE2e!123';
let exitCode = 0;

function ok(name, detail = '') {
  console.log(`PASS  ${name}${detail ? ' — ' + detail : ''}`);
}
function fail(name, detail = '') {
  console.log(`FAIL  ${name}${detail ? ' — ' + detail : ''}`);
  exitCode = 1;
}

async function main() {
  if (!API_KEY) throw new Error('GOOGLE_API_KEY missing in .env');

  // ── 1. Firebase user via REST ────────────────────────────────────────────
  const signUp = await fetch(
    `https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=${API_KEY}`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password, returnSecureToken: true }),
    }
  );
  const signUpJson = await signUp.json();
  if (!signUp.ok) {
    console.error('Firebase REST signup failed:', JSON.stringify(signUpJson).slice(0, 300));
    process.exit(1);
  }
  const idToken = signUpJson.idToken;
  const firebaseUid = signUpJson.localId;
  ok('Firebase user created via REST', email);

  // ── 2. Backend sign-in ───────────────────────────────────────────────────
  const signIn = await fetch(`${BASE}/auth/firebase/sign-in`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ idToken }),
  });
  const signInJson = await signIn.json();
  if (!signIn.ok || !signInJson.token) {
    fail('firebase/sign-in', JSON.stringify(signInJson).slice(0, 200));
    return;
  }
  ok('firebase/sign-in exchanged real idToken', `${signInJson.user.role} · ${signInJson.user.email}`);
  const jwt = signInJson.token;

  // ── 3. send-verification-email with REAL idToken ────────────────────────
  const sendVerif = await fetch(`${BASE}/auth/firebase/send-verification-email`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ idToken }),
  });
  const verifJson = await sendVerif.json();
  if (sendVerif.ok && verifJson.ok) {
    ok('send-verification-email generated a real link', `for ${verifJson.email}`);
  } else {
    fail('send-verification-email', JSON.stringify(verifJson).slice(0, 200));
  }

  // ── 4. upgrade-to-seller while unverified → 403 ─────────────────────────
  const upgradeBlocked = await fetch(`${BASE}/auth/firebase/upgrade-to-seller`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${jwt}` },
    body: JSON.stringify({ idToken }),
  });
  if (upgradeBlocked.status === 403) {
    ok('upgrade blocked for unverified email (403)');
  } else {
    fail('upgrade guard', `expected 403, got ${upgradeBlocked.status}`);
  }

  // ── 5. Verify email via Admin SDK, retry upgrade ────────────────────────
  const sa = JSON.parse(
    fs.readFileSync(path.join(__dirname, 'secrets', 'firebase-admin-key.json'), 'utf-8')
  );
  const admin = getAuth(initializeApp({ credential: cert(sa) }, 'e2e-test'));
  await admin.updateUser(firebaseUid, { emailVerified: true });
  ok('email marked verified via Admin SDK');

  const upgrade = await fetch(`${BASE}/auth/firebase/upgrade-to-seller`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${jwt}` },
    body: JSON.stringify({ idToken }),
  });
  const upgradeJson = await upgrade.json();
  if (upgrade.ok && upgradeJson.user?.role === 'seller') {
    ok('upgrade-to-seller succeeded after verification', `role=${upgradeJson.user.role}`);
  } else {
    fail('upgrade after verification', JSON.stringify(upgradeJson).slice(0, 200));
  }

  // ── 6. Cleanup ───────────────────────────────────────────────────────────
  try {
    await admin.deleteUser(firebaseUid);
    ok('Firebase test user deleted');
  } catch (e) {
    fail('Firebase cleanup', String(e).slice(0, 120));
  }
  const pool = new pg.Pool({
    connectionString: process.env.DATABASE_URL || 'postgresql://app:app@127.0.0.1:5433/scottstechx',
  });
  await pool.query('DELETE FROM users WHERE email = $1', [email]);
  await pool.end();
  ok('PG test rows cleaned');

  console.log(exitCode === 0 ? '\nALL FIREBASE E2E CHECKS PASSED' : '\nSOME CHECKS FAILED');
  process.exit(exitCode);
}

main().catch((err) => {
  console.error('E2E crashed:', err);
  process.exit(1);
});
