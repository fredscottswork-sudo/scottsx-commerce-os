/**
 * MainNav — the site-wide horizontal navigation bar.
 *
 * Rendered directly under the topbar in BOTH the public (logged-out) and the
 * authenticated shell, so every destination in the marketplace is one click
 * away regardless of who is looking. Three parts:
 *
 *   • a category mega-menu driven by the LIVE facet counts from the backend
 *     (so it can never drift from the catalogue),
 *   • primary destinations (Market / Deals / Nearby / AI / role dashboards),
 *   • a mobile bottom bar mirroring the Android app's bottom nav.
 */
import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { Link, NavLink, useLocation } from 'react-router-dom';
import {
  ChevronDown, Grid3x3, Heart, Home, LayoutDashboard, MapPin, MessageCircle,
  Package, Search, ShieldCheck, ShoppingBag, ShoppingCart, Sparkles, Store, Tag,
  User,
} from 'lucide-react';
import { productService } from '../api/services';
import type { Facets } from '../api/types';
import { categoryIcon, mergedCategories } from './categories';

/** Facets change rarely; fetch once per page load and share across mounts. */
let facetCache: Facets | null = null;
let facetPromise: Promise<Facets> | null = null;
function loadFacets(): Promise<Facets> {
  if (facetCache) return Promise.resolve(facetCache);
  if (!facetPromise) {
    facetPromise = productService.facets()
      .then((f) => { facetCache = f; return f; })
      .catch((e) => { facetPromise = null; throw e; });
  }
  return facetPromise;
}

export interface MainNavCounts {
  cart: number;
  messages: number;
  notifications: number;
}

interface Props {
  role: 'buyer' | 'seller' | 'admin' | null;
  counts: MainNavCounts;
}

