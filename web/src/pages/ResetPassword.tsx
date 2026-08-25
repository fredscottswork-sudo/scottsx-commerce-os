/**
 * ScottsTechX — /reset-password
 *
 * The destination of the link in the password-reset email: ?token= is a
 * single-use, 30-minute bearer credential. This page is the ONLY thing that
 * redeems it — no session, no other proof of ownership is involved, because
 * the token itself IS the proof (32 random bytes that only reached the
 * person who can read that inbox).
 *
 * The token is stripped from the address bar the moment the page loads, so
 * it never sits in history and is never re-sent on refresh.
 */
import { useCallback, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { KeyRound, LogIn } from 'lucide-react';
import { authService } from '../api/services';
import { ApiError } from '../api/client';
import { Btn, Field, Input } from '../components/ui';
import { BrandLockup } from '../components/BrandLogo';
import { useSeo } from '../hooks/useSeo';

export default function ResetPassword() {
  useSeo({ title: 'Reset your password', noIndex: true });

  const [searchParams, setSearchParams] = useSearchParams();
  const token = searchParams.get('token') || '';
  const navigate = useNavigate();

  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [done, setDone] = useState(false);

  const stripToken = useCallback(() => {
    if (searchParams.toString()) setSearchParams({}, { replace: true });
  }, [searchParams, setSearchParams]);

  const submit = useCallback(
    async (e: React.FormEvent) => {
      e.preventDefault();
      setError('');
      if (password.length < 6) {
        setError('Password must be at least 6 characters.');
        return;
      }
      if (password !== confirm) {
        setError('The two passwords do not match.');
        return;
      }
      setBusy(true);
      try {
        await authService.resetPassword(token, password);
        stripToken();
        setDone(true);
      } catch (err) {
        setError(err instanceof ApiError ? err.message : 'Could not reset the password. Try the link again.');
      } finally {
        setBusy(false);
      }
    },
    [password, confirm, token, stripToken]
  );

  return (
    <div className="auth-wrap" data-testid="reset-password-page">
      <div className="auth-brand">
        <BrandLockup width={300} className="auth-lockup" />
        <h1 className="auth-tagline">Set a new password.</h1>
        <p style={{ opacity: 0.9, fontSize: 16, maxWidth: 'min(420px, 100%)' }}>
          The link in your email is one-time and expires in 30 minutes, so finish this now.
        </p>
      </div>

      <div className="auth-form">
        <div className="auth-card">
          {done ? (
            <div data-testid="reset-success">
              <div className="verify-sent-icon verify-done-icon" aria-hidden="true">
                <KeyRound size={30} />
              </div>
              <h2 style={{ marginTop: 12 }}>Password updated</h2>
              <p className="muted">Your new password is in effect. Sign in with it.</p>
              <Btn
                variant="primary"
                className="btn-block btn-lg mt-8"
                onClick={() => navigate('/login', { replace: true })}
                data-testid="reset-go-login"
              >
                <LogIn size={16} />
                <span>Sign in</span>
              </Btn>
            </div>
          ) : token ? (
            <form onSubmit={submit}>
              <div className="verify-sent-icon" aria-hidden="true">
                <KeyRound size={30} />
              </div>
              <h2 style={{ marginTop: 12 }}>Choose a new password</h2>
              <p className="muted">At least 6 characters. Something you have not used here before.</p>
              <Field label="New password">
                <Input
                  type="password"
                  required
                  autoComplete="new-password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="••••••••"
                />
              </Field>
              <Field label="Confirm new password">
                <Input
                  type="password"
                  required
                  autoComplete="new-password"
                  value={confirm}
                  onChange={(e) => setConfirm(e.target.value)}
                  placeholder="••••••••"
                />
              </Field>
              {error && <div className="field-error mb-8" data-testid="reset-error">{error}</div>}
              <Btn type="submit" variant="primary" className="btn-block btn-lg" disabled={busy}>
                {busy ? 'Updating…' : 'Update password'}
              </Btn>
            </form>
          ) : (
            <div data-testid="reset-no-token">
              <div className="verify-sent-icon" aria-hidden="true">
                <KeyRound size={30} />
              </div>
              <h2 style={{ marginTop: 12 }}>No reset link found</h2>
              <p className="muted">
                This page needs the link from the reset email — it does not work on its own.
              </p>
              <Btn
                variant="primary"
                className="btn-block btn-lg mt-8"
                onClick={() => navigate('/login', { replace: true })}
              >
                <span>Back to sign in</span>
              </Btn>
              <p className="muted mt-16" style={{ textAlign: 'center', fontSize: 13 }}>
                Lost the email? Request a new link from <Link to="/login">sign in</Link>.
              </p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
