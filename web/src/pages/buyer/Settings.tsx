/**
 * Account settings — the buyer/seller control centre.
 *
 * Tabbed rather than one long scroll: Profile, Appearance, Notifications,
 * Privacy & data, Security. Every panel writes to the shared backend so the
 * mobile app reflects the same state immediately.
 */
import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react';
import {
  BadgeCheck, Bell, Camera, Check, Eye, EyeOff, KeyRound, Loader2, LogOut,
  Monitor, Moon, Palette, Search, Shield, Smartphone, Sun, Trash2, User as UserIcon, X,
} from 'lucide-react';
import { authService, buyerService, socialService } from '../../api/services';
import type { UserSettings } from '../../api/types';
import { useAuth } from '../../store/AuthContext';
import { useTheme } from '../../store/ThemeContext';
import { useToast } from '../../store/ToastContext';
import {
  Badge, Btn, Card, ConfirmModal, Field, Input, Loading, PageHeader, Switch, Tabs,
} from '../../components/ui';

type TabKey = 'profile' | 'appearance' | 'notifications' | 'privacy' | 'security';

const TABS: { id: TabKey; label: string }[] = [
  { id: 'profile', label: 'Profile' },
  { id: 'appearance', label: 'Appearance' },
  { id: 'notifications', label: 'Notifications' },
  { id: 'privacy', label: 'Privacy & data' },
  { id: 'security', label: 'Security' },
];

/** Password strength 0–4 with a human label. */
function strengthOf(pw: string): { score: number; label: string; tone: string } {
  let score = 0;
  if (pw.length >= 8) score++;
  if (pw.length >= 12) score++;
  if (/[A-Z]/.test(pw) && /[a-z]/.test(pw)) score++;
  if (/\d/.test(pw) && /[^A-Za-z0-9]/.test(pw)) score++;
  const labels = ['Too short', 'Weak', 'Fair', 'Good', 'Strong'];
  const tones = ['danger', 'danger', 'warning', 'primary', 'success'];
  return { score, label: labels[score], tone: tones[score] };
}

