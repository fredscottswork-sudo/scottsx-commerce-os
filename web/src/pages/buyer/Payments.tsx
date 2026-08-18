import { useEffect, useState, type FormEvent } from 'react';
import { CreditCard, Plus, Trash2, Wallet } from 'lucide-react';
import { buyerService } from '../../api/services';
import type { PaymentMethod } from '../../api/types';
import { useToast } from '../../store/ToastContext';
import { Btn, Card, Empty, ErrorBox, Field, Input, Loading, Modal, PageHeader, Select } from '../../components/ui';

export default function Payments() {
  const { toast } = useToast();
  const [items, setItems] = useState<PaymentMethod[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState({ type: 'momo', label: 'MTN MoMo', phone: '', last4: '' });
  const [busy, setBusy] = useState(false);

  async function load() {
    setLoading(true);
    buyerService.paymentMethods().then((r) => setItems(r.paymentMethods)).catch((e: any) => setError(e.message)).finally(() => setLoading(false));
  }
  useEffect(() => { load(); }, []);

  async function save(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    try {
      await buyerService.createPaymentMethod({
        type: form.type,
        label: form.label,
        phone: form.type === 'momo' ? form.phone : '',
        last4: form.type === 'card' ? form.phone : '',
        isDefault: items.length === 0,
      });
      toast('Payment method added', 'success');
      setOpen(false);
      setForm({ type: 'momo', label: 'MTN MoMo', phone: '', last4: '' });
      load();
    } catch (err: any) {
      toast(err.message, 'error');
    } finally {
      setBusy(false);
    }
  }

  async function del(id: string) {
    await buyerService.deletePaymentMethod(id);
    toast('Removed', 'info');
    load();
  }

  return (
    <>
      <PageHeader title="Payment methods" sub="Mobile Money & cards — shared with the mobile app."
        actions={<Btn variant="primary" onClick={() => setOpen(true)}><Plus size={16} /> Add method</Btn>} />
      {loading ? <Loading /> : error ? <ErrorBox message={error} onRetry={load} /> :
        items.length === 0 ? <Empty icon={<CreditCard size={28} />} title="No payment methods" subtitle="Add Mobile Money or a card." /> :
        <div className="grid grid-2">
          {items.map((m) => (
            <Card key={m.id}>
              <div className="row-between">
                <div className="row">
                  <span className="stat-icon" style={{ background: 'linear-gradient(135deg, var(--primary), var(--purple))' }}><Wallet size={18} /></span>
                  <div>
                    <div className="row">
                      <strong>{m.label || m.type}</strong>
                      {m.isDefault && <span className="badge badge-green">Default</span>}
                    </div>
                    <span className="muted">{m.type === 'momo' ? m.phone : `•••• ${m.last4}`}</span>
                  </div>
                </div>
                <Btn size="sm" onClick={() => del(m.id)} aria-label="Delete"><Trash2 size={15} /></Btn>
              </div>
            </Card>
          ))}
        </div>}

      <Modal open={open} onClose={() => setOpen(false)} title="Add payment method" footer={
        <Btn variant="primary" onClick={save} disabled={busy || !form.phone}>{busy ? 'Saving…' : 'Save'}</Btn>
      }>
        <form onSubmit={save}>
          <Field label="Type">
            <Select value={form.type} onChange={(e) => {
              const type = e.target.value;
              setForm({ ...form, type, label: type === 'momo' ? 'MTN MoMo' : 'Visa / Mastercard' });
            }}>
              <option value="momo">📲 Mobile Money</option>
              <option value="card">💳 Card</option>
            </Select>
          </Field>
          <Field label="Label"><Input value={form.label} onChange={(e) => setForm({ ...form, label: e.target.value })} /></Field>
          <Field label={form.type === 'momo' ? 'MoMo number' : 'Last 4 digits'}>
            <Input value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} placeholder={form.type === 'momo' ? '07xx xxx xxx' : '1234'} />
          </Field>
        </form>
      </Modal>
    </>
  );
}
