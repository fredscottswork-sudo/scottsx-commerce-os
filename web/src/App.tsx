import { useEffect, type ReactNode, lazy, Suspense } from 'react';
import { Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { useAuth } from './store/AuthContext';
import { AppShell } from './components/AppShell';
import { useSeo } from './hooks/useSeo';
import { Loading } from './components/ui';

const Login = lazy(() => import('./pages/Login'));
const Register = lazy(() => import('./pages/Register'));
const VerifyEmail = lazy(() => import('./pages/VerifyEmail'));
const ForgotPassword = lazy(() => import('./pages/ForgotPassword'));
const ResetPassword = lazy(() => import('./pages/ResetPassword'));
const Home = lazy(() => import('./pages/Home'));
const ProductDetail = lazy(() => import('./pages/ProductDetail'));
const SellerStorefront = lazy(() => import('./pages/SellerStorefront'));
const Nearby = lazy(() => import('./pages/Nearby'));
const Search = lazy(() => import('./pages/Search'));
const Ai = lazy(() => import('./pages/Ai'));
const CmsPage = lazy(() => import('./pages/CmsPage'));
const NotFound = lazy(() => import('./pages/NotFound'));

const BuyerDashboard = lazy(() => import('./pages/buyer/BuyerDashboard'));
const Orders = lazy(() => import('./pages/buyer/Orders'));
const Saved = lazy(() => import('./pages/buyer/Saved'));
const Addresses = lazy(() => import('./pages/buyer/Addresses'));
const Payments = lazy(() => import('./pages/buyer/Payments'));
const Refunds = lazy(() => import('./pages/buyer/Refunds'));
const Support = lazy(() => import('./pages/buyer/Support'));
const SettingsPage = lazy(() => import('./pages/buyer/Settings'));
const AiChat = lazy(() => import('./pages/buyer/AiChat'));
const Cart = lazy(() => import('./pages/buyer/Cart'));
const Messages = lazy(() => import('./pages/Messages'));
const Thread = lazy(() => import('./pages/Thread'));
const Notifications = lazy(() => import('./pages/Notifications'));

const SellerDashboard = lazy(() => import('./pages/seller/SellerDashboard'));
const Inventory = lazy(() => import('./pages/seller/Inventory'));
const AddProduct = lazy(() => import('./pages/seller/AddProduct'));
const BulkImport = lazy(() => import('./pages/seller/BulkImport'));
const SellerOrders = lazy(() => import('./pages/seller/SellerOrders'));
const SellerAnalytics = lazy(() => import('./pages/seller/SellerAnalytics'));
const SellerAi = lazy(() => import('./pages/seller/SellerAi'));
const StoreSettingsPage = lazy(() => import('./pages/seller/StoreSettings'));

const AdminDashboard = lazy(() => import('./pages/admin/AdminDashboard'));
const AdminUsers = lazy(() => import('./pages/admin/AdminUsers'));
const AdminProducts = lazy(() => import('./pages/admin/AdminProducts'));
const AdminQueue = lazy(() => import('./pages/admin/AdminQueue'));
const AdminSupport = lazy(() => import('./pages/admin/AdminSupport'));

function SuspenseFallback() {
  return <div style={{ padding: 24 }}><Loading /></div>;
}

/**
 * Is the visitor arriving from a verification link?
 *
 * Read straight off window.location rather than useSearchParams because this
 * is evaluated while deciding which element to render, above the router's own
 * param context.
 */
function hasVerificationToken(): boolean {
  if (typeof window === 'undefined') return false;
  return new URLSearchParams(window.location.search).has('token');
}

function RequireRole({ role, children }: { role: 'buyer' | 'seller' | 'admin'; children: ReactNode }) {
  const { user } = useAuth();
  const location = useLocation();
  // Dashboards are per-user and behind auth; a crawler that reaches one must
  // not index it. Declared here so every present and future private route
  // inherits it automatically.
  useSeo({ noIndex: true });
  if (!user) return <Navigate to="/login" state={{ from: location.pathname }} replace />;
  // An unverified address is not a usable account. This is the gate: signing
  // up no longer drops you straight into a dashboard.
  if (!user.emailVerified) return <Navigate to="/verify-email" replace />;
  if (user.role !== role) {
    // Auto-redirect each role to its own home.
    const home = user.role === 'admin' ? '/admin' : user.role === 'seller' ? '/seller' : '/buyer';
    return <Navigate to={home} replace />;
  }
  return <>{children}</>;
}

function RequireAuth({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  useSeo({ noIndex: true });
  if (!user) return <Navigate to="/login" replace />;
  if (!user.emailVerified) return <Navigate to="/verify-email" replace />;
  return <>{children}</>;
}

function RedirectByRole() {
  const { user } = useAuth();
  if (!user) return <Navigate to="/" replace />;
  // Never bounce an unverified account into a dashboard — that is exactly the
  // "logged in without verifying" behaviour this gate exists to stop.
  if (!user.emailVerified) return <Navigate to="/verify-email" replace />;
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

  // Backend refused an unverified account → send them to the gate. Belt and
  // braces: the guards already redirect, but an API call can be refused from a
  // page that was reached before the flag was corrected.
  useEffect(() => {
    const onUnverified = () => {
      if (window.location.pathname !== '/verify-email') window.location.assign('/verify-email');
    };
    window.addEventListener('stx:email-unverified', onUnverified);
    return () => window.removeEventListener('stx:email-unverified', onUnverified);
  }, []);

  return (
    <AppShell>
      <Suspense fallback={<SuspenseFallback />}>
        <Routes>
          {/* public */}
          <Route path="/" element={<Home />} />
          <Route path="/login" element={user ? <RedirectByRole /> : <Login />} />
          <Route path="/register" element={user ? <RedirectByRole /> : <Register />} />
          <Route path="/forgot-password" element={<ForgotPassword />} />
          <Route path="/reset-password" element={<ResetPassword />} />
          <Route
            path="/verify-email"
            element={
              hasVerificationToken() ? (
                <VerifyEmail />
              ) : !user ? (
                <Navigate to="/login" replace />
              ) : user.emailVerified ? (
                <RedirectByRole />
              ) : (
                <VerifyEmail />
              )
            }
          />
          <Route path="/product/:id" element={<ProductDetail />} />
          <Route path="/seller/:id" element={<SellerStorefront />} />
          <Route path="/nearby" element={<Nearby />} />
          <Route path="/search" element={<Search />} />
          <Route path="/ai" element={<Ai />} />
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
          <Route path="/cart" element={<RequireRole role="buyer"><Cart /></RequireRole>} />

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
          <Route path="/admin/queue" element={<RequireRole role="admin"><AdminQueue /></RequireRole>} />
          <Route path="/admin/support" element={<RequireRole role="admin"><AdminSupport /></RequireRole>} />

          <Route path="*" element={<NotFound />} />
        </Routes>
      </Suspense>
    </AppShell>
  );
}
