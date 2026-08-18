import { useEffect, useState } from 'react';
import { Inbox } from 'lucide-react';
import { buyerService } from '../../api/services';
import type { Order, Refund } from '../../api/types';
import { formatUgx } from '../../api/types';
import { useToast } from '../../store/ToastContext';
import { Btn, Card, Empty, ErrorBox, Field, Loading, Modal, PageHeader, Select, StatusBadge, TextArea } from '../../components/ui';

export default function Refunds() {
  const { toast } = useToast();
  const [refunds, setRefunds] = useState<Refund[]>([]);
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [open, setOpen] = useState(false);
  const [orderId, setOrderId] = useState('');
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);

  async function load() {
    setLoading(true);
    Promise.all([buyerService.refunds(), buyerService.orders()])
      .then(([r, o]) => { setRefunds(r.refunds); setOrders(o.orders); })
      .catch((e: any) => setError(e.message))
      .finally(() => setLoading(false));
  }
  useEffect(() => { load(); }, []);

  async function submit() {
    setBusy(true);
    try {
      await buyerService.createRefund(orderId, reason);
      toast('Refund claim opened', 'success');
      setOpen(false);
      setReason('');
      load();
    } catch (e: any) {
      toast(e.message, 'error');
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <PageHeader title="Refunds" sub="Open and track refund claims — statuses are shared with mobile."
        actions={<Btn variant="primary" onClick={() => setOpen(true)} disabled={orders.length === 0}>New claim</Btn>} />
      {loading ? <Loading /> : error ? <ErrorBox message={error} onRetry={load} /> :
        refunds.length === 0 ? <Empty icon={<Inbox size={28} />} title="No refund claims" subtitle="If an order isn't right, open a claim within 7 days." /> :
        <div className="grid grid-2">
          {refunds.map((r) => (
            <Card key={r.id}>
              <div className="row-between mb-8">
                <strong>{r.reason}</strong>
                <StatusBadge status={r.status} />
              </div>
              <span className="muted" style={{ fontSize: 13 }}>Order {r.orderId.slice(0, 8)} · {new Date(r.createdAt).toLocaleDateString()}</span>
            </Card>
          ))}
        </div>}

      <Modal open={open} onClose={() => setOpen(false)} title="New refund claim" footer={
        <Btn variant="primary" onClick={submit} disabled={busy || !orderId || reason.length < 3}>{busy ? 'Submitting…' : 'Submit claim'}</Btn>
      }>
        <Field label="Order">
          <Select value={orderId} onChange={(e) => setOrderId(e.target.value)}>
            <option value="">Select an order…</option>
            {orders.map((o) => <option key={o.id} value={o.id}>{o.title} — {formatUgx(o.amount)}</option>)}
          </Select>
        </Field>
        <Field label="Reason">
          <TextArea rows={3} value={reason} onChange={(e) => setReason(e.target.value)} placeholder="Item not delivered / not as described…" />
        </Field>
      </Modal>
    </>
  );
}
