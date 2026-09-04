import { useEffect, useMemo, useState, type CSSProperties } from 'react';
import { Link } from 'react-router-dom';
import { CreditCard, MapPin, Search, ShoppingBag, Sparkles } from 'lucide-react';
import { productService, geoService } from '../api/services';
import type { Product, NearbySeller } from '../api/types';
import { ProductCard } from '../components/ProductCard';
import VerifiedStoreCarousel from '../components/VerifiedStoreCarousel';
import ExtraDealDisplay from '../components/ExtraDealDisplay';
import { Empty, ErrorBox, Loading, Btn } from '../components/ui';
import { useSeo } from '../hooks/useSeo';
import { BrandMark } from '../components/BrandLogo';

const CATEGORIES = ['All', 'Electronics', 'Fashion', 'Sports', 'Beauty', 'Home & Living', 'Groceries', 'Automotive'];

/**
 * Kampala — the marketplace's home city, and the fallback centre for the
 * verified-store strip.
 *
 * The API needs *some* coordinate to sort stores by distance. When the browser
 * cannot (or will not) say where the buyer is, we ask from here rather than
 * showing nothing: the strip still lists real verified stores, it just stops
 * claiming how far away they are (see `showDistance`).
 */
/* ── Mobile-only extraordinary advert — one box joining all cards + STX ────
   Video-like, compact, perfect, not taking much space. Only mobile.
   Cycles through Mobile Money, Nearby, AI, Genuine with STX glow.
─────────────────────────────────────────────────────────────────────────── */
function MobileAdvert() {
  const items = [
    { icon: <CreditCard size={14} />, label: 'Mobile Money', color: '#0ea5e9' },
    { icon: <MapPin size={14} />, label: 'Nearby', color: '#10b981' },
    { icon: <Sparkles size={14} />, label: 'AI Shopper', color: '#8b5cf6' },
    { icon: <ShoppingBag size={14} />, label: 'Genuine', color: '#f59e0b' },
  ];
  const [idx, setIdx] = useState(0);
  useEffect(() => {
    const t = setInterval(() => setIdx((i) => (i + 1) % items.length), 2200);
    return () => clearInterval(t);
  }, []);
  const active = items[idx];

  return (
    <div className="madvert" data-testid="mobile-advert">
      <div className="madvert-bg" aria-hidden="true">
        <div className="madvert-orb madvert-orb-1" />
        <div className="madvert-orb madvert-orb-2" />
        <div className="madvert-orb madvert-orb-3" />
        <div className="madvert-grid" />
        <div className="madvert-stx" />
      </div>
      <div className="madvert-shine" aria-hidden="true" />
      <div className="madvert-content">
        <div className="madvert-logo-wrap">
          <BrandMark size={32} className="madvert-logo" />
          <span className="madvert-live">LIVE</span>
        </div>
        <div className="madvert-text">
          <strong className="madvert-title">
            ScottsTechX <span className="madvert-title-accent">Marketplace</span>
          </strong>
          <div className="madvert-sub">
            <span className="madvert-sub-dot" />
            <span className="madvert-sub-text">
              Everything • Verified • Nearby • AI • Pay
            </span>
          </div>
          <div className="madvert-cycle">
            <span className="madvert-cycle-icon" style={{ color: active.color, background: `${active.color}14`, borderColor: `${active.color}22` }}>
              {active.icon}
            </span>
            <span className="madvert-cycle-label" style={{ color: active.color }}>
              {active.label}
            </span>
            <span className="madvert-cycle-dots">
              {items.map((_, i) => (
                <span key={i} className={`madvert-dot ${i === idx ? 'active' : ''}`} />
              ))}
            </span>
          </div>
        </div>
        <div className="madvert-cta">
          <span className="madvert-cta-icon"><Search size={12} /></span>
        </div>
      </div>
    </div>
  );
}

const FALLBACK_CENTER = { lat: 0.3476, lng: 32.5825 };

/** How long we are willing to wait for the browser's own position. */
const GEO_TIMEOUT_MS = 4000;

/**
 * Best-effort position for the strip, in order of trustworthiness:
 * a fresh browser fix → the position this account last saved → Kampala.
 *
 * Never rejects: the marketplace grid below does not depend on it, so a
 * refused location prompt must not put an error state on the home page.
 */
async function resolveCenter(): Promise<{ lat: number; lng: number; precise: boolean }> {
  const fix = await new Promise<{ lat: number; lng: number } | null>((resolve) => {
    if (typeof navigator === 'undefined' || !navigator.geolocation) {
      resolve(null);
      return;
    }
    let settled = false;
    const finish = (value: { lat: number; lng: number } | null) => {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      resolve(value);
    };
    const timer = setTimeout(() => finish(null), GEO_TIMEOUT_MS);
    try {
      navigator.geolocation.getCurrentPosition(
        (pos) => finish({ lat: pos.coords.latitude, lng: pos.coords.longitude }),
        () => finish(null),
        { timeout: GEO_TIMEOUT_MS - 500, maximumAge: 5 * 60 * 1000 },
      );
    } catch {
      finish(null);
    }
  });

  if (fix) return { ...fix, precise: true };

  // Signed-in buyers have a stored last-known position.
  try {
    const saved = await geoService.myLocation();
    if (saved.position) return { ...saved.position, precise: true };
  } catch {
    // Logged out (401) or the endpoint is unavailable — fall through.
  }

  return { ...FALLBACK_CENTER, precise: false };
}

