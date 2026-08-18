import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../store/AuthContext';
import { useToast } from '../store/ToastContext';
import { Btn, Field, Input, Select } from '../components/ui';
import { ApiError } from '../api/client';
import GoogleButton from '../components/GoogleButton';
import { BrandLockup } from '../components/BrandLogo';
import { useSeo } from '../hooks/useSeo';

export default function Register() {
  useSeo({
    title: 'Create an account',
    description: 'Join ScottsTechX as a buyer or open a store as a seller. Free to sign up.',
  });

  const { register } = useAuth();
  const { toast } = useToast();
  const navigate = useNavigate();
  const [form, setForm] = useState({ displayName: '', email: '', phone: '', password: '', confirm: '', role: 'buyer', storeName: '' });
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
    setForm((f) => ({ ...f, [k]: e.target.value }));

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError('');
    if (form.password.length < 6) return setError('Password must be at least 6 characters');
    if (form.password !== form.confirm) return setError('Passwords do not match');
    setBusy(true);
    try {
      await register({
        email: form.email.trim(),
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

  return (
    <div className="auth-wrap">
      <div className="auth-brand">
        <BrandLockup width={300} className="auth-lockup" />
        <h1 className="auth-tagline">Join the marketplace built for Uganda.</h1>
        <p style={{ opacity: 0.9, fontSize: 16, maxWidth: 420 }}>
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
