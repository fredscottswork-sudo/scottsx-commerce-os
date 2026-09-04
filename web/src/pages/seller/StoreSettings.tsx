import { useEffect, useState, type FormEvent } from 'react';
import { sellerService } from '../../api/services';
import type { StoreSettings } from '../../api/types';
import { useToast } from '../../store/ToastContext';
import { Btn, Card, ErrorBox, Field, Input, Loading, PageHeader, Select, TextArea, Toggle } from '../../components/ui';

const BLANK: StoreSettings = {
  storeName: '', storeDescription: '', storeLogoUrl: '', legalName: '', tin: '', businessEmail: '', businessPhone: '',
  address: '', pickupInstructions: '', serviceRadiusKm: 20, deliveryFeeUgx: 0, freeAboveUgx: 0, codEnabled: true,
  notifOrderUpdates: true, notifBuyerMessages: true, notifMarketing: false,
  notifWeeklyDigest: true, twoFactorEnabled: false, returnsWindowDays: 7, refundPolicy: '', terms: '',
  contactEmail: '', contactPhone: '', city: '', verified: false, rating: 0,
};

const SECTIONS: Array<{ key: string; label: string; fields: Array<keyof StoreSettings> }> = [
  { key: 'store-profile', label: 'Store profile', fields: ['storeName', 'storeDescription', 'storeLogoUrl', 'city'] },
  { key: 'business-info', label: 'Business info', fields: ['legalName', 'tin', 'businessEmail', 'businessPhone'] },
  { key: 'store-location', label: 'Store location', fields: ['address', 'pickupInstructions', 'serviceRadiusKm'] },
  { key: 'delivery', label: 'Delivery', fields: ['deliveryFeeUgx', 'freeAboveUgx'] },
  { key: 'policies', label: 'Policies', fields: ['returnsWindowDays', 'refundPolicy', 'terms'] },
  { key: 'help', label: 'Help & contact', fields: ['contactEmail', 'contactPhone'] },
];

export default function StoreSettingsPage() {
  const { toast } = useToast();
  const [settings, setSettings] = useState<StoreSettings>(BLANK);
  const [section, setSection] = useState('store-profile');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    sellerService.storeSettings().then((r) => setSettings(r.settings)).catch((e: any) => setError(e.message)).finally(() => setLoading(false));
  }, []);

  async function save(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    try {
      await sellerService.saveStoreSettings(settings);
      toast('Store settings saved', 'success');
    } catch (err: any) {
      toast(err.message, 'error');
    } finally {
      setBusy(false);
    }
  }

  if (loading) return <Loading />;
  if (error) return <ErrorBox message={error} onRetry={() => window.location.reload()} />;

  const active = SECTIONS.find((s) => s.key === section) ?? SECTIONS[0];
  const num = (k: keyof StoreSettings) => String(settings[k] as number ?? '');
  const setNum = (k: keyof StoreSettings) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setSettings({ ...settings, [k]: Number(e.target.value) || 0 });

  return (
    <>
      <PageHeader title="Store settings" sub="The 8 settings sections — one PATCH endpoint, same as the mobile app." />
      <div className="grid settings-split">
        <div className="card" style={{ padding: 8, alignSelf: 'start' }}>
          {SECTIONS.map((s) => (
            <button key={s.key} className={`nav-link ${section === s.key ? 'active' : ''}`} style={{ width: '100%' }} onClick={() => setSection(s.key)}>
              {s.label}
            </button>
          ))}
        </div>

        <Card>
          <form onSubmit={save}>
            <h3 style={{ marginTop: 0 }} className="mb-16">{active.label}</h3>
            {active.fields.map((k) => {
              const val = settings[k];
              if (typeof val === 'boolean') {
                return <Toggle key={k} checked={val} onChange={(v) => setSettings({ ...settings, [k]: v })} label={human(k)} />;
              }
              if (typeof val === 'number') {
                return (
                  <div className="form-row" key={k}>
                    <Field label={human(k)}><Input type="number" value={num(k)} onChange={setNum(k)} /></Field>
                  </div>
                );
              }
              const isLong = k === 'storeDescription' || k === 'refundPolicy' || k === 'terms';
              return (
                <Field key={k} label={human(k)}>
                  {isLong
                    ? <TextArea rows={3} value={val} onChange={(e) => setSettings({ ...settings, [k]: e.target.value })} />
                    : <Input value={val} onChange={(e) => setSettings({ ...settings, [k]: e.target.value })} />}
                </Field>
              );
            })}
            <div className="mt-16">
              <Btn type="submit" variant="primary" disabled={busy}>{busy ? 'Saving…' : 'Save changes'}</Btn>
            </div>
          </form>
        </Card>
      </div>
    </>
  );
}

function human(k: string): string {
  return k.replace(/([A-Z])/g, ' $1').replace(/^./, (c) => c.toUpperCase());
}
