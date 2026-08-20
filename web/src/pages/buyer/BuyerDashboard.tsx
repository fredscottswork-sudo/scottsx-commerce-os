import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  Sparkles, ShoppingBag, Heart, Package, MapPin, Clock, Flame, TrendingUp,
  ArrowRight, Store, Bell, Search, ShoppingCart, Star,
} from 'lucide-react';
import {
  productService, buyerService, socialService,
} from '../../api/services';
import type { Product, Order, FavoriteSeller, Facets } from '../../api/types';
import { formatUgx } from '../../api/types';
import { useAuth } from '../../store/AuthContext';
import { useCart } from '../../store/CartContext';
import { useToast } from '../../store/ToastContext';
import { ProductGrid } from '../../components/ProductCard';
import {
  Btn, Empty, ErrorBox, SkeletonGrid, SkeletonStats, StatCard, CountUp, Tabs,
} from '../../components/ui';

type Feed = 'for-you' | 'flash' | 'following' | 'trending';

const CATEGORY_ICONS: Record<string, string> = {
  Electronics: '📱', Fashion: '👗', Home: '🏠', Beauty: '💄', Sports: '⚽',
  Automotive: '🚗', Books: '📚', Groceries: '🛒', Toys: '🧸', Health: '💊',
};

export default function BuyerDashboard() {
  const { user } = useAuth();
  const { cart, add, favoriteSellerIds, savedIds, toggleFavoriteSeller } = useCart();
  const { toast } = useToast();
  const navigate = useNavigate();

  const [feed, setFeed] = useState<Feed>('for-you');
  const [products, setProducts] = useState<Product[]>([]);
  const [loadingFeed, setLoadingFeed] = useState(true);
  const [feedError, setFeedError] = useState('');

  const [orders, setOrders] = useState<Order[]>([]);
  const [following, setFollowing] = useState<FavoriteSeller[]>([]);
  const [facets, setFacets] = useState<Facets | null>(null);
  const [recent, setRecent] = useState<{ id: string; query: string }[]>([]);
  const [loadingSide, setLoadingSide] = useState(true);

  // ── Side rails: orders, follows, categories, recent searches ─────────────
  useEffect(() => {
    let alive = true;
    (async () => {
      const [o, f, fa, h] = await Promise.allSettled([
        buyerService.orders(),
        socialService.favorites(),
        productService.facets(),
        socialService.searchHistory(),
      ]);
      if (!alive) return;
      if (o.status === 'fulfilled') setOrders(o.value.orders);
      if (f.status === 'fulfilled') setFollowing(f.value.sellers);
      if (fa.status === 'fulfilled') setFacets(fa.value);
      if (h.status === 'fulfilled') {
        const seen = new Set<string>();
        setRecent(
          h.value.history
            .filter((x) => (seen.has(x.query) ? false : (seen.add(x.query), true)))
            .slice(0, 6)
            .map((x) => ({ id: x.id, query: x.query }))
        );
      }
      setLoadingSide(false);
    })();
    return () => { alive = false; };
  }, []);

  // ── Main feed ────────────────────────────────────────────────────────────
  const loadFeed = useCallback(async (which: Feed) => {
    setLoadingFeed(true);
    setFeedError('');
    try {
      if (which === 'following') {
        const r = await socialService.favoritesFeed(24);
        setProducts(r.products);
      } else if (which === 'flash') {
        const r = await productService.list({ flashOnly: true, pageSize: 24, sort: 'newest' });
        setProducts(r.products);
      } else if (which === 'trending') {
        const r = await productService.list({ sort: 'popular', pageSize: 24 });
        setProducts(r.products);
      } else {
        const r = await productService.list({ sort: 'newest', pageSize: 24, inStock: true });
        setProducts(r.products);
      }
    } catch (e: any) {
      setFeedError(e?.message || 'Could not load products');
    } finally {
      setLoadingFeed(false);
    }
  }, []);

  useEffect(() => { void loadFeed(feed); }, [feed, loadFeed]);

  const activeOrders = useMemo(
    () => orders.filter((o) => !['delivered', 'cancelled', 'refunded'].includes((o.status || '').toLowerCase())),
    [orders]
  );
  const totalSpent = useMemo(
    () => orders.filter((o) => ['paid', 'shipped', 'delivered'].includes((o.status || '').toLowerCase()))
      .reduce((s, o) => s + (o.amount || 0), 0),
    [orders]
  );
  const newFromFollows = useMemo(() => following.reduce((s, f) => s + (f.newThisWeek || 0), 0), [following]);

  const firstName = (user?.displayName || 'there').split(' ')[0];
  const hour = new Date().getHours();
  const greeting = hour < 12 ? 'Good morning' : hour < 17 ? 'Good afternoon' : 'Good evening';

  return (
    <div className="col-lg">
      {/* ── Hero: greeting + AI entry point ─────────────────────────────── */}
      <section className="hero anim-up">
        <div className="hero-inner">
          <div className="grow" style={{ minWidth: 260 }}>
            <span className="badge badge-primary mb-8">{greeting}</span>
            <h1 className="hero-title">Hi {firstName} 👋</h1>
            <p className="hero-sub">
              {activeOrders.length > 0
                ? `You have ${activeOrders.length} order${activeOrders.length > 1 ? 's' : ''} on the way.`
                : 'Ask the AI shopper anything — it knows every product in the store.'}
            </p>
            <div className="row wrap mt-16">
              <Btn variant="primary" icon={<Sparkles size={16} />} onClick={() => navigate('/buyer/ai')}>
                Ask AI shopper
              </Btn>
              <Btn icon={<Search size={16} />} onClick={() => navigate('/search')}>Browse catalogue</Btn>
              <Btn icon={<MapPin size={16} />} onClick={() => navigate('/nearby')}>Stores near me</Btn>
            </div>
          </div>

          {cart.itemCount > 0 && (
            <Link to="/cart" className="hero-cart card-hover">
              <div className="row-between">
                <span className="tiny semi muted">Your cart</span>
                <ShoppingCart size={16} className="t-primary" />
              </div>
              <div className="stat-value" style={{ fontSize: 24 }}>{formatUgx(cart.subtotalMinor)}</div>
              <span className="tiny muted">{cart.itemCount} item{cart.itemCount > 1 ? 's' : ''} · tap to check out</span>
            </Link>
          )}
        </div>
      </section>

      {/* ── Stats ───────────────────────────────────────────────────────── */}
      {loadingSide ? (
        <SkeletonStats />
      ) : (
        <div className="grid grid-4 stagger">
          <StatCard index={0} label="Active orders" value={<CountUp value={activeOrders.length} />}
            icon={<ShoppingBag size={18} />} color="linear-gradient(135deg, var(--primary), var(--purple))" hint={`${orders.length} lifetime`} />
          <StatCard index={1} label="Total spent" value={formatUgx(totalSpent)}
            icon={<TrendingUp size={18} />} color="linear-gradient(135deg, var(--success), var(--primary))" hint="Paid orders" />
          <StatCard index={2} label="Saved items" value={<CountUp value={savedIds.size} />}
            icon={<Heart size={18} />} color="linear-gradient(135deg, var(--pink), var(--warning))" hint="In your wishlist" />
          <StatCard index={3} label="Following" value={<CountUp value={following.length} />}
            icon={<Store size={18} />} color="linear-gradient(135deg, var(--purple), var(--pink))"
            hint={newFromFollows > 0 ? `${newFromFollows} new this week` : 'Sellers you follow'} />
        </div>
      )}

      {/* ── Active orders strip ─────────────────────────────────────────── */}
      {activeOrders.length > 0 && (
        <section className="card">
          <div className="card-head">
            <h2 className="card-title"><Package size={17} /> On the way</h2>
            <Link to="/buyer/orders" className="link-arrow">All orders <ArrowRight size={14} /></Link>
          </div>
          <div className="hscroll">
            {activeOrders.slice(0, 6).map((o, i) => (
              <Link key={o.id} to="/buyer/orders" className="track-card stagger-item" style={{ '--i': i } as React.CSSProperties}>
                {o.imageUrl && <img src={o.imageUrl} alt="" className="track-thumb" loading="lazy" />}
                <div style={{ minWidth: 0 }}>
                  <div className="ellipsis semi">{o.title}</div>
                  <div className="tiny muted">{o.storeName || 'Seller'} · {formatUgx(o.amount)}</div>
                  <span className={`badge badge-${o.status === 'shipped' ? 'cyan' : 'amber'} mt-4`}>{o.status}</span>
                </div>
              </Link>
            ))}
          </div>
        </section>
      )}

      {/* ── Categories ──────────────────────────────────────────────────── */}
      {facets && facets.categories.length > 0 && (
        <section className="card">
          <div className="card-head">
            <h2 className="card-title">Shop by category</h2>
            <Link to="/search" className="link-arrow">See all <ArrowRight size={14} /></Link>
          </div>
          <div className="cat-grid stagger">
            {facets.categories.slice(0, 10).map((c, i) => (
              <Link key={c.name} to={`/search?category=${encodeURIComponent(c.name)}`}
                className="cat-tile stagger-item" style={{ '--i': i } as React.CSSProperties}>
                <span className="cat-emoji">{CATEGORY_ICONS[c.name] ?? '🛍️'}</span>
                <span className="cat-name">{c.name}</span>
                <span className="tiny muted-2">{c.count} item{c.count === 1 ? '' : 's'}</span>
              </Link>
            ))}
          </div>
        </section>
      )}

      {/* ── Recent searches ─────────────────────────────────────────────── */}
      {recent.length > 0 && (
        <section className="row wrap" style={{ gap: 8, alignItems: 'center' }}>
          <span className="tiny semi muted"><Clock size={13} style={{ verticalAlign: -2 }} /> Recent:</span>
          {recent.map((r) => (
            <button key={r.id} className="chip" onClick={() => navigate(`/search?q=${encodeURIComponent(r.query)}`)}>
              {r.query}
            </button>
          ))}
          <button className="chip chip-ghost" onClick={async () => {
            await socialService.clearSearchHistory().catch(() => undefined);
            setRecent([]);
            toast('Search history cleared', 'success');
          }}>Clear</button>
        </section>
      )}

      {/* ── Main feed ───────────────────────────────────────────────────── */}
      <section>
        <div className="row-between wrap mb-12">
          <Tabs<Feed>
            active={feed}
            onChange={setFeed}
            tabs={[
              { id: 'for-you', label: 'For you' },
              { id: 'flash', label: '⚡ Flash deals' },
              { id: 'trending', label: 'Trending' },
              { id: 'following', label: 'From sellers you follow', count: newFromFollows },
            ]}
          />
        </div>

        {loadingFeed ? (
          <SkeletonGrid count={8} />
        ) : feedError ? (
          <ErrorBox message={feedError} onRetry={() => loadFeed(feed)} />
        ) : products.length === 0 ? (
          <Empty
            icon={feed === 'following' ? <Store size={28} /> : <Flame size={28} />}
            title={feed === 'following' ? 'No products from your sellers yet' : 'Nothing here right now'}
            subtitle={
              feed === 'following'
                ? 'Follow sellers to get their new listings here — and a push notification the moment they post.'
                : 'Try another tab or browse the full catalogue.'
            }
            action={<Btn variant="primary" onClick={() => navigate('/search')}>Browse catalogue</Btn>}
          />
        ) : (
          <ProductGrid
            products={products}
            onAddToCart={(p) => void add(p)}
            onToggleFavorite={(p) => void toggleFavoriteSeller(p.seller.id, p.seller.name)}
            favoriteSellerIds={favoriteSellerIds}
          />
        )}
      </section>

      {/* ── Sellers you follow ──────────────────────────────────────────── */}
      {following.length > 0 && (
        <section className="card">
          <div className="card-head">
            <h2 className="card-title"><Bell size={17} /> Sellers you follow</h2>
            <Link to="/buyer/saved" className="link-arrow">Manage <ArrowRight size={14} /></Link>
          </div>
          <div className="hscroll">
            {following.map((s, i) => (
              <Link key={s.id} to={`/seller/${s.id}`} className="seller-chip stagger-item" style={{ '--i': i } as React.CSSProperties}>
                <span className="avatar avatar-lg">
                  {s.logoUrl ? <img src={s.logoUrl} alt="" /> : (s.storeName || 'S')[0].toUpperCase()}
                </span>
                <div style={{ minWidth: 0 }}>
                  <div className="ellipsis semi">{s.storeName}</div>
                  <div className="tiny muted">
                    <Star size={10} style={{ verticalAlign: -1, color: 'var(--warning)' }} fill="currentColor" />
                    {' '}{Number(s.rating || 0).toFixed(1)} · {s.productCount} products
                  </div>
                  {s.newThisWeek > 0 && <span className="badge badge-green mt-4">{s.newThisWeek} new</span>}
                </div>
              </Link>
            ))}
          </div>
        </section>
      )}
    </div>
  );
}
