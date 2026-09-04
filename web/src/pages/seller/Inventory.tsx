import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useSearchParams, useNavigate } from 'react-router-dom';
import {
  Package, PlusCircle, Trash2, Send, Pencil, AlertTriangle, Eye, Clock, XCircle,
} from 'lucide-react';
import { sellerService } from '../../api/services';
import type { Product, ProductStatus } from '../../api/types';
import { formatUgx } from '../../api/types';
import { useToast } from '../../store/ToastContext';
import {
  Btn, Empty, ErrorBox, PageHeader, SkeletonRows, Tabs, Table, StatusBadge,
  ConfirmModal, Modal, Field, Input, TextArea, Select, SearchInput, Switch,
} from '../../components/ui';
import { ImageUploader } from '../../components/ImageUploader';
import { resolveMediaUrl } from '../../api/client';

type TabId = 'all' | ProductStatus;

const CATEGORIES = ['Electronics','Fashion','Home','Beauty','Sports','Automotive','Books','Groceries','Toys','Health','Other'];

type EditForm = {
  title: string;
  description: string;
  priceMinor: number;
  oldPriceMinor: number | null;
  stockQuantity: number;
  category: string;
  brand: string;
  location: string;
  imageUrl: string;
  mediaUrls: string[];
  isFlashDeal: boolean;
  discountPercent: number;
};

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
  const [form, setForm] = useState<EditForm>({
    title: '', description: '', priceMinor: 0, oldPriceMinor: null, stockQuantity: 0,
    category: 'Other', brand: '', location: '', imageUrl: '', mediaUrls: [],
    isFlashDeal: false, discountPercent: 0,
  });
  const [saving, setSaving] = useState(false);
  const [editErrors, setEditErrors] = useState<Partial<Record<keyof EditForm, string>>>({});

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
    if (!p.imageUrl && !(p as any).mediaUrls?.length) {
      toast('Add a photo before submitting — listings without a real image cannot be published', 'warning');
      return;
    }
    if (!p.priceMinor || p.priceMinor <= 0) {
      toast('Set a price above zero before submitting', 'warning');
      return;
    }
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
    setEditErrors({});
    setForm({
      title: p.title,
      description: p.description || '',
      priceMinor: p.priceMinor,
      oldPriceMinor: p.oldPriceMinor ?? null,
      stockQuantity: p.stockQuantity,
      category: p.category || 'Other',
      brand: p.brand || '',
      location: (p as any).location || '',
      imageUrl: p.imageUrl || '',
      // If backend ever returns media array, use it; otherwise seed with single imageUrl
      mediaUrls: (p as any).mediaUrls?.length ? (p as any).mediaUrls : (p.imageUrl ? [p.imageUrl] : []),
      isFlashDeal: !!p.isFlashDeal,
      discountPercent: p.discountPercent ?? 0,
    });
  };

  const validateEdit = () => {
    const e: Partial<Record<keyof EditForm, string>> = {};
    if (form.title.trim().length < 3) e.title = 'Title must be at least 3 characters';
    if (!form.priceMinor || form.priceMinor <= 0) e.priceMinor = 'Price must be greater than zero';
    if (form.stockQuantity < 0) e.stockQuantity = 'Stock cannot be negative';
    if (form.discountPercent < 0 || form.discountPercent > 100) e.discountPercent = 'Discount must be 0-100';
    if (!form.mediaUrls.length && !form.imageUrl.trim()) e.imageUrl = 'Add at least one photo';
    setEditErrors(e);
    return Object.keys(e).length === 0;
  };

  const saveEdit = async () => {
    if (!editing) return;
    if (!validateEdit()) { toast('Fix the highlighted fields', 'warning'); return; }
    setSaving(true);
    try {
      const payload: any = {
        title: form.title.trim(),
        description: form.description,
        priceMinor: Math.round(form.priceMinor),
        oldPriceMinor: form.oldPriceMinor ? Math.round(form.oldPriceMinor) : null,
        stockQuantity: Math.round(form.stockQuantity),
        category: form.category,
        brand: form.brand.trim(),
        location: form.location.trim(),
        // Always send mediaUrls so backend syncs gallery; imageUrl derived from cover
        mediaUrls: form.mediaUrls,
        imageUrl: form.mediaUrls[0] || form.imageUrl,
        isFlashDeal: form.isFlashDeal,
        discountPercent: form.isFlashDeal ? Math.round(form.discountPercent) : 0,
      };
      await sellerService.updateProduct(editing.id, payload);
      const contentChanged =
        form.title !== editing.title ||
        form.description !== (editing.description || '') ||
        (form.category || '') !== (editing.category || '') ||
        (form.mediaUrls[0] || form.imageUrl || '') !== (editing.imageUrl || '');
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
                  {p.imageUrl ? (
                    <img src={resolveMediaUrl(p.imageUrl)} alt="" className="row-thumb" loading="lazy" onError={(e) => { (e.currentTarget as HTMLImageElement).style.display = 'none'; }} />
                  ) : (
                    <span className="row-thumb center" style={{ fontWeight: 800, background: 'var(--surface-3)', color: 'var(--text-3)' }}>{p.title[0]?.toUpperCase() || '•'}</span>
                  )}
                  <div style={{ minWidth: 0 }}>
                    <div className="semi ellipsis" style={{ maxWidth: 280 }}>{p.title}</div>
                    <div className="tiny muted">{p.category}{p.brand ? ` · ${p.brand}` : ''}</div>
                    {p.status === 'rejected' && p.rejectionReason && (
                      <div className="tiny t-danger mt-4">
                        <XCircle size={11} style={{ verticalAlign: -1 }} /> {p.rejectionReason}
                        {p.visionDecision && (
                          <span className="muted-2"> · AI review: {p.visionDecision.replace('_', ' ')}</span>
                        )}
                      </div>
                    )}
                    {p.status === 'approved' && p.visionDecision === 'approved' && (
                      <div className="tiny t-success mt-4">✓ Passed automated photo review</div>
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

      {/* ── Edit — full product form with gallery ─────────────────────── */}
      <Modal
        open={!!editing}
        onClose={() => setEditing(null)}
        title={`Edit “${editing?.title ?? ''}”`}
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
            <Clock size={12} style={{ verticalAlign: -2 }} /> Editing the title, description, category or photos sends this
            listing back to review. Price and stock changes stay live immediately.
          </p>
        )}
        <div className="col">
          <section>
            <h4 className="mb-8" style={{ fontSize: 13 }}>Photos</h4>
            <ImageUploader
              images={form.mediaUrls}
              max={8}
              onChange={(next) => setForm((f) => ({ ...f, mediaUrls: next, imageUrl: next[0] || '' }))}
            />
            {editErrors.imageUrl && <p className="tiny t-danger mt-4">{editErrors.imageUrl}</p>}
          </section>

          <Field label="Title" required error={editErrors.title}>
            <Input value={form.title} invalid={!!editErrors.title} onChange={(e) => setForm({ ...form, title: e.target.value })} placeholder="Product title" />
          </Field>
          <Field label="Description">
            <TextArea rows={4} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} placeholder="What buyers get, condition, warranty…" />
          </Field>

          <div className="form-row">
            <Field label="Price (UGX)" required error={editErrors.priceMinor}>
              <Input type="number" min={1} value={form.priceMinor || ''} invalid={!!editErrors.priceMinor}
                onChange={(e) => setForm({ ...form, priceMinor: Number(e.target.value) })} />
            </Field>
            <Field label="Was price (UGX)" hint="Optional — shows original price struck through">
              <Input type="number" min={0} value={form.oldPriceMinor ?? ''} onChange={(e) => setForm({ ...form, oldPriceMinor: e.target.value ? Number(e.target.value) : null })} />
            </Field>
          </div>

          <div className="form-row">
            <Field label="Stock" error={editErrors.stockQuantity}>
              <Input type="number" min={0} value={form.stockQuantity} invalid={!!editErrors.stockQuantity}
                onChange={(e) => setForm({ ...form, stockQuantity: Number(e.target.value) })} />
            </Field>
            <Field label="Category">
              <Select value={form.category} onChange={(e) => setForm({ ...form, category: e.target.value })}>
                {CATEGORIES.map((c) => <option key={c} value={c}>{c}</option>)}
              </Select>
            </Field>
          </div>

          <div className="form-row">
            <Field label="Brand">
              <Input value={form.brand} onChange={(e) => setForm({ ...form, brand: e.target.value })} placeholder="e.g. Samsung" />
            </Field>
            <Field label="Location" hint="Where buyers collect or you dispatch from">
              <Input value={form.location} onChange={(e) => setForm({ ...form, location: e.target.value })} placeholder="Kampala, Nakasero" />
            </Field>
          </div>

          <div className="control-row" style={{ borderBottom: 'none', paddingBottom: 0 }}>
            <div>
              <div className="semi tiny">Flash deal</div>
              <p className="tiny muted">Highlights in the flash-deals rail</p>
            </div>
            <Switch checked={form.isFlashDeal} onChange={(v) => setForm((f) => ({ ...f, isFlashDeal: v }))} label="" />
          </div>
          {form.isFlashDeal && (
            <Field label="Discount percent" error={editErrors.discountPercent}>
              <Input type="number" min={0} max={100} value={form.discountPercent} invalid={!!editErrors.discountPercent}
                onChange={(e) => setForm({ ...form, discountPercent: Number(e.target.value) })} />
            </Field>
          )}
        </div>
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
