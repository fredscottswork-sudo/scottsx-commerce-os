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
 * Verification is by LINK only. The user clicks the link in their email and
 * lands here with a ?token=, which this page redeems. There is deliberately no
 * six-digit code entry: a code the site cannot accept is a dead end, and
 * offering both made the code look like the intended path.
 *
 * The page polls while it is open. Firebase's link opens in the mail client,
 * not this tab, so nothing would otherwise tell the app it was clicked — and
 * asking someone to press a button to see whether the thing they just did
 * worked is a poor experience when we can simply check.
 */
import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { MailCheck, RefreshCw, LogOut, ShieldCheck } from 'lucide-react';
import { useAuth } from '../store/AuthContext';
import { useToast } from '../store/ToastContext';
import { authService } from '../api/services';
import { ApiError, tokenStore } from '../api/client';
import { Btn } from '../components/ui';
import { BrandLockup } from '../components/BrandLogo';
import GoogleButton from '../components/GoogleButton';
import { useSeo } from '../hooks/useSeo';
import { clearDevCode, readDevLink, rememberDevLink, clearDevLink } from '../lib/devCode';

/** How often to ask Firebase whether the link has been clicked. */
const POLL_MS = 4000;

export default function VerifyEmail() {
  useSeo({ title: 'Verify your email', noIndex: true });

  const { user, setUser, loginWithFirebase, logout } = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();
  const linkToken = searchParams.get('token');
  const { toast } = useToast();
  const navigate = useNavigate();

  const [busy, setBusy] = useState(false);
  const [checking, setChecking] = useState(false);
  const [resending, setResending] = useState(false);
  const [error, setError] = useState('');
  const [note, setNote] = useState('');
  const [devLink, setDevLink] = useState('');
  const [undeliverable, setUndeliverable] = useState(false);
  const [cooldown, setCooldown] = useState(0);
  const [done, setDone] = useState(false);
  // Redeeming a ?token= from the email link, before anything else is shown.
  const [redeeming, setRedeeming] = useState(false);
  const [linkFailed, setLinkFailed] = useState('');

  // Guards the poll so a slow request cannot overlap the next tick, and so
  // nothing runs after the component is gone.
  const alive = useRef(true);
  const inFlight = useRef(false);

  useEffect(() => {
    alive.current = true;
    setDevLink(readDevLink());
    return () => {
      alive.current = false;
    };
  }, []);

  const succeed = useCallback(
    (next?: { role?: string }) => {
      clearDevCode();
      clearDevLink();
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
   * Redeem a ?token= from the verification email.
   *
   * This is THE path the product is meant to use: click the link, done. It
   * runs before any of the code-entry machinery and works with no session,
   * because the link is usually opened in a different browser from the one
   * that signed up. The server hands back a session with the confirmation, so
   * the click lands the user inside the app rather than at a login form.
   */
  useEffect(() => {
    if (!linkToken || redeeming || done) return;
    let cancelled = false;
    setRedeeming(true);
    setLinkFailed('');

    (async () => {
      try {
        const res = await authService.confirmVerificationLink(linkToken);
        if (cancelled) return;
        if (res?.token) tokenStore.set(res.token);
        if (res?.user) setUser({ ...res.user, emailVerified: true });
        // Drop the token from the address bar: it is a bearer credential and
        // has no business sitting in history, or being re-sent on refresh.
        setSearchParams({}, { replace: true });
        succeed({ role: res?.user?.role });
      } catch (err) {
        if (cancelled) return;
        setLinkFailed(
          err instanceof ApiError
            ? err.message
            : 'That verification link could not be used. Request a new one below.'
        );
        setSearchParams({}, { replace: true });
      } finally {
        if (!cancelled) setRedeeming(false);
      }
    })();

    return () => {
      cancelled = true;
    };
    // Deliberately keyed on the token alone: this must run exactly once per
    // link, not again whenever an unrelated callback identity changes.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [linkToken]);

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

  // Tick the resend cooldown down so the button says when it will work again.
  useEffect(() => {
    if (cooldown <= 0) return;
    const id = window.setInterval(() => setCooldown((n) => (n > 0 ? n - 1 : 0)), 1000);
    return () => window.clearInterval(id);
  }, [cooldown]);

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
        /* not a Firebase session — fall through to our own link mailer */
      }
      const res = await authService.requestVerification();
      if (res.alreadyVerified) {
        setUser({ ...user!, emailVerified: true });
        succeed();
        return;
      }
      if (res.devLink) {
        rememberDevLink(res.devLink);
        setDevLink(res.devLink);
      }
      // The server now reports what actually happened. Telling someone to check
      // an inbox that will never receive anything is worse than saying nothing.
      if (res.undeliverable) {
        setUndeliverable(true);
        setNote('');
        setError(
          'This site cannot send verification emails yet, so no link could be delivered. ' +
            'Signing in with Google below verifies the same address instantly and keeps ' +
            'your account.'
        );
      } else {
        setNote(
          res.sent
            ? 'Sent — check your inbox and click the link.'
            : 'A new link was generated.'
        );
      }
    } catch (err) {
      // 429: the server is protecting its send quota — and the user's inbox.
      // Show the wait rather than an unexplained refusal.
      if (err instanceof ApiError && err.status === 429) {
        const wait = err.retryAfterSec ?? 60;
        setCooldown(wait);
        setError(err.message);
      } else {
        setError(err instanceof ApiError ? err.message : 'Could not send a new link.');
      }
    } finally {
      setResending(false);
    }
  }, [setUser, succeed, user]);

  // A link opened on a device with no session: there is no user to render a
  // page around, but there IS work to do. Show the progress of redeeming the
  // token instead of a blank screen, and show the failure if it does not work
  // - silently rendering nothing is how "the link is broken" gets reported.
  if (!user) {
    if (redeeming || (linkToken && !linkFailed)) {
      return (
        <div className="auth-wrap" data-testid="verify-email-page">
          <div className="auth-form">
            <div className="auth-card verify-done" data-testid="verify-link-working">
              <div className="verify-sent-icon" aria-hidden="true">
                <MailCheck size={30} />
              </div>
              <h2 style={{ marginTop: 12 }}>Confirming your email…</h2>
              <p className="muted">One moment.</p>
            </div>
          </div>
        </div>
      );
    }
    if (linkFailed) {
      return (
        <div className="auth-wrap" data-testid="verify-email-page">
          <div className="auth-form">
            <div className="auth-card" data-testid="verify-link-failed">
              <div className="verify-sent-icon" aria-hidden="true">
                <MailCheck size={30} />
              </div>
              <h2 style={{ marginTop: 12 }}>That link did not work</h2>
              <p className="muted">{linkFailed}</p>
              <Btn
                variant="primary"
                className="btn-block btn-lg"
                onClick={() => navigate('/login', { replace: true })}
              >
                <span>Sign in to get a new link</span>
              </Btn>
            </div>
          </div>
        </div>
      );
    }
    return null;
  }

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

              {linkFailed && (
                <div className="field-error mb-8" data-testid="verify-page-link-failed">
                  {linkFailed}
                </div>
              )}

              {devLink ? (
                // No mail server on this deployment, so the link cannot be
                // delivered. Show the real link rather than a bare code: the
                // point is that clicking a link is the flow, and this keeps
                // local development on the same path as production.
                <div className="field-note mb-8" data-testid="verify-page-dev-link">
                  Email delivery is not set up for this site yet, so the link could not be
                  sent. Use it directly:{' '}
                  <a href={devLink} data-testid="verify-page-dev-link-anchor">Confirm my email</a>
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

              {note && <div className="field-note mt-8" data-testid="verify-page-note">{note}</div>}
              {error && (
                <div className="field-error mt-8" data-testid="verify-page-error">{error}</div>
              )}

              <div className="verify-page-actions">
                <Btn
                  onClick={resend}
                  disabled={resending || cooldown > 0}
                  data-testid="verify-page-resend"
                >
                  {resending
                    ? 'Sending…'
                    : cooldown > 0
                      ? `Resend in ${cooldown}s`
                      : 'Resend email'}
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

              {undeliverable && (
                <div className="verify-rescue" data-testid="verify-rescue">
                  <div className="verify-page-divider"><span>or verify instantly</span></div>
                  {/* Google proves the address itself, so it needs no mailer.
                      It adopts the existing account by email rather than making
                      a second one, so orders and messages are kept. */}
                  <GoogleButton />
                  <p className="muted" style={{ fontSize: 12, marginTop: 8 }}>
                    Use the same address you signed up with and your account stays as it is.
                  </p>
                </div>
              )}

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
