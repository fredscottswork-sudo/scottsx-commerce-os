/**
 * Email verification prompt.
 *
 * Registration used to mark every account `email_verified = true`, so an
 * address only had to *parse* to become a real user. The backend now issues a
 * six-digit code on sign-up and this is where the user spends it.
 *
 * It renders nothing for verified users and nothing for signed-out visitors,
 * so it is safe to mount once in the app shell.
 */
import { useCallback, useEffect, useState } from 'react';
import { useAuth } from '../store/AuthContext';
import { useToast } from '../store/ToastContext';
import { authService } from '../api/services';
import { ApiError } from '../api/client';
import { Btn, Input } from './ui';

/** Where the code lands when SMTP is not configured (local/dev only). */
const DEV_CODE_KEY = 'stx_dev_verify_code';

export function rememberDevCode(code?: string) {
  try {
    if (code) sessionStorage.setItem(DEV_CODE_KEY, code);
  } catch {
    /* private mode */
  }
}

function readDevCode(): string {
  try {
    return sessionStorage.getItem(DEV_CODE_KEY) || '';
  } catch {
    return '';
  }
}

function clearDevCode() {
  try {
    sessionStorage.removeItem(DEV_CODE_KEY);
  } catch {
    /* ignore */
  }
}

export default function VerifyEmailBanner() {
  const { user, setUser, loginWithFirebase } = useAuth();
  const { toast } = useToast();
  const [open, setOpen] = useState(false);
  const [code, setCode] = useState('');
  const [busy, setBusy] = useState(false);
  const [resending, setResending] = useState(false);
  const [checking, setChecking] = useState(false);
  const [error, setError] = useState('');
  const [devCode, setDevCode] = useState('');

  useEffect(() => {
    if (user && !user.emailVerified) setDevCode(readDevCode());
  }, [user]);

  const confirm = useCallback(async () => {
    const trimmed = code.replace(/\D/g, '');
    if (trimmed.length !== 6) {
      setError('Enter the 6-digit code from your email.');
      return;
    }
    setBusy(true);
    setError('');
    try {
      const res = await authService.confirmVerification(trimmed);
      if (res?.user) setUser({ ...user!, ...res.user, emailVerified: true });
      clearDevCode();
      setOpen(false);
      setCode('');
      toast('Email verified — thank you!', 'success');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not verify that code.');
    } finally {
      setBusy(false);
    }
  }, [code, setUser, toast, user]);

  /**
   * Ask Firebase whether the address has been verified since we last looked.
   *
   * Firebase's verification link opens in the mail client, not in this tab, so
   * nothing tells the app it happened. This is the "I've clicked the link"
   * button. A forced token refresh is essential: without it the cached ID
   * token keeps its stale email_verified:false claim.
   */
  const recheck = useCallback(async () => {
    setChecking(true);
    setError('');
    try {
      const { refreshVerificationState } = await import('../lib/firebase');
      const { verified, idToken } = await refreshVerificationState();
      if (!idToken) {
        setError('Please sign in again to refresh your verification status.');
        return;
      }
      if (!verified) {
        setError('Not verified yet — open the link in the email, then try again.');
        return;
      }
      // Hand the refreshed token to the backend so our own record catches up.
      const stored = await loginWithFirebase(idToken);
      setUser({ ...stored, emailVerified: true });
      toast('Email verified — thank you!', 'success');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not check your verification status.');
    } finally {
      setChecking(false);
    }
  }, [loginWithFirebase, setUser, toast]);

  const resend = useCallback(async () => {
    setResending(true);
    setError('');
    try {
      // Prefer Firebase: it sends the mail itself, so no SMTP is required.
      try {
        const { resendVerificationEmail } = await import('../lib/firebase');
        if (await resendVerificationEmail()) {
          toast('Verification email sent — check your inbox.', 'success');
          return;
        }
      } catch {
        /* not a Firebase session; fall through to our own code mailer */
      }
      const res = await authService.requestVerification();
      if (res.alreadyVerified) {
        setUser({ ...user!, emailVerified: true });
        toast('Your email is already verified.', 'success');
        return;
      }
      if (res.devCode) {
        rememberDevCode(res.devCode);
        setDevCode(res.devCode);
      }
      toast(res.sent ? 'A new code is on its way.' : 'A new code was generated.', 'success');
      setOpen(true);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not send a new code.');
    } finally {
      setResending(false);
    }
  }, [setUser, toast, user]);

  if (!user || user.emailVerified) return null;

  return (
    <div className="verify-banner" data-testid="verify-banner" role="status">
      <div className="verify-banner-main">
        <span className="verify-banner-dot" aria-hidden="true" />
        <div>
          <strong>Verify your email</strong>
          <p className="verify-banner-sub">
            {devCode ? (
              <>Enter the code below to confirm <b>{user.email}</b>.</>
            ) : (
              <>
                Open the verification link we emailed to <b>{user.email}</b>, then press
                “I&rsquo;ve verified”.
              </>
            )}{' '}
            Verifying keeps your account recoverable and unlocks selling.
          </p>
        </div>
      </div>

      {!open ? (
        <div className="verify-banner-actions">
          <Btn variant="primary" onClick={() => setOpen(true)} data-testid="verify-open">
            {devCode ? 'Enter code' : 'Enter a code instead'}
          </Btn>
          <Btn onClick={resend} disabled={resending} data-testid="verify-resend">
            {resending ? 'Sending…' : 'Resend'}
          </Btn>
          <Btn onClick={recheck} disabled={checking} data-testid="verify-recheck">
            {checking ? 'Checking…' : "I've verified"}
          </Btn>
        </div>
      ) : (
        <form
          className="verify-banner-form"
          onSubmit={(e) => {
            e.preventDefault();
            void confirm();
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
            data-testid="verify-code-input"
          />
          <Btn type="submit" variant="primary" disabled={busy} data-testid="verify-submit">
            {busy ? 'Checking…' : 'Verify'}
          </Btn>
          <Btn onClick={resend} disabled={resending} data-testid="verify-resend">
            {resending ? 'Sending…' : 'Resend'}
          </Btn>
        </form>
      )}

      {devCode && (
        // Shown only when the backend reports no SMTP configured; with a real
        // mailer the API never returns a code at all.
        <p className="verify-banner-dev" data-testid="verify-dev-code">
          Email delivery is not set up for this site yet, so no link could be
          sent. Use this code instead: <b>{devCode}</b>
        </p>
      )}
      {error && (
        <p className="field-error" data-testid="verify-error">
          {error}
        </p>
      )}
    </div>
  );
}
