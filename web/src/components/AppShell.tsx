import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react';
import { Link, NavLink, useNavigate, useLocation } from 'react-router-dom';
import {
  LayoutDashboard, Package, ShoppingBag, Heart, MapPin, MessageCircle, Bell,
  Settings, Store, BarChart3, Users, LogOut, Sun, Moon, PlusCircle,
  Receipt, LifeBuoy, Sparkles, ShieldCheck, ShoppingCart, X, Search,
  Upload, ClipboardCheck, Headphones, Camera, PanelLeftClose, PanelLeftOpen,
} from 'lucide-react';
import { useAuth } from '../store/AuthContext';
import { useTheme } from '../store/ThemeContext';
import { useCart } from '../store/CartContext';
import { buyerService, chatService } from '../api/services';
import { MainNav, BottomNav } from './MainNav';
import { VisualSearch } from './VisualSearch';
import { Modal, Btn } from './ui';
import { stashImageSearchResult } from '../lib/imageSearch';
import { SEARCH_WATERMARKS, useRotatingPlaceholder } from '../hooks/useRotatingPlaceholder';

/** Remembers the desktop sidebar preference across visits. */
const SIDEBAR_KEY = 'stx:sidebar-hidden';
function readSidebarHidden(): boolean {
  try { return localStorage.getItem(SIDEBAR_KEY) === '1'; } catch { return false; }
}

interface NavItem { to: string; label: string; icon: ReactNode; end?: boolean; badge?: number }

