/**
 * "Continue with Google" — used by both Login and Register.
 *
 * Two implementations, tried in order:
 *
 *   1. Firebase Authentication (preferred). One popup, and Firebase also owns
 *      the email-verification flow, so there is no SMTP to configure. It
 *      returns a Firebase ID token, which the backend verifies against
 *      Google's securetoken key set.
 *
 *   2. Google Identity Services (fallback). The original path, kept because it
 *      keeps working if Firebase Auth is not enabled on the project yet. It
 *      returns a Google id_token, verified against a different key set.
 *
 * Falling back matters: enabling Google in the Firebase console is a manual
 * step, and until it happens Firebase answers with auth/operation-not-allowed.
 * Rather than showing a dead button, we quietly use the path that works.
 *
 * Four states are handled, because a button that silently does nothing is the
 * exact bug this component has already caused once:
 *   loading      — SDK on the way
 *   ready        — usable
 *   unavailable  — blocked/offline: say so instead of pretending
 *   error        — the popup or the backend rejected it
 */
import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../store/AuthContext';
import { useToast } from '../store/ToastContext';
import { renderGoogleButton } from '../lib/google';
import { ApiError } from '../api/client';

/** Firebase codes that mean "this project is not set up for Google yet". */
const NOT_CONFIGURED = new Set([
  'auth/operation-not-allowed',
  'auth/configuration-not-found',
  'auth/invalid-api-key',
  'auth/api-key-not-valid',
]);

/** Codes where the *user* chose to stop — not an error worth shouting about. */
const USER_CANCELLED = new Set([
  'auth/popup-closed-by-user',
  'auth/cancelled-popup-request',
  'auth/user-cancelled',
]);

export default function GoogleButton({ redirectTo = '/', divider = true }: { redirectTo?: string; divider?: boolean }) {
  const host = useRef<HTMLDivElement>(null);
  const { loginWithGoogle, loginWithFirebase } = useAuth();
  const { toast } = useToast();
  const navigate = useNavigate();
  const [status, setStatus] = useState<'loading' | 'ready' | 'unavailable'>('loading');
  /** 'firebase' once the SDK is up; 'gis' if we had to fall back. */
  const [mode, setMode] = useState<'firebase' | 'gis'>('firebase');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const land = useCallback(
    (user: { role: string; displayName: string; email: string; roleChosen?: boolean }) => {
      // First sign-in: pick buyer / seller before landing anywhere.
      if (user.roleChosen === false && user.role !== 'admin') {
        navigate('/onboarding', { replace: true });
        return;
      }
      toast(`Welcome back, ${user.displayName || user.email}`, 'success');
      navigate(user.role === 'admin' ? '/admin' : user.role === 'seller' ? '/seller' : user.role === 'buyer' ? '/buyer' : redirectTo);
    },
    [navigate, redirectTo, toast]
  );

  // ── Path 1: Firebase ──────────────────────────────────────────────────────
  useEffect(() => {
    let alive = true;
    (async () => {
      try {
        const { loadFirebase } = await import('../lib/firebase');
        await loadFirebase();
        if (alive) {
          setMode('firebase');
          setStatus('ready');
        }
      } catch {
        if (alive) setMode('gis');
      }
    })();
    return () => {
      alive = false;
    };
  }, []);

  const onFirebaseClick = useCallback(async () => {
    setError('');
    setBusy(true);
    try {
      const { signInWithGoogle, friendlyAuthError } = await import('../lib/firebase');
      const { idToken } = await signInWithGoogle();
      const user = await loginWithFirebase(idToken);
      land(user);
    } catch (err) {
      const code = (err as { code?: string })?.code || '';
      if (USER_CANCELLED.has(code)) {
        setBusy(false);
        return;
      }
      if (NOT_CONFIGURED.has(code)) {
        setMode('gis');
        setBusy(false);
        return;
      }
      try {
        const { friendlyAuthError } = await import('../lib/firebase');
        setError(err instanceof ApiError ? err.message : friendlyAuthError(err));
      } catch {
        setError(err instanceof ApiError ? err.message : 'Google sign-in failed');
      }
    } finally {
      setBusy(false);
    }
  }, [land, loginWithFirebase]);

  // ── Path 2: Google Identity Services ──────────────────────────────────────
  useEffect(() => {
    if (mode !== 'gis') return;
    let alive = true;

    async function handle(idToken: string) {
      setError('');
      try {
        const user = await loginWithGoogle(idToken);
        if (!alive) return;
        land(user);
      } catch (err) {
        if (!alive) return;
        setError(
          err instanceof ApiError
            ? err.message
            : err instanceof Error && err.message
              ? `Google sign-in failed: ${err.message}`
              : 'Google sign-in failed. Please try again.'
        );
      }
    }

    (async () => {
      try {
        if (!host.current) return;
        await renderGoogleButton(host.current, handle);
        if (alive) setStatus('ready');
      } catch {
        if (alive) setStatus('unavailable');
      }
    })();

    return () => {
      alive = false;
    };
  }, [mode, land, loginWithGoogle]);

  return (
    <div className="google-signin" data-testid="google-signin" data-status={status} data-mode={mode}>
      {divider && <div className="google-divider"><span>or</span></div>}

      {mode === 'firebase' ? (
        <button
          type="button"
          className="btn btn-block btn-lg google-btn"
          onClick={onFirebaseClick}
          disabled={busy || status === 'loading'}
          data-testid="google-continue"
        >
          <svg width="18" height="18" viewBox="0 0 18 18" aria-hidden="true" focusable="false">
            <path fill="#4285F4" d="M17.64 9.2c0-.64-.06-1.25-.16-1.84H9v3.48h4.84a4.14 4.14 0 0 1-1.8 2.72v2.26h2.92c1.7-1.57 2.68-3.88 2.68-6.62Z" />
            <path fill="#34A853" d="M9 18c2.43 0 4.47-.8 5.96-2.18l-2.92-2.26c-.81.54-1.84.86-3.04.86-2.34 0-4.32-1.58-5.03-3.7H.96v2.33A9 9 0 0 0 9 18Z" />
            <path fill="#FBBC05" d="M3.97 10.72a5.4 5.4 0 0 1 0-3.44V4.95H.96a9 9 0 0 0 0 8.1l3.01-2.33Z" />
            <path fill="#EA4335" d="M9 3.58c1.32 0 2.5.45 3.44 1.35l2.58-2.58C13.46.9 11.43 0 9 0A9 9 0 0 0 .96 4.95l3.01 2.33C4.68 5.16 6.66 3.58 9 3.58Z" />
          </svg>
          <span>{busy ? 'Signing in…' : 'Continue with Google'}</span>
        </button>
      ) : (
        <div ref={host} className="google-btn-host" aria-busy={status === 'loading'} />
      )}

      {status === 'loading' && mode === 'gis' && (
        <div className="google-fallback muted" data-testid="google-loading">
          Loading Google Sign-In…
        </div>
      )}
      {status === 'unavailable' && (
        <div className="google-fallback muted" data-testid="google-unavailable">
          Google Sign-In is unavailable right now — please continue with your email instead.
        </div>
      )}
      {error && (
        <div className="field-error mt-8" data-testid="google-error">
          {error}
        </div>
      )}
    </div>
  );
}