/* ── Desktop / tablet horizontal bar ─────────────────────────────────────── */
export function MainNav({ role, counts }: Props) {
  const [facets, setFacets] = useState<Facets | null>(facetCache);
  const [open, setOpen] = useState(false);
  const location = useLocation();
  const wrapRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => { loadFacets().then(setFacets).catch(() => undefined); }, []);
  useEffect(() => { setOpen(false); }, [location.pathname, location.search]);

  // Dismiss the mega-menu on outside click / Escape.
  useEffect(() => {
    if (!open) return;
    const onDown = (e: MouseEvent) => {
      if (wrapRef.current && !wrapRef.current.contains(e.target as Node)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') setOpen(false); };
    document.addEventListener('mousedown', onDown);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onDown);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  // Merge the LIVE facet categories with the fixed 16 so the mega-menu grid
  // stays even and complete even when the DB has no listings in a category.
  const categories = useMemo(
    () => (facets ? mergedCategories(facets.categories) : []),
    [facets]
  );
  const brands = useMemo(() => (facets?.brands ?? []).slice(0, 8), [facets]);
  const totalItems = useMemo(
    () => categories.reduce((s, c) => s + (c.count || 0), 0),
    [categories]
  );

  const links: { to: string; label: string; icon: ReactNode; end?: boolean }[] = [
    { to: '/', label: 'Market', icon: <Store size={15} />, end: true },
    // Keep Deals on the catalog's validated sort union. `flashOnly` is the
    // actual deal filter; `discount` was never accepted by the API and made
    // this link fail before the results page could render.
    { to: '/search?flashOnly=1&sort=relevance', label: 'Deals', icon: <Tag size={15} /> },
    { to: '/nearby', label: 'Nearby', icon: <MapPin size={15} /> },
    { to: role === 'buyer' ? '/buyer/ai' : role === 'seller' ? '/seller/ai' : '/ai', label: 'AI', icon: <Sparkles size={15} /> },
  ];

  if (role === 'buyer') {
    links.push(
      { to: '/buyer', label: 'Dashboard', icon: <LayoutDashboard size={15} />, end: true },
      { to: '/buyer/orders', label: 'Orders', icon: <ShoppingBag size={15} /> },
      { to: '/buyer/saved', label: 'Saved', icon: <Heart size={15} /> },
    );
  } else if (role === 'seller') {
    links.push(
      { to: '/seller', label: 'Dashboard', icon: <LayoutDashboard size={15} />, end: true },
      { to: '/seller/inventory', label: 'Inventory', icon: <Package size={15} /> },
      { to: '/seller/orders', label: 'Orders', icon: <ShoppingBag size={15} /> },
    );
  } else if (role === 'admin') {
    links.push(
      { to: '/admin', label: 'Overview', icon: <LayoutDashboard size={15} />, end: true },
      { to: '/admin/queue', label: 'Approvals', icon: <ShieldCheck size={15} /> },
      { to: '/admin/users', label: 'Users', icon: <User size={15} /> },
    );
  }

  return (
    <div className="mainnav" data-testid="mainnav">
      <div className="mainnav-inner">
        <div className="mainnav-cats-wrap" ref={wrapRef}>
          <button
            type="button"
            className={`mainnav-cats ${open ? 'open' : ''}`}
            aria-expanded={open}
            aria-haspopup="true"
            aria-controls="mainnav-mega"
            onClick={() => setOpen((v) => !v)}
          >
            <Grid3x3 size={16} />
            <span>All categories</span>
            <ChevronDown size={15} className="mainnav-caret" />
          </button>

          {open && (
            <div className="mainnav-mega" id="mainnav-mega" role="menu">
              <div className="mainnav-mega-grid">
                {categories.length === 0 && <span className="muted tiny">Loading categories…</span>}
                {categories.map((c) => (
                  <Link
                    key={c.name}
                    role="menuitem"
                    className="mega-item"
                    to={`/search?category=${encodeURIComponent(c.name)}`}
                  >
                    <span className="mega-ico">{categoryIcon(c.name)}</span>
                    <span className="grow ellipsis">{c.name}</span>
                    {c.count > 0 && <span className="mega-count">{c.count}</span>}
                  </Link>
                ))}
              </div>

              {brands.length > 0 && (
                <div className="mainnav-mega-side">
                  <div className="mega-heading">Top brands</div>
                  <div className="row wrap" style={{ gap: 6 }}>
                    {brands.map((b) => (
                      <Link
                        key={b.name}
                        className="chip"
                        to={`/search?brand=${encodeURIComponent(b.name)}`}
                        role="menuitem"
                      >
                        {b.name}
                      </Link>
                    ))}
                  </div>
                  <Link to="/search" className="mega-all" role="menuitem">
                    Browse all {totalItems} products →
                  </Link>
                </div>
              )}
            </div>
          )}
        </div>

        <nav className="mainnav-links" aria-label="Primary">
          {links.map((l) => (
            <NavLink
              key={l.to}
              to={l.to}
              end={l.end}
              className={({ isActive }) => `mainnav-link ${isActive ? 'active' : ''}`}
            >
              {l.icon}
              <span>{l.label}</span>
            </NavLink>
          ))}
        </nav>

        <span className="grow" />

        <nav className="mainnav-right" aria-label="Quick actions">
          {role && (
            <NavLink to="/messages" className={({ isActive }) => `mainnav-link ${isActive ? 'active' : ''}`}>
              <MessageCircle size={15} />
              <span>Messages</span>
              {counts.messages > 0 && <span className="badge badge-red">{counts.messages > 99 ? '99+' : counts.messages}</span>}
            </NavLink>
          )}
          {role === 'buyer' && (
            <NavLink to="/cart" className={({ isActive }) => `mainnav-link ${isActive ? 'active' : ''}`}>
              <ShoppingCart size={15} />
              <span>Cart</span>
              {counts.cart > 0 && <span className="badge badge-blue">{counts.cart > 99 ? '99+' : counts.cart}</span>}
            </NavLink>
          )}
          {role === 'seller' && (
            <Link to="/seller/add-product" className="mainnav-link mainnav-cta">
              <Package size={15} /> <span>Add product</span>
            </Link>
          )}
          {!role && (
            <>
              <Link to="/cart" className={`mainnav-link ${location.pathname === '/cart' ? 'active' : ''}`}>
                <ShoppingCart size={15} />
                <span>Cart</span>
                {counts.cart > 0 && <span className="badge badge-blue">{counts.cart > 99 ? '99+' : counts.cart}</span>}
              </Link>
              <Link to="/register" className="mainnav-link mainnav-cta">
                <Store size={15} /> <span>Sell on ScottsTechX</span>
              </Link>
            </>
          )}
        </nav>
      </div>
    </div>
  );
}

/* ── Mobile bottom bar (mirrors the Android bottom nav) ──────────────────── */
export function BottomNav({ role, counts }: Props) {
  const items = useMemo(() => {
    const home = role === 'admin' ? '/admin' : role === 'seller' ? '/seller' : role === 'buyer' ? '/buyer' : '/';
    const ai = role === 'seller' ? '/seller/ai' : role === 'buyer' ? '/buyer/ai' : '/ai';
    const account = role === 'admin' ? '/admin/users' : role === 'seller' ? '/seller/store-settings' : role === 'buyer' ? '/buyer/settings' : '/login';

    // Keep the same five destinations on every phone. Role-specific entry
    // points are appended rather than replacing Nearby, AI, or Account: the
    // old role-specific arrays made whole sections disappear on mobile and
    // left buyers/admins without the links their desktop nav exposed.
    const base = [
      { to: home, label: 'Home', icon: <Home size={19} />, end: true, badge: 0 },
      { to: '/search', label: 'Search', icon: <Search size={19} />, badge: 0 },
      { to: '/nearby', label: 'Nearby', icon: <MapPin size={19} />, badge: 0 },
      { to: ai, label: 'AI', icon: <Sparkles size={19} />, badge: 0 },
      { to: account, label: 'Account', icon: <User size={19} />, badge: 0 },
    ];

    // Buyers and guests both get a cart (guests hold it locally until sign-in);
    // sellers/admin keep just their role entry points.
    if (role === 'buyer' || role === null) {
      base.push({ to: '/cart', label: 'Cart', icon: <ShoppingCart size={19} />, badge: counts.cart });
    } else if (role === 'admin') {
      base.push({ to: '/admin/queue', label: 'Queue', icon: <ShieldCheck size={19} />, badge: 0 });
    }
    return base;
  }, [role, counts.cart]);

  return (
    <nav className="bottomnav" aria-label="Mobile navigation" data-testid="bottomnav">
      {items.map((i) => (
        <NavLink
          key={i.to}
          to={i.to}
          end={i.end}
          className={({ isActive }) => `bottomnav-item ${isActive ? 'active' : ''}`}
        >
          <span className="bottomnav-ico">
            {i.icon}
            {i.badge > 0 && <span className="icon-badge">{i.badge > 9 ? '9+' : i.badge}</span>}
          </span>
          <span className="bottomnav-label">{i.label}</span>
        </NavLink>
      ))}
    </nav>
  );
}