export function AppShell({ children }: { children: ReactNode }) {
  const { user, logout } = useAuth();
  const { resolved, toggle } = useTheme();
  const { cart } = useCart();
  const navigate = useNavigate();
  const location = useLocation();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [sidebarHidden, setSidebarHidden] = useState(readSidebarHidden);
  const [imgSearchOpen, setImgSearchOpen] = useState(false);
  const [unreadMsgs, setUnreadMsgs] = useState(0);
  const [unreadNotifs, setUnreadNotifs] = useState(0);
  const [query, setQuery] = useState('');
  const watermark = useRotatingPlaceholder(SEARCH_WATERMARKS);
  const mounted = useRef(true);

  useEffect(() => () => { mounted.current = false; }, []);
  useEffect(() => { setDrawerOpen(false); }, [location.pathname]);

  // Desktop: the top menu button collapses the whole sidebar so content gets
  // the full width — pressed again and it comes back. Mobile: same button
  // opens the slide-in drawer (unchanged behaviour).
  const toggleSidebar = useCallback(() => {
    if (typeof window !== 'undefined' && window.matchMedia('(max-width: 960px)').matches) {
      setDrawerOpen(true);
      return;
    }
    setSidebarHidden((v) => {
      const next = !v;
      try { localStorage.setItem(SIDEBAR_KEY, next ? '1' : '0'); } catch { /* ignore */ }
      return next;
    });
  }, []);

  const onImageResults = useCallback((r: any) => {
    stashImageSearchResult(r);
    setImgSearchOpen(false);
    navigate('/search?img=1');
  }, [navigate]);

  // Poll the two badge counters. Cheap endpoints, 20s cadence, paused when the
  // tab is hidden so a backgrounded dashboard costs nothing.
  const poll = useCallback(async () => {
    if (!user || document.hidden) return;
    const [c, n] = await Promise.allSettled([
      chatService.conversations(),
      buyerService.unreadCount(),
    ]);
    if (!mounted.current) return;
    if (c.status === 'fulfilled') {
      setUnreadMsgs(c.value.conversations.reduce((s, x) => s + (x.unread || 0), 0));
    }
    if (n.status === 'fulfilled') setUnreadNotifs(n.value.unread);
  }, [user]);

  useEffect(() => {
    if (!user) { setUnreadMsgs(0); setUnreadNotifs(0); return; }
    void poll();
    const t = setInterval(() => { void poll(); }, 20000);
    const onVis = () => { if (!document.hidden) void poll(); };
    document.addEventListener('visibilitychange', onVis);
    window.addEventListener('stx:refresh-badges', poll as EventListener);
    return () => {
      clearInterval(t);
      document.removeEventListener('visibilitychange', onVis);
      window.removeEventListener('stx:refresh-badges', poll as EventListener);
    };
  }, [user, poll]);

  const submitSearch = (e: React.FormEvent) => {
    e.preventDefault();
    navigate(`/search?q=${encodeURIComponent(query.trim())}`);
  };

  // ── Public chrome (logged out) ────────────────────────────────────────────
  if (!user) {
    return (
      <div className="public-shell">
        <header className="public-topbar">
          <Link to="/" className="brand"><span className="brand-logo">S</span> ScottsTechX</Link>
          <form className="searchbar public-search searchbar-glow" onSubmit={submitSearch}>
            <Search size={18} className="muted-2" />
            <input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder={watermark}
              aria-label="Search products"
            />
            <button type="button" className="btn btn-icon search-cam" onClick={() => setImgSearchOpen(true)}
              title="Search by image" aria-label="Search by image">
              <Camera size={17} />
            </button>
          </form>
          <span className="grow" />
          <nav className="row public-links">
            <NavLink to="/" end className={({ isActive }) => `top-link ${isActive ? 'active' : ''}`}>Market</NavLink>
            <NavLink to="/nearby" className={({ isActive }) => `top-link ${isActive ? 'active' : ''}`}>Nearby</NavLink>
            <NavLink to="/ai" className={({ isActive }) => `top-link ${isActive ? 'active' : ''}`}>AI Shopper</NavLink>
          </nav>
          <Link to="/cart" className="btn btn-icon icon-badge-wrap" title="Your cart" aria-label="Your cart">
            <ShoppingCart size={17} />
            {cart.itemCount > 0 && <span className="icon-badge">{cart.itemCount > 9 ? '9+' : cart.itemCount}</span>}
          </Link>
          <button className="btn btn-icon" onClick={toggle} aria-label="Toggle theme" title="Toggle dark / light">
            {resolved === 'dark' ? <Sun size={16} /> : <Moon size={16} />}
          </button>
          <Link to="/login" className="btn btn-sm">Sign in</Link>
          <Link to="/register" className="btn btn-primary btn-sm join-cta">Get started</Link>
        </header>
        <MainNav role={null} counts={{ cart: cart.itemCount, messages: 0, notifications: 0 }} />
        <main className="public-content">{children}</main>
        <footer className="public-footer">
          <div className="row wrap" style={{ gap: 18, justifyContent: 'center' }}>
            <Link to="/cms/about" className="muted">About</Link>
            <Link to="/cms/terms" className="muted">Terms</Link>
            <Link to="/cms/privacy" className="muted">Privacy</Link>
            <Link to="/cms/buyer-protection" className="muted">Buyer protection</Link>
          </div>
          <p className="tiny muted-2 center mt-8">© {new Date().getFullYear()} ScottsTechX · Kampala, Uganda</p>
        </footer>
        <BottomNav role={null} counts={{ cart: cart.itemCount, messages: 0, notifications: 0 }} />

        <Modal open={imgSearchOpen} onClose={() => setImgSearchOpen(false)} title="Search by image"
          footer={<Btn onClick={() => setImgSearchOpen(false)}>Close</Btn>}>
          <VisualSearch
            compact
            showResults={false}
            onResults={(r) => { onImageResults(r); }}
          />
        </Modal>
      </div>
    );
  }

  // ── Authenticated chrome ──────────────────────────────────────────────────
  const nav: NavItem[] =
    user.role === 'admin'
      ? [
          { to: '/admin', label: 'Overview', icon: <LayoutDashboard size={17} />, end: true },
          { to: '/admin/queue', label: 'Approval queue', icon: <ClipboardCheck size={17} /> },
          { to: '/admin/products', label: 'Products', icon: <Package size={17} /> },
          { to: '/admin/users', label: 'Users', icon: <Users size={17} /> },
          { to: '/admin/support', label: 'Support desk', icon: <Headphones size={17} /> },
          { to: '/messages', label: 'Messages', icon: <MessageCircle size={17} />, badge: unreadMsgs },
          { to: '/notifications', label: 'Notifications', icon: <Bell size={17} />, badge: unreadNotifs },
        ]
      : user.role === 'seller'
        ? [
            { to: '/seller', label: 'Dashboard', icon: <LayoutDashboard size={17} />, end: true },
            { to: '/seller/inventory', label: 'Inventory', icon: <Package size={17} /> },
            { to: '/seller/add-product', label: 'Add product', icon: <PlusCircle size={17} /> },
            { to: '/seller/bulk-import', label: 'Bulk import', icon: <Upload size={17} /> },
            { to: '/seller/orders', label: 'Orders', icon: <ShoppingBag size={17} /> },
            { to: '/seller/analytics', label: 'Analytics', icon: <BarChart3 size={17} /> },
            { to: '/seller/ai', label: 'AI copilot', icon: <Sparkles size={17} /> },
            { to: '/seller/store-settings', label: 'Store settings', icon: <Settings size={17} /> },
            { to: '/messages', label: 'Messages', icon: <MessageCircle size={17} />, badge: unreadMsgs },
            { to: '/notifications', label: 'Notifications', icon: <Bell size={17} />, badge: unreadNotifs },
          ]
        : [
            { to: '/buyer', label: 'Dashboard', icon: <LayoutDashboard size={17} />, end: true },
            { to: '/buyer/ai', label: 'AI shopper', icon: <Sparkles size={17} /> },
            { to: '/cart', label: 'Cart', icon: <ShoppingCart size={17} />, badge: cart.itemCount },
            { to: '/buyer/orders', label: 'Orders', icon: <ShoppingBag size={17} /> },
            { to: '/buyer/saved', label: 'Saved & following', icon: <Heart size={17} /> },
            { to: '/messages', label: 'Messages', icon: <MessageCircle size={17} />, badge: unreadMsgs },
            { to: '/notifications', label: 'Notifications', icon: <Bell size={17} />, badge: unreadNotifs },
            { to: '/buyer/addresses', label: 'Addresses', icon: <MapPin size={17} /> },
            { to: '/buyer/refunds', label: 'Refunds', icon: <Receipt size={17} /> },
            { to: '/buyer/support', label: 'Support', icon: <LifeBuoy size={17} /> },
            { to: '/buyer/settings', label: 'Settings', icon: <Settings size={17} /> },
          ];

  const roleHome = user.role === 'admin' ? '/admin' : user.role === 'seller' ? '/seller' : '/buyer';
  const navCounts = { cart: cart.itemCount, messages: unreadMsgs, notifications: unreadNotifs };

  const sidebar = (
    <>
      <div className="sidebar-head">
        <Link to={roleHome} className="brand"><span className="brand-logo">S</span> ScottsTechX</Link>
        <button className="btn btn-ghost btn-icon drawer-close" onClick={() => setDrawerOpen(false)} aria-label="Close menu">
          <X size={18} />
        </button>
      </div>

      <nav className="sidebar-nav">
        {nav.map((n) => (
          <NavLink key={n.to} to={n.to} end={n.end} className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
            {n.icon}
            <span className="grow">{n.label}</span>
            {!!n.badge && n.badge > 0 && <span className="badge badge-red">{n.badge > 99 ? '99+' : n.badge}</span>}
          </NavLink>
        ))}

        <div className="nav-section">Marketplace</div>
        <NavLink to="/" end className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
          <Store size={17} /> <span className="grow">Browse all</span>
        </NavLink>
        <NavLink to="/nearby" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
          <MapPin size={17} /> <span className="grow">Nearby stores</span>
        </NavLink>
        {user.role === 'admin' && (
          <NavLink to="/admin/queue" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
            <ShieldCheck size={17} /> <span className="grow">Moderation</span>
          </NavLink>
        )}
      </nav>

      <div className="sidebar-footer">
        <Link to={user.role === 'buyer' ? '/buyer/settings' : '/seller/store-settings'} className="user-chip">
          <span className="avatar">
            {user.profilePhotoUrl
              ? <img src={user.profilePhotoUrl} alt="" />
              : (user.displayName || user.email || 'U')[0].toUpperCase()}
          </span>
          <span style={{ minWidth: 0 }}>
            <span className="user-chip-name">{user.displayName || user.email}</span>
            <span className="user-chip-role">{user.role}</span>
          </span>
        </Link>
        <div className="row" style={{ gap: 6, marginTop: 10 }}>
          <button className="btn btn-sm grow" onClick={toggle}>
            {resolved === 'dark' ? <Sun size={15} /> : <Moon size={15} />}
            {resolved === 'dark' ? 'Light' : 'Dark'}
          </button>
          <button className="btn btn-sm" title="Log out" aria-label="Log out"
            onClick={() => { logout(); navigate('/'); }}>
            <LogOut size={15} />
          </button>
        </div>
      </div>
    </>
  );

  return (
    <div className={`app-shell ${sidebarHidden ? 'sidebar-hidden' : ''}`}>
      <aside className={`sidebar ${drawerOpen ? 'open' : ''}`} aria-label="Main navigation">{sidebar}</aside>
      {drawerOpen && <div className="drawer-scrim" onClick={() => setDrawerOpen(false)} aria-hidden />}

      <div className="main">
        <header className="topbar">
          <button className="btn btn-icon menu-btn" aria-label={sidebarHidden ? 'Show navigation' : 'Hide navigation'}
            title={sidebarHidden ? 'Show navigation' : 'Hide navigation'}
            onClick={toggleSidebar}>
            {sidebarHidden ? <PanelLeftOpen size={19} /> : <PanelLeftClose size={19} />}
          </button>

          <form className="searchbar topbar-search searchbar-glow" onSubmit={submitSearch}>
            <Search size={18} className="muted-2" />
            <input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder={watermark}
              aria-label="Search products"
            />
            <button type="button" className="btn btn-icon search-cam" onClick={() => setImgSearchOpen(true)}
              title="Search by image" aria-label="Search by image">
              <Camera size={17} />
            </button>
          </form>

          <span className="grow" />

          {user.role === 'buyer' && (
            <Link to="/cart" className="btn btn-icon icon-badge-wrap" title="Cart" aria-label="Cart">
              <ShoppingCart size={17} />
              {cart.itemCount > 0 && <span className="icon-badge">{cart.itemCount > 9 ? '9+' : cart.itemCount}</span>}
            </Link>
          )}
          <Link to="/messages" className="btn btn-icon icon-badge-wrap" title="Messages" aria-label="Messages">
            <MessageCircle size={17} />
            {unreadMsgs > 0 && <span className="icon-badge">{unreadMsgs > 9 ? '9+' : unreadMsgs}</span>}
          </Link>
          <Link to="/notifications" className="btn btn-icon icon-badge-wrap" title="Notifications" aria-label="Notifications">
            <Bell size={17} />
            {unreadNotifs > 0 && <span className="icon-badge">{unreadNotifs > 9 ? '9+' : unreadNotifs}</span>}
          </Link>
          <button className="btn btn-icon" onClick={toggle} aria-label="Toggle theme">
            {resolved === 'dark' ? <Sun size={17} /> : <Moon size={17} />}
          </button>
        </header>

        <MainNav role={user.role} counts={navCounts} />

        <main className="content page" key={location.pathname}>{children}</main>
        <BottomNav role={user.role} counts={navCounts} />
      </div>

      <Modal open={imgSearchOpen} onClose={() => setImgSearchOpen(false)} title="Search by image"
        footer={<Btn onClick={() => setImgSearchOpen(false)}>Close</Btn>}>
        <VisualSearch
          compact
          showResults={false}
          onResults={(r) => { onImageResults(r); }}
        />
      </Modal>
    </div>
  );
}
