import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../store/AuthContext';
import { useToast } from '../store/ToastContext';
import { Btn } from '../components/ui';
import { ApiError } from '../api/client';
import GoogleButton from '../components/GoogleButton';
import { BrandMark } from '../components/BrandLogo';
import { useSeo } from '../hooks/useSeo';
import { Mail, Lock, Eye, EyeOff, ArrowRight, ShieldCheck, AlertCircle } from 'lucide-react';

export default function Login() {
  useSeo({ title: 'Sign in', noIndex: true });

  const { login, loginWithFirebase } = useAuth();
  const { toast } = useToast();
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPass, setShowPass] = useState(false);
  const [remember, setRemember] = useState(true);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError('');
    setBusy(true);
    const addr = email.trim();
    try {
      try {
        const { signInWithEmail } = await import('../lib/firebase');
        const { idToken } = await signInWithEmail(addr, password);
        await loginWithFirebase(idToken);
        toast('Signed in', 'success');
        navigate('/');
        return;
      } catch (fbErr) {
        const code = (fbErr as { code?: string })?.code || '';
        const notInFirebase =
          code === 'auth/user-not-found' ||
          code === 'auth/invalid-credential' ||
          code === 'auth/wrong-password' ||
          code === 'auth/operation-not-allowed' ||
          code === 'auth/configuration-not-found';
        if (!notInFirebase) throw fbErr;
      }

      await login(addr, password);
      toast('Signed in', 'success');
      navigate('/');
    } catch (err) {
      const { friendlyAuthError } = await import('../lib/firebase');
      setError(err instanceof ApiError ? err.message : friendlyAuthError(err));
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
            <h2>Welcome back</h2>
            <p className="muted">Sign in to ScottsTechX</p>
          </div>

          <form onSubmit={onSubmit} className="auth-better-form">
            <div className="auth-field">
              <label className="auth-label" htmlFor="login-email">Email address</label>
              <div className="auth-input-wrap">
                <Mail size={16} className="auth-input-icon" />
                <input
                  id="login-email"
                  className="auth-input"
                  type="email"
                  required
                  autoComplete="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="you@example.com"
                />
              </div>
            </div>

            <div className="auth-field">
              <div className="auth-label-row">
                <label className="auth-label" htmlFor="login-pass">Password</label>
                <Link to="/forgot-password" className="auth-link-sm">Forgot?</Link>
              </div>
              <div className="auth-input-wrap">
                <Lock size={16} className="auth-input-icon" />
                <input
                  id="login-pass"
                  className="auth-input"
                  type={showPass ? 'text' : 'password'}
                  required
                  autoComplete="current-password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="••••••••"
                />
                <button
                  type="button"
                  className="auth-pass-toggle"
                  onClick={() => setShowPass((v) => !v)}
                  aria-label={showPass ? 'Hide password' : 'Show password'}
                >
                  {showPass ? <EyeOff size={16} /> : <Eye size={16} />}
                </button>
              </div>
            </div>

            {error && (
              <div className="auth-error">
                <AlertCircle size={14} />
                <span>{error}</span>
              </div>
            )}

            <div className="auth-remember-row">
              <label className="auth-checkbox">
                <input type="checkbox" checked={remember} onChange={(e) => setRemember(e.target.checked)} />
                <span className="auth-checkbox-box" />
                <span>Remember me</span>
              </label>
              <span className="auth-secure-hint">
                <ShieldCheck size={12} /> Secure
              </span>
            </div>

            <Btn type="submit" variant="primary" className="btn-block auth-submit" disabled={busy}>
              {busy ? (
                <span className="auth-btn-loading"><span className="spinner" /> Signing in…</span>
              ) : (
                <span className="auth-btn-content">Sign in <ArrowRight size={16} /></span>
              )}
            </Btn>
          </form>

          <div className="auth-extra-foot">
            <div className="auth-divider"><span>or continue with</span></div>
            <GoogleButton />
            <p className="auth-foot-text">
              New here? <Link to="/register" className="auth-link">Create an account</Link>
            </p>
            <div className="auth-trust">
              <ShieldCheck size={13} /> Secured & encrypted • Trusted by 10k+ stores
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
