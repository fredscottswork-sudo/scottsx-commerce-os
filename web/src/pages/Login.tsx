import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../store/AuthContext';
import { useToast } from '../store/ToastContext';
import { Btn, Field, Input } from '../components/ui';
import { ApiError } from '../api/client';

export default function Login() {
  const { login } = useAuth();
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
    try {
      await login(email.trim(), password);
      toast('Signed in', 'success');
      navigate('/');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Sign-in failed');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="auth-wrap">
      <div className="auth-brand">
        <div style={{ fontSize: 40, marginBottom: 12 }}>🛍️</div>
        <h1 style={{ fontSize: 34, margin: 0 }}>ScottsTechX</h1>
        <p style={{ opacity: 0.9, fontSize: 16, maxWidth: 420 }}>
          Uganda's marketplace. Same account, same data on mobile and web — buy from local
          sellers, pay with Mobile Money, and chat in real time.
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
          <p className="muted mt-16" style={{ textAlign: 'center' }}>
            New here? <Link to="/register">Create an account</Link>
          </p>
          <p className="muted" style={{ textAlign: 'center', fontSize: 12.5 }}>
            Demo admin: admin@scottstechx.ug / Admin123! · Seller: techhub@scottstechx.ug / Seller123!
          </p>
        </div>
      </div>
    </div>
  );
}
