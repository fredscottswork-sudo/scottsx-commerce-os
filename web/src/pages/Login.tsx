/**
 * Sign in / sign up — one screen, no passwords.
 *
 *   step "email"  → email field · Continue with Google · Continue as guest
 *   step "code"   → six-digit code the API emailed (SMTP) · resend · change email
 *
 * A correct code signs the visitor in. New accounts (and first-time Google
 * users) are sent to /onboarding to pick buyer or seller; returning users go
 * straight to their dashboard. Guests land on the marketplace home; Nearby and
 * the AI Shopper ask them to sign in when they get there.
 */
import { useEffect, useRef, useState, type ClipboardEvent, type CSSProperties, type FormEvent, type KeyboardEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../store/AuthContext';
import { useToast } from '../store/ToastContext';
import { Btn } from '../components/ui';
import { ApiError } from '../api/client';
import { authService } from '../api/services';
import GoogleButton from '../components/GoogleButton';
import AnimatedWordmark from '../components/AnimatedWordmark';
import { useSeo } from '../hooks/useSeo';
import { Mail, ArrowRight, ArrowLeft, AlertCircle, ShieldCheck, Compass, RefreshCw, MapPin, Sparkles, MessageCircle } from 'lucide-react';

const CODE_LEN = 6;

/** Same photos and claims as the mobile marketplace advert on the home page. */
const SIDE_SLIDES = [
  { id: 'chat', img: '/ads/chat.jpg', accent: '#38bdf8', kicker: 'Message first', title: 'Talk to the seller before you buy', icon: <MessageCircle size={14} /> },
  { id: 'nearby', img: '/ads/nearby.jpg', accent: '#34d399', kicker: 'Nearby', title: 'Real stores in your village, right now', icon: <MapPin size={14} /> },
  { id: 'ai', img: '/ads/ai.jpg', accent: '#a78bfa', kicker: 'AI shopper', title: 'Snap a photo. We find it and the best price.', icon: <Sparkles size={14} /> },
  { id: 'genuine', img: '/ads/genuine.jpg', accent: '#fbbf24', kicker: 'Verified sellers', title: 'Local, genuine and delivered to your door', icon: <ShieldCheck size={14} /> },
] as const;

function SidePanel() {
  const [idx, setIdx] = useState(0);
  useEffect(() => {
    const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    const t = setInterval(() => setIdx((i) => (i + 1) % SIDE_SLIDES.length), reduce ? 9000 : 5500);
    return () => clearInterval(t);
  }, []);
  const active = SIDE_SLIDES[idx];
  return (
    <aside className="ali-side" aria-hidden="true">
      {SIDE_SLIDES.map((sl, i) => (
        <div
          key={sl.id}
          className={`ali-slide${i === idx ? ' is-active' : ''}`}
          style={{ backgroundImage: `url(${sl.img})` } as CSSProperties}
        />
      ))}
      <div className="ali-scrim" />
      <div className="ali-side-inner">
        <AnimatedWordmark />
        <h1>Uganda's marketplace for verified tech &amp; more</h1>
        <div className="ali-claim" key={active.id}>
          <span className="ali-kicker" style={{ color: active.accent }}>{active.icon}{active.kicker}</span>
          <strong>{active.title}</strong>
        </div>
        <ul className="ali-perks">
          <li><ShieldCheck size={18} /><div><strong>Verified sellers</strong><span>Every store is checked before it can list.</span></div></li>
          <li><MapPin size={18} /><div><strong>Shop nearby</strong><span>Find stores around you on a live map.</span></div></li>
          <li><Sparkles size={18} /><div><strong>AI Shopper</strong><span>Compare prices and stock in one chat.</span></div></li>
          <li><MessageCircle size={18} /><div><strong>Chat &amp; bargain</strong><span>Message sellers and make offers directly.</span></div></li>
        </ul>
        <div className="ali-dots">
          {SIDE_SLIDES.map((sl, i) => (
            <button key={sl.id} type="button" tabIndex={-1} className={`ali-dot${i === idx ? ' active' : ''}`} onClick={() => setIdx(i)} />
          ))}
        </div>
      </div>
    </aside>
  );
}

function nextRoute(u: { role: string; roleChosen?: boolean }, fallback?: string): string {
  if (u.roleChosen === false && u.role !== 'admin') return '/onboarding';
  if (u.role === 'admin') return '/admin';
  if (u.role === 'seller') return '/seller';
  if (fallback && fallback !== '/login') return fallback;
  return '/buyer';
}

export default function Login() {
  useSeo({ title: 'Sign in', noIndex: true });
  const { verifyEmailCode } = useAuth();
  const { toast } = useToast();
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from;
  const reason = (location.state as { reason?: string } | null)?.reason;

  const [step, setStep] = useState<'email' | 'code'>('email');
  const [email, setEmail] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [isNew, setIsNew] = useState(false);
  const [devCode, setDevCode] = useState<string | undefined>();
  const [resendIn, setResendIn] = useState(0);
  const [digits, setDigits] = useState<string[]>(Array(CODE_LEN).fill(''));
  const boxes = useRef<Array<HTMLInputElement | null>>([]);

  // Resend cool-down ticker.
  useEffect(() => {
    if (resendIn <= 0) return;
    const t = setInterval(() => setResendIn((s) => (s > 0 ? s - 1 : 0)), 1000);
    return () => clearInterval(t);
  }, [resendIn]);

  useEffect(() => {
    if (step === 'code') setTimeout(() => boxes.current[0]?.focus(), 50);
  }, [step]);

  async function sendCode(addr: string) {
    setBusy(true);
    setError('');
    try {
      const res = await authService.otpStart(addr);
      setIsNew(res.isNew);
      setDevCode(res.devCode);
      setResendIn(res.resendInSec || 60);
      setDigits(Array(CODE_LEN).fill(''));
      setStep('code');
      if (res.sent) toast(`Code sent to ${addr}`, 'success');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not send the code. Check your connection and try again.');
    } finally {
      setBusy(false);
    }
  }

  async function onEmailSubmit(e: FormEvent) {
    e.preventDefault();
    const addr = email.trim().toLowerCase();
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(addr)) {
      setError('Enter a valid email address.');
      return;
    }
    setEmail(addr);
    await sendCode(addr);
  }

  async function submitCode(code: string) {
    if (busy) return;
    setBusy(true);
    setError('');
    try {
      const u = await verifyEmailCode(email, code);
      if (u.roleChosen === false) {
        navigate('/onboarding', { replace: true });
      } else {
        toast(`Welcome back, ${u.displayName || u.email}`, 'success');
        navigate(nextRoute(u, from), { replace: true });
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'That code did not work. Try again.');
      setDigits(Array(CODE_LEN).fill(''));
      setTimeout(() => boxes.current[0]?.focus(), 30);
    } finally {
      setBusy(false);
    }
  }

  function setDigit(i: number, raw: string) {
    const v = raw.replace(/\D/g, '');
    if (!v) {
      setDigits((d) => { const n = [...d]; n[i] = ''; return n; });
      return;
    }
    // Typing or pasting several digits fills forward from this box.
    const n = [...digits];
    let j = i;
    for (const ch of v) { if (j >= CODE_LEN) break; n[j++] = ch; }
    setDigits(n);
    setError('');
    const next = Math.min(j, CODE_LEN - 1);
    boxes.current[next]?.focus();
    if (n.every((c) => c !== '')) void submitCode(n.join(''));
  }

  function onKey(i: number, e: KeyboardEvent<HTMLInputElement>) {
    if (e.key === 'Backspace' && !digits[i] && i > 0) {
      boxes.current[i - 1]?.focus();
      setDigits((d) => { const n = [...d]; n[i - 1] = ''; return n; });
      e.preventDefault();
    } else if (e.key === 'ArrowLeft' && i > 0) boxes.current[i - 1]?.focus();
    else if (e.key === 'ArrowRight' && i < CODE_LEN - 1) boxes.current[i + 1]?.focus();
    else if (e.key === 'Enter' && digits.every((c) => c)) void submitCode(digits.join(''));
  }

  function onPaste(e: ClipboardEvent<HTMLInputElement>) {
    const text = e.clipboardData.getData('text').replace(/\D/g, '').slice(0, CODE_LEN);
    if (text) { e.preventDefault(); setDigit(0, text); }
  }

  return (
    <div className="ali-auth">
      <SidePanel />

      <div className="ali-card" data-step={step}>
          <div className="ali-card-brand show-900" aria-hidden="true"><AnimatedWordmark compact /></div>
          {step === 'email' ? (
            <>
              <div className="ali-head">
                <h2>Sign in or create account</h2>
                <p className="muted">One email, one code. No password to remember.</p>
              </div>

              {reason && (
                <div className="signin-reason" role="status">
                  <ShieldCheck size={14} /> <span>{reason}</span>
                </div>
              )}

              <form onSubmit={onEmailSubmit} className="auth-better-form" noValidate>
                <div className="auth-field">
                  <label className="auth-label" htmlFor="login-email">Email address</label>
                  <div className="auth-input-wrap">
                    <Mail size={16} className="auth-input-icon" />
                    <input
                      id="login-email"
                      className="auth-input"
                      type="email"
                      inputMode="email"
                      required
                      autoFocus
                      autoComplete="email"
                      value={email}
                      onChange={(e) => { setEmail(e.target.value); setError(''); }}
                      placeholder="you@example.com"
                    />
                  </div>
                </div>

                {error && (
                  <div className="auth-error" role="alert">
                    <AlertCircle size={14} />
                    <span>{error}</span>
                  </div>
                )}

                <Btn type="submit" variant="primary" className="btn-block auth-submit" disabled={busy || !email.trim()}>
                  {busy ? (
                    <span className="auth-btn-loading"><span className="spinner" /> Sending code…</span>
                  ) : (
                    <span className="auth-btn-content">Continue with email <ArrowRight size={16} /></span>
                  )}
                </Btn>
              </form>

              <div className="ali-alt">
                <div className="auth-divider"><span>or continue with</span></div>
                <GoogleButton divider={false} />
                <Link to="/" className="btn btn-block btn-lg guest-btn" data-testid="continue-guest">
                  <Compass size={17} /> <span>Continue as guest</span>
                </Link>
                <p className="ali-legal">
                  <Link to="/">← Back to marketplace</Link><br />
                  By continuing you agree to our <Link to="/cms/terms">Terms of Use</Link> and <Link to="/cms/privacy">Privacy Policy</Link>.
                  Guests can browse; <strong>Nearby</strong>, the <strong>AI Shopper</strong>, orders and messages need an account.
                </p>
              </div>
            </>
          ) : (
            <>
              <button type="button" className="signin-back" onClick={() => { setStep('email'); setError(''); }}>
                <ArrowLeft size={15} /> Use a different email
              </button>
              <div className="ali-head">
                <div className="signin-mail-badge" aria-hidden="true"><Mail size={22} /></div>
                <h2>Enter the code we emailed</h2>
                <p className="muted">
                  We sent a 6-digit code to <strong className="signin-email">{email}</strong>.
                  {isNew ? ' Entering it creates your account.' : ''}
                </p>
              </div>

              <div className="otp-row" role="group" aria-label="6-digit code" data-testid="otp-inputs">
                {digits.map((d, i) => (
                  <input
                    key={i}
                    ref={(el) => { boxes.current[i] = el; }}
                    className={`otp-box ${d ? 'filled' : ''}`}
                    inputMode="numeric"
                    pattern="[0-9]*"
                    autoComplete={i === 0 ? 'one-time-code' : 'off'}
                    maxLength={CODE_LEN}
                    value={d}
                    disabled={busy}
                    aria-label={`Digit ${i + 1}`}
                    onChange={(e) => setDigit(i, e.target.value)}
                    onKeyDown={(e) => onKey(i, e)}
                    onPaste={onPaste}
                    onFocus={(e) => e.currentTarget.select()}
                  />
                ))}
              </div>

              {error && (
                <div className="auth-error" role="alert">
                  <AlertCircle size={14} />
                  <span>{error}</span>
                </div>
              )}

              {devCode && (
                <div className="signin-devcode" data-testid="dev-code">
                  <strong>Local dev:</strong> no SMTP configured on this server, so your code is <code>{devCode}</code>.
                </div>
              )}

              <Btn
                type="button"
                variant="primary"
                className="btn-block auth-submit"
                disabled={busy || digits.some((c) => !c)}
                onClick={() => submitCode(digits.join(''))}
              >
                {busy ? (
                  <span className="auth-btn-loading"><span className="spinner" /> Verifying…</span>
                ) : (
                  <span className="auth-btn-content">Verify and continue <ArrowRight size={16} /></span>
                )}
              </Btn>

              <div className="signin-resend">
                <span className="muted">Didn't get it? Check spam, or</span>
                <button
                  type="button"
                  className="auth-link signin-resend-btn"
                  disabled={busy || resendIn > 0}
                  onClick={() => sendCode(email)}
                >
                  <RefreshCw size={13} /> {resendIn > 0 ? `resend in ${resendIn}s` : 'resend code'}
                </button>
              </div>
            </>
          )}
      </div>
    </div>
  );
}
