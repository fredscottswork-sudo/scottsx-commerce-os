import { useEffect, useState } from 'react';
import { Link, useParams, useNavigate } from 'react-router-dom';
import { Star, Heart, MessageCircle, ExternalLink, ShoppingCart, Store, ShieldCheck, Package, BadgeCheck } from 'lucide-react';
import { productService, paymentService, chatService } from '../api/services';
import type { Product } from '../api/types';
import { formatUgx } from '../api/types';
import { useAuth } from '../store/AuthContext';
import { useToast } from '../store/ToastContext';
import { useCart } from '../store/CartContext';
import { Btn, Card, ErrorBox, Loading, Modal, Badge } from '../components/ui';
import { useSeo } from '../hooks/useSeo';
import { IMAGE_FALLBACK, ProductGrid } from '../components/ProductCard';
import { resolveMediaUrl } from '../api/client';

export default function ProductDetail() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { user } = useAuth();
  const { toast } = useToast();
  const { add, savedIds, toggleSaved } = useCart();
  const [product, setProduct] = useState<Product | null>(null);
  const [related, setRelated] = useState<Product[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [buying, setBuying] = useState(false);
  const [payModal, setPayModal] = useState<{ mode: string; link: string | null; ref: string; status: string } | null>(null);
  const [quantity, setQuantity] = useState(1);

  useEffect(() => {
    setLoading(true);
    setError('');
    productService.byId(id!)
      .then((r) => {
        setProduct(r.product);
        setQuantity(1);
        // related
        productService.related(r.product.id).then((rel) => setRelated(rel.products)).catch(() => undefined);
      })
      .catch((e: any) => setError(e.message))
      .finally(() => setLoading(false));
  }, [id]);

  useSeo({
    title: product ? product.title : undefined,
    description: product
      ? `${formatUgx(product.priceMinor)} — ${product.description || product.title}. Available from ${product.seller?.name || 'a verified seller'} on ScottsTechX.`
      : undefined,
    image: product?.imageUrl ? resolveMediaUrl(product.imageUrl) : undefined,
    type: 'product',
  });

  async function buy() {
    if (!user) { toast('Please sign in to buy — we saved this product for you', 'warning'); navigate('/login', { state: { from: `/product/${id}` } }); return; }
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
    if (!user) { toast('Sign in to message this seller', 'warning'); navigate('/login', { state: { from: `/product/${id}` } }); return; }
    try {
      const r = await chatService.open(product!.seller.id, product!.id);
      navigate(`/messages/${r.conversation.id}`);
    } catch (e: any) {
      toast(e.message || 'Could not open chat', 'error');
    }
  }

  async function handleToggleSaved() {
    if (!user) { toast('Sign in to save products', 'warning'); navigate('/login', { state: { from: `/product/${id}` } }); return; }
    try {
      await toggleSaved(product!.id);
    } catch (e: any) {
      toast(e.message, 'error');
    }
  }

  if (loading) return <Loading />;
  if (error || !product) return <ErrorBox message={error} onRetry={() => window.location.reload()} />;
  const p = product;
  const isSaved = savedIds.has(p.id);

  return (
    <>
      <Link to="/" className="muted">← Back to marketplace</Link>
      <div className="product-detail mt-16">
        <div className="card product-gallery">
          <img
            className="product-hero-img"
            src={p.imageUrl ? resolveMediaUrl(p.imageUrl) : IMAGE_FALLBACK}
            alt={p.title}
            onError={(e) => { (e.currentTarget as HTMLImageElement).src = IMAGE_FALLBACK; }}
          />
        </div>
        <div className="product-info">
          <div className="row wrap">
            {p.isFlashDeal && <span className="badge badge-red">FLASH -{p.discountPercent}%</span>}
            <span className="badge badge-blue">{p.category}</span>
            {p.stockQuantity > 5 ? <span className="badge badge-green">In stock ({p.stockQuantity})</span> : <span className="badge badge-amber">Only {p.stockQuantity} left</span>}
            {p.seller.verified && <span className="badge badge-green"><ShieldCheck size={11} /> Verified seller</span>}
          </div>
          <h1 className="product-title" style={{ margin: '10px 0 6px', fontSize: 26 }}>{p.title}</h1>
          <div className="row muted mb-16" style={{ gap: 6 }}>
            <span className="row" style={{ gap: 4 }}><Star size={15} style={{ color: 'var(--warning)' }} fill="currentColor" /> {Number(p.rating || 0).toFixed(1)}</span>
            <span>· {p.ratingCount || 0} ratings</span>
            {p.brand && <><span>·</span><span>{p.brand}</span></>}
          </div>
          <div className="product-price-row">
            <span className="product-price" style={{ fontSize: 30, fontWeight: 800, color: 'var(--primary)' }}>
              {formatUgx(p.priceMinor)}
            </span>
            {p.oldPriceMinor && p.oldPriceMinor > p.priceMinor && (
              <span className="product-price-old">{formatUgx(p.oldPriceMinor)}</span>
            )}
          </div>
          <p className="muted mt-16" style={{ whiteSpace: 'pre-wrap' }}>{p.description || 'No description provided.'}</p>

          <Card className="mt-16">
            <div className="row" style={{ gap: 12 }}>
              <span className="avatar">{p.seller.name?.[0]?.toUpperCase() || 'S'}</span>
              <div className="grow" style={{ minWidth: 0 }}>
                <div className="row" style={{ gap: 6 }}>
                  <strong className="ellipsis">{p.seller.name}</strong>
                  {p.seller.verified && <BadgeCheck size={14} className="t-success" />}
                </div>
                <span className="muted tiny">{p.seller.location || 'Uganda'}</span>
              </div>
              <Link to={`/seller/${p.seller.id}`}><Btn size="sm" icon={<Store size={14} />}>Store</Btn></Link>
            </div>
          </Card>

          <div className="row mt-16 wrap" style={{ gap: 10 }}>
            <label className="muted tiny semi">Qty</label>
            <select className="select" style={{ width: 84 }} value={quantity} onChange={(e) => setQuantity(Number(e.target.value))}>
              {Array.from({ length: Math.min(10, Math.max(1, p.stockQuantity)) }, (_, i) => i + 1).map((n) => <option key={n} value={n}>{n}</option>)}
            </select>
            <span className="tiny muted">{p.stockQuantity} available</span>
          </div>

          <div className="row mt-16 wrap product-actions" style={{ gap: 8 }}>
            <Btn variant="primary" size="lg" onClick={buy} disabled={buying || p.stockQuantity === 0} style={{ flex: '1 1 180px' }} icon={<ShoppingCart size={16} />}>
              {buying ? 'Creating payment…' : p.stockQuantity === 0 ? 'Out of stock' : `Buy now · ${formatUgx(p.priceMinor * quantity)}`}
            </Btn>
            <Btn size="lg" variant="outline" onClick={() => void add(p, quantity)} icon={<Package size={16} />}>Add to cart</Btn>
            <Btn size="lg" onClick={messageSeller} icon={<MessageCircle size={16} />}>Message</Btn>
            <Btn size="lg" variant={isSaved ? 'primary' : 'default'} onClick={handleToggleSaved} aria-label="Save" icon={<Heart size={16} fill={isSaved ? 'currentColor' : 'none'} />}>
              {isSaved ? 'Saved' : 'Save'}
            </Btn>
          </div>
        </div>
      </div>

      {related.length > 0 && (
        <section className="mt-24">
          <h2 className="mb-12" style={{ fontSize: 18 }}>Related products</h2>
          <ProductGrid products={related} onAddToCart={(prod) => void add(prod)} favoriteSellerIds={new Set()} />
        </section>
      )}

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
            <span className="muted tiny">Status: {payModal.status} · Ref: {payModal.ref.slice(0, 8)}</span>
          </p>
        ) : (
          <p>Open your secure payment link to complete the purchase. <br />
            <a href={payModal?.link ?? '#'} target="_blank" rel="noreferrer">{payModal?.link}</a></p>
        )}
      </Modal>
    </>
  );
}
