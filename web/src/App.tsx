import { useEffect, type ReactNode } from 'react';
import { Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { useAuth } from './store/AuthContext';
import { AppShell } from './components/AppShell';

import Login from './pages/Login';
import Register from './pages/Register';
import Home from './pages/Home';
import ProductDetail from './pages/ProductDetail';
import SellerStorefront from './pages/SellerStorefront';
import Nearby from './pages/Nearby';
import CmsPage from './pages/CmsPage';
import NotFound from './pages/NotFound';

import BuyerDashboard from './pages/buyer/BuyerDashboard';
import Orders from './pages/buyer/Orders';
import Saved from './pages/buyer/Saved';
import Addresses from './pages/buyer/Addresses';
import Payments from './pages/buyer/Payments';
import Refunds from './pages/buyer/Refunds';
import Support from './pages/buyer/Support';
import SettingsPage from './pages/buyer/Settings';
import AiChat from './pages/buyer/AiChat';
import Messages from './pages/Messages';
import Thread from './pages/Thread';
import Notifications from './pages/Notifications';

import SellerDashboard from './pages/seller/SellerDashboard';
import Inventory from './pages/seller/Inventory';
import AddProduct from './pages/seller/AddProduct';
import BulkImport from './pages/seller/BulkImport';
import SellerOrders from './pages/seller/SellerOrders';
import SellerAnalytics from './pages/seller/SellerAnalytics';
import SellerAi from './pages/seller/SellerAi';
import StoreSettingsPage from './pages/seller/StoreSettings';

import AdminDashboard from './pages/admin/AdminDashboard';
import AdminUsers from './pages/admin/AdminUsers';
import AdminProducts from './pages/admin/AdminProducts';

function RequireRole({ role, children }: { role: 'buyer' | 'seller' | 'admin'; children: ReactNode }) {
  const { user } = useAuth();
  const location = useLocation();
  if (!user) return <Navigate to="/login" state={{ from: location.pathname }} replace />;
  if (user.role !== role) {
    // Auto-redirect each role to its own home.
    const home = user.role === 'admin' ? '/admin' : user.role === 'seller' ? '/seller' : '/buyer';
    return <Navigate to={home} replace />;
  }
  return <>{children}</>;
}

function RequireAuth({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  if (!user) return <Navigate to="/login" replace />;
  return <>{children}</>;
}

function RedirectByRole() {
  const { user } = useAuth();
  if (!user) return <Navigate to="/" replace />;
  return <Navigate to={user.role === 'admin' ? '/admin' : user.role === 'seller' ? '/seller' : '/buyer'} replace />;
}

export default function App() {
  const { user } = useAuth();

  // Global 401 → drop to login.
  useEffect(() => {
    const onUnauthorized = () => window.location.assign('/login');
    window.addEventListener('stx:unauthorized', onUnauthorized);
    return () => window.removeEventListener('stx:unauthorized', onUnauthorized);
  }, []);

  return (
    <AppShell>
      <Routes>
        {/* public */}
        <Route path="/" element={<Home />} />
        <Route path="/login" element={user ? <RedirectByRole /> : <Login />} />
        <Route path="/register" element={user ? <RedirectByRole /> : <Register />} />
        <Route path="/product/:id" element={<ProductDetail />} />
        <Route path="/seller/:id" element={<SellerStorefront />} />
        <Route path="/nearby" element={<Nearby />} />
        <Route path="/cms/:slug" element={<CmsPage />} />

        {/* buyer */}
        <Route path="/buyer" element={<RequireRole role="buyer"><BuyerDashboard /></RequireRole>} />
        <Route path="/buyer/orders" element={<RequireRole role="buyer"><Orders /></RequireRole>} />
        <Route path="/buyer/saved" element={<RequireRole role="buyer"><Saved /></RequireRole>} />
        <Route path="/buyer/addresses" element={<RequireRole role="buyer"><Addresses /></RequireRole>} />
        <Route path="/buyer/payments" element={<RequireRole role="buyer"><Payments /></RequireRole>} />
        <Route path="/buyer/refunds" element={<RequireRole role="buyer"><Refunds /></RequireRole>} />
        <Route path="/buyer/support" element={<RequireRole role="buyer"><Support /></RequireRole>} />
        <Route path="/buyer/settings" element={<RequireRole role="buyer"><SettingsPage /></RequireRole>} />
        <Route path="/buyer/ai" element={<RequireRole role="buyer"><AiChat /></RequireRole>} />

        {/* seller */}
        <Route path="/seller" element={<RequireRole role="seller"><SellerDashboard /></RequireRole>} />
        <Route path="/seller/inventory" element={<RequireRole role="seller"><Inventory /></RequireRole>} />
        <Route path="/seller/add-product" element={<RequireRole role="seller"><AddProduct /></RequireRole>} />
        <Route path="/seller/bulk-import" element={<RequireRole role="seller"><BulkImport /></RequireRole>} />
        <Route path="/seller/orders" element={<RequireRole role="seller"><SellerOrders /></RequireRole>} />
        <Route path="/seller/analytics" element={<RequireRole role="seller"><SellerAnalytics /></RequireRole>} />
        <Route path="/seller/ai" element={<RequireRole role="seller"><SellerAi /></RequireRole>} />
        <Route path="/seller/store-settings" element={<RequireRole role="seller"><StoreSettingsPage /></RequireRole>} />

        {/* shared authenticated */}
        <Route path="/messages" element={<RequireAuth><Messages /></RequireAuth>} />
        <Route path="/messages/:id" element={<RequireAuth><Thread /></RequireAuth>} />
        <Route path="/notifications" element={<RequireAuth><Notifications /></RequireAuth>} />

        {/* admin */}
        <Route path="/admin" element={<RequireRole role="admin"><AdminDashboard /></RequireRole>} />
        <Route path="/admin/users" element={<RequireRole role="admin"><AdminUsers /></RequireRole>} />
        <Route path="/admin/products" element={<RequireRole role="admin"><AdminProducts /></RequireRole>} />

        <Route path="*" element={<NotFound />} />
      </Routes>
    </AppShell>
  );
}
