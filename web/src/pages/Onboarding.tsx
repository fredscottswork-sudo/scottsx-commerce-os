/**
 * First-run onboarding — shown once after a new account's first sign-in
 * (email code or Google). Two questions at most:
 *
 *   1. "How will you use ScottsTechX?"  buyer | seller
 *   2. (seller only) store name + logo
 *
 * Buyer → /buyer.  Seller → /seller.  The choice is saved server-side
 * (users.role_chosen) so the page never shows again for this account.
 */
import { useEffect, useRef, useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../store/AuthContext';
import { useToast } from '../store/ToastContext';
import { Btn } from '../components/ui';
import { ApiError, multipart, resolveMediaUrl } from '../api/client';
import { BrandMark } from '../components/BrandLogo';
import { useSeo } from '../hooks/useSeo';
import { ShoppingBag, Store, ArrowRight, ArrowLeft, Upload, Check, AlertCircle, ImagePlus } from 'lucide-react';

export default function Onboarding() {
  useSeo({ title: 'Set up your account', noIndex: true });
  const { user, completeOnboarding, logout } = useAuth();
  const { toast } = useToast();
  const navigate = useNavigate();

  const [role, setRole] = useState<'buyer' | 'seller' | null>(null);
  const [step, setStep] = useState<'role' | 'store'>('role');
  const [storeName, setStoreName] = useState('');
  const [logoUrl, setLogoUrl] = useState('');
  const [logoFile, setLogoFile] = useState<File | null>(null);
  const [logoPreview, setLogoPreview] = useState('');
  const [uploading, setUploading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const fileInput = useRef<HTMLInputElement>(null);

  // Local preview of the picked logo (upload happens on submit).
  useEffect(() => {
    if (!logoFile) { setLogoPreview(''); return; }
    const url = URL.createObjectURL(logoFile);
    setLogoPreview(url);
    return () => URL.revokeObjectURL(url);
  }, [logoFile]);

  const name = user?.displayName && !user.displayName.includes('@') ? user.displayName : '';

  async function finishAsBuyer() {
    setBusy(true);
    setError('');
    try {
      await completeOnboarding({ role: 'buyer' });
      toast(`Welcome${name ? `, ${name}` : ''}! Happy shopping.`, 'success');
      navigate('/buyer', { replace: true });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not save your choice. Try again.');
    } finally {
      setBusy(false);
    }
  }

  function continueFromRole() {
    if (role === 'buyer') void finishAsBuyer();
    else if (role === 'seller') setStep('store');
  }

  async function finishAsSeller(e: FormEvent) {
    e.preventDefault();
    const sn = storeName.trim();
    if (sn.length < 2) { setError('Give your store a name (at least 2 characters).'); return; }
    setBusy(true);
    setError('');
    try {
      let finalLogo: string | null = logoUrl.trim() || null;
      if (logoFile) {
        setUploading(true);
        try {
          const form = new FormData();
          form.append('image', logoFile);
          const up = (await multipart('/uploads/images', form)) as { url: string };
          finalLogo = up.url;
        } finally {
          setUploading(false);
        }
      }
      await completeOnboarding({ role: 'seller', storeName: sn, storeLogoUrl: finalLogo });
      toast(`${sn} is live. Add your first product!`, 'success');
      navigate('/seller', { replace: true });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not create your store. Try again.');
    } finally {
      setBusy(false);
    }
  }

  const initials = (storeName.trim() || 'S').split(/\s+/).map((w) => w[0]).join('').slice(0, 2).toUpperCase();

  return (
    <div className="ali-auth ali-auth--center">
      <div className="ali-card ali-card--wide onboarding-card" data-step={step}>
        <div>
          <div className="onboarding-progress" aria-hidden="true">
            <span className={`dot ${step === 'role' ? 'active' : 'done'}`} />
            <span className={`bar ${step === 'store' ? 'active' : ''}`} />
            <span className={`dot ${step === 'store' ? 'active' : ''}`} />
          </div>

          {step === 'role' ? (
            <>
              <div className="auth-card-head">
                <div className="auth-logo-wrap">
                  <BrandMark size={48} className="auth-logo-top auth-logo-top--mark" />
                </div>
                <h2>{name ? `Hi ${name.split(' ')[0]}, how` : 'How'} will you use ScottsTechX?</h2>
                <p className="muted">You can always switch or add selling later from your settings.</p>
              </div>

              <div className="role-grid" role="radiogroup" aria-label="Account type">
                <button
                  type="button"
                  role="radio"
                  aria-checked={role === 'buyer'}
                  className={`role-card ${role === 'buyer' ? 'selected' : ''}`}
                  onClick={() => setRole('buyer')}
                  data-testid="role-buyer"
                >
                  <span className="role-icon role-icon--buyer"><ShoppingBag size={22} /></span>
                  <span className="role-title">I'm here to shop</span>
                  <span className="role-desc">Browse verified stores, find deals nearby, chat with sellers and let the AI Shopper compare for you.</span>
                  <span className="role-check"><Check size={14} /></span>
                </button>
                <button
                  type="button"
                  role="radio"
                  aria-checked={role === 'seller'}
                  className={`role-card ${role === 'seller' ? 'selected' : ''}`}
                  onClick={() => setRole('seller')}
                  data-testid="role-seller"
                >
                  <span className="role-icon role-icon--seller"><Store size={22} /></span>
                  <span className="role-title">I want to sell</span>
                  <span className="role-desc">Open a storefront in a minute — list products, take orders and reach buyers across Uganda.</span>
                  <span className="role-check"><Check size={14} /></span>
                </button>
              </div>

              {error && (
                <div className="auth-error" role="alert"><AlertCircle size={14} /><span>{error}</span></div>
              )}

              <Btn
                type="button"
                variant="primary"
                className="btn-block auth-submit"
                disabled={!role || busy}
                onClick={continueFromRole}
                data-testid="onboarding-continue"
              >
                {busy ? (
                  <span className="auth-btn-loading"><span className="spinner" /> Setting up…</span>
                ) : (
                  <span className="auth-btn-content">
                    {role === 'seller' ? 'Set up my store' : 'Continue'} <ArrowRight size={16} />
                  </span>
                )}
              </Btn>

              <p className="auth-foot-text" style={{ marginTop: 14 }}>
                Signed in as <strong>{user?.email}</strong> ·{' '}
                <button type="button" className="auth-link linklike" onClick={() => { logout(); navigate('/login', { replace: true }); }}>
                  Not you?
                </button>
              </p>
            </>
          ) : (
            <>
              <button type="button" className="signin-back" onClick={() => { setStep('role'); setError(''); }}>
                <ArrowLeft size={15} /> Back
              </button>
              <div className="auth-card-head">
                <h2>Name your store</h2>
                <p className="muted">This is what buyers see on your storefront and on every product you list.</p>
              </div>

              <form onSubmit={finishAsSeller} className="auth-better-form" noValidate>
                <div className="store-setup">
                  <button
                    type="button"
                    className={`logo-drop ${logoPreview || logoUrl ? 'has-logo' : ''}`}
                    onClick={() => fileInput.current?.click()}
                    aria-label="Upload store logo"
                    data-testid="logo-drop"
                  >
                    {logoPreview || logoUrl ? (
                      <img src={logoPreview || resolveMediaUrl(logoUrl)} alt="" />
                    ) : (
                      <span className="logo-initials">{initials}</span>
                    )}
                    <span className="logo-drop-badge"><ImagePlus size={13} /></span>
                  </button>
                  <div className="store-setup-fields">
                    <div className="auth-field">
                      <label className="auth-label" htmlFor="ob-store">Store name</label>
                      <div className="auth-input-wrap">
                        <Store size={16} className="auth-input-icon" />
                        <input
                          id="ob-store"
                          className="auth-input"
                          autoFocus
                          maxLength={80}
                          value={storeName}
                          onChange={(e) => { setStoreName(e.target.value); setError(''); }}
                          placeholder="e.g. Kampala Gadget Hub"
                        />
                      </div>
                    </div>
                    <div className="logo-actions">
                      <button type="button" className="btn btn-sm" onClick={() => fileInput.current?.click()}>
                        <Upload size={14} /> {logoFile ? 'Change logo' : 'Upload logo'}
                      </button>
                      {(logoFile || logoUrl) && (
                        <button type="button" className="btn btn-sm btn-ghost" onClick={() => { setLogoFile(null); setLogoUrl(''); }}>
                          Remove
                        </button>
                      )}
                      <span className="muted logo-hint">PNG / JPG, square, up to 3 MB. Optional.</span>
                    </div>
                    <input
                      ref={fileInput}
                      type="file"
                      accept="image/png,image/jpeg,image/webp"
                      hidden
                      onChange={(e) => {
                        const f = e.target.files?.[0] ?? null;
                        if (f && f.size > 3 * 1024 * 1024) { setError('Logo must be under 3 MB.'); return; }
                        setLogoFile(f);
                        setError('');
                      }}
                    />
                  </div>
                </div>

                <details className="logo-url-details">
                  <summary>…or paste a logo URL</summary>
                  <input
                    className="auth-input auth-input--plain"
                    type="url"
                    value={logoUrl}
                    onChange={(e) => { setLogoUrl(e.target.value); setLogoFile(null); }}
                    placeholder="https://…/logo.png"
                  />
                </details>

                {error && (
                  <div className="auth-error" role="alert"><AlertCircle size={14} /><span>{error}</span></div>
                )}

                <Btn type="submit" variant="primary" className="btn-block auth-submit" disabled={busy || storeName.trim().length < 2} data-testid="create-store">
                  {busy ? (
                    <span className="auth-btn-loading"><span className="spinner" /> {uploading ? 'Uploading logo…' : 'Creating your store…'}</span>
                  ) : (
                    <span className="auth-btn-content">Open my store <ArrowRight size={16} /></span>
                  )}
                </Btn>
                <p className="auth-foot-text" style={{ marginTop: 10 }}>
                  You can edit the name, logo, description and location any time in Store settings.
                </p>
              </form>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
