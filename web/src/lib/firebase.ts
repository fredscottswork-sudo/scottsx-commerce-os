/**
 * ScottsTechX web — Firebase Authentication.
 *
 * Why Firebase rather than raw Google Identity Services + our own mailer:
 * Firebase issues the verification email itself (1,000/day on the free Spark
 * plan), so there are no SMTP credentials to obtain, store or rotate. And
 * because `email_verified` travels inside the signed ID token, the browser
 * cannot fake it — the flag our backend stores is attested by Google.
 *
 * Everything is loaded lazily. A visitor who never touches sign-in pays none
 * of the SDK's weight, and a blocked/offline Firebase degrades to a clear
 * "unavailable" message instead of a dead button.
 *
 * Config comes from Vite env vars, with the scottstechx-52bab project as the
 * default so a fresh deployment works with nothing configured. All of these
 * values are public by design — they identify the project, they do not
 * authorise anything. Access is controlled by Firebase's Authorised domains
 * list and by our backend's token verification.
 */
import type { FirebaseApp } from 'firebase/app';
import type { Auth, User, UserCredential } from 'firebase/auth';

export const FIREBASE_CONFIG = {
  apiKey: (import.meta.env.VITE_FIREBASE_API_KEY as string) || 'AIzaSyDwOGoMIRVRwVeC_yNNCbxUtVipHa_UHCA',
  authDomain: (import.meta.env.VITE_FIREBASE_AUTH_DOMAIN as string) || 'scottstechx-52bab.firebaseapp.com',
  projectId: (import.meta.env.VITE_FIREBASE_PROJECT_ID as string) || 'scottstechx-52bab',
  storageBucket:
    (import.meta.env.VITE_FIREBASE_STORAGE_BUCKET as string) || 'scottstechx-52bab.firebasestorage.app',
  messagingSenderId: (import.meta.env.VITE_FIREBASE_SENDER_ID as string) || '911393008938',
  appId: (import.meta.env.VITE_FIREBASE_APP_ID as string) || '1:911393008938:web:scottstechx',
};

export class FirebaseUnavailableError extends Error {
  constructor(message = 'Sign-in could not load. Check your connection, or use your email and password.') {
    super(message);
    this.name = 'FirebaseUnavailableError';
  }
}

let appPromise: Promise<{ app: FirebaseApp; auth: Auth }> | null = null;

/** Loads the Firebase SDK once. Rejects with FirebaseUnavailableError. */
export async function loadFirebase(): Promise<{ app: FirebaseApp; auth: Auth }> {
  if (appPromise) return appPromise;

  appPromise = (async () => {
    if (typeof window === 'undefined') {
      throw new FirebaseUnavailableError('Sign-in needs a browser.');
    }
    try {
      const [{ initializeApp, getApps, getApp }, { getAuth, setPersistence, browserLocalPersistence }] =
        await Promise.all([import('firebase/app'), import('firebase/auth')]);

      const app = getApps().length ? getApp() : initializeApp(FIREBASE_CONFIG);
      const auth = getAuth(app);
      // Survive a page reload so a user who verifies in another tab and comes
      // back is still signed in.
      await setPersistence(auth, browserLocalPersistence).catch(() => {
        /* storage blocked (private mode): in-memory persistence still works */
      });
      return { app, auth };
    } catch (err) {
      // Let a later attempt retry from scratch.
      appPromise = null;
      throw new FirebaseUnavailableError(
        err instanceof Error && err.message ? `Sign-in could not load: ${err.message}` : undefined
      );
    }
  })();

  return appPromise;
}

