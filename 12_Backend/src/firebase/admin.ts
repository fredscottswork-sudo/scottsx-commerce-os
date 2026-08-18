/**
 * ScottsTechX — Firebase Admin bootstrap.
 *
 * Reads the service account from secrets/firebase-admin-key.json (git-ignored).
 * If the key is missing, the module stays inert and Firebase-backed endpoints
 * return a clear 503 so the rest of the API keeps working.
 */
import fs from 'node:fs';
import path from 'node:path';
import { initializeApp, cert, type App } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';
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

/** Verify a Firebase idToken and return its payload (throws on failure). */
export async function verifyIdToken(idToken: string): Promise<Record<string, any>> {
  if (!firebaseReady()) {
    throw new ServiceUnavailableError(
      'Firebase is not configured: place your service account JSON at 12_Backend/secrets/firebase-admin-key.json'
    );
  }
  const decoded = await firebaseAuth().verifyIdToken(idToken);
  return decoded as unknown as Record<string, any>;
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
