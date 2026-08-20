import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../store/AuthContext';
import { useToast } from '../store/ToastContext';
import { Btn, Field, Input } from '../components/ui';
import { ApiError } from '../api/client';
import GoogleButton from '../components/GoogleButton';
import { BrandLockup } from '../components/BrandLogo';
import { useSeo } from '../hooks/useSeo';

export default function Login() {
  useSeo({ title: 'Sign in', noIndex: true });

  const { login, loginWithFirebase } = useAuth();
  const { toast } = useToast();
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError('');
    setBusy(true);
    const addr = email.trim();
    try {
      // Try Firebase first so accounts created there sign in with the same
      // credentials — and so their verification state comes from Google.
      try {
        const { signInWithEmail } = await import('../lib/firebase');
        const { idToken } = await signInWithEmail(addr, password);
        await loginWithFirebase(idToken);
        toast('Signed in', 'success');
        navigate('/');
        return;
      } catch (fbErr) {
        const code = (fbErr as { code?: string })?.code || '';
        // "No such Firebase user" is expected for accounts that pre-date
        // Firebase or were made through the fallback path — try those below.
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
    <div className="auth-wrap">
      <div className="auth-brand">
        <BrandLockup width={300} className="auth-lockup" />
        <h1 className="auth-tagline">Uganda's marketplace, everywhere you are.</h1>
        <p style={{ opacity: 0.9, fontSize: 16, maxWidth: 'min(420px, 100%)' }}>
          Same account, same data on mobile and web — buy from local sellers, pay with
          Mobile Money, and chat in real time.
        </p>
      </div>
      <div className="auth-form">
        <div className="auth-card">
          <h2 style={{ marginTop: 0 }}>Welcome back</h2>
          <p className="muted">Sign in to your ScottsTechX account.</p>
          <form onSubmit={onSubmit}>
            <Field label="Email">
              <Input type="email" required autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="you@example.com" />
            </Field>
            <Field label="Password">
              <Input type="password" required autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder="••••••••" />
            </Field>
            {error && <div className="field-error mb-8">{error}</div>}
            <Btn type="submit" variant="primary" className="btn-block btn-lg" disabled={busy}>
              {busy ? 'Signing in…' : 'Sign in'}
            </Btn>
          </form>
          <GoogleButton />
          <p className="muted mt-16" style={{ textAlign: 'center' }}>
            New here? <Link to="/register">Create an account</Link>
          </p>
        </div>
      </div>
    </div>
  );
}
