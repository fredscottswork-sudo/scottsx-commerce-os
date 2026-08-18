import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { Sparkles } from 'lucide-react';
import { sellerService } from '../../api/services';
import { useToast } from '../../store/ToastContext';
import { Btn, Card, Field, Input, PageHeader, Select, TextArea } from '../../components/ui';

const CATEGORIES = ['Electronics', 'Fashion', 'Sports', 'Beauty', 'Home & Living', 'Groceries', 'Automotive', 'Other'];

export default function AddProduct() {
  const { toast } = useToast();
  const navigate = useNavigate();
  const [form, setForm] = useState({
    title: '', description: '', category: CATEGORIES[0], brand: '',
    priceMinor: '', oldPriceMinor: '', stockQuantity: '1', imageUrl: '',
    location: '', isFlashDeal: false, discountPercent: '0',
  });
  const [suggestion, setSuggestion] = useState<{ title: string; description: string; category: string } | null>(null);
  const [busy, setBusy] = useState(false);
  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) =>
    setForm((f) => ({ ...f, [k]: e.target.value }));

  async function aiSuggest() {
    try {
      const r = await sellerService.generateProduct(form.imageUrl, form.title);
      setSuggestion({ title: r.title, description: r.description, category: r.category });
    } catch (e: any) {
      toast(e.message, 'error');
    }
  }

  async function submit(e: FormEvent) {
    e.preventDefault();
    const price = Number(form.priceMinor);
    if (!form.title || !price) return toast('Title and price are required', 'warning');
    setBusy(true);
    try {
      await sellerService.createProduct({
        title: form.title,
        description: form.description,
        category: form.category,
        brand: form.brand,
        priceMinor: price,
        oldPriceMinor: form.oldPriceMinor ? Number(form.oldPriceMinor) : null,
        stockQuantity: Number(form.stockQuantity) || 1,
        imageUrl: form.imageUrl,
        location: form.location,
        isFlashDeal: form.isFlashDeal,
        discountPercent: Number(form.discountPercent) || 0,
      });
      toast('Product published 🎉', 'success');
      navigate('/seller/inventory');
    } catch (err: any) {
      toast(err.message, 'error');
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <PageHeader title="Add product" sub="Published to the shared backend — buyers on web and mobile see it immediately." />
      <div className="grid grid-2">
        <Card>
          <form onSubmit={submit}>
            <Field label="Product name"><Input required value={form.title} onChange={set('title')} placeholder="e.g. Ankara Maxi Dress" /></Field>
            <Field label="Description">
              <TextArea rows={3} value={form.description} onChange={set('description')} placeholder="Condition, size, delivery info…" />
            </Field>
            <div className="form-row">
              <Field label="Category">
                <Select value={form.category} onChange={set('category')}>{CATEGORIES.map((c) => <option key={c}>{c}</option>)}</Select>
              </Field>
              <Field label="Brand"><Input value={form.brand} onChange={set('brand')} placeholder="Optional" /></Field>
            </div>
            <div className="form-row">
              <Field label="Price (UGX)"><Input type="number" min={0} required value={form.priceMinor} onChange={set('priceMinor')} placeholder="85000" /></Field>
              <Field label="Old price (UGX)"><Input type="number" min={0} value={form.oldPriceMinor} onChange={set('oldPriceMinor')} placeholder="Optional" /></Field>
            </div>
            <div className="form-row">
              <Field label="Stock"><Input type="number" min={0} value={form.stockQuantity} onChange={set('stockQuantity')} /></Field>
              <Field label="Location"><Input value={form.location} onChange={set('location')} placeholder="Kampala" /></Field>
            </div>
            <Field label="Image URL">
              <div className="row">
                <Input value={form.imageUrl} onChange={set('imageUrl')} placeholder="https://images.unsplash.com/photo-…" />
                <Btn type="button" size="sm" onClick={aiSuggest} disabled={!form.imageUrl} title="AI suggest from photo"><Sparkles size={15} /> AI</Btn>
              </div>
            </Field>
            <label className="row mb-16" style={{ cursor: 'pointer' }}>
              <input type="checkbox" checked={form.isFlashDeal} onChange={(e) => setForm({ ...form, isFlashDeal: e.target.checked })} style={{ width: 17, height: 17, accentColor: 'var(--primary)' }} />
              Flash deal
            </label>
            {form.isFlashDeal && (
              <Field label="Discount %"><Input type="number" min={1} max={90} value={form.discountPercent} onChange={set('discountPercent')} /></Field>
            )}
            <Btn type="submit" variant="primary" className="btn-block btn-lg" disabled={busy}>{busy ? 'Publishing…' : 'Publish product'}</Btn>
          </form>
        </Card>

        {form.imageUrl && (
          <Card>
            <h3 style={{ marginTop: 0 }} className="mb-8">Preview</h3>
            <img src={form.imageUrl} alt="Preview" style={{ width: '100%', aspectRatio: '1/1', objectFit: 'cover', borderRadius: 12 }} onError={(e) => ((e.target as HTMLImageElement).style.opacity = '0.2')} />
            {suggestion && (
              <div className="mt-16" style={{ background: 'color-mix(in srgb, var(--primary) 8%, transparent)', borderRadius: 12, padding: 12 }}>
                <strong className="row" style={{ color: 'var(--primary)' }}><Sparkles size={15} /> AI suggestion</strong>
                <p style={{ margin: '6px 0' }}><strong>{suggestion.title}</strong> · {suggestion.category}</p>
                <p className="muted" style={{ fontSize: 13 }}>{suggestion.description}</p>
                <div className="row">
                  <Btn size="sm" variant="primary" onClick={() => {
                    setForm((f) => ({ ...f, title: suggestion.title, category: suggestion.category, description: suggestion.description }));
                    setSuggestion(null);
                  }}>Apply</Btn>
                  <Btn size="sm" onClick={() => setSuggestion(null)}>Dismiss</Btn>
                </div>
              </div>
            )}
          </Card>
        )}
      </div>
    </>
  );
}
