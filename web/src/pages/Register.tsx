import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../store/AuthContext';
import { useToast } from '../store/ToastContext';
import { Btn, Field, Input, Select } from '../components/ui';
import { ApiError } from '../api/client';
import GoogleButton from '../components/GoogleButton';
import { MailCheck } from 'lucide-react';
import { BrandLockup } from '../components/BrandLogo';
import { useSeo } from '../hooks/useSeo';

/** Signals "Firebase could not be used" so the catch can fall back. */
class FirebaseFallback extends Error {}

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
  const [sentTo, setSentTo] = useState('');
  const [resending, setResending] = useState(false);
  const [resendNote, setResendNote] = useState('');

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
        // Anything else (Firebase disabled, offline, SDK blocked) is an
        // infrastructure problem, not the user's fault: fall back below.
        throw new FirebaseFallback();
      }

      await loginWithFirebase(idToken, profile);
      setSentTo(email);
      toast('Account created — check your email to verify', 'success');
      return;
    } catch (err) {
      if (!(err instanceof FirebaseFallback)) {
        setError(err instanceof ApiError ? err.message : 'Registration failed');
        setBusy(false);
        return;
      }
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
      toast('Account created', 'success');
      navigate('/');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Registration failed');
    } finally {
      setBusy(false);
    }
  }

  // Firebase has emailed the link. Say so plainly and give the two actions
  // that actually help: resend, and continue browsing while they go check.
  if (sentTo) {
    return (
      <div className="auth-wrap">
        <div className="auth-brand">
          <BrandLockup width={300} className="auth-lockup" />
          <h1 className="auth-tagline">One more step.</h1>
          <p style={{ opacity: 0.9, fontSize: 16, maxWidth: 'min(420px, 100%)' }}>
            Verifying your email keeps your account recoverable and unlocks selling.
          </p>
        </div>
        <div className="auth-form">
          <div className="auth-card" data-testid="verify-sent">
            <div className="verify-sent-icon" aria-hidden="true">
              <MailCheck size={30} />
            </div>
            <h2 style={{ marginTop: 12 }}>Check your email</h2>
            <p className="muted">
              We sent a verification link to <b>{sentTo}</b>. Open it to confirm this
              address is yours.
            </p>
            <p className="muted" style={{ fontSize: 13 }}>
              Nothing yet? It can take a minute — and it is worth checking your spam
              folder.
            </p>

            {resendNote && (
              <div className="field-note mb-8" data-testid="verify-sent-note">{resendNote}</div>
            )}

            <Btn
              variant="primary"
              className="btn-block btn-lg"
              disabled={resending}
              data-testid="verify-sent-resend"
              onClick={async () => {
                setResending(true);
                setResendNote('');
                try {
                  const { resendVerificationEmail } = await import('../lib/firebase');
                  setResendNote(
                    (await resendVerificationEmail())
                      ? 'Sent again — check your inbox.'
                      : 'Please sign in again to resend.'
                  );
                } catch (err) {
                  const { friendlyAuthError } = await import('../lib/firebase');
                  setResendNote(friendlyAuthError(err));
                } finally {
                  setResending(false);
                }
              }}
            >
              {resending ? 'Sending…' : 'Resend the email'}
            </Btn>

            <Btn
              className="btn-block mt-8"
              data-testid="verify-sent-continue"
              onClick={() => navigate('/')}
            >
              Continue to ScottsTechX
            </Btn>

            <p className="muted mt-16" style={{ textAlign: 'center', fontSize: 13 }}>
              You are signed in already. We will keep reminding you until the address
              is verified.
            </p>
          </div>
        </div>
      </div>
    );
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