export default function Settings() {
  const { user, setUser, refresh, logout } = useAuth();
  const { mode, setMode, resolved } = useTheme();
  const { toast } = useToast();

  const [tab, setTab] = useState<TabKey>('profile');

  // -- profile ---------------------------------------------------------------
  const [displayName, setDisplayName] = useState(user?.displayName ?? '');
  const [phone, setPhone] = useState(user?.phone ?? '');
  const [city, setCity] = useState(user?.city ?? '');
  const [photoUrl, setPhotoUrl] = useState(user?.profilePhotoUrl ?? '');
  const [savingProfile, setSavingProfile] = useState(false);
  const fileRef = useRef<HTMLInputElement>(null);

  // -- preferences -----------------------------------------------------------
  const [prefs, setPrefs] = useState<UserSettings>({
    theme: 'system', language: 'en', currency: 'UGX',
    notifyOrderUpdates: true, notifyMessages: true, notifyMarketing: false,
  });
  const [prefsLoaded, setPrefsLoaded] = useState(false);
  const [savingPrefs, setSavingPrefs] = useState(false);

  // -- security --------------------------------------------------------------
  const [oldPw, setOldPw] = useState('');
  const [newPw, setNewPw] = useState('');
  const [confirmPw, setConfirmPw] = useState('');
  const [showPw, setShowPw] = useState(false);
  const [changingPw, setChangingPw] = useState(false);

  // -- privacy ---------------------------------------------------------------
  const [history, setHistory] = useState<{ id: string; query: string; createdAt: string }[]>([]);
  const [confirmClear, setConfirmClear] = useState(false);
  const [confirmLogout, setConfirmLogout] = useState(false);

  useEffect(() => {
    buyerService.preferences()
      .then((r) => { setPrefs(r.preferences); setPrefsLoaded(true); })
      .catch(() => setPrefsLoaded(true));
    socialService.searchHistory()
      .then((r) => setHistory(r.history))
      .catch(() => undefined);
  }, []);

  // Keep the local form in sync if the user object is refreshed elsewhere.
  useEffect(() => {
    if (!user) return;
    setDisplayName(user.displayName ?? '');
    setPhone(user.phone ?? '');
    setCity(user.city ?? '');
    setPhotoUrl(user.profilePhotoUrl ?? '');
  }, [user?.id]);

  const dirty = useMemo(() => (
    displayName !== (user?.displayName ?? '') ||
    phone !== (user?.phone ?? '') ||
    city !== (user?.city ?? '') ||
    photoUrl !== (user?.profilePhotoUrl ?? '')
  ), [displayName, phone, city, photoUrl, user]);

  async function saveProfile(e: FormEvent) {
    e.preventDefault();
    if (!displayName.trim()) { toast('Display name cannot be empty', 'warning'); return; }
    setSavingProfile(true);
    try {
      // One request carries every field the backend accepts.
      await authService.updateMe({
        displayName: displayName.trim(),
        phone: phone.trim(),
        city: city.trim(),
        profilePhotoUrl: photoUrl.trim() || null,
      });
      await refresh();
      toast('Profile saved', 'success');
    } catch (err: any) {
      toast(err.message, 'error');
    } finally {
      setSavingProfile(false);
    }
  }

  async function uploadPhoto(file: File | null) {
    if (!file) return;
    if (file.size > 3 * 1024 * 1024) { toast('Image must be under 3 MB', 'warning'); return; }
    try {
      const r = await authService.uploadPhoto(file);
      setPhotoUrl(r.profilePhotoUrl);
      setUser({ ...user!, profilePhotoUrl: r.profilePhotoUrl });
      toast('Photo updated', 'success');
    } catch (e: any) {
      toast(e.message || 'Upload failed — paste an image URL instead', 'error');
    }
  }

  async function savePref<K extends keyof UserSettings>(key: K, value: UserSettings[K]) {
    const previous = prefs;
    setPrefs({ ...prefs, [key]: value });   // optimistic
    setSavingPrefs(true);
    try {
      const r = await buyerService.savePreferences({ [key]: value } as Partial<UserSettings>);
      setPrefs(r.preferences);
    } catch (e: any) {
      setPrefs(previous);                    // roll back
      toast(e.message, 'error');
    } finally {
      setSavingPrefs(false);
    }
  }

  async function changePassword(e: FormEvent) {
    e.preventDefault();
    if (newPw.length < 6) { toast('Password must be at least 6 characters', 'warning'); return; }
    if (newPw !== confirmPw) { toast('New passwords do not match', 'warning'); return; }
    if (newPw === oldPw) { toast('Choose a password you have not used here before', 'warning'); return; }
    setChangingPw(true);
    try {
      await buyerService.changePassword(oldPw, newPw);
      toast('Password updated', 'success');
      setOldPw(''); setNewPw(''); setConfirmPw('');
    } catch (err: any) {
      toast(err.message, 'error');
    } finally {
      setChangingPw(false);
    }
  }

  async function clearHistory() {
    try {
      await socialService.clearSearchHistory();
      setHistory([]);
      setConfirmClear(false);
      toast('Search history cleared', 'success');
    } catch (e: any) { toast(e.message, 'error'); }
  }

  if (!user) return <Loading />;

  const pwStrength = strengthOf(newPw);
  const initials = (user.displayName || user.email || 'U').charAt(0).toUpperCase();

  return (
    <>
      <PageHeader
        title="Account settings"
        sub="Everything here syncs instantly with the ScottsTechX mobile app."
      />

      {/* -------------------------------------------------- identity summary */}
      <Card className="settings-hero mb-16">
        <span className="avatar avatar-lg">
          {photoUrl ? <img src={photoUrl} alt="" /> : initials}
        </span>
        <div className="grow" style={{ minWidth: 0 }}>
          <div className="row" style={{ gap: 8 }}>
            <strong style={{ fontSize: 'var(--fs-lg)' }}>{user.displayName || 'Unnamed account'}</strong>
            <Badge tone={user.role === 'admin' ? 'violet' : user.role === 'seller' ? 'cyan' : 'primary'}>
              {user.role}
            </Badge>
            {user.emailVerified && (
              <Badge tone="green"><BadgeCheck size={11} /> verified</Badge>
            )}
          </div>
          <span className="muted ellipsis" style={{ display: 'block' }}>{user.email}</span>
        </div>
      </Card>

      <Tabs tabs={TABS} active={tab} onChange={(t) => setTab(t as TabKey)} />

      {/* ----------------------------------------------------------- profile */}
      {tab === 'profile' && (
        <div className="grid grid-2 mt-16 stagger">
          <Card title="Personal details" className="stagger-item">
            <form onSubmit={saveProfile}>
              <Field label="Display name" required>
                <Input value={displayName} onChange={(e) => setDisplayName(e.target.value)} placeholder="How sellers see you" />
              </Field>
              <Field label="Phone" hint="Used for delivery coordination and order updates.">
                <Input value={phone} onChange={(e) => setPhone(e.target.value)} placeholder="+256 7xx xxx xxx" />
              </Field>
              <Field label="City / town" hint="Helps us sort nearby stores by distance.">
                <Input value={city} onChange={(e) => setCity(e.target.value)} placeholder="Kampala" />
              </Field>
              <Field label="Email" hint="Your sign-in identity — shared with the mobile app.">
                <Input value={user.email} disabled />
              </Field>
              <Btn type="submit" variant="primary" loading={savingProfile} disabled={!dirty}>
                {dirty ? 'Save changes' : 'Saved'}
              </Btn>
            </form>
          </Card>

          <Card title="Profile photo" className="stagger-item" style={{ '--i': 1 } as React.CSSProperties}>
            <div className="row mb-16" style={{ gap: 14 }}>
              <span className="avatar avatar-lg">
                {photoUrl ? <img src={photoUrl} alt="" /> : initials}
              </span>
              <div className="col" style={{ gap: 8 }}>
                <Btn type="button" size="sm" icon={<Camera size={15} />} onClick={() => fileRef.current?.click()}>
                  Upload photo
                </Btn>
                <input
                  ref={fileRef} type="file" accept="image/*" hidden
                  aria-label="Upload a profile photo"
                  onChange={(e) => uploadPhoto(e.target.files?.[0] ?? null)}
                />
                {photoUrl && (
                  <Btn type="button" size="sm" icon={<X size={14} />} onClick={() => setPhotoUrl('')}>
                    Remove
                  </Btn>
                )}
              </div>
            </div>
            <Field label="…or paste an image URL" hint="Useful when uploads are unavailable. Max 3 MB for uploads.">
              <Input value={photoUrl} onChange={(e) => setPhotoUrl(e.target.value)} placeholder="https://…" />
            </Field>
            <p className="muted" style={{ fontSize: 'var(--fs-xs)' }}>
              Remember to press <strong>Save changes</strong> on the left to apply a pasted URL.
            </p>
          </Card>
        </div>
      )}

      {/* -------------------------------------------------------- appearance */}
      {tab === 'appearance' && (
        <div className="grid grid-2 mt-16 stagger">
          <Card title="Theme" className="stagger-item">
            <p className="muted mb-16">
              ScottsTechX is dark blue and black by default. Switching to light swaps black for white
              across both web and the mobile app.
            </p>
            <div className="theme-picker">
              {([
                { key: 'dark', label: 'Dark', icon: <Moon size={17} /> },
                { key: 'light', label: 'Light', icon: <Sun size={17} /> },
                { key: 'system', label: 'System', icon: <Monitor size={17} /> },
              ] as const).map((opt) => (
                <button
                  key={opt.key}
                  className={`theme-option${mode === opt.key ? ' active' : ''}`}
                  onClick={() => { setMode(opt.key); savePref('theme', opt.key); }}
                  aria-pressed={mode === opt.key}
                >
                  <span className={`theme-swatch theme-swatch-${opt.key}`} />
                  <span className="row" style={{ gap: 6 }}>{opt.icon}{opt.label}</span>
                  {mode === opt.key && <Check size={15} className="theme-check" />}
                </button>
              ))}
            </div>
            <p className="muted mt-16" style={{ fontSize: 'var(--fs-xs)' }}>
              <Palette size={12} style={{ verticalAlign: -2 }} /> Currently showing the{' '}
              <strong>{resolved}</strong> palette.
            </p>
          </Card>

          <Card title="Regional" className="stagger-item" style={{ '--i': 1 } as React.CSSProperties}>
            <Field label="Language">
              <select
                className="input"
                value={prefs.language}
                onChange={(e) => savePref('language', e.target.value)}
              >
                <option value="en">English</option>
                <option value="sw">Kiswahili</option>
                <option value="lg">Luganda</option>
              </select>
            </Field>
            <Field label="Currency" hint="Prices are settled in Ugandan shillings.">
              <select
                className="input"
                value={prefs.currency}
                onChange={(e) => savePref('currency', e.target.value)}
              >
                <option value="UGX">UGX — Ugandan shilling</option>
                <option value="USD">USD — US dollar</option>
                <option value="KES">KES — Kenyan shilling</option>
              </select>
            </Field>
            {savingPrefs && (
              <span className="muted row" style={{ gap: 6, fontSize: 'var(--fs-xs)' }}>
                <Loader2 size={12} className="anim-spin" /> Saving…
              </span>
            )}
          </Card>
        </div>
      )}

      {/* ----------------------------------------------------- notifications */}
      {tab === 'notifications' && (
        <div className="grid grid-2 mt-16 stagger">
          <Card title="Push & in-app alerts" className="stagger-item">
            {!prefsLoaded ? <Loading /> : (
              <div className="settings-toggles">
                <div className="settings-toggle-row">
                  <div>
                    <strong>Order updates</strong>
                    <span className="muted">Order confirmations, dispatch and delivery.</span>
                  </div>
                  <Switch
                    checked={prefs.notifyOrderUpdates}
                    onChange={(v) => savePref('notifyOrderUpdates', v)}
                    label="Order updates"
                  />
                </div>
                <div className="settings-toggle-row">
                  <div>
                    <strong>Messages</strong>
                    <span className="muted">New chats and offers from buyers or sellers.</span>
                  </div>
                  <Switch
                    checked={prefs.notifyMessages}
                    onChange={(v) => savePref('notifyMessages', v)}
                    label="Messages"
                  />
                </div>
                <div className="settings-toggle-row">
                  <div>
                    <strong>New products from stores you follow</strong>
                    <span className="muted">Alerts when a favourite seller lists something new.</span>
                  </div>
                  <Switch
                    checked={prefs.notifyMarketing}
                    onChange={(v) => savePref('notifyMarketing', v)}
                    label="Marketing"
                  />
                </div>
              </div>
            )}
            <p className="muted mt-16" style={{ fontSize: 'var(--fs-xs)' }}>
              <Bell size={12} style={{ verticalAlign: -2 }} /> Changes apply immediately — no save button needed.
            </p>
          </Card>

          <Card title="Devices" className="stagger-item" style={{ '--i': 1 } as React.CSSProperties}>
            <p className="muted">
              Phone push notifications are delivered to every device where you are signed in to the
              ScottsTechX app.
            </p>
            <div className="device-row">
              <Smartphone size={17} />
              <div className="grow">
                <strong>This browser</strong>
                <span className="muted" style={{ display: 'block' }}>
                  Receiving in-app alerts in the notification centre.
                </span>
              </div>
              <Badge tone="green" live>active</Badge>
            </div>
            <p className="muted mt-16" style={{ fontSize: 'var(--fs-xs)' }}>
              Install the Android app and sign in with <strong>{user.email}</strong> to receive alerts
              on your phone.
            </p>
          </Card>
        </div>
      )}

      {/* -------------------------------------------------------- privacy */}
      {tab === 'privacy' && (
        <div className="grid grid-2 mt-16 stagger">
          <Card title="Search history" className="stagger-item">
            {history.length === 0 ? (
              <p className="muted">No searches recorded yet. Your recent searches will appear here.</p>
            ) : (
              <>
                <div className="chip-row" style={{ flexWrap: 'wrap' }}>
                  {history.slice(0, 12).map((h) => (
                    <span key={h.id} className="chip">
                      <Search size={12} /> {h.query}
                    </span>
                  ))}
                </div>
                <Btn
                  className="mt-16" icon={<Trash2 size={15} />}
                  onClick={() => setConfirmClear(true)}
                >
                  Clear {history.length} search{history.length === 1 ? '' : 'es'}
                </Btn>
              </>
            )}
          </Card>

          <Card title="Your data" className="stagger-item" style={{ '--i': 1 } as React.CSSProperties}>
            <div className="settings-toggles">
              <div className="settings-toggle-row">
                <div>
                  <strong>Account created</strong>
                  <span className="muted">
                    {user.createdAt ? new Date(user.createdAt).toLocaleDateString([], {
                      day: 'numeric', month: 'long', year: 'numeric',
                    }) : '—'}
                  </span>
                </div>
                <UserIcon size={17} className="muted" />
              </div>
              <div className="settings-toggle-row">
                <div>
                  <strong>Role</strong>
                  <span className="muted">
                    {user.role === 'seller'
                      ? 'You can list products for sale.'
                      : user.role === 'admin'
                        ? 'Full platform moderation access.'
                        : 'Buyer account — upgrade any time to start selling.'}
                  </span>
                </div>
                <Badge tone="primary">{user.role}</Badge>
              </div>
            </div>
            <p className="muted mt-16" style={{ fontSize: 'var(--fs-xs)' }}>
              <Shield size={12} style={{ verticalAlign: -2 }} /> We never sell your data. Contact support
              to request a full export or account deletion.
            </p>
          </Card>
        </div>
      )}

      {/* -------------------------------------------------------- security */}
      {tab === 'security' && (
        <div className="grid grid-2 mt-16 stagger">
          <Card title="Change password" className="stagger-item">
            <form onSubmit={changePassword}>
              <Field label="Current password">
                <div className="pw-field">
                  <Input
                    type={showPw ? 'text' : 'password'}
                    value={oldPw}
                    onChange={(e) => setOldPw(e.target.value)}
                    autoComplete="current-password"
                  />
                  <button
                    type="button" className="pw-toggle"
                    onClick={() => setShowPw((v) => !v)}
                    aria-label={showPw ? 'Hide passwords' : 'Show passwords'}
                  >
                    {showPw ? <EyeOff size={15} /> : <Eye size={15} />}
                  </button>
                </div>
              </Field>
              <Field label="New password" hint="At least 6 characters. Longer is stronger.">
                <Input
                  type={showPw ? 'text' : 'password'}
                  value={newPw}
                  onChange={(e) => setNewPw(e.target.value)}
                  autoComplete="new-password"
                />
              </Field>
              {newPw && (
                <div className="pw-strength">
                  <div className="pw-bar">
                    <span
                      className={`pw-bar-fill tone-${pwStrength.tone}`}
                      style={{ width: `${(pwStrength.score / 4) * 100}%` }}
                    />
                  </div>
                  <span className="muted" style={{ fontSize: 'var(--fs-xs)' }}>{pwStrength.label}</span>
                </div>
              )}
              <Field label="Confirm new password"
                error={confirmPw && confirmPw !== newPw ? 'Passwords do not match' : undefined}>
                <Input
                  type={showPw ? 'text' : 'password'}
                  value={confirmPw}
                  onChange={(e) => setConfirmPw(e.target.value)}
                  autoComplete="new-password"
                />
              </Field>
              <Btn
                type="submit" variant="primary" icon={<KeyRound size={15} />}
                loading={changingPw}
                disabled={!oldPw || !newPw || newPw !== confirmPw}
              >
                Update password
              </Btn>
            </form>
          </Card>

          <Card title="Session" className="stagger-item" style={{ '--i': 1 } as React.CSSProperties}>
            <p className="muted">
              You are signed in on this browser. Signing out clears the session token stored on this
              device only — your phone stays signed in.
            </p>
            <Btn
              className="mt-16" icon={<LogOut size={15} />}
              onClick={() => setConfirmLogout(true)}
            >
              Sign out
            </Btn>
          </Card>
        </div>
      )}

      <ConfirmModal
        open={confirmClear}
        title="Clear search history?"
        message="Your recent searches will be permanently removed. This does not affect your orders or saved items."
        confirmLabel="Clear history"
        danger
        onCancel={() => setConfirmClear(false)}
        onConfirm={clearHistory}
      />

      <ConfirmModal
        open={confirmLogout}
        title="Sign out?"
        message="You will need to sign in again on this browser to access your account."
        confirmLabel="Sign out"
        onCancel={() => setConfirmLogout(false)}
        onConfirm={() => { setConfirmLogout(false); logout(); }}
      />
    </>
  );
}
