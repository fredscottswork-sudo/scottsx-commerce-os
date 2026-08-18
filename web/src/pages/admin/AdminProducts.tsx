import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Trash2, Package, CheckCircle2, XCircle, Ban, History, Eye, ShieldCheck,
} from 'lucide-react';
import { adminService } from '../../api/services';
import type { AdminProductRow, ProductStatus } from '../../api/types';
import { formatUgx } from '../../api/types';
import { useToast } from '../../store/ToastContext';
import {
  Btn, Empty, ErrorBox, PageHeader, Pagination, SearchInput, Table, Tabs,
  SkeletonRows, StatusBadge, ConfirmModal, Modal, Field, TextArea, Badge,
} from '../../components/ui';

type TabId = 'all' | ProductStatus;
const PAGE_SIZE = 25;

export default function AdminProducts() {
  const { toast } = useToast();
  const [items, setItems] = useState<AdminProductRow[]>([]);
  const [counts, setCounts] = useState<Record<string, number>>({});
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [search, setSearch] = useState('');
  const [tab, setTab] = useState<TabId>('all');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [busyId, setBusyId] = useState('');

  const [del, setDel] = useState<AdminProductRow | null>(null);
  const [moderating, setModerating] = useState<{ row: AdminProductRow; action: 'reject' | 'suspend' } | null>(null);
  const [reason, setReason] = useState('');
  const [historyFor, setHistoryFor] = useState<AdminProductRow | null>(null);
  const [history, setHistory] = useState<{ id: string; action: string; reason: string; adminName: string; createdAt: string }[]>([]);

  const load = useCallback(async (p = 1) => {
    setLoading(true);
    setError('');
    try {
      const r = await adminService.products({
        search: search || undefined,
        status: tab === 'all' ? undefined : tab,
        page: p,
        pageSize: PAGE_SIZE,
      });
      setItems(r.products);
      setTotal(r.total);
      setPage(r.page);
      setCounts(r.counts || {});
    } catch (e: any) {
      setError(e?.message || 'Could not load products');
    } finally {
      setLoading(false);
    }
  }, [search, tab]);

  useEffect(() => {
    const t = setTimeout(() => void load(1), search ? 280 : 0);
    return () => clearTimeout(t);
  }, [load, search]);

  const approve = async (row: AdminProductRow) => {
    setBusyId(row.id);
    try {
      const r = await adminService.approve(row.id);
      toast(`Published${r.followersNotified ? ` · ${r.followersNotified} followers notified` : ''}`, 'success');
      await load(page);
    } catch (e: any) {
      toast(e?.message || 'Approve failed', 'error');
    } finally {
      setBusyId('');
    }
  };

  const doModerate = async () => {
    if (!moderating) return;
    if (!reason.trim()) { toast('A reason is required', 'warning'); return; }
    setBusyId(moderating.row.id);
    try {
      if (moderating.action === 'reject') await adminService.reject(moderating.row.id, reason.trim());
      else await adminService.suspend(moderating.row.id, reason.trim());
      toast(moderating.action === 'reject' ? 'Listing rejected' : 'Listing suspended', 'success');
      setModerating(null);
      setReason('');
      await load(page);
    } catch (e: any) {
      toast(e?.message || 'Action failed', 'error');
    } finally {
      setBusyId('');
    }
  };

  const confirmDelete = async () => {
    if (!del) return;
    setBusyId(del.id);
    try {
      await adminService.deleteProduct(del.id);
      toast('Product removed from the marketplace', 'success');
      setDel(null);
      await load(page);
    } catch (e: any) {
      toast(e?.message || 'Delete failed', 'error');
    } finally {
      setBusyId('');
    }
  };

  const openHistory = async (row: AdminProductRow) => {
    setHistoryFor(row);
    setHistory([]);
    try {
      const r = await adminService.history(row.id);
      setHistory(r.history);
    } catch (e: any) {
      toast(e?.message || 'Could not load history', 'error');
    }
  };

  return (
    <>
      <PageHeader
        title="All products"
        sub="Every listing on the platform, in any state. Moderation actions are audited and the seller is always told why."
        actions={<Link to="/admin/queue" className="btn btn-primary"><ShieldCheck size={15} /> Approval queue</Link>}
      />

      <Tabs<TabId>
        active={tab}
        onChange={(t) => { setTab(t); setPage(1); }}
        tabs={[
          { id: 'all', label: 'All' },
          { id: 'approved', label: 'Live', count: counts.approved },
          { id: 'pending', label: 'Pending', count: counts.pending },
          { id: 'rejected', label: 'Rejected', count: counts.rejected },
          { id: 'suspended', label: 'Suspended', count: counts.suspended },
          { id: 'draft', label: 'Drafts', count: counts.draft },
        ]}
      />

      <div className="row-between wrap mt-16 mb-12">
        <div style={{ flex: '1 1 280px', maxWidth: 380 }}>
          <SearchInput value={search} onChange={setSearch} placeholder="Search title, category, brand or seller…" />
        </div>
        <span className="tiny muted">{total.toLocaleString()} listing{total === 1 ? '' : 's'}</span>
      </div>

      {loading ? (
        <SkeletonRows rows={8} height={56} />
      ) : error ? (
        <ErrorBox message={error} onRetry={() => load(page)} />
      ) : items.length === 0 ? (
        <Empty icon={<Package size={28} />} title="No products found"
          subtitle={search ? 'Try a different search term.' : 'Nothing in this state right now.'} />
      ) : (
        <>
          <Table<AdminProductRow>
            rows={items}
            keyOf={(p) => p.id}
            columns={[
              {
                key: 'product', header: 'Product',
                render: (p) => (
                  <div className="row" style={{ gap: 10 }}>
                    <img src={p.imageUrl} alt="" className="row-thumb" loading="lazy" />
                    <div style={{ minWidth: 0 }}>
                      <div className="semi ellipsis" style={{ maxWidth: 250 }}>{p.title}</div>
                      <div className="tiny muted">{p.category}{p.brand ? ` · ${p.brand}` : ''}</div>
                      {p.rejectionReason && <div className="tiny t-danger ellipsis" style={{ maxWidth: 250 }}>{p.rejectionReason}</div>}
                    </div>
                  </div>
                ),
              },
              {
                key: 'seller', header: 'Seller', hideSm: true,
                render: (p) => (
                  <Link to={`/seller/${p.sellerId}`} className="tiny" style={{ textDecoration: 'none' }}>
                    <div className="semi">{p.sellerName}</div>
                    <div className="muted-2">{p.sellerEmail}</div>
                  </Link>
                ),
              },
              { key: 'price', header: 'Price', render: (p) => <span className="semi">{formatUgx(p.priceMinor)}</span> },
              { key: 'stock', header: 'Stock', hideSm: true, render: (p) => <span className={p.stockQuantity === 0 ? 't-danger' : ''}>{p.stockQuantity}</span> },
              {
                key: 'status', header: 'Status',
                render: (p) => (
                  <div className="col" style={{ gap: 4 }}>
                    <StatusBadge status={p.status} />
                    {p.isFlashDeal && <Badge tone="red">flash</Badge>}
                  </div>
                ),
              },
              {
                key: 'actions', header: '',
                render: (p) => (
                  <div className="row" style={{ gap: 4, justifyContent: 'flex-end' }}>
                    {p.status === 'pending' && (
                      <Btn size="sm" variant="success" loading={busyId === p.id} icon={<CheckCircle2 size={13} />}
                        onClick={() => approve(p)} aria-label="Approve" />
                    )}
                    {(p.status === 'pending' || p.status === 'draft') && (
                      <Btn size="sm" variant="danger" icon={<XCircle size={13} />}
                        onClick={() => { setModerating({ row: p, action: 'reject' }); setReason(''); }} aria-label="Reject" />
                    )}
                    {p.status === 'approved' && (
                      <>
                        <Link to={`/product/${p.id}`} className="btn btn-sm" title="View public page"><Eye size={13} /></Link>
                        <Btn size="sm" variant="danger" icon={<Ban size={13} />}
                          onClick={() => { setModerating({ row: p, action: 'suspend' }); setReason(''); }} aria-label="Suspend" />
                      </>
                    )}
                    <Btn size="sm" variant="ghost" icon={<History size={13} />} onClick={() => openHistory(p)} aria-label="History" />
                    <Btn size="sm" variant="ghost" icon={<Trash2 size={13} />} onClick={() => setDel(p)} aria-label="Delete" />
                  </div>
                ),
              },
            ]}
          />
          <Pagination page={page} pageSize={PAGE_SIZE} total={total} onPage={(p) => void load(p)} />
        </>
      )}

      {/* ── Reject / suspend ────────────────────────────────────────── */}
      <Modal
        open={!!moderating}
        onClose={() => setModerating(null)}
        title={moderating?.action === 'reject' ? 'Reject listing' : 'Suspend listing'}
        footer={
          <>
            <Btn onClick={() => setModerating(null)}>Cancel</Btn>
            <Btn variant="danger" loading={busyId === moderating?.row.id} onClick={doModerate}>
              {moderating?.action === 'reject' ? 'Reject' : 'Suspend'}
            </Btn>
          </>
        }
      >
        <p className="tiny muted mb-12">
          “{moderating?.row.title}” — the seller receives this reason and can fix and resubmit.
        </p>
        <Field label="Reason" required>
          <TextArea rows={3} value={reason} onChange={(e) => setReason(e.target.value)}
            placeholder="Explain exactly what is wrong…" />
        </Field>
      </Modal>

      {/* ── Audit trail ─────────────────────────────────────────────── */}
      <Modal open={!!historyFor} onClose={() => setHistoryFor(null)} title={`History — ${historyFor?.title ?? ''}`}>
        {history.length === 0 ? (
          <p className="muted tiny">No moderation actions recorded yet.</p>
        ) : (
          <div className="col">
            {history.map((h) => (
              <div key={h.id} className="order-row">
                <div className="grow" style={{ minWidth: 0 }}>
                  <div className="tiny semi"><StatusBadge status={h.action} /> by {h.adminName}</div>
                  {h.reason && <div className="tiny muted mt-4">{h.reason}</div>}
                </div>
                <span className="tiny muted-2">{new Date(h.createdAt).toLocaleString()}</span>
              </div>
            ))}
          </div>
        )}
      </Modal>

      <ConfirmModal
        open={!!del}
        title="Remove product"
        message={`Permanently remove “${del?.title ?? ''}” from the marketplace? This cannot be undone.`}
        confirmLabel="Delete permanently"
        danger
        loading={busyId === del?.id}
        onCancel={() => setDel(null)}
        onConfirm={confirmDelete}
      />
    </>
  );
}
