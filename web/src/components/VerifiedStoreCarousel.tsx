/**
 * ScottsTechX — verified-store carousel (animated premium edition).
 *
 * Horizontally scrolling strip of verified stores — live API data only.
 * Now with:
 *   • staggered entrance (spring)
 *   • travelling sheen + gradient border on hover
 *   • floating logo, pinging verified badge, twinkling star
 *   • slow auto-scroll that pauses on hover/touch/drag
 *   • drag-to-scroll + arrow enhancement
 *   • skeleton shimmer
 *
 * Touch / trackpad is primary: real overflow-x scroller with snap so momentum
 * and swipe stay native. Arrows are progressive enhancement.
 */
import { useCallback, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { BadgeCheck, ChevronLeft, ChevronRight, MapPin, Package, Star, Sparkles } from 'lucide-react';
import type { NearbySeller } from '../api/types';

function initials(name: string): string {
  const letters = name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((w) => w[0]!.toUpperCase())
    .join('');
  return letters || 'S';
}

export function formatDistance(km: number | null | undefined): string | null {
  if (km === null || km === undefined || !Number.isFinite(km)) return null;
  if (km < 1) return `${Math.max(50, Math.round(km * 1000))} m`;
  return `${km.toFixed(1)} km`;
}

function StoreLogo({ seller, index }: { seller: NearbySeller; index: number }) {
  const [broken, setBroken] = useState(false);
  const src = seller.logoUrl;
  if (src && !broken) {
    return (
      <span className="vstore-logo" style={{ ['--i' as any]: index }}>
        <img src={src} alt="" loading="lazy" decoding="async" onError={() => setBroken(true)} />
      </span>
    );
  }
  return (
    <span className="vstore-logo vstore-logo-fallback" style={{ ['--i' as any]: index }} aria-hidden>
      {initials(seller.storeName || seller.name)}
    </span>
  );
}

function StoreCard({ seller, showDistance, index }: { seller: NearbySeller; showDistance: boolean; index: number }) {
  const name = seller.storeName || seller.name;
  const distance = showDistance ? formatDistance(seller.distanceKm) : null;

  return (
    <Link
      to={`/seller/${seller.id}`}
      className="vstore-card"
      data-testid="verified-store-card"
      aria-label={`${name} — verified store in ${seller.city || 'Uganda'}`}
      style={{ ['--i' as any]: index } as any}
    >
      <div className="vstore-card-top">
        <StoreLogo seller={seller} index={index} />
        <div className="vstore-card-id">
          <p className="vstore-name">
            <span className="vstore-name-text">{name}</span>
            <span className="vstore-check" role="img" aria-label="Verified store" title="Verified by ScottsTechX">
              <BadgeCheck size={13} aria-hidden />
            </span>
          </p>
          <p className="vstore-meta">
            <MapPin size={11} style={{ verticalAlign: -1 }} /> {seller.city || seller.placeLabel || 'Uganda'}
          </p>
        </div>
      </div>

      <p className="vstore-desc">{seller.description || `Trusted seller in ${seller.city || 'Uganda'} — verified.`}</p>

      <div className="vstore-stats">
        <span className="vstore-rating" title={`Rated ${Number(seller.rating || 0).toFixed(1)} out of 5`}>
          <Star size={12} fill="currentColor" /> {Number(seller.rating || 0).toFixed(1)}
        </span>
        {distance ? (
          <span className="vstore-distance">
            <MapPin size={10} /> {distance}
          </span>
        ) : (
          <span className="vstore-count" style={{ opacity: 0.7 }}>
            <Sparkles size={10} /> Verified
          </span>
        )}
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
  showDistance?: boolean;
  title?: string;
  subtitle?: string;
}) {
  const trackRef = useRef<HTMLDivElement | null>(null);
  const [scrollable, setScrollable] = useState(false);
  const [atStart, setAtStart] = useState(true);
  const [atEnd, setAtEnd] = useState(true);
  const [isHovered, setIsHovered] = useState(false);
  const [isDragging, setIsDragging] = useState(false);
  const dragRef = useRef({ startX: 0, scrollLeft: 0, dragging: false });
  const autoRef = useRef<number | null>(null);

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
    if (typeof ResizeObserver !== 'undefined') {
      const ro = new ResizeObserver(measure);
      ro.observe(el);
      return () => ro.disconnect();
    }
    window.addEventListener('resize', measure);
    return () => window.removeEventListener('resize', measure);
  }, [measure, sellers.length]);

  // Auto-scroll: RAF drift, pauses on hover/drag/visibility
  useEffect(() => {
    if (loading || sellers.length <= 2) return;
    const el = trackRef.current;
    if (!el) return;
    const prefersReduced = typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (prefersReduced) return;
    if (typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;

    let raf = 0;
    let last = performance.now();
    const speed = 0.04; // px per ms ~ 24px/s
    const loop = (now: number) => {
      const dt = now - last;
      last = now;
      if (!isHovered && !isDragging && !document.hidden && el) {
        if (el.scrollLeft >= el.scrollWidth - el.clientWidth - 2) {
          el.scrollTo({ left: 0, behavior: 'auto' });
        } else {
          el.scrollLeft += dt * speed;
        }
      }
      raf = requestAnimationFrame(loop);
    };
    const startDelay = setTimeout(() => {
      last = performance.now();
      raf = requestAnimationFrame(loop);
    }, 1800);

    const onVis = () => { if (!document.hidden) last = performance.now(); };
    document.addEventListener('visibilitychange', onVis);

    return () => {
      clearTimeout(startDelay);
      cancelAnimationFrame(raf);
      document.removeEventListener('visibilitychange', onVis);
      if (autoRef.current) clearInterval(autoRef.current);
    };
  }, [loading, sellers.length, isHovered, isDragging]);

  const page = (direction: -1 | 1) => {
    const el = trackRef.current;
    if (!el) return;
    el.scrollBy({ left: direction * Math.max(220, el.clientWidth * 0.82), behavior: 'smooth' });
  };

  // Drag to scroll
  const onPointerDown = (e: React.PointerEvent) => {
    const el = trackRef.current;
    if (!el) return;
    dragRef.current = { startX: e.clientX, scrollLeft: el.scrollLeft, dragging: true };
    setIsDragging(true);
    (e.target as Element).setPointerCapture?.(e.pointerId);
  };
  const onPointerMove = (e: React.PointerEvent) => {
    if (!dragRef.current.dragging) return;
    const el = trackRef.current;
    if (!el) return;
    const dx = e.clientX - dragRef.current.startX;
    el.scrollLeft = dragRef.current.scrollLeft - dx;
    measure();
  };
  const onPointerUp = (e: React.PointerEvent) => {
    dragRef.current.dragging = false;
    setIsDragging(false);
    (e.target as Element).releasePointerCapture?.(e.pointerId);
  };

  if (!loading && sellers.length === 0) return null;

  return (
    <section
      className="vstores"
      data-testid="verified-stores"
      aria-roledescription="carousel"
      aria-label={title}
      data-auto={isHovered || isDragging ? '0' : '1'}
      onMouseEnter={() => setIsHovered(true)}
      onMouseLeave={() => setIsHovered(false)}
      onTouchStart={() => setIsHovered(true)}
      onTouchEnd={() => setTimeout(() => setIsHovered(false), 1200)}
    >
      <div className="vstores-head">
        <div>
          <h2 className="vstores-title" id="verified-stores-title">
            <span style={{ display: 'inline-flex', position: 'relative' }}>
              <BadgeCheck size={20} aria-hidden />
              <span
                style={{
                  position: 'absolute',
                  inset: -6,
                  borderRadius: '50%',
                  background: 'color-mix(in srgb, var(--primary) 22%, transparent)',
                  filter: 'blur(6px)',
                  zIndex: -1,
                }}
                aria-hidden
              />
            </span>
            {title}
          </h2>
          <p className="vstores-sub">
            {subtitle ||
              (loading
                ? 'Finding trusted sellers near you…'
                : `${sellers.length} store${sellers.length === 1 ? '' : 's'} checked by our team • drag to explore`)}
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
        data-dragging={isDragging ? '1' : '0'}
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={onPointerUp}
        onPointerLeave={onPointerUp}
      >
        {loading
          ? Array.from({ length: 5 }).map((_, i) => (
              <div className="vstore-card vstore-card-skeleton" key={i} style={{ ['--i' as any]: i } as any} aria-hidden>
                <div className="row" style={{ gap: 10 }}>
                  <div className="skeleton" style={{ width: 48, height: 48, borderRadius: 14 }} />
                  <div className="col" style={{ gap: 6 }}>
                    <div className="skeleton" style={{ height: 14, width: 92 }} />
                    <div className="skeleton" style={{ height: 10, width: 64 }} />
                  </div>
                </div>
                <div className="skeleton" style={{ height: 10, width: '88%', marginTop: 8 }} />
                <div className="skeleton" style={{ height: 10, width: '42%' }} />
              </div>
            ))
          : sellers.map((s, idx) => <StoreCard key={s.id} seller={s} showDistance={showDistance} index={idx} />)}
      </div>
    </section>
  );
}
