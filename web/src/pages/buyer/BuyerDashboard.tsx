import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { ShoppingBag, Heart, Bell, MessageCircle, Sparkles } from 'lucide-react';
import { buyerService, chatService, productService } from '../../api/services';
import type { Order, Product } from '../../api/types';
import { formatUgx } from '../../api/types';
import { useAuth } from '../../store/AuthContext';
import { Card, Empty, Loading, PageHeader, StatusBadge, Table, Btn } from '../../components/ui';
import { ProductCard } from '../../components/ProductCard';

export default function BuyerDashboard() {
  const { user } = useAuth();
  const [orders, setOrders] = useState<Order[]>([]);
  const [saved, setSaved] = useState<Product[]>([]);
  const [notifCount, setNotifCount] = useState(0);
  const [unreadChat, setUnreadChat] = useState(0);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    Promise.all([
      buyerService.orders().then((r) => setOrders(r.orders)),
      buyerService.bookmarks().then((r) => setSaved(r.products)),
      buyerService.notifications().then((r) => setNotifCount(r.notifications.filter((n) => !n.read).length)),
      chatService.conversations().then((r) => setUnreadChat(r.conversations.reduce((s, c) => s + c.unread, 0))).catch(() => 0),
    ]).finally(() => setLoading(false));
  }, []);

  if (loading) return <Loading />;

  const stats = [
    { label: 'Orders', value: orders.length, icon: <ShoppingBag size={18} />, color: 'linear-gradient(135deg, var(--primary), var(--purple))', to: '/buyer/orders' },
    { label: 'Saved', value: saved.length, icon: <Heart size={18} />, color: 'linear-gradient(135deg, var(--pink), var(--warning))', to: '/buyer/saved' },
    { label: 'Unread chat', value: unreadChat, icon: <MessageCircle size={18} />, color: 'linear-gradient(135deg, var(--success), var(--primary))', to: '/messages' },
    { label: 'Notifications', value: notifCount, icon: <Bell size={18} />, color: 'linear-gradient(135deg, var(--purple), var(--pink))', to: '/notifications' },
  ];

  return (
    <>
      <PageHeader title={`Welcome, ${user?.displayName || 'shopper'} 👋`} sub="Your buyer dashboard — same account, same data as the mobile app."
        actions={<Link to="/buyer/ai"><Btn variant="primary"><Sparkles size={16} /> Ask AI</Btn></Link>} />

      <div className="grid grid-4 mb-16">
        {stats.map((s) => (
          <Link to={s.to} key={s.label} style={{ textDecoration: 'none', color: 'inherit' }}>
            <Card>
              <div className="row-between">
                <div>
                  <div className="stat-label">{s.label}</div>
                  <div className="stat-value">{s.value}</div>
                </div>
                <span className="stat-icon" style={{ background: s.color }}>{s.icon}</span>
              </div>
            </Card>
          </Link>
        ))}
      </div>

      <div className="grid grid-2">
        <Card>
          <div className="row-between mb-16">
            <strong>Recent orders</strong>
            <Link to="/buyer/orders" className="muted" style={{ fontSize: 13 }}>View all</Link>
          </div>
          {orders.length === 0 ? <Empty emoji="📦" title="No orders yet" subtitle="Buy something from the marketplace." /> : (
            <Table head={['Order', 'Amount', 'Status', 'Date']}>
              {orders.slice(0, 6).map((o) => (
                <tr key={o.id}>
                  <td>{o.title}</td>
                  <td>{formatUgx(o.amount)} ×{o.quantity}</td>
                  <td><StatusBadge status={o.status} /></td>
                  <td>{new Date(o.createdAt).toLocaleDateString()}</td>
                </tr>
              ))}
            </Table>
          )}
        </Card>
        <Card>
          <div className="row-between mb-16">
            <strong>Saved products</strong>
            <Link to="/buyer/saved" className="muted" style={{ fontSize: 13 }}>View all</Link>
          </div>
          {saved.length === 0 ? <Empty emoji="❤️" title="Nothing saved" subtitle="Tap the heart on any product." /> : (
            <div className="pgrid" style={{ gridTemplateColumns: 'repeat(auto-fill, minmax(150px, 1fr))' }}>
              {saved.slice(0, 4).map((p) => <ProductCard key={p.id} product={p} />)}
            </div>
          )}
        </Card>
      </div>
    </>
  );
}
