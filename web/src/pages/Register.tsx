import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../store/AuthContext';
import { useToast } from '../store/ToastContext';
import { Btn, Field, Input, Select } from '../components/ui';
import { ApiError } from '../api/client';
import GoogleButton from '../components/GoogleButton';
import { BrandLockup } from '../components/BrandLogo';
import { useSeo } from '../hooks/useSeo';

/** Signals "Firebase could not be used" so the catch can fall back. */
class FirebaseFallback extends Error {
  constructor(public reason: string) {
    super(reason);
  }
}

/**
 * Firebase codes that mean the project is not finished being set up. These get
 * an explicit instruction rather than a vague apology, because the fix is a
 * console toggle and only the site owner can do it.
 */
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

export default function Register() {
  useSeo({
    title: 'Create an account',
    description: 'Join ScottsTechX as a buyer or open a store as a seller. Free to sign up.',
  });

  const { register, loginWithFirebase } = useAuth();
  const { toast } = useToast();
  const navigate = useNavigate();
  const [form, setForm] = useState({ displayName: '', email: '', phone: '', password: '', confirm: '', role: 'buyer', storeName: '' });
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  /** Set once Firebase has emailed a verification link. */

  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
    setForm((f) => ({ ...f, [k]: e.target.value }));

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError('');
    if (form.password.length < 6) return setError('Password must be at least 6 characters');
    if (form.password !== form.confirm) return setError('Passwords do not match');
    setBusy(true);
    const email = form.email.trim();
    /** Why Firebase was skipped, if it was. Shown with the fallback code. */
    let fallbackReason = '';
    const profile = {
      displayName: form.displayName,
      phone: form.phone,
      role: form.role,
      storeName: form.storeName,
    };

    try {
      // Preferred path: create the account in Firebase, which emails the
      // verification link itself. email_verified rides inside the signed token,
      // so the address is proven by Google rather than asserted by us.
      const { registerWithEmail, friendlyAuthError } = await import('../lib/firebase');
      let idToken: string;
      try {
        ({ idToken } = await registerWithEmail(email, form.password, form.displayName));
      } catch (fbErr) {
        const code = (fbErr as { code?: string })?.code || '';
        // An address already in Firebase is a real, actionable error — do NOT
        // fall through and quietly make a second, unverified local account.
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
        // Anything else is an infrastructure problem, not the user's fault.
        // We still fall back so sign-up never becomes impossible — but we
        // record WHY, because silently handing someone a six-digit code when
        // they were promised an email link is indistinguishable from the
        // feature being broken. It is the reason this bug went unnoticed.
        // eslint-disable-next-line no-console
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
      // A misconfigured project is not a transient glitch: say exactly what to
      // fix rather than quietly downgrading to the code and looking broken.
      const setupMsg = SETUP_INCOMPLETE[err.reason];
      if (setupMsg) {
        setError(setupMsg);
        setBusy(false);
        return;
      }
      fallbackReason = err.reason;
    }

    // Fallback: our own account + six-digit code. Used when Firebase Auth is
    // unavailable, so sign-up never becomes impossible.
    try {
      await register({
        email,
        password: form.password,
        displayName: form.displayName,
        phone: form.phone,
        role: form.role as 'buyer' | 'seller',
        storeName: form.storeName,
      } as any);
      toast(
        fallbackReason
          ? 'Account created — verify with the code shown on screen'
          : 'Account created — verify your email to continue',
        'success'
      );
      // The fallback path is unverified too, so it goes through the same gate.
      navigate('/verify-email', { replace: true });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Registration failed');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="auth-wrap">
      <div className="auth-brand">
        <BrandLockup width={300} className="auth-lockup" />
        <h1 className="auth-tagline">Join the marketplace built for Uganda.</h1>
        <p style={{ opacity: 0.9, fontSize: 16, maxWidth: 'min(420px, 100%)' }}>
          One account for web and mobile. Buy from local sellers or open your own store —
          the same backend powers every screen.
        </p>
      </div>
      <div className="auth-form">
        <div className="auth-card">
          <h2 style={{ marginTop: 0 }}>Create account</h2>
          <p className="muted">Buy, sell, chat — all in one place.</p>
          <form onSubmit={onSubmit}>
            <Field label="Full name">
              <Input required value={form.displayName} onChange={set('displayName')} placeholder="Kato Fred" />
            </Field>
            <Field label="Email">
              <Input type="email" required value={form.email} onChange={set('email')} placeholder="you@example.com" />
            </Field>
            <div className="form-row">
              <Field label="Phone">
                <Input value={form.phone} onChange={set('phone')} placeholder="+256 7xx xxx xxx" />
              </Field>
              <Field label="I am a…">
                <Select value={form.role} onChange={set('role')}>
                  <option value="buyer">Buyer</option>
                  <option value="seller">Seller</option>
                </Select>
              </Field>
            </div>
            {form.role === 'seller' && (
              <Field label="Store name">
                <Input value={form.storeName} onChange={set('storeName')} placeholder="e.g. Tech Hub Uganda" />
              </Field>
            )}
            <div className="form-row">
              <Field label="Password">
                <Input type="password" required value={form.password} onChange={set('password')} placeholder="min 6 characters" />
              </Field>
              <Field label="Confirm password">
                <Input type="password" required value={form.confirm} onChange={set('confirm')} placeholder="repeat it" />
              </Field>
            </div>
            {error && <div className="field-error mb-8">{error}</div>}
            <Btn type="submit" variant="primary" className="btn-block btn-lg" disabled={busy}>
              {busy ? 'Creating…' : 'Create account'}
            </Btn>
          </form>
          <GoogleButton />
          <p className="muted mt-16" style={{ textAlign: 'center' }}>
            Already have an account? <Link to="/login">Sign in</Link>
          </p>
        </div>
      </div>
    </div>
  );
}
