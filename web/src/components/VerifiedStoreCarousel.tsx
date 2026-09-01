/**
 * ScottsTechX — verified-store carousel.
 *
 * A horizontally scrolling strip of the stores the marketplace has verified.
 * Everything in it is live API data: no placeholder tiles, no fake ratings.
 *
 * Behaviour worth knowing before you edit this:
 *
 *   • Touch / trackpad scrolling is the primary interaction, so the track is a
 *     real `overflow-x` scroller with CSS scroll snap. That keeps momentum
 *     scrolling and swipe gestures native instead of re-implemented in JS.
 *   • Arrow buttons are an *enhancement* for pointer devices. They hide when
 *     the strip fits on screen (including in jsdom, where every width is 0)
 *     rather than sitting there disabled.
 *   • The caller hides the whole section when there is nothing verified, so
 *     this component never has to render an empty strip.
 */
import { useCallback, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { BadgeCheck, ChevronLeft, ChevronRight, MapPin, Package, Star } from 'lucide-react';
import type { NearbySeller } from '../api/types';

/** Up to two letters from the store name — the fallback when there is no logo. */
function initials(name: string): string {
  const letters = name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((word) => word[0]!.toUpperCase())
    .join('');
  return letters || 'S';
}

/** "1.2 km" / "800 m" — only rendered when we know where the buyer is. */
export function formatDistance(km: number | null | undefined): string | null {
  if (km === null || km === undefined || !Number.isFinite(km)) return null;
  if (km < 1) return `${Math.max(50, Math.round(km * 1000))} m`;
  return `${km.toFixed(1)} km`;
}

function StoreLogo({ seller }: { seller: NearbySeller }) {
  const [broken, setBroken] = useState(false);
  const src = seller.logoUrl;

  if (src && !broken) {
    return (
      <span className="vstore-logo">
        <img
          src={src}
          alt=""
          loading="lazy"
          decoding="async"
          onError={() => setBroken(true)}
        />
      </span>
    );
  }
  return (
    <span className="vstore-logo vstore-logo-fallback" aria-hidden>
      {initials(seller.storeName || seller.name)}
    </span>
  );
}

function StoreCard({ seller, showDistance }: { seller: NearbySeller; showDistance: boolean }) {
  const name = seller.storeName || seller.name;
  const distance = showDistance ? formatDistance(seller.distanceKm) : null;

  return (
    <Link
      to={`/seller/${seller.id}`}
      className="vstore-card"
      data-testid="verified-store-card"
      aria-label={`${name} — verified store in ${seller.city || 'Uganda'}`}
    >
      <div className="vstore-card-top">
        <StoreLogo seller={seller} />
        <div className="vstore-card-id">
          <p className="vstore-name">
            <span className="vstore-name-text">{name}</span>
            {/* lucide icons take no `title`, so the accessible name and the
                tooltip live on a wrapper span instead. */}
            <span className="vstore-check" role="img" aria-label="Verified store" title="Verified by ScottsTechX">
              <BadgeCheck size={15} aria-hidden />
            </span>
          </p>
          <p className="vstore-meta">
            <MapPin size={11} style={{ verticalAlign: -1 }} /> {seller.city || seller.placeLabel || 'Uganda'}
          </p>
        </div>
      </div>

      <p className="vstore-desc">{seller.description}</p>

      <div className="vstore-stats">
        <span className="vstore-rating" title={`Rated ${Number(seller.rating || 0).toFixed(1)} out of 5`}>
          <Star size={12} fill="currentColor" /> {Number(seller.rating || 0).toFixed(1)}
        </span>
        <span className="vstore-count">
          <Package size={11} style={{ verticalAlign: -1 }} /> {seller.productCount ?? 0} listings
        </span>
        {distance && <span className="vstore-distance">{distance} away</span>}
      </div>
    </Link>
  );
}

export default function VerifiedStoreCarousel({
  sellers,
  loading = false,
  showDistance = false,
  title = 'Verified stores',
  subtitle,
}: {
  sellers: NearbySeller[];
  loading?: boolean;
  /** False when we fell back to a default centre — distances would be fiction. */
  showDistance?: boolean;
  title?: string;
  subtitle?: string;
}) {
  const trackRef = useRef<HTMLDivElement | null>(null);
  const [scrollable, setScrollable] = useState(false);
  const [atStart, setAtStart] = useState(true);
  const [atEnd, setAtEnd] = useState(true);

  /** Recompute arrow visibility. Runs on scroll, on mount and on resize. */
  const measure = useCallback(() => {
    const el = trackRef.current;
    if (!el) return;
    const max = el.scrollWidth - el.clientWidth;
    setScrollable(max > 8);
    setAtStart(el.scrollLeft <= 4);
    setAtEnd(el.scrollLeft >= max - 4);
  }, []);

  useEffect(() => {
    measure();
    const el = trackRef.current;
    if (!el) return;

    // ResizeObserver is absent in jsdom and in very old Safari; scrolling still
    // works without it, so this is a progressive enhancement, not a dependency.
    if (typeof ResizeObserver !== 'undefined') {
      const ro = new ResizeObserver(measure);
      ro.observe(el);
      return () => ro.disconnect();
    }
    window.addEventListener('resize', measure);
    return () => window.removeEventListener('resize', measure);
  }, [measure, sellers.length]);

  const page = (direction: -1 | 1) => {
    const el = trackRef.current;
    if (!el) return;
    el.scrollBy({ left: direction * Math.max(180, el.clientWidth * 0.8), behavior: 'smooth' });
  };

  if (!loading && sellers.length === 0) return null;

  return (
    <section
      className="vstores"
      data-testid="verified-stores"
      aria-roledescription="carousel"
      aria-label={title}
    >
      <div className="vstores-head">
        <div>
          <h2 className="vstores-title" id="verified-stores-title">
            <BadgeCheck size={17} aria-hidden />
            {title}
          </h2>
          <p className="vstores-sub">
            {subtitle ||
              (loading
                ? 'Loading stores…'
                : `${sellers.length} store${sellers.length === 1 ? '' : 's'} checked out by our team.`)}
          </p>
        </div>

        {scrollable && (
          <div className="vstores-arrows">
            <button
              type="button"
              className="vstores-arrow"
              onClick={() => page(-1)}
              disabled={atStart}
              aria-label="Previous verified stores"
              aria-controls="verified-stores-track"
            >
              <ChevronLeft size={16} />
            </button>
            <button
              type="button"
              className="vstores-arrow"
              onClick={() => page(1)}
              disabled={atEnd}
              aria-label="Next verified stores"
              aria-controls="verified-stores-track"
            >
              <ChevronRight size={16} />
            </button>
          </div>
        )}
      </div>

      <div
        className="vstores-track"
        id="verified-stores-track"
        ref={trackRef}
        onScroll={measure}
        data-testid="verified-stores-track"
      >
        {loading
          ? Array.from({ length: 4 }).map((_, i) => (
              <div className="vstore-card vstore-card-skeleton" key={i} aria-hidden>
                <div className="skeleton" style={{ height: 14, width: '55%', marginBottom: 12 }} />
                <div className="skeleton" style={{ height: 10, width: '80%', marginBottom: 8 }} />
                <div className="skeleton" style={{ height: 10, width: '40%' }} />
              </div>
            ))
          : sellers.map((s) => (
              <StoreCard key={s.id} seller={s} showDistance={showDistance} />
            ))}
      </div>
    </section>
  );
}
