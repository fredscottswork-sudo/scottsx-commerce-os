import { useEffect, useState, type ReactNode } from 'react';
import { Link, NavLink, useNavigate, useLocation } from 'react-router-dom';
import {
  LayoutDashboard, Package, ShoppingBag, Heart, MapPin, MessageCircle, Bell,
  Settings, Store, BarChart3, Users, Menu, LogOut, Sun, Moon, Monitor, PlusCircle,
  Megaphone, FileText, Receipt, Wallet, LifeBuoy, Sparkles, ShieldCheck,
} from 'lucide-react';
import { useAuth } from '../store/AuthContext';
import { useTheme } from '../store/ThemeContext';
import { chatService } from '../api/services';
import { useEffect as _useEffect } from 'react';

interface NavItem { to: string; label: string; icon: ReactNode; end?: boolean }

export function AppShell({ children }: { children: ReactNode }) {
  const { user, logout } = useAuth();
  const { mode, resolved, setMode } = useTheme();
  const navigate = useNavigate();
  const location = useLocation();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [unread, setUnread] = useState(0);

  useEffect(() => {
    setDrawerOpen(false);
  }, [location.pathname]);

  useEffect(() => {
    if (!user) return;
    const poll = () => chatService.conversations().then((r) => {
      setUnread(r.conversations.reduce((s, c) => s + (c.unread || 0), 0));
    }).catch(() => undefined);
    poll();
    const t = setInterval(poll, 15000);
    return () => clearInterval(t);
  }, [user]);

  if (!user) return <>{children}</>;

  const nav: NavItem[] = user.role === 'admin'
    ? [
        { to: '/admin', label: 'Overview', icon: <LayoutDashboard size={17} />, end: true },
        { to: '/admin/users', label: 'Users', icon: <Users size={17} /> },
        { to: '/admin/products', label: 'Products', icon: <Package size={17} /> },
        { to: '/admin/messages', label: 'Messages', icon: <MessageCircle size={17} />, end: true },
      ]
    : user.role === 'seller'
      ? [
          { to: '/seller', label: 'Dashboard', icon: <LayoutDashboard size={17} />, end: true },
          { to: '/seller/inventory', label: 'Inventory', icon: <Package size={17} /> },
          { to: '/seller/add-product', label: 'Add product', icon: <PlusCircle size={17} /> },
          { to: '/seller/orders', label: 'Orders', icon: <ShoppingBag size={17} /> },
          { to: '/seller/analytics', label: 'Analytics', icon: <BarChart3 size={17} /> },
          { to: '/seller/ai', label: 'AI Assistant', icon: <Sparkles size={17} /> },
          { to: '/seller/store-settings', label: 'Store settings', icon: <Settings size={17} /> },
          { to: '/messages', label: 'Messages', icon: <MessageCircle size={17} /> },
          { to: '/notifications', label: 'Notifications', icon: <Bell size={17} /> },
        ]
      : [
          { to: '/buyer', label: 'Dashboard', icon: <LayoutDashboard size={17} />, end: true },
          { to: '/buyer/orders', label: 'Orders', icon: <ShoppingBag size={17} /> },
          { to: '/buyer/saved', label: 'Saved', icon: <Heart size={17} /> },
          { to: '/buyer/payments', label: 'Payments', icon: <Wallet size={17} /> },
          { to: '/buyer/addresses', label: 'Addresses', icon: <MapPin size={17} /> },
          { to: '/buyer/refunds', label: 'Refunds', icon: <Receipt size={17} /> },
          { to: '/buyer/support', label: 'Support', icon: <LifeBuoy size={17} /> },
          { to: '/messages', label: 'Messages', icon: <MessageCircle size={17} /> },
          { to: '/notifications', label: 'Notifications', icon: <Bell size={17} /> },
          { to: '/buyer/ai', label: 'AI Assistant', icon: <Sparkles size={17} /> },
          { to: '/buyer/settings', label: 'Settings', icon: <Settings size={17} /> },
        ];

  const brand = (
    <Link to="/" className="brand" style={{ textDecoration: 'none' }}>
      <span className="brand-logo">🛍️</span> ScottsTechX
    </Link>
  );

  const navList = (
    <>
      {nav.map((n) => (
        <NavLink key={n.to} to={n.to} end={n.end} className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
          {n.icon}
          <span className="grow">{n.label}</span>
          {n.label === 'Messages' && unread > 0 && <span className="badge badge-red">{unread}</span>}
        </NavLink>
      ))}
      <div className="nav-section">Public</div>
      <NavLink to="/" end className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}><Store size={17} /> Marketplace</NavLink>
      <NavLink to="/nearby" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}><MapPin size={17} /> Nearby</NavLink>
    </>
  );

  const footer = (
    <div className="sidebar-footer">
      <div className="row mb-8">
        <span className="avatar" style={{ width: 32, height: 32, fontSize: 13 }}>
          {user.profilePhotoUrl ? <img src={user.profilePhotoUrl} alt="" /> : (user.displayName || 'U')[0].toUpperCase()}
        </span>
        <div style={{ minWidth: 0 }}>
          <div style={{ fontWeight: 600, fontSize: 13.5, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{user.displayName || user.email}</div>
          <div className="muted" style={{ fontSize: 12, textTransform: 'capitalize' }}>{user.role}</div>
        </div>
      </div>
      <div className="row" style={{ gap: 6 }}>
        <button className="btn btn-sm" title="Light" onClick={() => setMode('light')}><Sun size={15} /></button>
        <button className="btn btn-sm" title="Dark" onClick={() => setMode('dark')}><Moon size={15} /></button>
        <button className="btn btn-sm" title="System" onClick={() => setMode('system')}><Monitor size={15} /></button>
        <button className="btn btn-sm" title="Log out" onClick={() => { logout(); navigate('/'); }}><LogOut size={15} /></button>
      </div>
    </div>
  );

  return (
    <div className="app-shell">
      <aside className={`sidebar ${drawerOpen ? 'open' : ''}`} aria-label="Navigation">
        {brand}
        <nav>{navList}</nav>
        {footer}
      </aside>
      {drawerOpen && <div className="modal-backdrop" style={{ alignItems: 'flex-start' }} onClick={() => setDrawerOpen(false)} />}
      <div className="main">
        <header className="topbar">
          <button className="btn btn-sm" aria-label="Menu" onClick={() => setDrawerOpen(true)} style={{ display: 'grid', placeItems: 'center', width: 36, padding: 6 }}>
            <Menu size={19} />
          </button>
          <Link to="/" style={{ fontWeight: 700, color: 'var(--text)', textDecoration: 'none', fontSize: 16 }}>ScottsTechX</Link>
          <span className="grow" />
          <Link to="/buyer/settings" className="btn btn-sm" title="Settings"><Settings size={16} /></Link>
          <Link to="/notifications" className="btn btn-sm" title="Notifications" style={{ position: 'relative' }}>
            <Bell size={16} />
            {unread > 0 && <span className="badge badge-red" style={{ position: 'absolute', top: -5, right: -5 }}>{unread}</span>}
          </Link>
        </header>
        <main className="content">{children}</main>
      </div>
    </div>
  );
}
