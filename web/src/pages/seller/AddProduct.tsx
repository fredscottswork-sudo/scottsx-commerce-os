import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Sparkles, Save, Send, ImagePlus, CheckCircle2, Info, Wand2, TrendingUp,
} from 'lucide-react';
import { sellerService, aiService } from '../../api/services';
import type { Product } from '../../api/types';
import { formatUgx } from '../../api/types';
import { useToast } from '../../store/ToastContext';
import {
  Btn, Field, Input, TextArea, Select, PageHeader, Switch, Badge, Modal,
} from '../../components/ui';

const CATEGORIES = [
  'Electronics', 'Fashion', 'Home', 'Beauty', 'Sports', 'Automotive',
  'Books', 'Groceries', 'Toys', 'Health', 'Other',
];

interface Form {
  title: string;
  description: string;
  category: string;
  brand: string;
  priceMinor: number;
  oldPriceMinor: number | null;
  stockQuantity: number;
  imageUrl: string;
  location: string;
  isFlashDeal: boolean;
  discountPercent: number;
}

const BLANK: Form = {
  title: '', description: '', category: 'Electronics', brand: '',
  priceMinor: 0, oldPriceMinor: null, stockQuantity: 1, imageUrl: '',
  location: '', isFlashDeal: false, discountPercent: 0,
};

