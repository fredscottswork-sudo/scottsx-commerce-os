import { useState, type FormEvent, useMemo } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../store/AuthContext';
import { useToast } from '../store/ToastContext';
import { Btn } from '../components/ui';
import { ApiError } from '../api/client';
import GoogleButton from '../components/GoogleButton';
import { BrandMark } from '../components/BrandLogo';
import { useSeo } from '../hooks/useSeo';
import { Mail, Lock, Eye, EyeOff, ArrowRight, ShieldCheck, AlertCircle, User, Phone, Store, Briefcase } from 'lucide-react';

class FirebaseFallback extends Error {
  constructor(public reason: string) {
    super(reason);
  }
}

const SETUP_INCOMPLETE: Record<string, string> = {
  'auth/operation-not-allowed':
    'Email sign-up is not enabled for this site yet. Enable Email/Password in Firebase Console → Authentication → Sign-in method.',
  'auth/configuration-not-found':
    'Sign-in is not configured for this site yet. Open Firebase Console → Authentication and enable Email/Password.',
  'auth/unauthorized-domain':
    'This website is not authorised for sign-in yet. Add it in Firebase Console → Authentication → Settings → Authorised domains.',
  'auth/invalid-api-key':
    'The sign-in configuration for this site is invalid. Check VITE_FIREBASE_API_KEY.',
  'auth/api-key-not-valid':
    'The sign-in configuration for this site is invalid. Check VITE_FIREBASE_API_KEY.',
};

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

