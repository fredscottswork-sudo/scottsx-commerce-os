import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Wallet, ShoppingBag, Package, AlertTriangle, PlusCircle, Sparkles } from 'lucide-react';
import { sellerService, chatService } from '../../api/services';
import type { Order, SellerDashboardStats } from '../../api/types';
import { formatUgx } from '../../api/types';
import { useAuth } from '../../store/AuthContext';
import { Btn, Card, Empty, ErrorBox, Loading, PageHeader, StatusBadge, Table } from '../../components/ui';

export default function SellerDashboard() {
  const { user } = useAuth();
  const [stats, setStats] = useState<SellerDashboardStats | null>(null);
  const [orders, setOrders] = useState<Order[]>([]);
  const [unread, setUnread] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    Promise.all([
      sellerService.dashboardStats().then((r) => setStats(r.stats)),
      sellerService.orders().then((r) => setOrders(r.orders)),
      chatService.conversations().then((r) => setUnread(r.conversations.reduce((s, c) => s + c.unread, 0))).catch(() => 0),
    ]).catch((e: any) => setError(e.message)).finally(() => setLoading(false));
  }, []);

  if (loading) return <Loading />;
  if (error || !stats) return <ErrorBox message={error} onRetry={() => window.location.reload()} />;

  const cards = [
    { label: 'Revenue', value: formatUgx(stats.revenueUgx), icon: <Wallet size={18} />, color: 'linear-gradient(135deg, var(--success), var(--primary))' },
    { label: 'Orders', value: String(stats.orders), icon: <ShoppingBag size={18} />, color: 'linear-gradient(135deg, var(--primary), var(--purple))' },
    { label: 'Listings', value: String(stats.totalProducts), icon: <Package size={18} />, color: 'linear-gradient(135deg, var(--purple), var(--pink))' },
    { label: 'Low stock', value: String(stats.lowStock), icon: <AlertTriangle size={18} />, color: 'linear-gradient(135deg, var(--warning), var(--danger))' },
  ];

  return (
    <>
      <PageHeader title={`Store dashboard`} sub={`Welcome, ${user?.displayName || 'seller'} — ${stats.topProduct ? `top seller: ${stats.topProduct}` : 'list your first product to get going.'}`}
        actions={
          <div className="row wrap">
            <Link to="/seller/add-product"><Btn variant="primary"><PlusCircle size={16} /> Add product</Btn></Link>
            <Link to="/seller/ai"><Btn><Sparkles size={16} /> AI assistant</Btn></Link>
          </div>
        } />

      <div className="grid grid-4 mb-16">
        {cards.map((c) => (
          <Card key={c.label}>
            <div className="row-between">
              <div>
                <div className="stat-label">{c.label}</div>
                <div className="stat-value" style={{ fontSize: 21 }}>{c.value}</div>
              </div>
              <span className="stat-icon" style={{ background: c.color }}>{c.icon}</span>
            </div>
          </Card>
        ))}
      </div>

      {stats.lowStock > 0 && (
        <Card className="mb-16" >
          <div className="row" style={{ color: 'var(--warning)' }}>
            <AlertTriangle size={18} />
            <strong>{stats.lowStock} product(s) low on stock</strong>
            <Link to="/seller/inventory" className="muted">Review inventory →</Link>
          </div>
        </Card>
      )}

      <Card>
        <div className="row-between mb-16">
          <strong>Recent orders ({orders.length})</strong>
          <Link to="/seller/orders" className="muted" style={{ fontSize: 13 }}>View all</Link>
        </div>
        {orders.length === 0 ? <Empty emoji="🧾" title="No orders yet" subtitle="Orders from buyers will appear here." /> : (
          <Table head={['Product', 'Buyer', 'Qty', 'Amount', 'Status', 'Date']}>
            {orders.slice(0, 8).map((o) => (
              <tr key={o.id}>
                <td>{o.title}</td>
                <td>{o.buyerName || '—'}</td>
                <td>{o.quantity}</td>
                <td>{formatUgx(o.amount)}</td>
                <td><StatusBadge status={o.status} /></td>
                <td>{new Date(o.createdAt).toLocaleDateString()}</td>
              </tr>
            ))}
          </Table>
        )}
      </Card>
    </>
  );
}
