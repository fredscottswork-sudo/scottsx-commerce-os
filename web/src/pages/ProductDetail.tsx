import { useEffect, useState } from 'react';
import { Link, useParams, useNavigate, useLocation } from 'react-router-dom';
import { Star, Heart, MessageCircle, ExternalLink } from 'lucide-react';
import { productService, paymentService, chatService, buyerService } from '../api/services';
import type { Product } from '../api/types';
import { formatUgx } from '../api/types';
import { useAuth } from '../store/AuthContext';
import { useToast } from '../store/ToastContext';
import { Btn, Card, ErrorBox, Loading, Modal } from '../components/ui';
import { PRODUCT_IMAGE_FALLBACK } from '../components/ProductCard';

export default function ProductDetail() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const location = useLocation();
  const { user } = useAuth();
  const { toast } = useToast();
  const [product, setProduct] = useState<Product | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [buying, setBuying] = useState(false);
  const [payModal, setPayModal] = useState<{ mode: string; link: string | null; ref: string; status: string } | null>(null);
  const [quantity, setQuantity] = useState(1);

  useEffect(() => {
    let alive = true;
    setLoading(true);
    setError('');
    setProduct(null);
    if (!id) {
      setError('Product not found');
      setLoading(false);
      return () => { alive = false; };
    }
    productService.byId(id)
      .then((r) => { if (alive) setProduct(r.product); })
      .catch((e: any) => { if (alive) setError(e?.message || 'Could not load product'); })
      .finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
  }, [id]);

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
  if (error || !product) return <ErrorBox message={error || 'Product not found'} onRetry={() => window.location.reload()} />;
  const p = product;
  const requestedBack = (location.state as { from?: string } | null)?.from;
  const backTo = requestedBack?.startsWith('/') && !requestedBack.startsWith('//') ? requestedBack : '/';
  const maxQuantity = Math.min(Math.max(p.stockQuantity, 1), 5);
  const soldOut = p.stockQuantity <= 0;

  return (
    <>
      <Link to={backTo} className="muted">← Back to marketplace</Link>
      <div className="grid grid-2 mt-16" style={{ gridTemplateColumns: 'minmax(0, 420px) 1fr' }}>
        <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
          <img
            src={p.imageUrl || PRODUCT_IMAGE_FALLBACK}
            alt={p.title}
            onError={(e) => { e.currentTarget.src = PRODUCT_IMAGE_FALLBACK; }}
            style={{ width: '100%', aspectRatio: '1/1', objectFit: 'cover', display: 'block' }}
          />
        </div>
        <div>
          <div className="row wrap">
            {p.isFlashDeal && <span className="badge badge-red">FLASH -{p.discountPercent}%</span>}
            <span className="badge badge-blue">{p.category}</span>
            {soldOut
              ? <span className="badge badge-red">Sold out</span>
              : p.stockQuantity > 5
                ? <span className="badge badge-green">In stock ({p.stockQuantity})</span>
                : <span className="badge badge-amber">Only {p.stockQuantity} left</span>}
          </div>
          <h1 style={{ margin: '10px 0 6px', fontSize: 26 }}>{p.title}</h1>
          <div className="row muted mb-16">
            <Star size={15} style={{ color: 'var(--warning)' }} /> {p.rating} · {p.ratingCount} ratings · {p.brand}
          </div>
          <div style={{ fontSize: 30, fontWeight: 800, color: 'var(--primary)' }}>
            {formatUgx(p.priceMinor)}
            {p.oldPriceMinor && p.oldPriceMinor > p.priceMinor && (
              <span style={{ fontSize: 16, color: 'var(--text-2)', textDecoration: 'line-through', fontWeight: 400, marginLeft: 10 }}>{formatUgx(p.oldPriceMinor)}</span>
            )}
          </div>
          <p className="muted mt-16">{p.description}</p>

          <Card className="mt-16">
            <div className="row">
              <span className="avatar">
                {p.seller.logoUrl
                  ? <img src={p.seller.logoUrl} alt="" onError={(e) => { e.currentTarget.style.display = 'none'; }} />
                  : (p.seller.name || 'S')[0].toUpperCase()}
              </span>
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
            <select className="select" style={{ width: 70 }} value={quantity} disabled={soldOut}
              onChange={(e) => setQuantity(Number(e.target.value))}>
              {Array.from({ length: maxQuantity }, (_, i) => i + 1).map((n) => (
                <option key={n} value={n}>{n}</option>
              ))}
            </select>
          </div>

          <div className="row mt-16 wrap">
            <Btn variant="primary" size="lg" onClick={buy} disabled={buying || soldOut} style={{ flex: 1, minWidth: 180 }}>
              {soldOut ? 'Sold out' : buying ? 'Creating payment…' : `Buy now · ${formatUgx(p.priceMinor * quantity)}`}
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