export default function Home() {
  useSeo({
    title: "Uganda's Marketplace",
    description:
      'Buy and sell on ScottsTechX — electronics, fashion, home goods and more ' +
      'from verified sellers across Kampala, Entebbe, Jinja and beyond. ' +
      'Cash on delivery available.',
  });

  const [products, setProducts] = useState<Product[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [category, setCategory] = useState('All');
  const [q, setQ] = useState('');

  const [stores, setStores] = useState<NearbySeller[]>([]);
  const [storesLoading, setStoresLoading] = useState(true);
  const [storesPrecise, setStoresPrecise] = useState(false);

  async function load() {
    setLoading(true);
    setError('');
    try {
      const r = await productService.list({ pageSize: 24, sort: 'popular' });
      setProducts(r.products);
    } catch (e: any) {
      setError(e.message || 'Could not load products');
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => { load(); }, []);

  /**
   * Verified stores, nearest/best first.
   *
   * Deliberately isolated from `load()`: this strip is an enhancement, so a
   * failing store lookup hides the strip and leaves the marketplace intact
   * rather than blanking the page.
   */
  useEffect(() => {
    let cancelled = false;

    (async () => {
      try {
        const at = await resolveCenter();
        if (cancelled) return;
        const r = await productService.nearby({
          lat: at.lat,
          lng: at.lng,
          verifiedOnly: true,
          sort: 'rating',
          limit: 12,
        });
        if (cancelled) return;
        setStores((r.sellers || []).filter((s) => s.verified));
        setStoresPrecise(at.precise);
      } catch {
        // No strip — nothing else on the page is affected.
      } finally {
        if (!cancelled) setStoresLoading(false);
      }
    })();

    return () => { cancelled = true; };
  }, []);

  const filtered = useMemo(() => {
    const needle = q.trim().toLowerCase();
    return products.filter((p) =>
      (category === 'All' || p.category === category) &&
      (needle === '' || (p.title + ' ' + p.brand + ' ' + p.description).toLowerCase().includes(needle))
    );
  }, [products, category, q]);

  const flash = useMemo(() => products.filter((p) => p.isFlashDeal).slice(0, 6), [products]);
  const showStores = storesLoading || stores.length > 0;

  const benefits = [
    { anim: 'pay', icon: <CreditCard size={22} />, title: 'Mobile Money & Cards' },
    { anim: 'nearby', icon: <MapPin size={22} />, title: 'Nearby sellers' },
    { anim: 'ai', icon: <Sparkles size={22} />, title: 'AI assistant' },
    { anim: 'trust', icon: <ShoppingBag size={22} />, title: 'Local & genuine' },
  ];

  return (
    <>
      <div className="grid grid-4 mb-16 feature-grid">
        {benefits.map((b, i) => (
          <div
            className="card feature-card feature-card--image"
            key={b.title}
            data-anim={b.anim}
            style={{ '--i': i } as CSSProperties}
          >
            <div className="feature-card-bg" aria-hidden="true" />
            <div className="feature-card-overlay" aria-hidden="true" />
            <div className="feature-card-content">
              <span className="feature-card-icon">{b.icon}</span>
              <strong className="feature-card-title">{b.title}</strong>
            </div>
          </div>
        ))}
      </div>

      <MobileAdvert />

      <div className="home-search-wrap">
        <div className="home-searchbar">
          <div className="home-searchbar-bg" aria-hidden="true">
            <div className="home-searchbar-orb" />
            <div className="home-searchbar-grid" />
            <div className="home-searchbar-stx" />
          </div>
          <Search size={15} className="home-searchbar-icon" />
          <input
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="Search products, brands, stores…"
            aria-label="Search products"
          />
          {q && (
            <button className="home-searchbar-clear" onClick={() => setQ('')} aria-label="Clear search">
              <span className="home-searchbar-clear-ico">×</span>
            </button>
          )}
          <div className="home-searchbar-divider" aria-hidden="true" />
          <button className="home-searchbar-ai" onClick={() => q.trim() && (window.location.href = `/search?q=${encodeURIComponent(q.trim())}`)}>
            <Sparkles size={12} /> AI
          </button>
        </div>
        <div className="home-search-hint">
          <span className="home-search-hint-dot" /> Try “phones”, “shoes”, “nearby”
        </div>
      </div>

      <div className="category-row mb-16" role="tablist" aria-label="Categories">
        {CATEGORIES.map((c) => (
          <button key={c} role="tab" aria-selected={category === c} className={`chip category-chip ${category === c ? 'active' : ''}`} onClick={() => setCategory(c)}>{c}</button>
        ))}
      </div>

      {showStores && (
        <VerifiedStoreCarousel
          sellers={stores}
          loading={storesLoading}
          showDistance={storesPrecise}
        />
      )}

      {flash.length > 0 ? (
        <ExtraDealDisplay deals={flash} />
      ) : (
        products.length > 0 && (
          <ExtraDealDisplay
            deals={products
              .filter((p) => (p.discountPercent || 0) > 0 || p.oldPriceMinor)
              .slice(0, 6)
              .concat(products.slice(0, 6))
              .slice(0, 6)}
          />
        )
      )}

      <h2 className="mb-16">All products</h2>
      {loading ? <Loading /> : error ? <ErrorBox message={error} onRetry={load} /> :
        filtered.length === 0 ? <Empty icon={<Search size={28} />} title="No products found" subtitle="Try another category or search." /> :
        <div className="pgrid">
          {filtered.map((p) => <ProductCard key={p.id} product={p} />)}
        </div>}

      <div className="card card-pad mt-24 row-between wrap">
        <div>
          <strong>Are you a seller?</strong>
          <div className="muted">List products, manage inventory and track orders on the web.</div>
        </div>
        <Link to="/register"><Btn variant="primary" className="join-cta">Open a store</Btn></Link>
      </div>
    </>
  );
}
