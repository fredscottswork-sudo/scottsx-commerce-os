import { useEffect, useState, type FormEvent } from 'react';
import { Camera, Link as LinkIcon, Upload, X } from 'lucide-react';
import { authService, buyerService } from '../../api/services';
import { useAuth } from '../../store/AuthContext';
import { useTheme } from '../../store/ThemeContext';
import { useToast } from '../../store/ToastContext';
import { Btn, Card, Field, Input, Loading, PageHeader, Toggle } from '../../components/ui';

export default function Settings() {
  const { user, setUser, refresh } = useAuth();
  const { mode, setMode } = useTheme();
  const { toast } = useToast();
  const [displayName, setDisplayName] = useState(user?.displayName ?? '');
  const [phone, setPhone] = useState(user?.phone ?? '');
  const [photoUrl, setPhotoUrl] = useState(user?.profilePhotoUrl ?? '');
  const [prefs, setPrefs] = useState({ notifyOrderUpdates: true, notifyMessages: true, notifyMarketing: false });
  const [oldPw, setOldPw] = useState('');
  const [newPw, setNewPw] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    buyerService.preferences().then((r) => setPrefs(r.preferences)).catch(() => undefined);
  }, []);

  async function saveProfile(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    try {
      await authService.updateMe({ displayName, phone });
      if (photoUrl !== user?.profilePhotoUrl) await authService.updateMe({ displayName, phone }).catch(() => undefined);
      await refresh();
      toast('Profile saved', 'success');
    } catch (err: any) {
      toast(err.message, 'error');
    } finally {
      setBusy(false);
    }
  }

  async function uploadPhoto(file: File | null) {
    if (!file) return;
    try {
      const r = await authService.uploadPhoto(file);
      setUser({ ...user!, profilePhotoUrl: r.profilePhotoUrl });
      toast('Photo updated', 'success');
    } catch (e: any) {
      toast(e.message || 'Upload failed — enable Firebase Storage or use a URL', 'error');
    }
  }

  async function savePrefs() {
    await buyerService.savePreferences(prefs);
    toast('Preferences saved', 'success');
  }

  async function changePassword(e: FormEvent) {
    e.preventDefault();
    if (newPw.length < 6) return toast('Password must be at least 6 characters', 'warning');
    try {
      await buyerService.changePassword(oldPw, newPw);
      toast('Password updated', 'success');
      setOldPw(''); setNewPw('');
    } catch (err: any) {
      toast(err.message, 'error');
    }
  }

  if (!user) return <Loading />;

  return (
    <>
      <PageHeader title="Settings" sub="Account, photo, theme and notifications — all synced to the shared backend." />
      <div className="grid grid-2">
        <Card>
          <h3 style={{ marginTop: 0 }} className="mb-8">Profile</h3>
          <div className="row mb-16">
            <span className="avatar" style={{ width: 64, height: 64, fontSize: 24 }}>
              {user.profilePhotoUrl ? <img src={user.profilePhotoUrl} alt="" /> : (user.displayName || 'U')[0].toUpperCase()}
            </span>
            <div className="row wrap">
              <label className="btn btn-sm" style={{ cursor: 'pointer' }}>
                <Camera size={15} /> Upload photo
                <input type="file" accept="image/*" hidden onChange={(e) => uploadPhoto(e.target.files?.[0] ?? null)} />
              </label>
              <span className="muted" style={{ fontSize: 12.5 }}>Stored in Firebase Storage</span>
            </div>
          </div>
          <form onSubmit={saveProfile}>
            <Field label="Display name"><Input value={displayName} onChange={(e) => setDisplayName(e.target.value)} /></Field>
            <Field label="Phone"><Input value={phone} onChange={(e) => setPhone(e.target.value)} placeholder="+256 7xx xxx xxx" /></Field>
            <Field label="Email (cannot change)" hint="Sign-in identity shared with the mobile app.">
              <Input value={user.email} disabled />
            </Field>
            <Field label="Photo URL (alternative to upload)">
              <div className="row">
                <Input value={photoUrl} onChange={(e) => setPhotoUrl(e.target.value)} placeholder="https://…" />
                <Btn type="button" size="sm" onClick={() => setPhotoUrl('')} aria-label="Clear"><X size={15} /></Btn>
              </div>
            </Field>
            <Btn type="submit" variant="primary" disabled={busy}>{busy ? 'Saving…' : 'Save profile'}</Btn>
          </form>
        </Card>

        <div className="grid" style={{ gap: 16 }}>
          <Card>
            <h3 style={{ marginTop: 0 }} className="mb-8">Theme</h3>
            <div className="row wrap">
              {(['light', 'dark', 'system'] as const).map((m) => (
                <button key={m} className={`chip ${mode === m ? 'active' : ''}`} onClick={() => setMode(m)} style={{ textTransform: 'capitalize' }}>{m}</button>
              ))}
            </div>
            <p className="muted" style={{ fontSize: 13 }}>Persisted to the backend — the mobile app reads the same preference.</p>
          </Card>

          <Card>
            <h3 style={{ marginTop: 0 }} className="mb-8">Notifications</h3>
            <div className="grid" style={{ gap: 10 }}>
              <Toggle checked={prefs.notifyOrderUpdates} onChange={(v) => setPrefs({ ...prefs, notifyOrderUpdates: v })} label="Order updates" />
              <Toggle checked={prefs.notifyMessages} onChange={(v) => setPrefs({ ...prefs, notifyMessages: v })} label="Messages" />
              <Toggle checked={prefs.notifyMarketing} onChange={(v) => setPrefs({ ...prefs, notifyMarketing: v })} label="Marketing" />
            </div>
            <Btn className="mt-16" onClick={savePrefs}>Save preferences</Btn>
          </Card>

          <Card>
            <h3 style={{ marginTop: 0 }} className="mb-8">Change password</h3>
            <form onSubmit={changePassword}>
              <Field label="Current password"><Input type="password" value={oldPw} onChange={(e) => setOldPw(e.target.value)} /></Field>
              <Field label="New password"><Input type="password" value={newPw} onChange={(e) => setNewPw(e.target.value)} placeholder="min 6 characters" /></Field>
              <Btn type="submit" variant="outline" disabled={!oldPw || !newPw}>Update password</Btn>
            </form>
          </Card>
        </div>
      </div>
    </>
  );
}
