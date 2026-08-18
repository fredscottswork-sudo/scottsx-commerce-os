import { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { BadgeCheck, MapPin, Package, Star } from 'lucide-react';
import { productService, chatService } from '../api/services';
import type { Product } from '../api/types';
import { formatUgx } from '../api/types';
import { useAuth } from '../store/AuthContext';
import { useSeo } from '../hooks/useSeo';
import { useToast } from '../store/ToastContext';
import { ProductCard } from '../components/ProductCard';
import { Btn, Empty, ErrorBox, Loading } from '../components/ui';

export default function SellerStorefront() {
  const { id } = useParams<{ id: string }>();
  const { user } = useAuth();
  const { toast } = useToast();
  const [seller, setSeller] = useState<any>(null);
  const [products, setProducts] = useState<Product[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useSeo({
    title: seller?.name ? `${seller.name} — store` : undefined,
    description: seller
      ? `Shop ${products.length} product${products.length === 1 ? '' : 's'} from ${seller.name}` +
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
    if (!user) { window.location.assign('/login'); return; }
    try {
      const r = await chatService.open(seller.id);
      window.location.assign(`/messages/${r.conversation.id}`);
    } catch (e: any) {
      toast(e.message, 'error');
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
              {seller.logoUrl ? <img src={seller.logoUrl} alt="" /> : seller.name[0]}
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
            {products.map((p) => <ProductCard key={p.id} product={p} />)}
          </div>
        </>
      )}
    </>
  );
}
