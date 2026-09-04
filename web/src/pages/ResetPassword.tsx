/**
 * ScottsTechX — /reset-password
 * Extraordinary GitHub/Alibaba style, STX mark only
 */
import { useCallback, useState, useMemo } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { KeyRound, LogIn, Lock, Eye, EyeOff, ArrowRight, ShieldCheck, AlertCircle, CheckCircle2 } from 'lucide-react';
import { authService } from '../api/services';
import { ApiError } from '../api/client';
import { Btn } from '../components/ui';
import { BrandMark } from '../components/BrandLogo';
import { useSeo } from '../hooks/useSeo';

function getStrength(pw: string) {
  if (!pw) return { score: 0, label: '', color: '' };
  let s = 0;
  if (pw.length >= 6) s++;
  if (pw.length >= 10) s++;
  if (/[A-Z]/.test(pw) && /[a-z]/.test(pw)) s++;
  if (/\d/.test(pw)) s++;
  if (/[^A-Za-z0-9]/.test(pw)) s++;
  if (s <= 2) return { score: 1, label: 'Weak', color: '#ef4444' };
  if (s <= 3) return { score: 2, label: 'Fair', color: '#f59e0b' };
  if (s <= 4) return { score: 3, label: 'Good', color: '#10b981' };
  return { score: 4, label: 'Strong', color: '#059669' };
}

export default function ResetPassword() {
  useSeo({ title: 'Reset your password', noIndex: true });

  const [searchParams, setSearchParams] = useSearchParams();
  const token = searchParams.get('token') || '';
  const navigate = useNavigate();

  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [showPass, setShowPass] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [done, setDone] = useState(false);

  const strength = useMemo(() => getStrength(password), [password]);

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
    <div className="auth-form auth-form--solo auth-form--extra">
      <div className="auth-card auth-card--extra auth-card--github auth-card--better" style={{ width: '100%', maxWidth: 380 }}>
        <div className="auth-card-glow" aria-hidden="true" />
        <div className="auth-card-inner">
          <div className="auth-card-head">
            <div className="auth-logo-wrap">
              <BrandMark size={52} className="auth-logo-top auth-logo-top--mark" />
            </div>
            <h2>{done ? 'Password updated' : token ? 'Set new password' : 'No reset link'}</h2>
            <p className="muted">
              {done ? 'Your new password is active' : token ? 'At least 6 characters, unique to this site' : 'This page needs the email link'}
            </p>
          </div>

          {done ? (
            <div className="auth-success">
              <div className="auth-success-icon auth-success-icon--green">
                <CheckCircle2 size={28} />
              </div>
              <p className="auth-success-text">Your new password is in effect. Sign in with it now.</p>
              <Btn variant="primary" className="btn-block auth-submit" onClick={() => navigate('/login', { replace: true })}>
                <span className="auth-btn-content"><LogIn size={16} /> Sign in</span>
              </Btn>
              <div className="auth-trust" style={{ marginTop: 16 }}>
                <ShieldCheck size={13} /> Secured • Encrypted
              </div>
            </div>
          ) : token ? (
            <form onSubmit={submit} className="auth-better-form">
              <div className="auth-icon-badge">
                <KeyRound size={20} />
              </div>

              <div className="auth-field">
                <label className="auth-label" htmlFor="new-pass">New password</label>
                <div className="auth-input-wrap">
                  <Lock size={16} className="auth-input-icon" />
                  <input
                    id="new-pass"
                    className="auth-input"
                    type={showPass ? 'text' : 'password'}
                    required
                    autoComplete="new-password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    placeholder="••••••••"
                  />
                  <button type="button" className="auth-pass-toggle" onClick={() => setShowPass((v) => !v)} aria-label="Toggle password">
                    {showPass ? <EyeOff size={16} /> : <Eye size={16} />}
                  </button>
                </div>
                {password && (
                  <div className="auth-strength">
                    <div className="auth-strength-bar">
                      <span style={{ width: `${(strength.score / 4) * 100}%`, background: strength.color }} />
                    </div>
                    <span className="auth-strength-label" style={{ color: strength.color }}>{strength.label}</span>
                  </div>
                )}
              </div>

              <div className="auth-field">
                <label className="auth-label" htmlFor="confirm-pass">Confirm new password</label>
                <div className="auth-input-wrap">
                  <Lock size={16} className="auth-input-icon" />
                  <input
                    id="confirm-pass"
                    className="auth-input"
                    type={showConfirm ? 'text' : 'password'}
                    required
                    autoComplete="new-password"
                    value={confirm}
                    onChange={(e) => setConfirm(e.target.value)}
                    placeholder="••••••••"
                  />
                  <button type="button" className="auth-pass-toggle" onClick={() => setShowConfirm((v) => !v)} aria-label="Toggle confirm">
                    {showConfirm ? <EyeOff size={16} /> : <Eye size={16} />}
                  </button>
                </div>
              </div>

              {error && (
                <div className="auth-error">
                  <AlertCircle size={14} />
                  <span>{error}</span>
                </div>
              )}

              <Btn type="submit" variant="primary" className="btn-block auth-submit" disabled={busy}>
                {busy ? (
                  <span className="auth-btn-loading"><span className="spinner" /> Updating…</span>
                ) : (
                  <span className="auth-btn-content">Update password <ArrowRight size={16} /></span>
                )}
              </Btn>

              <div className="auth-terms">
                <ShieldCheck size={12} /> One-time link • Expires in 30 minutes • Encrypted
              </div>
            </form>
          ) : (
            <div className="auth-success">
              <div className="auth-success-icon">
                <KeyRound size={24} />
              </div>
              <p className="auth-success-text">This page needs the link from the reset email — it does not work on its own.</p>
              <Btn variant="primary" className="btn-block auth-submit" onClick={() => navigate('/login', { replace: true })}>
                <span className="auth-btn-content">Back to sign in</span>
              </Btn>
              <p className="auth-foot-text" style={{ marginTop: 12 }}>
                Lost the email? Request a new link from <Link to="/forgot-password" className="auth-link">forgot password</Link>.
              </p>
            </div>
          )}

          {!done && token && (
            <div className="auth-extra-foot">
              <p className="auth-foot-text">
                Remembered? <Link to="/login" className="auth-link">Sign in</Link>
              </p>
              <div className="auth-trust">
                <ShieldCheck size={13} /> Secured & encrypted
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
