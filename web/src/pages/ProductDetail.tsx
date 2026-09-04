import { useEffect, useState } from 'react';
import { Link, useParams, useNavigate } from 'react-router-dom';
import { Star, Heart, MessageCircle, ShoppingCart, Store, ShieldCheck, BadgeCheck } from 'lucide-react';
import { productService, chatService } from '../api/services';
import type { Product } from '../api/types';
import { formatUgx } from '../api/types';
import { useAuth } from '../store/AuthContext';
import { useToast } from '../store/ToastContext';
import { useCart } from '../store/CartContext';
import { Btn, Card, ErrorBox, Loading, Badge } from '../components/ui';
import { useSeo } from '../hooks/useSeo';
import { IMAGE_FALLBACK, ProductGrid } from '../components/ProductCard';
import { resolveMediaUrl } from '../api/client';

/** Seller avatar with a graceful fallback to the store initial when the logo
 *  is missing or fails to load (broken link, deleted upload, offline host). */
function SellerAvatar({ name, logoUrl }: { name: string; logoUrl?: string | null }) {
  const [failed, setFailed] = useState(false);
  const showImg = !!logoUrl && !failed;
  return (
    <span className="avatar" style={{ width: 48, height: 48, fontSize: 18 }}>
      {showImg
        ? <img src={resolveMediaUrl(logoUrl!)} alt={`${name} logo`} onError={() => setFailed(true)} />
        : name?.[0]?.toUpperCase() || 'S'}
    </span>
  );
}

export default function ProductDetail() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { user } = useAuth();
  const { toast } = useToast();
  const { add, savedIds, toggleSaved, favoriteSellerIds, toggleFavoriteSeller } = useCart();
  const [product, setProduct] = useState<Product | null>(null);
  const [related, setRelated] = useState<Product[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
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

  function addToInquiry() {
    const target = product;
    if (target && target.stockQuantity > 0) {
      add(target, quantity);
      toast(`Added to inquiry — ${quantity} × ${target.title}`, 'success');
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

  function handleToggleFollowSeller() {
    if (!user) { toast('Sign in to follow this store', 'warning'); navigate('/login', { state: { from: `/product/${id}` } }); return; }
    if (product) void toggleFavoriteSeller(product.seller.id, product.seller.name);
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
              <SellerAvatar name={p.seller.name} logoUrl={p.seller.logoUrl} />
              <div className="grow" style={{ minWidth: 0 }}>
                <div className="row" style={{ gap: 6 }}>
                  <strong className="ellipsis">{p.seller.name}</strong>
                  {p.seller.verified && <BadgeCheck size={14} className="t-success" />}
                </div>
                <span className="muted tiny">{p.seller.location || 'Uganda'}</span>
              </div>
              <div className="row" style={{ gap: 6 }}>
                <Btn
                  size="sm"
                  variant={favoriteSellerIds.has(p.seller.id) ? 'primary' : 'outline'}
                  onClick={handleToggleFollowSeller}
                  icon={<Heart size={14} fill={favoriteSellerIds.has(p.seller.id) ? 'currentColor' : 'none'} />}
                  aria-label={favoriteSellerIds.has(p.seller.id) ? `Unfollow ${p.seller.name}` : `Follow ${p.seller.name}`}
                >
                  {favoriteSellerIds.has(p.seller.id) ? 'Following' : 'Follow'}
                </Btn>
                <Link to={`/seller/${p.seller.id}`}><Btn size="sm" icon={<Store size={14} />}>Store</Btn></Link>
              </div>
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
            <Btn variant="primary" size="lg" onClick={messageSeller} style={{ flex: '1 1 180px' }} icon={<MessageCircle size={16} />}>
              Chat with seller
            </Btn>
            <Btn size="lg" variant="outline" onClick={addToInquiry} disabled={p.stockQuantity === 0} icon={<ShoppingCart size={16} />}>
              Add to inquiry
            </Btn>
            <Btn size="lg" variant={isSaved ? 'primary' : 'default'} onClick={handleToggleSaved} aria-label="Save" icon={<Heart size={16} fill={isSaved ? 'currentColor' : 'none'} />}>
              {isSaved ? 'Saved' : 'Save'}
            </Btn>
          </div>

          <p className="tiny muted-2 mt-12 row" style={{ gap: 7 }}>
            <ShieldCheck size={14} className="t-success" />
            Payment is agreed with the seller — message them to arrange delivery (cash on delivery, bank or collect).
          </p>
        </div>
      </div>

      {related.length > 0 && (
        <section className="mt-24">
          <h2 className="mb-12" style={{ fontSize: 18 }}>Related products</h2>
          <ProductGrid products={related} onAddToCart={(prod) => void add(prod)} favoriteSellerIds={new Set()} />
        </section>
      )}
    </>
  );
}
