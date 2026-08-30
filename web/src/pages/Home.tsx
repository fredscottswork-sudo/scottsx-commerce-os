import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { MapPin, Search, Sparkles } from 'lucide-react';
import { productService, aiService } from '../api/services';
import type { Product } from '../api/types';
import { ProductCard } from '../components/ProductCard';
import { Empty, ErrorBox, Loading, PageHeader, SearchInput, Btn } from '../components/ui';

const CATEGORIES = ['All', 'Electronics', 'Fashion', 'Sports', 'Beauty', 'Home & Living', 'Groceries', 'Automotive'];

export default function Home() {
  const [products, setProducts] = useState<Product[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [category, setCategory] = useState('All');
  const [q, setQ] = useState('');

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

  const filtered = products.filter((p) =>
    (category === 'All' || p.category === category) &&
    (q.trim() === '' || (p.title + ' ' + p.brand + ' ' + p.description).toLowerCase().includes(q.toLowerCase()))
  );
  const flash = products.filter((p) => p.isFlashDeal).slice(0, 6);

  // Benefits strip — payment + local cards REMOVED per product direction;
  // only the two differentiators remain (Nearby sellers, AI assistant),
  // now a two-up animated strip (staggered entrance, floating icons,
  // hover sheen + gradient underline, live-ring on AI).
  const benefits = [
    { icon: <MapPin size={20} />, title: 'Nearby sellers', sub: 'Kampala · Entebbe · Jinja · Mbarara · Gulu · Mbale', live: false },
    { icon: <Sparkles size={20} />, title: 'AI assistant', sub: 'Answers from the live catalog', live: true },
  ];

  return (
    <>
      <div className="benefit-strip mb-16">
        {benefits.map((b) => (
          <div className="card card-pad benefit-card" key={b.title}>
            <div className="row">
              <span className={`stat-icon benefit-icon${b.live ? ' benefit-live' : ''}`} style={{ background: 'linear-gradient(135deg, var(--primary), var(--purple))' }}>{b.icon}</span>
              <div>
                <strong>{b.title}</strong>
                <div className="muted" style={{ fontSize: 12.5 }}>{b.sub}</div>
              </div>
            </div>
          </div>
        ))}
      </div>

      <PageHeader title="Marketplace" sub="Live catalog from the ScottsTechX backend — same data as the mobile app."
        actions={<SearchInput value={q} onChange={setQ} placeholder="Search products…" />} />

      <div className="row wrap mb-16">
        {CATEGORIES.map((c) => (
          <button key={c} className={`chip ${category === c ? 'active' : ''}`} onClick={() => setCategory(c)}>{c}</button>
        ))}
      </div>

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
        <Link to="/register"><Btn variant="primary">Open a store</Btn></Link>
      </div>
    </>
  );
}
