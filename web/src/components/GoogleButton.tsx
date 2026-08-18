/**
 * "Continue with Google" — used by both Login and Register.
 *
 * Google's own rendered button is required for the popup flow, so this mounts
 * it into a host div. Three states matter and all three are handled:
 *   loading      — script on the way
 *   unavailable  — script blocked/offline: we say so instead of showing a
 *                  button that does nothing
 *   error        — backend rejected the token
 */
import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../store/AuthContext';
import { useToast } from '../store/ToastContext';
import { renderGoogleButton } from '../lib/google';
import { ApiError } from '../api/client';

export default function GoogleButton({ redirectTo = '/' }: { redirectTo?: string }) {
  const host = useRef<HTMLDivElement>(null);
  const { loginWithGoogle } = useAuth();
  const { toast } = useToast();
  const navigate = useNavigate();
  const [status, setStatus] = useState<'loading' | 'ready' | 'unavailable'>('loading');
  const [error, setError] = useState('');

  useEffect(() => {
    let alive = true;

    async function handle(idToken: string) {
      setError('');
      try {
        const user = await loginWithGoogle(idToken);
        if (!alive) return;
        toast(`Welcome, ${user.displayName || user.email}`, 'success');
        navigate(user.role === 'admin' ? '/admin' : user.role === 'seller' ? '/seller' : redirectTo);
      } catch (err) {
        if (!alive) return;
        // Anything that is not an ApiError used to fall through as a bare
        // TypeError and left the user staring at an unchanged page after a
        // successful Google popup. Every failure now says something.
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
  }, [loginWithGoogle, navigate, redirectTo, toast]);

  return (
    <div className="google-signin" data-testid="google-signin" data-status={status}>
      <div className="google-divider"><span>or</span></div>

      <div ref={host} className="google-btn-host" aria-busy={status === 'loading'} />

      {status === 'loading' && (
        <div className="google-fallback muted" data-testid="google-loading">
          Loading Google Sign-In…
        </div>
      )}
      {status === 'unavailable' && (
        <div className="google-fallback muted" data-testid="google-unavailable">
          Google Sign-In is unavailable right now — please use your email and password.
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
