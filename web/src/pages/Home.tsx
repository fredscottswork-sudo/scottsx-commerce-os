import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Search } from 'lucide-react';
import { productService, geoService } from '../api/services';
import type { Product, NearbySeller } from '../api/types';
import { ProductCard } from '../components/ProductCard';
import VerifiedStoreCarousel from '../components/VerifiedStoreCarousel';
import { Empty, ErrorBox, Loading, PageHeader, SearchInput, Btn } from '../components/ui';

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
      const r = await productService.list();
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

  const filtered = products.filter((p) =>
    (category === 'All' || p.category === category) &&
    (q.trim() === '' || (p.title + ' ' + p.brand + ' ' + p.description).toLowerCase().includes(q.toLowerCase()))
  );
  const flash = products.filter((p) => p.isFlashDeal).slice(0, 6);
  const showStores = storesLoading || stores.length > 0;

  return (
    <>
      <PageHeader title="Marketplace" sub="Live catalog from the ScottsTechX backend — same data as the mobile app."
        actions={<SearchInput value={q} onChange={setQ} placeholder="Search products…" />} />

      <div className="row wrap mb-16">
        {CATEGORIES.map((c) => (
          <button key={c} className={`chip ${category === c ? 'active' : ''}`} onClick={() => setCategory(c)}>{c}</button>
        ))}
      </div>

      {showStores && (
        <VerifiedStoreCarousel
          sellers={stores}
          loading={storesLoading}
          showDistance={storesPrecise}
        />
      )}

      {flash.length > 0 && (
        <>
          <h2 className="mb-16" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>⚡ Flash deals</h2>
          <div className="pgrid mb-24" style={{ marginBottom: 28 }}>
            {flash.map((p) => <ProductCard key={p.id} product={p} />)}
          </div>
        </>
      )}

      <h2 className="mb-16">All products ({filtered.length})</h2>
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
