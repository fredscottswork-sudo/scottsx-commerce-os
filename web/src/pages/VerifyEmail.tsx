/**
 * ScottsTechX — /verify-email
 *
 * The gate between "account created" and "account usable".
 *
 * Previously verification was a dismissible banner, so a new user was simply
 * logged straight in and could ignore it forever. Every private route now
 * redirects here until the address is proven, which is the whole point: an
 * address that cannot receive mail must not become a working account.
 *
 * Both proofs are accepted, because two sign-up paths exist:
 *   - the Firebase link  (normal path — user clicks it in their inbox)
 *   - a six-digit code   (fallback when Firebase Auth is unavailable)
 *
 * The page polls while it is open. Firebase's link opens in the mail client,
 * not this tab, so nothing would otherwise tell the app it was clicked — and
 * asking someone to press a button to see whether the thing they just did
 * worked is a poor experience when we can simply check.
 */
import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { MailCheck, RefreshCw, LogOut, ShieldCheck } from 'lucide-react';
import { useAuth } from '../store/AuthContext';
import { useToast } from '../store/ToastContext';
import { authService } from '../api/services';
import { ApiError } from '../api/client';
import { Btn, Input } from '../components/ui';
import { BrandLockup } from '../components/BrandLogo';
import { useSeo } from '../hooks/useSeo';
import { readDevCode, rememberDevCode, clearDevCode } from '../lib/devCode';

/** How often to ask Firebase whether the link has been clicked. */
const POLL_MS = 4000;

