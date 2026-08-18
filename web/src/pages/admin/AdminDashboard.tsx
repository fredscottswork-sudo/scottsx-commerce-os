import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Users, Package, ShoppingCart, MessageCircle } from 'lucide-react';
import { adminService } from '../../api/services';
import type { AdminStats } from '../../api/types';
import { formatUgx } from '../../api/types';
import { Card, ErrorBox, Loading, PageHeader, StatusBadge, Table } from '../../components/ui';
import { Donut } from '../../components/charts';

export default function AdminDashboard() {
  const [data, setData] = useState<AdminStats | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    adminService.stats().then(setData).catch((e: any) => setError(e.message)).finally(() => setLoading(false));
  }, []);

  if (loading) return <Loading />;
  if (error || !data) return <ErrorBox message={error} onRetry={() => window.location.reload()} />;
  const s = data.stats;

  const cards = [
    { label: 'Users', value: String(s.users.total), icon: <Users size={18} />, color: 'linear-gradient(135deg, var(--primary), var(--purple))', to: '/admin/users' },
    { label: 'Products', value: String(s.products.total), icon: <Package size={18} />, color: 'linear-gradient(135deg, var(--purple), var(--pink))', to: '/admin/products' },
    { label: 'Orders', value: String(s.orders.total), icon: <ShoppingCart size={18} />, color: 'linear-gradient(135deg, var(--success), var(--primary))', to: '/admin/users' },
    { label: 'Conversations', value: String(s.conversations), icon: <MessageCircle size={18} />, color: 'linear-gradient(135deg, var(--warning), var(--danger))', to: '/admin/users' },
  ];

  return (
    <>
      <PageHeader title="Platform overview" sub="Super admin console — read-only platform metrics from the admin API." />
      <div className="grid grid-4 mb-16">
        {cards.map((c) => (
          <Link to={c.to} key={c.label} style={{ textDecoration: 'none', color: 'inherit' }}>
            <Card>
              <div className="row-between">
                <div>
                  <div className="stat-label">{c.label}</div>
                  <div className="stat-value" style={{ fontSize: 22 }}>{c.value}</div>
                </div>
                <span className="stat-icon" style={{ background: c.color }}>{c.icon}</span>
              </div>
            </Card>
          </Link>
        ))}
      </div>

      <div className="grid grid-2">
        <Card>
          <h3 style={{ marginTop: 0 }} className="mb-16">Users by role</h3>
          <div className="row" style={{ justifyContent: 'center' }}>
            <Donut segments={[
              { label: 'Buyers', value: s.users.buyers, color: 'var(--primary)' },
              { label: 'Sellers', value: s.users.sellers, color: 'var(--purple)' },
              { label: 'Admins', value: s.users.admins, color: 'var(--pink)' },
            ]} centerLabel={String(s.users.total)} />
          </div>
          <p className="muted" style={{ textAlign: 'center', fontSize: 13 }}>
            {s.users.buyers} buyers · {s.users.sellers} sellers · {s.users.admins} admins · {s.users.verified} verified
          </p>
        </Card>
        <Card>
          <h3 style={{ marginTop: 0 }} className="mb-16">Marketplace health</h3>
          <Table head={['Metric', 'Value']}>
            <tr><td>Paid orders</td><td>{s.orders.paid}</td></tr>
            <tr><td>Paid revenue</td><td><strong>{formatUgx(s.orders.revenue_ugx)}</strong></td></tr>
            <tr><td>Flash deals</td><td>{s.products.flash_deals}</td></tr>
            <tr><td>Low stock items</td><td><StatusBadge status={s.products.low_stock > 0 ? 'low stock' : 'ok'} /></td></tr>
          </Table>
        </Card>
      </div>

      <Card className="mt-16">
        <h3 style={{ marginTop: 0 }} className="mb-16">Recently joined</h3>
        <Table head={['Name', 'Email', 'Role', 'Verified', 'Joined']}>
          {data.recentUsers.map((u) => (
            <tr key={u.id}>
              <td><strong>{u.displayName || '—'}</strong></td>
              <td>{u.email}</td>
              <td><span className="badge badge-blue" style={{ textTransform: 'capitalize' }}>{u.role}</span></td>
              <td>{u.emailVerified ? <span className="badge badge-green">✓</span> : <span className="badge badge-gray">—</span>}</td>
              <td>{new Date(u.createdAt).toLocaleDateString()}</td>
            </tr>
          ))}
        </Table>
      </Card>
    </>
  );
}