/** Human-readable text for the Firebase error codes users actually hit. */
export function friendlyAuthError(err: unknown): string {
  const code = (err as { code?: string })?.code || '';
  switch (code) {
    case 'auth/invalid-email':
      return 'That email address is not valid.';
    case 'auth/email-already-in-use':
      return 'An account already exists with that email. Try signing in instead.';
    case 'auth/weak-password':
      return 'Please choose a password of at least 6 characters.';
    case 'auth/wrong-password':
    case 'auth/invalid-credential':
      return 'That email and password do not match.';
    case 'auth/user-not-found':
      return 'No account found with that email.';
    case 'auth/too-many-requests':
      return 'Too many attempts. Please wait a moment and try again.';
    case 'auth/popup-closed-by-user':
    case 'auth/cancelled-popup-request':
      return 'Sign-in was cancelled.';
    case 'auth/popup-blocked':
      return 'Your browser blocked the sign-in popup. Allow popups and try again.';
    case 'auth/unauthorized-domain':
      // The single most common deployment mistake, so name the actual fix.
      return 'This website is not authorised for sign-in yet. Add it under Firebase Console → Authentication → Settings → Authorised domains.';
    case 'auth/network-request-failed':
      return 'Network error. Check your connection and try again.';
    case 'auth/operation-not-allowed':
      return 'That sign-in method is not enabled for this project yet.';
    default:
      return err instanceof Error && err.message
        ? err.message.replace(/^Firebase:\s*/, '')
        : 'Sign-in failed. Please try again.';
  }
}

/** Continue with Google. Returns a Firebase ID token for our backend. */
export async function signInWithGoogle(): Promise<{ idToken: string; user: User }> {
  const { auth } = await loadFirebase();
  const { GoogleAuthProvider, signInWithPopup } = await import('firebase/auth');
  const provider = new GoogleAuthProvider();
  // Always let the user choose which account, rather than silently reusing one.
  provider.setCustomParameters({ prompt: 'select_account' });
  const cred: UserCredential = await signInWithPopup(auth, provider);
  return { idToken: await cred.user.getIdToken(), user: cred.user };
}

/**
 * Create an account with email + password and send the verification email.
 *
 * The address is unverified at this point, and the ID token says so, so the
 * backend cannot be tricked into treating it as verified.
 */
export async function registerWithEmail(
  email: string,
  password: string,
  displayName?: string
): Promise<{ idToken: string; user: User }> {
  const { auth } = await loadFirebase();
  const { createUserWithEmailAndPassword, sendEmailVerification, updateProfile } =
    await import('firebase/auth');
  const cred = await createUserWithEmailAndPassword(auth, email, password);
  if (displayName) {
    await updateProfile(cred.user, { displayName }).catch(() => {
      /* cosmetic only */
    });
  }
  // Best-effort: the account exists either way and the user can re-request.
  await sendEmailVerification(cred.user).catch(() => {
    /* surfaced by the caller as "could not send" */
  });
  return { idToken: await cred.user.getIdToken(), user: cred.user };
}

/** Sign in with email + password. */
export async function signInWithEmail(
  email: string,
  password: string
): Promise<{ idToken: string; user: User }> {
  const { auth } = await loadFirebase();
  const { signInWithEmailAndPassword } = await import('firebase/auth');
  const cred = await signInWithEmailAndPassword(auth, email, password);
  return { idToken: await cred.user.getIdToken(), user: cred.user };
}

/** Re-send the verification email to the currently signed-in Firebase user. */
export async function resendVerificationEmail(): Promise<boolean> {
  const { auth } = await loadFirebase();
  if (!auth.currentUser) return false;
  const { sendEmailVerification } = await import('firebase/auth');
  await sendEmailVerification(auth.currentUser);
  return true;
}

/**
 * Ask Firebase whether the address has been verified since we last looked.
 *
 * `reload()` refetches the user record, then a *forced* token refresh is
 * required — without `true` the cached ID token keeps its stale
 * email_verified:false claim and the backend would never see the change.
 */
export async function refreshVerificationState(): Promise<{ verified: boolean; idToken: string | null }> {
  const { auth } = await loadFirebase();
  const user = auth.currentUser;
  if (!user) return { verified: false, idToken: null };
  await user.reload();
  const idToken = await user.getIdToken(true);
  return { verified: user.emailVerified, idToken };
}

/** Send a password-reset email. */
export async function sendPasswordReset(email: string): Promise<void> {
  const { auth } = await loadFirebase();
  const { sendPasswordResetEmail } = await import('firebase/auth');
  await sendPasswordResetEmail(auth, email);
}

/** Sign out of Firebase (our own session is cleared separately). */
export async function firebaseSignOut(): Promise<void> {
  try {
    const { auth } = await loadFirebase();
    const { signOut } = await import('firebase/auth');
    await signOut(auth);
  } catch {
    /* never loaded — nothing to sign out of */
  }
}
