import { useEffect, useState } from 'react';
import { Link, useParams, useNavigate } from 'react-router-dom';
import { Star, Heart, MessageCircle, ExternalLink } from 'lucide-react';
import { productService, paymentService, chatService, buyerService } from '../api/services';
import type { Product } from '../api/types';
import { formatUgx } from '../api/types';
import { useAuth } from '../store/AuthContext';
import { useToast } from '../store/ToastContext';
import { Btn, Card, ErrorBox, Loading, Modal } from '../components/ui';
import { useSeo } from '../hooks/useSeo';
import { IMAGE_FALLBACK } from '../components/ProductCard';
import { resolveMediaUrl } from '../api/client';

export default function ProductDetail() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { user } = useAuth();
  const { toast } = useToast();
  const [product, setProduct] = useState<Product | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [buying, setBuying] = useState(false);
  const [payModal, setPayModal] = useState<{ mode: string; link: string | null; ref: string; status: string } | null>(null);
  const [quantity, setQuantity] = useState(1);

  useEffect(() => {
    setLoading(true);
    setError('');
    productService.byId(id!).then((r) => setProduct(r.product)).catch((e: any) => setError(e.message)).finally(() => setLoading(false));
  }, [id]);

  // A shared listing should show what is for sale and what it costs. Falling
  // back to the site defaults while loading avoids flashing an empty title.
  useSeo({
    title: product ? product.title : undefined,
    description: product
      ? `${formatUgx(product.priceMinor)} — ${product.description || product.title}. ` +
        `Available from ${product.seller?.name || 'a verified seller'} on ScottsTechX.`
      : undefined,
    image: product?.imageUrl ? resolveMediaUrl(product.imageUrl) : undefined,
    type: 'product',
  });

  async function buy() {
    if (!user) { navigate('/login'); return; }
    setBuying(true);
    try {
      const r = await paymentService.checkout(id!, quantity, user.phone);
      setPayModal({ mode: r.paymentMode, link: r.paymentLink, ref: r.paymentReference, status: r.status });
      toast(r.paymentMode === 'collect' ? 'Payment request sent to your phone' : 'Payment link ready', 'success');
    } catch (e: any) {
      toast(e.message || 'Could not create payment', 'error');
    } finally {
      setBuying(false);
    }
  }

  async function messageSeller() {
    if (!user) { navigate('/login'); return; }
    try {
      const r = await chatService.open(product!.seller.id, product!.id);
      navigate(`/messages/${r.conversation.id}`);
    } catch (e: any) {
      toast(e.message || 'Could not open chat', 'error');
    }
  }

  async function toggleSaved() {
    if (!user) { navigate('/login'); return; }
    try {
      await buyerService.toggleBookmark(product!.id);
      toast('Saved updated', 'success');
    } catch (e: any) {
      toast(e.message, 'error');
    }
  }

  if (loading) return <Loading />;
  if (error || !product) return <ErrorBox message={error} onRetry={() => window.location.reload()} />;
  const p = product;

  return (
    <>
      <Link to="/" className="muted">← Back to marketplace</Link>
      {/* The columns MUST come from the stylesheet, not an inline style. This
          was `style={{gridTemplateColumns:'minmax(0,420px) 1fr'}}`, and an
          inline declaration outranks a media query, so the phone rule that
          stacks .grid-2 into one column could never apply. The image track
          collapsed to 31px at 360px wide and the title wrapped one letter per
          line — the "weird vertical page". .product-detail stacks below 900px
          and only becomes two columns above it. */}
      <div className="product-detail mt-16">
        <div className="card product-gallery">
          <img
            className="product-hero-img"
            src={p.imageUrl || IMAGE_FALLBACK}
            alt={p.title}
            /* A blocked or dead image host otherwise leaves a large empty
               panel at the top of the page. */
            onError={(e) => { (e.currentTarget as HTMLImageElement).src = IMAGE_FALLBACK; }}
          />
        </div>
        <div className="product-info">
          <div className="row wrap">
            {p.isFlashDeal && <span className="badge badge-red">FLASH -{p.discountPercent}%</span>}
            <span className="badge badge-blue">{p.category}</span>
            {p.stockQuantity > 5 ? <span className="badge badge-green">In stock ({p.stockQuantity})</span> : <span className="badge badge-amber">Only {p.stockQuantity} left</span>}
          </div>
          <h1 className="product-title" style={{ margin: '10px 0 6px', fontSize: 26 }}>{p.title}</h1>
          <div className="row muted mb-16">
            <Star size={15} style={{ color: 'var(--warning)' }} /> {p.rating} · {p.ratingCount} ratings · {p.brand}
          </div>
          {/* Each amount is its own inline-block so "UGX 4,800,000" can never
              be split across two lines — the old price used to break after
              "UGX", leaving a stray strikethrough hanging beside the new one. */}
          <div className="product-price-row">
            <span className="product-price" style={{ fontSize: 30, fontWeight: 800, color: 'var(--primary)' }}>
              {formatUgx(p.priceMinor)}
            </span>
            {p.oldPriceMinor && p.oldPriceMinor > p.priceMinor && (
              <span className="product-price-old">{formatUgx(p.oldPriceMinor)}</span>
            )}
          </div>
          <p className="muted mt-16">{p.description}</p>

          <Card className="mt-16">
            <div className="row">
              <span className="avatar">{p.seller.name[0]}</span>
              <div className="grow">
                <div className="row">
                  <strong>{p.seller.name}</strong>
                  {p.seller.verified && <span className="badge badge-green">✓ Verified</span>}
                </div>
                <span className="muted" style={{ fontSize: 13 }}>{p.seller.location || 'Uganda'}</span>
              </div>
              <Link to={`/seller/${p.seller.id}`}><Btn size="sm">Store</Btn></Link>
            </div>
          </Card>

          <div className="row mt-16">
            <label className="muted" style={{ fontSize: 13 }}>Qty</label>
            <select className="select" style={{ width: 70 }} value={quantity} onChange={(e) => setQuantity(Number(e.target.value))}>
              {[1, 2, 3, 4, 5].map((n) => <option key={n} value={n}>{n}</option>)}
            </select>
          </div>

          <div className="row mt-16 wrap product-actions">
            <Btn variant="primary" size="lg" onClick={buy} disabled={buying} style={{ flex: 1, minWidth: 180 }}>
              {buying ? 'Creating payment…' : `Buy now · ${formatUgx(p.priceMinor * quantity)}`}
            </Btn>
            <Btn size="lg" onClick={messageSeller}><MessageCircle size={18} /> Message seller</Btn>
            <Btn size="lg" onClick={toggleSaved} aria-label="Save"><Heart size={18} /></Btn>
          </div>
        </div>
      </div>

      <Modal open={payModal !== null} onClose={() => setPayModal(null)} title={payModal?.mode === 'collect' ? 'Payment initiated 📲' : 'Payment ready 🎉'}
        footer={
          <>
            {payModal?.mode === 'collect'
              ? <Btn variant="primary" onClick={() => setPayModal(null)}>OK</Btn>
              : <Btn variant="primary" onClick={() => { if (payModal?.link) window.open(payModal.link, '_blank'); }}>
                  <ExternalLink size={16} /> Open payment page
                </Btn>}
          </>
        }
      >
        {payModal?.mode === 'collect' ? (
          <p>
            A Mobile Money request for <strong>{formatUgx(p.priceMinor * quantity)}</strong> was sent to your phone
            (MTN MoMo / Airtel Money). Enter your PIN to approve. <br />
            <span className="muted">Status: {payModal.status} · Ref: {payModal.ref.slice(0, 8)}</span>
          </p>
        ) : (
          <p>Open your secure payment link to complete the purchase. <br />
            <a href={payModal?.link ?? '#'} target="_blank" rel="noreferrer">{payModal?.link}</a></p>
        )}
      </Modal>
    </>
  );
}
