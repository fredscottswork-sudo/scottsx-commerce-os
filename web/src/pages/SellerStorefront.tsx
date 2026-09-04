import { useEffect, useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { BadgeCheck, MapPin, Package, Star, Heart } from 'lucide-react';
import { productService, chatService } from '../api/services';
import type { Product } from '../api/types';
import { formatUgx } from '../api/types';
import { useAuth } from '../store/AuthContext';
import { useSeo } from '../hooks/useSeo';
import { useToast } from '../store/ToastContext';
import { useCart } from '../store/CartContext';
import { ProductCard } from '../components/ProductCard';
import { Btn, Empty, ErrorBox, Loading } from '../components/ui';

export default function SellerStorefront() {
  const { id } = useParams<{ id: string }>();
  const nav = useNavigate();
  const { user } = useAuth();
  const { toast } = useToast();
  const { favoriteSellerIds, toggleFavoriteSeller } = useCart();
  const [seller, setSeller] = useState<any>(null);
  const [products, setProducts] = useState<Product[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useSeo({
    title: seller?.name ? `${seller.name} — store` : undefined,
    description: seller
      ? `Shop from ${seller.name}` +
        `${seller.city ? ` in ${seller.city}` : ''} on ScottsTechX. ` +
        `${seller.verified ? 'Verified seller. ' : ''}Message the seller and buy with cash on delivery.`
      : undefined,
    image: seller?.logoUrl || undefined,
    type: 'profile',
  });

  useEffect(() => {
    setLoading(true);
    setError('');
    productService.sellerPublic(id!)
      .then((r) => { setSeller(r.seller); setProducts(r.products); })
      .catch((e: any) => setError(e.message))
      .finally(() => setLoading(false));
  }, [id]);

  async function chat() {
    if (!user) { nav('/login'); return; }
    try {
      const r = await chatService.open(seller.id);
      nav(`/messages/${r.conversation.id}`);
    } catch (e: any) {
      toast(e.message, 'error');
    }
  }

  function toggleFollow() {
    if (!user) { nav('/login'); return; }
    void toggleFavoriteSeller(seller.id, seller.name);
  }

  if (loading) return <Loading />;
  if (error || !seller) return <ErrorBox message={error} onRetry={() => window.location.reload()} />;

  return (
    <>
      <div className="card card-pad mb-16">
        <div className="row-between wrap">
          <div className="row">
            <span className="avatar" style={{ width: 56, height: 56, fontSize: 22 }}>
              {seller.logoUrl ? <img src={seller.logoUrl} alt="" /> : seller.name[0]}
            </span>
            <div>
              <h1 style={{ margin: 0, fontSize: 24 }}>
                {seller.name} {seller.verified && <BadgeCheck size={20} style={{ verticalAlign: -3, color: 'var(--success)' }} />}
              </h1>
              <div className="muted row wrap" style={{ gap: 12 }}>
                <span className="row"><Star size={14} style={{ color: 'var(--warning)' }} /> {seller.rating} rating</span>
                <span className="row"><MapPin size={14} /> {seller.city || 'Uganda'}</span>
              </div>
            </div>
          </div>
          <div className="row" style={{ gap: 8 }}>
            <Btn
              variant={favoriteSellerIds.has(seller.id) ? 'primary' : 'outline'}
              onClick={toggleFollow}
              icon={<Heart size={16} fill={favoriteSellerIds.has(seller.id) ? 'currentColor' : 'none'} />}
              aria-label={favoriteSellerIds.has(seller.id) ? `Unfollow ${seller.name}` : `Follow ${seller.name}`}
            >
              {favoriteSellerIds.has(seller.id) ? 'Following' : 'Follow'}
            </Btn>
            <Btn variant="primary" onClick={chat}>Message store</Btn>
          </div>
        </div>
        {seller.description && <p className="muted mt-16" style={{ marginBottom: 0 }}>{seller.description}</p>}
      </div>

      {products.length === 0 ? (
        <Empty icon={<Package size={28} />} title="No products yet" />
      ) : (
        <>
          <h2 className="mb-16">Products</h2>
          <div className="pgrid">
            {products.map((p) => <ProductCard key={p.id} product={p} />)}
          </div>
        </>
      )}
    </>
  );
}