export default function AddProduct() {
  const { toast } = useToast();
  const navigate = useNavigate();

  const [form, setForm] = useState<Form>(BLANK);
  const [errors, setErrors] = useState<Partial<Record<keyof Form, string>>>({});
  const [saving, setSaving] = useState<'draft' | 'submit' | null>(null);
  const [aiBusy, setAiBusy] = useState(false);
  const [aiHint, setAiHint] = useState('');
  const [comparables, setComparables] = useState<Product[]>([]);
  const [done, setDone] = useState<{ title: string; status: string } | null>(null);

  const set = <K extends keyof Form>(k: K, v: Form[K]) => {
    setForm((f) => ({ ...f, [k]: v }));
    if (errors[k]) setErrors((e) => ({ ...e, [k]: undefined }));
  };

  const validate = (forPublish: boolean) => {
    const e: Partial<Record<keyof Form, string>> = {};
    if (form.title.trim().length < 3) e.title = 'Give the product a clear title (3+ characters)';
    if (forPublish) {
      if (!form.priceMinor || form.priceMinor <= 0) e.priceMinor = 'Set a price above zero';
      if (!/^https?:\/\//i.test(form.imageUrl.trim())) e.imageUrl = 'A public http(s) image link is required to publish';
    }
    setErrors(e);
    return Object.keys(e).length === 0;
  };

  // ── AI: fill title/description/price from an image + hint ───────────────
  const runAi = async () => {
    if (!form.imageUrl.trim() && !aiHint.trim()) {
      toast('Add an image link or a short hint first', 'warning');
      return;
    }
    setAiBusy(true);
    try {
      const r = await aiService.generateProduct({
        imageUrl: form.imageUrl.trim() || undefined,
        hint: aiHint.trim() || form.title.trim() || undefined,
      });
      setForm((f) => ({
        ...f,
        title: r.title || f.title,
        description: r.description || f.description,
        category: r.category || f.category,
        brand: r.brand || f.brand,
        priceMinor: r.suggestedPriceMinor || f.priceMinor,
      }));
      setComparables(r.comparables || []);
      toast(
        r.comparables?.length
          ? `Filled in from ${r.comparables.length} similar listing${r.comparables.length > 1 ? 's' : ''}`
          : 'Draft copy generated',
        'success'
      );
    } catch (e: any) {
      toast(e?.message || 'AI could not generate this listing', 'error');
    } finally {
      setAiBusy(false);
    }
  };

  const save = async (mode: 'draft' | 'submit') => {
    if (!validate(mode === 'submit')) {
      toast('Fix the highlighted fields', 'warning');
      return;
    }
    setSaving(mode);
    try {
      const r = await sellerService.createProduct({
        ...form,
        oldPriceMinor: form.oldPriceMinor || null,
        asDraft: mode === 'draft',
      });
      setDone({ title: r.product.title, status: r.product.status || 'pending' });
    } catch (e: any) {
      toast(e?.message || 'Could not save the product', 'error');
    } finally {
      setSaving(null);
    }
  };

  const median = comparables.length
    ? [...comparables].sort((a, b) => a.priceMinor - b.priceMinor)[Math.floor(comparables.length / 2)].priceMinor
    : 0;

  return (
    <>
      <PageHeader
        title="Add a product"
        sub="Fill it in yourself or let the AI draft it. Submitted listings go live once an admin approves them."
      />

      <div className="checkout-layout">
        {/* ── Form ────────────────────────────────────────────────── */}
        <div className="col-lg">
          <section className="card">
            <h2 className="card-title mb-12"><ImagePlus size={17} /> Photo</h2>
            <Field label="Image URL" error={errors.imageUrl} required
              hint="Paste a public link to a clear photo. Listings without a real photo cannot be published.">
              <Input value={form.imageUrl} invalid={!!errors.imageUrl}
                onChange={(e) => set('imageUrl', e.target.value)} placeholder="https://…/product.jpg" />
            </Field>
            {form.imageUrl && (
              <img src={form.imageUrl} alt="Preview" className="img-preview"
                onError={(e) => { (e.currentTarget as HTMLImageElement).style.opacity = '0.25'; }} />
            )}
          </section>

          <section className="card ai-assist">
            <div className="card-head">
              <h2 className="card-title"><Wand2 size={17} /> AI listing assistant</h2>
              <Badge tone="violet">Store-aware</Badge>
            </div>
            <p className="tiny muted mb-12">
              Describe the item in a few words. The assistant writes the title and description, picks a category,
              and suggests a price based on what similar products in this store actually sell for.
            </p>
            <div className="row wrap" style={{ gap: 9 }}>
              <Input className="grow" value={aiHint} onChange={(e) => setAiHint(e.target.value)}
                placeholder="e.g. Samsung 55 inch 4K smart TV, brand new, warranty"
                onKeyDown={(e) => { if (e.key === 'Enter') void runAi(); }} />
              <Btn variant="primary" loading={aiBusy} icon={<Sparkles size={15} />} onClick={runAi}>
                Generate
              </Btn>
            </div>
          </section>

          <section className="card">
            <h2 className="card-title mb-12"><Info size={17} /> Details</h2>
            <Field label="Title" error={errors.title} required>
              <Input value={form.title} invalid={!!errors.title}
                onChange={(e) => set('title', e.target.value)} placeholder="Samsung Galaxy A55 128GB — Awesome Navy" />
            </Field>
            <Field label="Description" hint="What buyers get, condition, warranty, what's in the box.">
              <TextArea rows={5} value={form.description} onChange={(e) => set('description', e.target.value)} />
            </Field>
            <div className="form-row">
              <Field label="Category">
                <Select value={form.category} onChange={(e) => set('category', e.target.value)}>
                  {CATEGORIES.map((c) => <option key={c} value={c}>{c}</option>)}
                </Select>
              </Field>
              <Field label="Brand">
                <Input value={form.brand} onChange={(e) => set('brand', e.target.value)} placeholder="Samsung" />
              </Field>
            </div>
            <Field label="Pickup location" hint="Where buyers collect or where you dispatch from.">
              <Input value={form.location} onChange={(e) => set('location', e.target.value)} placeholder="Kampala, Nakasero" />
            </Field>
          </section>

          <section className="card">
            <h2 className="card-title mb-12"><TrendingUp size={17} /> Price & stock</h2>
            <div className="form-row">
              <Field label="Price (UGX)" error={errors.priceMinor} required>
                <Input type="number" min={0} value={form.priceMinor || ''} invalid={!!errors.priceMinor}
                  onChange={(e) => set('priceMinor', Number(e.target.value))} placeholder="1650000" />
              </Field>
              <Field label="Was price (UGX)" hint="Optional — shows a struck-through original.">
                <Input type="number" min={0} value={form.oldPriceMinor ?? ''}
                  onChange={(e) => set('oldPriceMinor', e.target.value ? Number(e.target.value) : null)} />
              </Field>
            </div>
            <Field label="Stock quantity">
              <Input type="number" min={0} value={form.stockQuantity}
                onChange={(e) => set('stockQuantity', Number(e.target.value))} />
            </Field>

            <div className="control-row" style={{ borderBottom: 'none' }}>
              <div>
                <div className="semi tiny">Flash deal</div>
                <p className="tiny muted">Highlights the listing in the flash-deals rail.</p>
              </div>
              <Switch checked={form.isFlashDeal} onChange={(v) => set('isFlashDeal', v)} label="" />
            </div>
            {form.isFlashDeal && (
              <Field label="Discount percent">
                <Input type="number" min={0} max={100} value={form.discountPercent}
                  onChange={(e) => set('discountPercent', Number(e.target.value))} />
              </Field>
            )}
          </section>
        </div>

        {/* ── Side: preview, comparables, actions ─────────────────── */}
        <aside className="col-lg">
          <div className="card checkout-summary">
            <h3 className="card-title mb-12">Publish</h3>
            <p className="tiny muted mb-12">
              <CheckCircle2 size={12} style={{ verticalAlign: -2 }} className="t-success" />{' '}
              Submitting sends this to the admin approval queue. You'll get a notification when it goes live —
              and everyone following your store gets a push alert.
            </p>
            <Btn variant="primary" size="lg" className="w-full" loading={saving === 'submit'}
              icon={<Send size={16} />} onClick={() => save('submit')}>
              Submit for approval
            </Btn>
            <Btn className="w-full mt-8" loading={saving === 'draft'} icon={<Save size={15} />}
              onClick={() => save('draft')}>
              Save as draft
            </Btn>
            <p className="tiny muted-2 mt-12">Drafts stay private and skip validation — finish them anytime.</p>
          </div>

          {form.title && (
            <div className="card">
              <h3 className="card-title mb-12">Buyer preview</h3>
              <div className="pcard" style={{ maxWidth: 220, pointerEvents: 'none' }}>
                <div className="pcard-media">
                  {form.imageUrl
                    ? <img className="pcard-img" src={form.imageUrl} alt="" />
                    : <div className="skeleton" style={{ width: '100%', height: '100%' }} />}
                  {form.isFlashDeal && (
                    <div className="pcard-tags"><span className="badge badge-red">FLASH −{form.discountPercent}%</span></div>
                  )}
                </div>
                <div className="pcard-body">
                  <p className="pcard-title">{form.title}</p>
                  <div className="pcard-price-row">
                    <span className="pcard-price">{formatUgx(form.priceMinor || 0)}</span>
                    {!!form.oldPriceMinor && <span className="pcard-old">{formatUgx(form.oldPriceMinor)}</span>}
                  </div>
                </div>
              </div>
            </div>
          )}

          {comparables.length > 0 && (
            <div className="card">
              <h3 className="card-title mb-12"><TrendingUp size={16} /> Similar in this store</h3>
              <p className="tiny muted mb-12">Median price {formatUgx(median)} — price near this to stay competitive.</p>
              <div className="col">
                {comparables.slice(0, 5).map((c) => (
                  <div key={c.id} className="row-between tiny" style={{ padding: '5px 0' }}>
                    <span className="ellipsis" style={{ maxWidth: 170 }}>{c.title}</span>
                    <span className="semi">{formatUgx(c.priceMinor)}</span>
                  </div>
                ))}
              </div>
            </div>
          )}
        </aside>
      </div>

      <Modal
        open={!!done}
        onClose={() => navigate('/seller/inventory')}
        title={done?.status === 'draft' ? 'Draft saved' : 'Sent for approval'}
        footer={
          <>
            <Btn onClick={() => { setDone(null); setForm(BLANK); setComparables([]); setAiHint(''); }}>
              Add another
            </Btn>
            <Btn variant="primary" onClick={() => navigate('/seller/inventory')}>Go to inventory</Btn>
          </>
        }
      >
        <div className="center mb-16"><div className="success-ring"><CheckCircle2 size={34} /></div></div>
        <p className="center semi">{done?.title}</p>
        <p className="center muted tiny mt-4">
          {done?.status === 'draft'
            ? 'Saved privately. Submit it from your inventory when it is ready.'
            : 'An admin will review it shortly. You will be notified the moment it goes live.'}
        </p>
      </Modal>
    </>
  );
}
