import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  ClipboardCheck, CheckCircle2, XCircle, Clock, Store, ShieldCheck, AlertTriangle,
  ExternalLink, PackageCheck,
} from 'lucide-react';
import { adminService } from '../../api/services';
import type { AdminQueueItem } from '../../api/types';
import { formatUgx } from '../../api/types';
import { useToast } from '../../store/ToastContext';
import {
  Btn, Empty, ErrorBox, PageHeader, SkeletonRows, Modal, Field, TextArea, Badge, StatCard, CountUp,
} from '../../components/ui';

const REJECT_REASONS = [
  'Image is low quality or does not show the product',
  'Price looks incorrect or misleading',
  'Title/description is unclear or misleading',
  'Prohibited or restricted item',
  'Duplicate of an existing listing',
  'Missing key details buyers need',
];

export default function AdminQueue() {
  const { toast } = useToast();
  const [items, setItems] = useState<AdminQueueItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [busyId, setBusyId] = useState('');
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [rejecting, setRejecting] = useState<AdminQueueItem | null>(null);
  const [reason, setReason] = useState('');
  const [bulkBusy, setBulkBusy] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const r = await adminService.queue();
      setItems(r.products);
      setSelected(new Set());
    } catch (e: any) {
      setError(e?.message || 'Could not load the approval queue');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);

  const approve = async (item: AdminQueueItem) => {
    setBusyId(item.id);
    try {
      const r = await adminService.approve(item.id);
      setItems((xs) => xs.filter((x) => x.id !== item.id));
      toast(
        r.followersNotified > 0
          ? `Published — ${r.followersNotified} follower${r.followersNotified > 1 ? 's' : ''} notified`
          : 'Published and now visible to buyers',
        'success'
      );
    } catch (e: any) {
      toast(e?.message || 'Approve failed', 'error');
    } finally {
      setBusyId('');
    }
  };

  const doReject = async () => {
    if (!rejecting) return;
    if (!reason.trim()) { toast('Give the seller a reason', 'warning'); return; }
    setBusyId(rejecting.id);
    try {
      await adminService.reject(rejecting.id, reason.trim());
      setItems((xs) => xs.filter((x) => x.id !== rejecting.id));
      toast('Rejected — the seller has been told why', 'success');
      setRejecting(null);
      setReason('');
    } catch (e: any) {
      toast(e?.message || 'Reject failed', 'error');
    } finally {
      setBusyId('');
    }
  };

  const bulkApprove = async () => {
    if (selected.size === 0) return;
    setBulkBusy(true);
    try {
      const r = await adminService.bulk([...selected], 'approve');
      setItems((xs) => xs.filter((x) => !selected.has(x.id)));
      setSelected(new Set());
      toast(`${r.affected} listing${r.affected === 1 ? '' : 's'} published · ${r.followersNotified} follower alerts sent`, 'success');
    } catch (e: any) {
      toast(e?.message || 'Bulk approve failed', 'error');
    } finally {
      setBulkBusy(false);
    }
  };

  const toggle = (id: string) => {
    setSelected((s) => {
      const next = new Set(s);
      next.has(id) ? next.delete(id) : next.add(id);
      return next;
    });
  };

  const stats = useMemo(() => {
    const sellers = new Set(items.map((i) => i.sellerId));
    const newSellers = items.filter((i) => i.sellerApprovedCount === 0).length;
    const value = items.reduce((s, i) => s + i.priceMinor * (i.stockQuantity || 1), 0);
    return { pending: items.length, sellers: sellers.size, newSellers, value };
  }, [items]);

  return (
    <>
      <PageHeader
        title="Approval queue"
        sub="Nothing reaches buyers until it clears this desk. Approving pushes an alert to everyone following that seller."
        actions={
          <>
            <Btn onClick={load}>Refresh</Btn>
            {selected.size > 0 && (
              <Btn variant="success" loading={bulkBusy} icon={<PackageCheck size={15} />} onClick={bulkApprove}>
                Approve {selected.size} selected
              </Btn>
            )}
          </>
        }
      />

      <div className="grid grid-4 mb-16 stagger">
        <StatCard index={0} label="Awaiting review" value={<CountUp value={stats.pending} />}
          icon={<Clock size={18} />} color="var(--warning)" />
        <StatCard index={1} label="Sellers waiting" value={<CountUp value={stats.sellers} />}
          icon={<Store size={18} />} />
        <StatCard index={2} label="First-time sellers" value={<CountUp value={stats.newSellers} />}
          icon={<AlertTriangle size={18} />} color="var(--accent-violet)" hint="Review these closely" />
        <StatCard index={3} label="Inventory value" value={formatUgx(stats.value)}
          icon={<ShieldCheck size={18} />} color="var(--accent-emerald)" />
      </div>

      {loading ? (
        <SkeletonRows rows={4} height={140} />
      ) : error ? (
        <ErrorBox message={error} onRetry={load} />
      ) : items.length === 0 ? (
        <Empty
          icon={<CheckCircle2 size={28} />}
          title="Queue is clear 🎉"
          subtitle="Every submitted listing has been reviewed. New submissions land here instantly."
        />
      ) : (
        <div className="col stagger">
          {items.map((item, i) => (
            <article key={item.id} className="card review-card stagger-item" style={{ '--i': i } as React.CSSProperties}>
              <label className="review-check">
                <input type="checkbox" checked={selected.has(item.id)} onChange={() => toggle(item.id)}
                  aria-label={`Select ${item.title}`} />
              </label>

              <img src={item.imageUrl} alt={item.title} className="review-img" loading="lazy" />

              <div className="grow" style={{ minWidth: 0 }}>
                <div className="row wrap" style={{ gap: 8 }}>
                  <strong style={{ fontSize: 'var(--fs-md)' }}>{item.title}</strong>
                  <Badge tone="amber">pending</Badge>
                  {item.sellerApprovedCount === 0 && <Badge tone="violet">first listing</Badge>}
                  {item.sellerVerified && <Badge tone="green">verified seller</Badge>}
                </div>

                <p className="muted tiny mt-4 clamp-2">{item.description || 'No description provided.'}</p>

                <div className="row wrap mt-8" style={{ gap: 14 }}>
                  <span className="semi t-primary" style={{ fontSize: 'var(--fs-md)' }}>{formatUgx(item.priceMinor)}</span>
                  <span className="tiny muted">Stock: {item.stockQuantity}</span>
                  <span className="tiny muted">{item.category}{item.brand ? ` · ${item.brand}` : ''}</span>
                  <span className="tiny muted">
                    <Clock size={11} style={{ verticalAlign: -1 }} />{' '}
                    {item.submittedAt ? new Date(item.submittedAt).toLocaleString() : 'just now'}
                  </span>
                </div>

                <div className="row wrap mt-8" style={{ gap: 8 }}>
                  <Link to={`/seller/${item.sellerId}`} className="tiny link-arrow">
                    <Store size={12} /> {item.sellerName} ({item.sellerApprovedCount} live) <ExternalLink size={11} />
                  </Link>
                  <span className="tiny muted-2">{item.sellerEmail}</span>
                </div>
              </div>

              <div className="review-actions">
                <Btn variant="success" loading={busyId === item.id} icon={<CheckCircle2 size={15} />}
                  onClick={() => approve(item)}>
                  Approve
                </Btn>
                <Btn variant="danger" disabled={busyId === item.id} icon={<XCircle size={15} />}
                  onClick={() => { setRejecting(item); setReason(''); }}>
                  Reject
                </Btn>
              </div>
            </article>
          ))}
        </div>
      )}

      <Modal
        open={!!rejecting}
        onClose={() => setRejecting(null)}
        title={`Reject “${rejecting?.title ?? ''}”`}
        footer={
          <>
            <Btn onClick={() => setRejecting(null)}>Cancel</Btn>
            <Btn variant="danger" loading={busyId === rejecting?.id} onClick={doReject}>Reject listing</Btn>
          </>
        }
      >
        <p className="muted tiny mb-12">
          The seller gets this reason as a notification and can fix and resubmit. Be specific.
        </p>
        <div className="row wrap mb-12" style={{ gap: 7 }}>
          {REJECT_REASONS.map((r) => (
            <button key={r} className={`chip ${reason === r ? 'active' : ''}`} onClick={() => setReason(r)}>{r}</button>
          ))}
        </div>
        <Field label="Reason sent to the seller" required>
          <TextArea rows={3} value={reason} onChange={(e) => setReason(e.target.value)}
            placeholder="Explain what needs to change…" />
        </Field>
      </Modal>
    </>
  );
}
