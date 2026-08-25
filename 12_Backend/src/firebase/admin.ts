/**
 * ScottsTechX — Firebase Admin bootstrap.
 *
 * Reads the service account from secrets/firebase-admin-key.json (git-ignored).
 * If the key is missing, the module stays inert and Firebase-backed endpoints
 * return a clear 503 so the rest of the API keeps working.
 */
import fs from 'node:fs';
import path from 'node:path';
import { createRemoteJWKSet, jwtVerify, type JWTPayload } from 'jose';
import { initializeApp, cert, type App } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';
import { getMessaging as adminMessaging, type Messaging as FirebaseMessaging } from 'firebase-admin/messaging';
import { BACKEND_ROOT } from '../db.js';
import { ServiceUnavailableError } from '../errors.js';

const SECRET_PATH = path.join(BACKEND_ROOT, 'secrets', 'firebase-admin-key.json');

let app: App | null = null;
let missingKeyLogged = false;

export function firebaseReady(): boolean {
  // Local dev: service account file. Cloud Functions / Cloud Run: default
  // credentials are already available in the runtime environment.
  return (
    fs.existsSync(SECRET_PATH) ||
    Boolean(process.env.FIREBASE_CONFIG) ||
    Boolean(process.env.GOOGLE_APPLICATION_CREDENTIALS)
  );
}

const STORAGE_BUCKET = process.env.FIREBASE_STORAGE_BUCKET || 'scottstechx-52bab.firebasestorage.app';

export function getFirebaseApp(): App {
  if (app) return app;
  if (fs.existsSync(SECRET_PATH)) {
    const serviceAccount = JSON.parse(fs.readFileSync(SECRET_PATH, 'utf-8'));
    app = initializeApp({ credential: cert(serviceAccount), storageBucket: STORAGE_BUCKET });
  } else if (process.env.FIREBASE_CONFIG || process.env.GOOGLE_APPLICATION_CREDENTIALS) {
    // Cloud runtime: firebase-admin picks up the ambient service account.
    app = initializeApp({ storageBucket: STORAGE_BUCKET });
  } else {
    throw new ServiceUnavailableError(
      'Firebase is not configured: place your service account JSON at 12_Backend/secrets/firebase-admin-key.json'
    );
  }
  return app;
}

export function firebaseAuth() {
  try {
    return getAuth(getFirebaseApp());
  } catch (err) {
    if (!missingKeyLogged) {
      console.warn('[firebase]', (err as Error).message);
      missingKeyLogged = true;
    }
    throw err;
  }
}

/**
 * Firebase Cloud Messaging sender, or null when Firebase isn't configured.
 * Returning null (instead of throwing) lets push be strictly best-effort:
 * notifications are always persisted in Postgres regardless.
 */
export function getMessaging(): FirebaseMessaging | null {
  if (!firebaseReady()) return null;
  try {
    return adminMessaging(getFirebaseApp());
  } catch (err) {
    if (!missingKeyLogged) {
      console.warn('[firebase] messaging unavailable:', (err as Error).message);
      missingKeyLogged = true;
    }
    return null;
  }
}

/**
 * Firebase project id. Public by design — it is embedded in every client
 * bundle and in the token's own `aud` claim.
 */
export const FIREBASE_PROJECT_ID = process.env.FIREBASE_PROJECT_ID || 'scottstechx-52bab';

/**
 * Firebase's public signing keys.
 *
 * Verifying an ID token needs only the public JWKS — NOT a service account.
 * That distinction matters a lot here: it means Google Sign-In and email
 * verification work on a free Firebase project with nothing configured but a
 * project id. The service account stays optional, and is genuinely required
 * only for privileged operations (sending FCM pushes, minting verification
 * links, reading a user record server-side).
 *
 * Overridable so the offline test suite can point at a local key server.
 */
const SECURETOKEN_JWKS =
  'https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com';
const JWKS_URL = () => process.env.FIREBASE_JWKS_URL || SECURETOKEN_JWKS;
const ISSUER = () =>
  process.env.FIREBASE_ISSUER || `https://securetoken.google.com/${FIREBASE_PROJECT_ID}`;

const jwksCache = new Map<string, ReturnType<typeof createRemoteJWKSet>>();
function jwks() {
  const url = JWKS_URL();
  let set = jwksCache.get(url);
  if (!set) {
    set = createRemoteJWKSet(new URL(url), { cooldownDuration: 30_000, timeoutDuration: 8_000 });
    jwksCache.set(url, set);
  }
  return set;
}

/** True when the failure is "we could not reach Google", not "the token is bad". */
function isNetworkFailure(err: unknown): boolean {
  const e = err as { code?: string; name?: string; message?: string };
  if (e?.code === 'ERR_JWKS_TIMEOUT' || e?.name === 'JWKSTimeout') return true;
  const msg = String(e?.message || '');
  return /fetch failed|ENOTFOUND|ECONNREFUSED|EAI_AGAIN|network|socket hang up|timed? ?out/i.test(msg);
}

/**
 * Verify a Firebase idToken and return its payload (throws on failure).
 *
 * Uses the Admin SDK when a service account is present (it also checks for
 * revoked sessions), and otherwise falls back to verifying the signature
 * against Google's public JWKS. Both paths enforce the same claims, so the
 * fallback is not a weaker check — it simply cannot ask Firebase whether the
 * session was revoked server-side.
 */
export async function verifyIdToken(idToken: string): Promise<Record<string, any>> {
  if (firebaseReady()) {
    const decoded = await firebaseAuth().verifyIdToken(idToken);
    return decoded as unknown as Record<string, any>;
  }

  let payload: JWTPayload;
  try {
    ({ payload } = await jwtVerify(idToken, jwks(), {
      issuer: ISSUER(),
      // A Firebase ID token's audience is the bare project id. Checking it is
      // what stops a token minted for someone else's Firebase project from
      // being accepted here.
      audience: FIREBASE_PROJECT_ID,
      algorithms: ['RS256'],
      clockTolerance: 60,
    }));
  } catch (err) {
    if (isNetworkFailure(err)) {
      throw new ServiceUnavailableError('Could not reach Google to verify the sign-in. Try again.');
    }
    throw new Error(String((err as Error)?.message || 'invalid token').slice(0, 160));
  }

  if (!payload.sub) throw new Error('token has no subject');

  // auth_time is when the user actually authenticated; a future value is bogus.
  const authTime = Number(payload.auth_time ?? 0);
  if (authTime && authTime > Date.now() / 1000 + 60) throw new Error('token has an invalid auth time');

  // Admin SDK exposes the subject as `uid`; mirror that so callers are identical.
  return { ...payload, uid: String(payload.sub) } as Record<string, any>;
}

/** Send a Firebase email-verification link to the token's email. */
export async function sendVerificationEmailLink(idToken: string): Promise<{ email: string; link: string }> {
  const decoded = await verifyIdToken(idToken);
  const email = decoded.email as string;
  if (!email) throw new ServiceUnavailableError('Token has no email claim');
  const link = await firebaseAuth().generateEmailVerificationLink(email, {
    url: process.env.APP_DEEP_LINK || 'https://scottstechx-52bab.firebaseapp.com/__/auth/action',
  });
  return { email, link };
}

/**
 * Authoritative email-verified status straight from Firebase.
 * Returns null when Firebase isn't configured or the uid is unknown.
 */
export async function firebaseEmailVerified(uid: string): Promise<boolean | null> {
  if (!firebaseReady()) return null;
  try {
    const user = await firebaseAuth().getUser(uid);
    return user.emailVerified;
  } catch {
    return null;
  }
}