export default function VerifyEmail() {
  useSeo({ title: 'Verify your email', noIndex: true });

  const { user, setUser, loginWithFirebase, logout } = useAuth();
  const { toast } = useToast();
  const navigate = useNavigate();

  const [code, setCode] = useState('');
  const [busy, setBusy] = useState(false);
  const [checking, setChecking] = useState(false);
  const [resending, setResending] = useState(false);
  const [error, setError] = useState('');
  const [note, setNote] = useState('');
  const [devCode, setDevCode] = useState('');
  const [done, setDone] = useState(false);

  // Guards the poll so a slow request cannot overlap the next tick, and so
  // nothing runs after the component is gone.
  const alive = useRef(true);
  const inFlight = useRef(false);

  useEffect(() => {
    alive.current = true;
    setDevCode(readDevCode());
    return () => {
      alive.current = false;
    };
  }, []);

  const succeed = useCallback(
    (next?: { role?: string }) => {
      clearDevCode();
      setDone(true);
      toast('Email verified — welcome to ScottsTechX', 'success');
      const role = next?.role || user?.role;
      // A short beat so the success state is actually seen rather than a flash.
      window.setTimeout(() => {
        navigate(role === 'admin' ? '/admin' : role === 'seller' ? '/seller' : '/buyer', {
          replace: true,
        });
      }, 900);
    },
    [navigate, toast, user?.role]
  );

  /**
   * Ask Firebase whether the address is verified yet.
   *
   * `silent` is the polling case: a failure there is not worth shouting about,
   * because the user has not asked for anything.
   */
  const checkFirebase = useCallback(
    async (silent = false) => {
      if (inFlight.current) return false;
      inFlight.current = true;
      if (!silent) {
        setChecking(true);
        setError('');
      }
      try {
        const { refreshVerificationState } = await import('../lib/firebase');
        const { verified, idToken } = await refreshVerificationState();
        if (!idToken) {
          if (!silent) setError('Please sign in again to refresh your verification status.');
          return false;
        }
        if (!verified) {
          if (!silent) {
            setError('Not verified yet — open the link in the email, then try again.');
          }
          return false;
        }
        // Hand the refreshed token to our backend so its record catches up.
        const stored = await loginWithFirebase(idToken);
        if (!alive.current) return true;
        setUser({ ...stored, emailVerified: true });
        succeed(stored);
        return true;
      } catch (err) {
        if (!silent && alive.current) {
          setError(err instanceof ApiError ? err.message : 'Could not check your verification status.');
        }
        return false;
      } finally {
        inFlight.current = false;
        if (!silent && alive.current) setChecking(false);
      }
    },
    [loginWithFirebase, setUser, succeed]
  );

  // Poll while the page is open and the tab is visible. Firebase's link opens
  // elsewhere, so this is what makes the page notice on its own.
  useEffect(() => {
    if (done || !user || user.emailVerified) return;
    const id = window.setInterval(() => {
      if (document.visibilityState === 'visible') void checkFirebase(true);
    }, POLL_MS);
    // Coming back to the tab is the likeliest moment for it to have happened.
    const onFocus = () => void checkFirebase(true);
    window.addEventListener('focus', onFocus);
    return () => {
      window.clearInterval(id);
      window.removeEventListener('focus', onFocus);
    };
  }, [checkFirebase, done, user]);

  /** Fallback path: confirm the six-digit code against our own backend. */
  const confirmCode = useCallback(async () => {
    const digits = code.replace(/\D/g, '');
    if (digits.length !== 6) {
      setError('Enter the 6-digit code from your email.');
      return;
    }
    setBusy(true);
    setError('');
    try {
      const res = await authService.confirmVerification(digits);
      if (res?.user) setUser({ ...user!, ...res.user, emailVerified: true });
      succeed(res?.user);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not verify that code.');
    } finally {
      setBusy(false);
    }
  }, [code, setUser, succeed, user]);

  const resend = useCallback(async () => {
    setResending(true);
    setError('');
    setNote('');
    try {
      // Prefer Firebase: it sends a real link and needs no mail server.
      try {
        const { resendVerificationEmail } = await import('../lib/firebase');
        if (await resendVerificationEmail()) {
          setNote('Sent — check your inbox, and your spam folder.');
          return;
        }
      } catch (fbErr) {
        const fbCode = (fbErr as { code?: string })?.code || '';
        if (fbCode === 'auth/too-many-requests') {
          setError('Too many requests. Please wait a minute before asking again.');
          return;
        }
        /* not a Firebase session — fall through to the code mailer */
      }
      const res = await authService.requestVerification();
      if (res.alreadyVerified) {
        setUser({ ...user!, emailVerified: true });
        succeed();
        return;
      }
      if (res.devCode) {
        rememberDevCode(res.devCode);
        setDevCode(res.devCode);
      }
      setNote(res.sent ? 'Sent — check your inbox.' : 'A new code was generated.');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not send a new code.');
    } finally {
      setResending(false);
    }
  }, [setUser, succeed, user]);

  if (!user) return null;

  return (
    <div className="auth-wrap" data-testid="verify-email-page">
      <div className="auth-brand">
        <BrandLockup width={300} className="auth-lockup" />
        <h1 className="auth-tagline">One last step.</h1>
        <p style={{ opacity: 0.9, fontSize: 16, maxWidth: 'min(420px, 100%)' }}>
          Confirming your email keeps your account recoverable, protects buyers and sellers
          from fake listings, and unlocks everything on ScottsTechX.
        </p>
      </div>

      <div className="auth-form">
        <div className="auth-card">
          {done ? (
            <div className="verify-done" data-testid="verify-success">
              <div className="verify-sent-icon verify-done-icon" aria-hidden="true">
                <ShieldCheck size={30} />
              </div>
              <h2 style={{ marginTop: 12 }}>Email verified</h2>
              <p className="muted">Taking you to your dashboard…</p>
            </div>
          ) : (
            <>
              <div className="verify-sent-icon" aria-hidden="true">
                <MailCheck size={30} />
              </div>
              <h2 style={{ marginTop: 12 }}>Verify your email</h2>
              <p className="muted">
                We sent a verification link to <b>{user.email}</b>. Open it and this page will
                continue by itself.
              </p>

              {devCode ? (
                <div className="field-note mb-8" data-testid="verify-page-dev-code">
                  Email delivery is not set up for this site yet, so no link could be sent.
                  Use this code instead: <b>{devCode}</b>
                </div>
              ) : (
                <p className="muted" style={{ fontSize: 13 }}>
                  Nothing yet? It can take a minute — and it is worth checking your spam folder.
                </p>
              )}

              <Btn
                variant="primary"
                className="btn-block btn-lg"
                onClick={() => void checkFirebase(false)}
                disabled={checking}
                data-testid="verify-page-check"
              >
                <RefreshCw size={16} />
                <span>{checking ? 'Checking…' : "I've clicked the link"}</span>
              </Btn>

              <div className="verify-page-divider"><span>or enter the code</span></div>

              <form
                className="verify-page-form"
                onSubmit={(e) => {
                  e.preventDefault();
                  void confirmCode();
                }}
              >
                <Input
                  value={code}
                  onChange={(e) => setCode(e.target.value)}
                  inputMode="numeric"
                  autoComplete="one-time-code"
                  maxLength={6}
                  placeholder="123456"
                  aria-label="6-digit verification code"
                  data-testid="verify-page-code"
                />
                <Btn type="submit" disabled={busy} data-testid="verify-page-submit">
                  {busy ? 'Checking…' : 'Verify'}
                </Btn>
              </form>

              {note && <div className="field-note mt-8" data-testid="verify-page-note">{note}</div>}
              {error && (
                <div className="field-error mt-8" data-testid="verify-page-error">{error}</div>
              )}

              <div className="verify-page-actions">
                <Btn onClick={resend} disabled={resending} data-testid="verify-page-resend">
                  {resending ? 'Sending…' : 'Resend email'}
                </Btn>
                {/* Signing out must stay reachable: a typo in the address would
                    otherwise strand someone on a page they can never pass. */}
                <Btn
                  onClick={() => {
                    logout();
                    navigate('/login', { replace: true });
                  }}
                  data-testid="verify-page-signout"
                >
                  <LogOut size={15} />
                  <span>Use a different account</span>
                </Btn>
              </div>

              <p className="muted mt-16" style={{ textAlign: 'center', fontSize: 13 }}>
                Wrong address? Sign out and register again with the correct one.
              </p>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
