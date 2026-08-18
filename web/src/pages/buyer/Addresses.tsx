import { useEffect, useState, type FormEvent } from 'react';
import { Inbox, MapPin, Plus, Trash2 } from 'lucide-react';
import { buyerService } from '../../api/services';
import type { Address } from '../../api/types';
import { useToast } from '../../store/ToastContext';
import { Btn, Card, Empty, ErrorBox, Field, Input, Loading, Modal, PageHeader } from '../../components/ui';

export default function Addresses() {
  const { toast } = useToast();
  const [items, setItems] = useState<Address[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState({ label: 'Home', line1: '', city: 'Kampala', country: 'Uganda', isDefault: false });
  const [busy, setBusy] = useState(false);

  async function load() {
    setLoading(true);
    buyerService.addresses().then((r) => setItems(r.addresses)).catch((e: any) => setError(e.message)).finally(() => setLoading(false));
  }
  useEffect(() => { load(); }, []);

  async function save(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    try {
      await buyerService.createAddress(form);
      toast('Address added', 'success');
      setOpen(false);
      setForm({ label: 'Home', line1: '', city: 'Kampala', country: 'Uganda', isDefault: false });
      load();
    } catch (err: any) {
      toast(err.message, 'error');
    } finally {
      setBusy(false);
    }
  }

  async function del(id: string) {
    await buyerService.deleteAddress(id);
    toast('Address removed', 'info');
    load();
  }

  return (
    <>
      <PageHeader title="Addresses" sub="Saved addresses for faster checkout — shared with the mobile app."
        actions={<Btn variant="primary" onClick={() => setOpen(true)}><Plus size={16} /> Add address</Btn>} />
      {loading ? <Loading /> : error ? <ErrorBox message={error} onRetry={load} /> :
        items.length === 0 ? <Empty icon={<Inbox size={28} />} title="No addresses yet" subtitle="Add one for faster checkout." /> :
        <div className="grid grid-2">
          {items.map((a) => (
            <Card key={a.id}>
              <div className="row-between">
                <div className="row">
                  <span className="stat-icon" style={{ background: 'linear-gradient(135deg, var(--primary), var(--purple))' }}><MapPin size={18} /></span>
                  <div>
                    <div className="row">
                      <strong>{a.label}</strong>
                      {a.isDefault && <span className="badge badge-green">Default</span>}
                    </div>
                    <span className="muted">{a.line1} · {a.city}, {a.country}</span>
                  </div>
                </div>
                <Btn size="sm" onClick={() => del(a.id)} aria-label="Delete"><Trash2 size={15} /></Btn>
              </div>
            </Card>
          ))}
        </div>}

      <Modal open={open} onClose={() => setOpen(false)} title="Add address" footer={
        <Btn variant="primary" onClick={save} disabled={busy || !form.line1}>{busy ? 'Saving…' : 'Save address'}</Btn>
      }>
        <form onSubmit={save}>
          <div className="form-row">
            <Field label="Label"><Input value={form.label} onChange={(e) => setForm({ ...form, label: e.target.value })} /></Field>
            <Field label="City"><Input value={form.city} onChange={(e) => setForm({ ...form, city: e.target.value })} /></Field>
          </div>
          <Field label="Street / area"><Input required value={form.line1} onChange={(e) => setForm({ ...form, line1: e.target.value })} placeholder="Plot 12, Kampala Road" /></Field>
          <Field label="Country"><Input value={form.country} onChange={(e) => setForm({ ...form, country: e.target.value })} /></Field>
          <label className="row" style={{ cursor: 'pointer' }}>
            <input type="checkbox" checked={form.isDefault} onChange={(e) => setForm({ ...form, isDefault: e.target.checked })} style={{ width: 17, height: 17, accentColor: 'var(--primary)' }} />
            Make default
          </label>
        </form>
      </Modal>
    </>
  );
}
