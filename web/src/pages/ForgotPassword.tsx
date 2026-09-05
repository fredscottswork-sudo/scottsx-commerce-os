import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { authService } from '../api/services';
import { ApiError } from '../api/client';
import { Btn } from '../components/ui';
import { BrandMark } from '../components/BrandLogo';
import { useSeo } from '../hooks/useSeo';
import { Mail, ArrowRight, ArrowLeft, ShieldCheck, AlertCircle, CheckCircle2 } from 'lucide-react';

export default function ForgotPassword() {
  useSeo({ title: 'Forgot password', noIndex: true });
  const [identifier, setIdentifier] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [done, setDone] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError('');
    const id = identifier.trim();
    if (!id) return setError('Enter your email address');
    setBusy(true);
    try {
      await authService.requestPasswordReset(id);
      setDone(true);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not send reset link. Try again.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="auth-form auth-form--solo auth-form--extra">
      <div className="auth-card auth-card--extra auth-card--github auth-card--better" style={{ width: '100%', maxWidth: 380 }}>
        <div className="auth-card-glow" aria-hidden="true" />
        <div className="auth-card-inner">
          <div className="auth-card-head">
            <div className="auth-logo-wrap">
              <BrandMark size={52} className="auth-logo-top auth-logo-top--mark" />
            </div>
            <h2>{done ? 'Check your email' : 'Forgot password?'}</h2>
            <p className="muted">
              {done ? 'We sent a reset link if that email exists' : 'No worries — we’ll send you a reset link'}
            </p>
          </div>

          {done ? (
            <div className="auth-success">
              <div className="auth-success-icon">
                <CheckCircle2 size={28} />
              </div>
              <p className="auth-success-text">
                If an account exists for <strong>{identifier}</strong>, you’ll get an email with a 30-minute reset link.
              </p>
              <div className="auth-success-actions">
                <Link to="/login" className="btn btn-primary btn-block auth-submit" style={{ textDecoration: 'none', justifyContent: 'center' }}>
                  <ArrowLeft size={16} /> Back to sign in
                </Link>
              </div>
              <div className="auth-trust" style={{ marginTop: 16 }}>
                <ShieldCheck size={13} /> Link expires in 30 minutes • One-time use
              </div>
            </div>
          ) : (
            <form onSubmit={onSubmit} className="auth-better-form">
              <div className="auth-field">
                <label className="auth-label" htmlFor="forgot-email">Email address</label>
                <div className="auth-input-wrap">
                  <Mail size={16} className="auth-input-icon" />
                  <input
                    id="forgot-email"
                    className="auth-input"
                    type="email"
                    required
                    autoComplete="email"
                    value={identifier}
                    onChange={(e) => setIdentifier(e.target.value)}
                    placeholder="you@example.com"
                  />
                </div>
                <span className="auth-hint">Enter the email you used for ScottsTechX</span>
              </div>

              {error && (
                <div className="auth-error">
                  <AlertCircle size={14} />
                  <span>{error}</span>
                </div>
              )}

              <Btn type="submit" variant="primary" className="btn-block auth-submit" disabled={busy}>
                {busy ? (
                  <span className="auth-btn-loading"><span className="spinner" /> Sending link…</span>
                ) : (
                  <span className="auth-btn-content">Send reset link <ArrowRight size={16} /></span>
                )}
              </Btn>

              <div className="auth-foot-row">
                <Link to="/login" className="auth-link-sm" style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
                  <ArrowLeft size={14} /> Back to sign in
                </Link>
                <span className="auth-secure-hint"><ShieldCheck size={12} /> Secure</span>
              </div>
            </form>
          )}

          {!done && (
            <div className="auth-extra-foot">
              <p className="auth-foot-text">
                Remembered? <Link to="/login" className="auth-link">Sign in</Link> • <Link to="/login" className="auth-link">Create account</Link>
              </p>
              <div className="auth-trust">
                <ShieldCheck size={13} /> Secured & encrypted • ScottsTechX
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
