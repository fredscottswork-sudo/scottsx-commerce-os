import { useEffect, useState } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { BadgeCheck, MapPin, Package, Star } from 'lucide-react';
import { productService, chatService } from '../api/services';
import type { Product } from '../api/types';
import { formatUgx } from '../api/types';
import { useAuth } from '../store/AuthContext';
import { useToast } from '../store/ToastContext';
import { useCart } from '../store/CartContext';
import { ProductCard } from '../components/ProductCard';
import { Btn, Empty, ErrorBox, Loading } from '../components/ui';

export default function SellerStorefront() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { user } = useAuth();
  const { toast } = useToast();
  const { add, favoriteSellerIds, toggleFavoriteSeller } = useCart();
  const [seller, setSeller] = useState<any>(null);
  const [products, setProducts] = useState<Product[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let alive = true;
    setLoading(true);
    setError('');
    setSeller(null);
    setProducts([]);
    if (!id) {
      setError('Seller not found');
      setLoading(false);
      return () => { alive = false; };
    }
    productService.sellerPublic(id)
      .then((r) => {
        if (!alive) return;
        setSeller(r.seller);
        setProducts(r.products);
      })
      .catch((e: any) => { if (alive) setError(e?.message || 'Could not load store'); })
      .finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
  }, [id]);

  async function chat() {
    if (!user) { navigate('/login'); return; }
    try {
      const r = await chatService.open(seller.id);
      navigate(`/messages/${r.conversation.id}`);
    } catch (e: any) {
      toast(e?.message || 'Could not open the store chat', 'error');
    }
  }

  if (loading) return <Loading />;
  if (error || !seller) return <ErrorBox message={error} onRetry={() => window.location.reload()} />;

  return (
    <>
      <div className="card card-pad mb-16">
        <div className="row-between wrap">
          <div className="row">
            <span className="avatar" style={{ width: 56, height: 56, fontSize: 22 }}>
              {seller.logoUrl
                ? <img src={seller.logoUrl} alt="" onError={(e) => { e.currentTarget.style.display = 'none'; }} />
                : (seller.name || 'S')[0].toUpperCase()}
            </span>
            <div>
              <h1 style={{ margin: 0, fontSize: 24 }}>
                {seller.name} {seller.verified && <BadgeCheck size={20} style={{ verticalAlign: -3, color: 'var(--success)' }} />}
              </h1>
              <div className="muted row wrap" style={{ gap: 12 }}>
                <span className="row"><Star size={14} style={{ color: 'var(--warning)' }} /> {seller.rating} rating</span>
                <span className="row"><MapPin size={14} /> {seller.city || 'Uganda'}</span>
                <span>{products.length} products</span>
              </div>
            </div>
          </div>
          <Btn variant="primary" onClick={chat}>Message store</Btn>
        </div>
        {seller.description && <p className="muted mt-16" style={{ marginBottom: 0 }}>{seller.description}</p>}
      </div>

      {products.length === 0 ? (
        <Empty icon={<Package size={28} />} title="No products yet" />
      ) : (
        <>
          <h2 className="mb-16">Products ({products.length})</h2>
          <div className="pgrid">
            {products.map((p) => (
              <ProductCard
                key={p.id}
                product={p}
                onAddToCart={(product) => void add(product)}
                onToggleFavorite={(product) => {
                  if (product.seller?.id) void toggleFavoriteSeller(product.seller.id, product.seller.name);
                }}
                isFavorite={favoriteSellerIds.has(p.seller?.id ?? seller.id)}
              />
            ))}
          </div>
        </>
      )}
    </>
  );
}
