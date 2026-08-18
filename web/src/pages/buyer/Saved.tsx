import { useEffect, useState } from 'react';
import { Heart } from 'lucide-react';
import { buyerService } from '../../api/services';
import type { Product } from '../../api/types';
import { Empty, ErrorBox, Loading, PageHeader } from '../../components/ui';
import { ProductCard } from '../../components/ProductCard';

export default function Saved() {
  const [products, setProducts] = useState<Product[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    buyerService.bookmarks().then((r) => setProducts(r.products)).catch((e: any) => setError(e.message)).finally(() => setLoading(false));
  }, []);

  return (
    <>
      <PageHeader title="Saved products" sub="Your wishlist — shared with the mobile app." />
      {loading ? <Loading /> : error ? <ErrorBox message={error} /> :
        products.length === 0 ? <Empty icon={<Heart size={28} />} title="Nothing saved yet" subtitle="Tap the heart on any product to save it here." /> :
        <div className="pgrid">{products.map((p) => <ProductCard key={p.id} product={p} />)}</div>}
    </>
  );
}
