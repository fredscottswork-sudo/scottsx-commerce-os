import { useCallback, useEffect, useMemo, useState, type CSSProperties, memo } from 'react';
import { Link } from 'react-router-dom';
import { CreditCard, MessageCircle, MapPin, Search, ShoppingBag, Sparkles, Camera } from 'lucide-react';
import { productService, geoService } from '../api/services';
import type { Product, NearbySeller } from '../api/types';
import { ProductGrid } from '../components/ProductCard';
import VerifiedStoreCarousel from '../components/VerifiedStoreCarousel';
import ExtraDealDisplay from '../components/ExtraDealDisplay';
import { Empty, ErrorBox, Loading, Btn } from '../components/ui';
import { useSeo } from '../hooks/useSeo';
import { useRotatingPlaceholder, SEARCH_WATERMARKS } from '../hooks/useRotatingPlaceholder';
import { BrandMark } from '../components/BrandLogo';
import { useCart } from '../store/CartContext';
import { mergedCategories, categoryIcon } from '../components/categories';

const BENEFITS = [
  { anim: 'chat', icon: <MessageCircle size={22} />, title: 'Chat with sellers' },
  { anim: 'nearby', icon: <MapPin size={22} />, title: 'Nearby sellers' },
  { anim: 'ai', icon: <Sparkles size={22} />, title: 'AI assistant' },
  { anim: 'trust', icon: <ShoppingBag size={22} />, title: 'Local & genuine' },
] as const;

const AD_ITEMS = [
  { icon: <MessageCircle size={14} />, label: 'Chat first', color: '#0ea5e9' },
  { icon: <MapPin size={14} />, label: 'Nearby', color: '#10b981' },
  { icon: <Sparkles size={14} />, label: 'AI Shopper', color: '#8b5cf6' },
  { icon: <ShoppingBag size={14} />, label: 'Genuine', color: '#f59e0b' },
] as const;

const FALLBACK_CENTER = { lat: 0.3476, lng: 32.5825 };
const GEO_TIMEOUT_MS = 4000;

const MobileAdvert = memo(function MobileAdvert() {
  const [idx, setIdx] = useState(0);
  useEffect(() => {
    const t = setInterval(() => setIdx((i) => (i + 1) % AD_ITEMS.length), 3500);
    return () => clearInterval(t);
  }, []);
  const active = AD_ITEMS[idx];
  return (
    <div className="madvert" data-testid="mobile-advert" style={{ contentVisibility: 'auto', containIntrinsicSize: '80px' } as any}>
      <div className="madvert-bg" aria-hidden="true">
        <div className="madvert-orb madvert-orb-1" />
        <div className="madvert-orb madvert-orb-2" />
        <div className="madvert-grid" />
      </div>
      <div className="madvert-content">
        <div className="madvert-logo-wrap">
          <BrandMark size={32} className="madvert-logo" />
          <span className="madvert-live">LIVE</span>
        </div>
        <div className="madvert-text">
          <strong className="madvert-title">ScottsTechX <span className="madvert-title-accent">Marketplace</span></strong>
          <div className="madvert-sub"><span className="madvert-sub-dot" /><span>Everything • Verified • Nearby • AI • Chat</span></div>
          <div className="madvert-cycle">
            <span className="madvert-cycle-icon" style={{ color: active.color, background: `${active.color}14`, borderColor: `${active.color}22` }}>{active.icon}</span>
            <span className="madvert-cycle-label" style={{ color: active.color }}>{active.label}</span>
            <span className="madvert-cycle-dots">{AD_ITEMS.map((_, i) => (<span key={i} className={`madvert-dot ${i === idx ? 'active' : ''}`} />))}</span>
          </div>
        </div>
        <div className="madvert-cta"><Search size={12} /></div>
      </div>
    </div>
  );
});

