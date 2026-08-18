import { Link } from 'react-router-dom';
import { Star } from 'lucide-react';
import type { Product } from '../api/types';
import { formatUgx } from '../api/types';

export function ProductCard({ product }: { product: Product }) {
  return (
    <Link to={`/product/${product.id}`} className="pcard" aria-label={product.title}>
      <img className="pcard-img" src={product.imageUrl} alt={product.title} loading="lazy" />
      <div className="pcard-body">
        <p className="pcard-title">{product.title}</p>
        <p className="pcard-sub">
          {product.seller.name} · <Star size={11} style={{ verticalAlign: -1, color: 'var(--warning)' }} /> {product.rating} ({product.ratingCount})
        </p>
        <div>
          <span className="pcard-price">{formatUgx(product.priceMinor)}</span>
          {product.oldPriceMinor && product.oldPriceMinor > product.priceMinor && (
            <span className="pcard-old">{formatUgx(product.oldPriceMinor)}</span>
          )}
        </div>
        {product.isFlashDeal && <span className="badge badge-red mt-8" style={{ marginTop: 6 }}>FLASH -{product.discountPercent}%</span>}
      </div>
    </Link>
  );
}