export default function Register() {
  useSeo({
    title: 'Create an account',
    description: 'Join ScottsTechX as a buyer or open a store as a seller. Free to sign up.',
  });

  const { register, loginWithFirebase } = useAuth();
  const { toast } = useToast();
  const navigate = useNavigate();
  const [form, setForm] = useState({ displayName: '', email: '', phone: '', password: '', confirm: '', role: 'buyer', storeName: '' });
  const [showPass, setShowPass] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const strength = useMemo(() => getStrength(form.password), [form.password]);

  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
    setForm((f) => ({ ...f, [k]: e.target.value }));

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError('');
    if (form.password.length < 6) return setError('Password must be at least 6 characters');
    if (form.password !== form.confirm) return setError('Passwords do not match');
    setBusy(true);
    const email = form.email.trim();
    const profile = {
      displayName: form.displayName,
      phone: form.phone,
      role: form.role,
      storeName: form.storeName,
    };

    try {
      const { registerWithEmail, friendlyAuthError } = await import('../lib/firebase');
      let idToken: string;
      try {
        ({ idToken } = await registerWithEmail(email, form.password, form.displayName));
      } catch (fbErr) {
        const code = (fbErr as { code?: string })?.code || '';
        if (code === 'auth/email-already-in-use') {
          setError('An account already exists with that email. Try signing in instead.');
          setBusy(false);
          return;
        }
        if (code === 'auth/invalid-email' || code === 'auth/weak-password') {
          setError(friendlyAuthError(fbErr));
          setBusy(false);
          return;
        }
        console.error('[signup] Firebase unavailable, using fallback:', code || fbErr);
        throw new FirebaseFallback(code || (fbErr as Error)?.message || 'unknown');
      }

      await loginWithFirebase(idToken, profile);
      toast('Account created — verify your email to continue', 'success');
      navigate('/verify-email', { replace: true });
      return;
    } catch (err) {
      if (!(err instanceof FirebaseFallback)) {
        setError(err instanceof ApiError ? err.message : 'Registration failed');
        setBusy(false);
        return;
      }
      const setupMsg = SETUP_INCOMPLETE[err.reason];
      if (setupMsg) {
        setError(setupMsg);
        setBusy(false);
        return;
      }
    }

    try {
      await register({
        email,
        password: form.password,
        displayName: form.displayName,
        phone: form.phone,
        role: form.role as 'buyer' | 'seller',
        storeName: form.storeName,
      } as any);
      toast('Account created — check your email for the verification link', 'success');
      navigate('/verify-email', { replace: true });
    } catch (err) {
      if (err instanceof ApiError && err.status === 503) {
        setError(
          'Email sign-up is temporarily unavailable on this site — the server cannot ' +
            'send verification emails yet. You can continue with Google instead.'
        );
      } else {
        setError(err instanceof ApiError ? err.message : 'Registration failed');
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="auth-form auth-form--solo auth-form--extra">
      <div className="auth-card auth-card--extra auth-card--github auth-card--better" style={{ width: '100%', maxWidth: 420 }}>
        <div className="auth-card-glow" aria-hidden="true" />
        <div className="auth-card-inner">
          <div className="auth-card-head">
            <div className="auth-logo-wrap">
              <BrandMark size={52} className="auth-logo-top auth-logo-top--mark" />
            </div>
            <h2>Create account</h2>
            <p className="muted">Join ScottsTechX — free, secure, trusted</p>
          </div>

          <form onSubmit={onSubmit} className="auth-better-form">
            <div className="auth-field">
              <label className="auth-label" htmlFor="reg-name">Full name</label>
              <div className="auth-input-wrap">
                <User size={16} className="auth-input-icon" />
                <input id="reg-name" className="auth-input" required value={form.displayName} onChange={set('displayName')} placeholder="Kato Fred" />
              </div>
            </div>

            <div className="auth-field">
              <label className="auth-label" htmlFor="reg-email">Email address</label>
              <div className="auth-input-wrap">
                <Mail size={16} className="auth-input-icon" />
                <input id="reg-email" className="auth-input" type="email" required value={form.email} onChange={set('email')} placeholder="you@example.com" />
              </div>
            </div>

            <div className="auth-row">
              <div className="auth-field">
                <label className="auth-label" htmlFor="reg-phone">Phone</label>
                <div className="auth-input-wrap">
                  <Phone size={16} className="auth-input-icon" />
                  <input id="reg-phone" className="auth-input" value={form.phone} onChange={set('phone')} placeholder="+256 7xx xxx xxx" />
                </div>
              </div>
              <div className="auth-field">
                <label className="auth-label" htmlFor="reg-role">I am a…</label>
                <div className="auth-input-wrap">
                  <Briefcase size={16} className="auth-input-icon" />
                  <select id="reg-role" className="auth-input auth-select" value={form.role} onChange={set('role')}>
                    <option value="buyer">Buyer</option>
                    <option value="seller">Seller</option>
                  </select>
                </div>
              </div>
            </div>

            {form.role === 'seller' && (
              <div className="auth-field">
                <label className="auth-label" htmlFor="reg-store">Store name</label>
                <div className="auth-input-wrap">
                  <Store size={16} className="auth-input-icon" />
                  <input id="reg-store" className="auth-input" value={form.storeName} onChange={set('storeName')} placeholder="e.g. Tech Hub Uganda" />
                </div>
              </div>
            )}

            <div className="auth-row">
              <div className="auth-field">
                <label className="auth-label" htmlFor="reg-pass">Password</label>
                <div className="auth-input-wrap">
                  <Lock size={16} className="auth-input-icon" />
                  <input
                    id="reg-pass"
                    className="auth-input"
                    type={showPass ? 'text' : 'password'}
                    required
                    value={form.password}
                    onChange={set('password')}
                    placeholder="min 6 characters"
                  />
                  <button type="button" className="auth-pass-toggle" onClick={() => setShowPass((v) => !v)} aria-label="Toggle password">
                    {showPass ? <EyeOff size={16} /> : <Eye size={16} />}
                  </button>
                </div>
                {form.password && (
                  <div className="auth-strength">
                    <div className="auth-strength-bar">
                      <span style={{ width: `${(strength.score / 4) * 100}%`, background: strength.color }} />
                    </div>
                    <span className="auth-strength-label" style={{ color: strength.color }}>{strength.label}</span>
                  </div>
                )}
              </div>

              <div className="auth-field">
                <label className="auth-label" htmlFor="reg-confirm">Confirm</label>
                <div className="auth-input-wrap">
                  <Lock size={16} className="auth-input-icon" />
                  <input
                    id="reg-confirm"
                    className="auth-input"
                    type={showConfirm ? 'text' : 'password'}
                    required
                    value={form.confirm}
                    onChange={set('confirm')}
                    placeholder="repeat it"
                  />
                  <button type="button" className="auth-pass-toggle" onClick={() => setShowConfirm((v) => !v)} aria-label="Toggle confirm">
                    {showConfirm ? <EyeOff size={16} /> : <Eye size={16} />}
                  </button>
                </div>
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
                <span className="auth-btn-loading"><span className="spinner" /> Creating…</span>
              ) : (
                <span className="auth-btn-content">Create account <ArrowRight size={16} /></span>
              )}
            </Btn>

            <div className="auth-terms">
              <ShieldCheck size={12} /> By creating an account, you agree to our Terms & Privacy — encrypted & secure
            </div>
          </form>

          <div className="auth-extra-foot">
            <div className="auth-divider"><span>or continue with</span></div>
            <GoogleButton />
            <p className="auth-foot-text">
              Already have an account? <Link to="/login" className="auth-link">Sign in</Link>
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}
