import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useSearchParams, useNavigate } from 'react-router-dom';
import {
  Package, PlusCircle, Trash2, Send, Pencil, AlertTriangle, Eye, Clock, XCircle, Search as SearchIcon,
} from 'lucide-react';
import { sellerService } from '../../api/services';
import type { Product, ProductStatus } from '../../api/types';
import { formatUgx } from '../../api/types';
import { useToast } from '../../store/ToastContext';
import {
  Btn, Empty, ErrorBox, PageHeader, SkeletonRows, Tabs, Table, StatusBadge,
  ConfirmModal, Modal, Field, Input, TextArea, Select, SearchInput,
} from '../../components/ui';

type TabId = 'all' | ProductStatus;

export default function Inventory() {
  const { toast } = useToast();
  const nav = useNavigate();
  const [params, setParams] = useSearchParams();
  const tab = (params.get('status') as TabId) || 'all';

  const [products, setProducts] = useState<Product[]>([]);
  const [counts, setCounts] = useState<Record<string, number>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [q, setQ] = useState('');
  const [busyId, setBusyId] = useState('');

  const [deleting, setDeleting] = useState<Product | null>(null);
  const [editing, setEditing] = useState<Product | null>(null);
  const [form, setForm] = useState({ title: '', description: '', priceMinor: 0, stockQuantity: 0, category: '', brand: '', imageUrl: '' });
  const [saving, setSaving] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const r = await sellerService.inventory(tab);
      setProducts(r.products);
      setCounts(r.counts || {});
    } catch (e: any) {
      setError(e?.message || 'Could not load your inventory');
    } finally {
      setLoading(false);
    }
  }, [tab]);

  useEffect(() => { void load(); }, [load]);

  const setTab = (t: TabId) => {
    const p = new URLSearchParams(params);
    if (t === 'all') p.delete('status'); else p.set('status', t);
    setParams(p, { replace: true });
  };

  const submit = async (p: Product) => {
    setBusyId(p.id);
    try {
      await sellerService.submitForReview(p.id);
      toast('Sent for admin approval — you will be notified when it goes live', 'success');
      await load();
    } catch (e: any) {
      toast(e?.message || 'Could not submit for review', 'error');
    } finally {
      setBusyId('');
    }
  };

  const openEdit = (p: Product) => {
    setEditing(p);
    setForm({
      title: p.title, description: p.description || '', priceMinor: p.priceMinor,
      stockQuantity: p.stockQuantity, category: p.category || '', brand: p.brand || '',
      imageUrl: p.imageUrl || '',
    });
  };

  const saveEdit = async () => {
    if (!editing) return;
    setSaving(true);
    try {
      await sellerService.updateProduct(editing.id, form);
      const contentChanged =
        form.title !== editing.title ||
        form.description !== (editing.description || '') ||
        form.imageUrl !== (editing.imageUrl || '');
      toast(
        contentChanged && editing.status === 'approved'
          ? 'Saved — content edits go back to admin review before they show publicly'
          : 'Saved',
        'success'
      );
      setEditing(null);
      await load();
    } catch (e: any) {
      toast(e?.message || 'Could not save', 'error');
    } finally {
      setSaving(false);
    }
  };

  const remove = async () => {
    if (!deleting) return;
    setBusyId(deleting.id);
    try {
      await sellerService.deleteProduct(deleting.id);
      setProducts((xs) => xs.filter((x) => x.id !== deleting.id));
      toast('Product deleted', 'success');
      setDeleting(null);
    } catch (e: any) {
      toast(e?.message || 'Could not delete', 'error');
    } finally {
      setBusyId('');
    }
  };

  const filtered = useMemo(() => {
    const needle = q.trim().toLowerCase();
    if (!needle) return products;
    return products.filter((p) =>
      p.title.toLowerCase().includes(needle) ||
      (p.category || '').toLowerCase().includes(needle) ||
      (p.brand || '').toLowerCase().includes(needle)
    );
  }, [products, q]);

  return (
    <>
      <PageHeader
        title="Inventory"
        sub="Drafts stay private. Submitted listings need admin approval before buyers can see them."
        actions={
          <Btn variant="primary" icon={<PlusCircle size={15} />} onClick={() => nav('/seller/add-product')}>
            Add product
          </Btn>
        }
      />

      <Tabs<TabId>
        active={tab}
        onChange={setTab}
        tabs={[
          { id: 'all', label: 'All' },
          { id: 'approved', label: 'Live', count: counts.approved },
          { id: 'pending', label: 'In review', count: counts.pending },
          { id: 'draft', label: 'Drafts', count: counts.draft },
          { id: 'rejected', label: 'Rejected', count: counts.rejected },
          { id: 'suspended', label: 'Suspended', count: counts.suspended },
        ]}
      />

      <div className="row-between wrap mt-16 mb-12">
        <div style={{ flex: '1 1 260px', maxWidth: 340 }}>
          <SearchInput value={q} onChange={setQ} placeholder="Filter by title, brand or category…" />
        </div>
        <span className="tiny muted">{filtered.length} shown</span>
      </div>

      {loading ? (
        <SkeletonRows rows={6} height={58} />
      ) : error ? (
        <ErrorBox message={error} onRetry={load} />
      ) : filtered.length === 0 ? (
        <Empty
          icon={<Package size={28} />}
          title={q ? 'Nothing matched that filter' : tab === 'all' ? 'No products yet' : `No ${tab} products`}
          subtitle={q ? 'Try a different search term.' : 'Add your first product — it takes under a minute with AI help.'}
          action={!q ? <Btn variant="primary" onClick={() => nav('/seller/add-product')}>Add product</Btn> : undefined}
        />
      ) : (
        <Table<Product>
          rows={filtered}
          keyOf={(p) => p.id}
          columns={[
            {
              key: 'product',
              header: 'Product',
              render: (p) => (
                <div className="row" style={{ gap: 10 }}>
                  <img src={p.imageUrl} alt="" className="row-thumb" loading="lazy" />
                  <div style={{ minWidth: 0 }}>
                    <div className="semi ellipsis" style={{ maxWidth: 280 }}>{p.title}</div>
                    <div className="tiny muted">{p.category}{p.brand ? ` · ${p.brand}` : ''}</div>
                    {p.status === 'rejected' && p.rejectionReason && (
                      <div className="tiny t-danger mt-4">
                        <XCircle size={11} style={{ verticalAlign: -1 }} /> {p.rejectionReason}
                      </div>
                    )}
                  </div>
                </div>
              ),
            },
            { key: 'price', header: 'Price', render: (p) => <span className="semi">{formatUgx(p.priceMinor)}</span> },
            {
              key: 'stock',
              header: 'Stock',
              hideSm: true,
              render: (p) => (
                <span className={p.stockQuantity === 0 ? 't-danger semi' : p.stockQuantity < 5 ? 't-warning semi' : ''}>
                  {p.stockQuantity}
                  {p.stockQuantity === 0 && <AlertTriangle size={12} style={{ marginLeft: 4, verticalAlign: -2 }} />}
                </span>
              ),
            },
            {
              key: 'views', header: 'Views', hideSm: true,
              render: (p) => <span className="tiny muted"><Eye size={12} style={{ verticalAlign: -2 }} /> {p.viewCount ?? 0}</span>,
            },
            { key: 'status', header: 'Status', render: (p) => <StatusBadge status={p.status || 'approved'} /> },
            {
              key: 'actions',
              header: '',
              render: (p) => (
                <div className="row" style={{ gap: 5, justifyContent: 'flex-end' }}>
                  {(p.status === 'draft' || p.status === 'rejected') && (
                    <Btn size="sm" variant="primary" loading={busyId === p.id} icon={<Send size={13} />}
                      onClick={() => submit(p)}>
                      Submit
                    </Btn>
                  )}
                  {p.status === 'approved' && (
                    <Link to={`/product/${p.id}`} className="btn btn-sm" title="View public page">
                      <Eye size={13} />
                    </Link>
                  )}
                  <Btn size="sm" icon={<Pencil size={13} />} onClick={() => openEdit(p)} aria-label="Edit" />
                  <Btn size="sm" variant="ghost" icon={<Trash2 size={13} />} onClick={() => setDeleting(p)} aria-label="Delete" />
                </div>
              ),
            },
          ]}
        />
      )}

      {/* ── Edit ────────────────────────────────────────────────────── */}
      <Modal
        open={!!editing}
        onClose={() => setEditing(null)}
        title="Edit product"
        size="lg"
        footer={
          <>
            <Btn onClick={() => setEditing(null)}>Cancel</Btn>
            <Btn variant="primary" loading={saving} onClick={saveEdit}>Save changes</Btn>
          </>
        }
      >
        {editing?.status === 'approved' && (
          <p className="tiny muted mb-12">
            <Clock size={12} style={{ verticalAlign: -2 }} /> Editing the title, description or image sends this
            listing back to review. Price and stock changes stay live immediately.
          </p>
        )}
        <Field label="Title" required>
          <Input value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} />
        </Field>
        <Field label="Description">
          <TextArea rows={4} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
        </Field>
        <div className="form-row">
          <Field label="Price (UGX)" required>
            <Input type="number" min={1} value={form.priceMinor}
              onChange={(e) => setForm({ ...form, priceMinor: Number(e.target.value) })} />
          </Field>
          <Field label="Stock">
            <Input type="number" min={0} value={form.stockQuantity}
              onChange={(e) => setForm({ ...form, stockQuantity: Number(e.target.value) })} />
          </Field>
        </div>
        <div className="form-row">
          <Field label="Category">
            <Input value={form.category} onChange={(e) => setForm({ ...form, category: e.target.value })} />
          </Field>
          <Field label="Brand">
            <Input value={form.brand} onChange={(e) => setForm({ ...form, brand: e.target.value })} />
          </Field>
        </div>
        <Field label="Image URL" hint="Must be a public http(s) link. Listings without a real image cannot be published.">
          <Input value={form.imageUrl} onChange={(e) => setForm({ ...form, imageUrl: e.target.value })} />
        </Field>
        {form.imageUrl && <img src={form.imageUrl} alt="" className="img-preview" />}
      </Modal>

      <ConfirmModal
        open={!!deleting}
        title={`Delete “${deleting?.title ?? ''}”?`}
        message="This permanently removes the listing. Orders already placed are unaffected."
        confirmLabel="Delete product"
        danger
        loading={busyId === deleting?.id}
        onCancel={() => setDeleting(null)}
        onConfirm={remove}
      />
    </>
  );
}