async function resolveCenter(): Promise<{ lat: number; lng: number; precise: boolean }> {
  const fix = await new Promise<{ lat: number; lng: number } | null>((resolve) => {
    if (typeof navigator === 'undefined' || !navigator.geolocation) { resolve(null); return; }
    let settled = false;
    const finish = (value: { lat: number; lng: number } | null) => { if (settled) return; settled = true; clearTimeout(timer); resolve(value); };
    const timer = setTimeout(() => finish(null), GEO_TIMEOUT_MS);
    try {
      navigator.geolocation.getCurrentPosition((pos) => finish({ lat: pos.coords.latitude, lng: pos.coords.longitude }), () => finish(null), { timeout: GEO_TIMEOUT_MS - 500, maximumAge: 5 * 60 * 1000 });
    } catch { finish(null); }
  });
  if (fix) return { ...fix, precise: true };
  try { const saved = await geoService.myLocation(); if (saved.position) return { ...saved.position, precise: true }; } catch {}
  return { ...FALLBACK_CENTER, precise: false };
}

export default function Home() {
  useSeo({
    title: "Uganda's Marketplace",
    description: 'Buy and sell on ScottsTechX — electronics, fashion, home goods and more from verified sellers across Kampala, Entebbe, Jinja and beyond.',
  });

  const [products, setProducts] = useState<Product[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [category, setCategory] = useState('All');
  const [q, setQ] = useState('');
  const [qDebounced, setQDebounced] = useState('');
  const [stores, setStores] = useState<NearbySeller[]>([]);
  const [storesLoading, setStoresLoading] = useState(true);
  const [storesPrecise, setStoresPrecise] = useState(false);
  const [facets, setFacets] = useState<{ name: string; count: number }[] | null>(null);
  const watermark = useRotatingPlaceholder(SEARCH_WATERMARKS);
  const { add } = useCart();

  // Live facet counts feed the 16-category showcase (always full and even).
  useEffect(() => {
    productService.facets().then((r) => setFacets(r.categories)).catch(() => undefined);
  }, []);

  const categoryTiles = facets ? mergedCategories(facets) : [];

  /** Camera shortcut: open the (more capable) search page image modal. */
  const openImageSearch = () => {
    try { sessionStorage.setItem('stx:open-image-search', '1'); } catch { /* ignore */ }
    window.location.href = '/search?img=1';
  };

  useEffect(() => {
    const t = setTimeout(() => setQDebounced(q.trim().toLowerCase()), 250);
    return () => clearTimeout(t);
  }, [q]);

  const load = useCallback(async (cat: string) => {
    setLoading(true);
    setError('');
    try {
      const r = await productService.list({ pageSize: 24, sort: 'popular', category: cat !== 'All' ? cat : undefined } as any);
      setProducts(r.products);
    } catch (e: any) {
      setError(e.message || 'Could not load products');
    } finally { setLoading(false); }
  }, []);

  useEffect(() => { load(category); }, [category, load]);

  useEffect(() => {
    let cancelled = false;
    const cacheKey = 'vstores_cache';
    try {
      const raw = localStorage.getItem(cacheKey);
      if (raw) {
        const cached = JSON.parse(raw);
        if (Date.now() - cached.at < 5 * 60 * 1000 && cached.sellers?.length) {
          setStores(cached.sellers);
          setStoresPrecise(cached.precise);
          setStoresLoading(false);
          return;
        }
      }
    } catch {}
    (async () => {
      try {
        const at = await resolveCenter();
        if (cancelled) return;
        const r = await productService.nearby({ lat: at.lat, lng: at.lng, verifiedOnly: true, sort: 'rating', limit: 12 });
        if (cancelled) return;
        const sellers = (r.sellers || []).filter((s) => s.verified);
        setStores(sellers);
        setStoresPrecise(at.precise);
        try { localStorage.setItem(cacheKey, JSON.stringify({ at: Date.now(), sellers, precise: at.precise })); } catch {}
      } catch {} finally { if (!cancelled) setStoresLoading(false); }
    })();
    return () => { cancelled = true; };
  }, []);

  const filtered = useMemo(() => {
    if (!qDebounced) return products;
    return products.filter((p) => `${p.title} ${p.brand}`.toLowerCase().includes(qDebounced));
  }, [products, qDebounced]);

  const flash = useMemo(() => products.filter((p) => p.isFlashDeal).slice(0, 6), [products]);
  const deals = useMemo(() => {
    if (flash.length > 0) return flash;
    if (products.length === 0) return [];
    const discounted = products.filter((p) => (p.discountPercent || 0) > 0 || p.oldPriceMinor);
    return (discounted.length >= 3 ? discounted : products).slice(0, 6);
  }, [flash, products]);

  const showStores = storesLoading || stores.length > 0;

  return (
    <>
      <div className="grid grid-4 mb-16 feature-grid" style={{ contentVisibility: 'auto', containIntrinsicSize: '80px' } as any}>
        {BENEFITS.map((b, i) => (
          <div className="card feature-card feature-card--image" key={b.title} data-anim={b.anim} style={{ '--i': i, contentVisibility: 'auto', containIntrinsicSize: '80px' } as CSSProperties}>
            <div className="feature-card-bg" aria-hidden="true" />
            <div className="feature-card-overlay" aria-hidden="true" />
            <div className="feature-card-content"><span className="feature-card-icon">{b.icon}</span><strong className="feature-card-title">{b.title}</strong></div>
          </div>
        ))}
      </div>

      <MobileAdvert />

      <div className="home-search-wrap" style={{ contentVisibility: 'auto', containIntrinsicSize: '60px' } as any}>
        <div className="home-searchbar">
          <div className="home-searchbar-bg" aria-hidden="true"><div className="home-searchbar-orb" /></div>
          <Search size={15} className="home-searchbar-icon" />
          <input value={q} onChange={(e) => setQ(e.target.value)} placeholder={watermark} aria-label="Search products" />
          {q && <button className="home-searchbar-clear" onClick={() => setQ('')} aria-label="Clear search"><span>×</span></button>}
          <div className="home-searchbar-divider" aria-hidden="true" />
          <button className="home-searchbar-cam" onClick={openImageSearch} title="Search by image" aria-label="Search by image">
            <Camera size={14} />
          </button>
          <button className="home-searchbar-ai" onClick={() => q.trim() && (window.location.href = `/search?q=${encodeURIComponent(q.trim())}`)}><Sparkles size={12} /> AI</button>
        </div>
        <div className="home-search-hint"><span className="home-search-hint-dot" /> Try “phones”, “shoes”, “nearby”</div>
      </div>

      {/* ── Alibaba-style category showcase: 16 even tiles, live counts ── */}
      {categoryTiles.length > 0 && (
        <section className="cat-shower mb-24" style={{ contentVisibility: 'auto', containIntrinsicSize: '180px' } as any}>
          <div className="row-between mb-14">
            <h2 className="card-title">Shop by category</h2>
            <Link to="/search" className="link-arrow">Browse all →</Link>
          </div>
          <div className="cat-grid cat-grid-even">
            {categoryTiles.map((c, i) => (
              <Link
                key={c.name}
                to={`/search?category=${encodeURIComponent(c.name)}`}
                className="cat-tile cat-tile-modern"
                style={{ '--hue': (i * 23) % 360 } as CSSProperties}
              >
                <span className="cat-ico">{categoryIcon(c.name)}</span>
                <span className="cat-name">{c.name}</span>
                {c.count > 0 && <span className="cat-count">{c.count} item{c.count === 1 ? '' : 's'}</span>}
              </Link>
            ))}
          </div>
        </section>
      )}

      {showStores && <VerifiedStoreCarousel sellers={stores} loading={storesLoading} showDistance={storesPrecise} />}
      {deals.length > 0 && <ExtraDealDisplay deals={deals} />}

      <h2 className="mb-16">All products</h2>
      {loading ? <Loading /> : error ? <ErrorBox message={error} onRetry={() => load(category)} /> :
        filtered.length === 0 ? <Empty icon={<Search size={28} />} title="No products found" subtitle="Try another category or search." /> :
          <ProductGrid products={filtered} onAddToCart={(p) => void add(p)} />}

      <div className="card card-pad mt-24 row-between wrap" style={{ contentVisibility: 'auto', containIntrinsicSize: '80px' } as any}>
        <div><strong>Are you a seller?</strong><div className="muted">List products, manage inventory and track orders on the web.</div></div>
        <Link to="/register"><Btn variant="primary" className="join-cta">Open a store</Btn></Link>
      </div>
    </>
  );
}
