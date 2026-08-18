import { type MouseEvent } from 'react';
import { Link } from 'react-router-dom';
import { Star, ShoppingCart, Heart, MapPin, Eye } from 'lucide-react';
import type { Product } from '../api/types';
import { formatUgx } from '../api/types';
import { StatusBadge } from './ui';

const FALLBACK =
  'data:image/svg+xml;utf8,' +
  encodeURIComponent(
    `<svg xmlns="http://www.w3.org/2000/svg" width="400" height="400"><rect width="400" height="400" fill="#121a2f"/><text x="50%" y="50%" fill="#5a6a8c" font-family="sans-serif" font-size="20" text-anchor="middle">No image</text></svg>`
  );

export function ProductCard({
  product, index = 0, onAddToCart, onToggleFavorite, isFavorite, showStatus, compact,
}: {
  product: Product;
  index?: number;
  onAddToCart?: (p: Product) => void;
  onToggleFavorite?: (p: Product) => void;
  isFavorite?: boolean;
  showStatus?: boolean;
  compact?: boolean;
}) {
  const discounted = !!product.oldPriceMinor && product.oldPriceMinor > product.priceMinor;
  const soldOut = product.stockQuantity !== undefined && product.stockQuantity <= 0;

  const stop = (e: MouseEvent, fn?: () => void) => {
    e.preventDefault();
    e.stopPropagation();
    fn?.();
  };

  return (
    <Link
      to={`/product/${product.id}`}
      className="pcard stagger-item"
      style={{ '--i': index } as React.CSSProperties}
      aria-label={product.title}
    >
      <div className="pcard-media">
        <img
          className="pcard-img"
          src={product.imageUrl || FALLBACK}
          alt={product.title}
          loading="lazy"
          decoding="async"
          onError={(e) => { (e.currentTarget as HTMLImageElement).src = FALLBACK; }}
        />

        <div className="pcard-tags">
          {product.isFlashDeal && <span className="badge badge-red">FLASH −{product.discountPercent}%</span>}
          {!product.isFlashDeal && discounted && (
            <span className="badge badge-amber">
              −{Math.round(((product.oldPriceMinor! - product.priceMinor) / product.oldPriceMinor!) * 100)}%
            </span>
          )}
          {soldOut && <span className="badge">Sold out</span>}
          {showStatus && product.status && product.status !== 'approved' && <StatusBadge status={product.status} />}
        </div>

        {onToggleFavorite && (
          <button
            className={`pcard-fav ${isFavorite ? 'active' : ''}`}
            onClick={(e) => stop(e, () => onToggleFavorite(product))}
            aria-label={isFavorite ? 'Unfollow seller' : 'Follow seller'}
            title={isFavorite ? 'Unfollow this seller' : 'Follow seller for new-product alerts'}
          >
            <Heart size={15} fill={isFavorite ? 'currentColor' : 'none'} />
          </button>
        )}
      </div>

      <div className="pcard-body">
        <p className="pcard-title">{product.title}</p>

        {!compact && (
          <p className="pcard-sub">
            <Star size={11} style={{ verticalAlign: -1, color: 'var(--warning)' }} fill="currentColor" />
            {' '}{Number(product.rating || 0).toFixed(1)}
            <span className="muted-2"> ({product.ratingCount || 0})</span>
            {product.seller?.verified && <span className="t-success" title="Verified seller"> · ✓</span>}
            {product.viewCount ? (
              <span className="muted-2"> · <Eye size={10} style={{ verticalAlign: -1 }} /> {product.viewCount}</span>
            ) : null}
          </p>
        )}

        <div className="pcard-price-row">
          <span className="pcard-price">{formatUgx(product.priceMinor)}</span>
          {discounted && <span className="pcard-old">{formatUgx(product.oldPriceMinor!)}</span>}
        </div>

        {!compact && (
          <p className="pcard-sub" style={{ marginTop: 2 }}>
            <MapPin size={10} style={{ verticalAlign: -1 }} /> {product.location || product.seller?.location || 'Uganda'}
          </p>
        )}

        {onAddToCart && (
          <button
            className="btn btn-primary btn-sm pcard-cart"
            disabled={soldOut}
            onClick={(e) => stop(e, () => onAddToCart(product))}
            aria-label={`Add ${product.title} to cart`}
          >
            <ShoppingCart size={14} /> {soldOut ? 'Sold out' : 'Add to cart'}
          </button>
        )}
      </div>
    </Link>
  );
}

export function ProductGrid({
  products, onAddToCart, onToggleFavorite, favoriteSellerIds, showStatus,
}: {
  products: Product[];
  onAddToCart?: (p: Product) => void;
  onToggleFavorite?: (p: Product) => void;
  favoriteSellerIds?: Set<string>;
  showStatus?: boolean;
}) {
  return (
    <div className="pgrid stagger">
      {products.map((p, i) => (
        <ProductCard
          key={p.id}
          product={p}
          index={i}
          onAddToCart={onAddToCart}
          onToggleFavorite={onToggleFavorite}
          isFavorite={favoriteSellerIds?.has(p.seller?.id)}
          showStatus={showStatus}
        />
      ))}
    </div>
  );
}
