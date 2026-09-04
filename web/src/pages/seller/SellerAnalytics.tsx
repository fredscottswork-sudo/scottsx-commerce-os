import { useEffect, useState } from 'react';
import { Wallet, ShoppingBag, Package, AlertTriangle, TrendingUp, Users, Eye, Clock } from 'lucide-react';
import { sellerService } from '../../api/services';
import type { SellerDashboard } from '../../api/types';
import { formatUgx } from '../../api/types';
import { Card, ErrorBox, Loading, PageHeader, StatCard, CountUp, Badge } from '../../components/ui';
import { AreaChart, Donut, BarChart } from '../../components/charts';
import { Link } from 'react-router-dom';

export default function SellerAnalytics() {
  const [data, setData] = useState<SellerDashboard | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    sellerService.dashboard().then(setData).catch((e: any) => setError(e.message)).finally(() => setLoading(false));
  }, []);

  if (loading) return <Loading />;
  if (error || !data) return <ErrorBox message={error} onRetry={() => window.location.reload()} />;

  const { stats, salesSeries } = data;
  const revenueTrend = salesSeries.map((s) => s.revenue);
  const orderTrend = salesSeries.map((s) => s.orders);
  const labels = salesSeries.map((s) => new Date(s.date).toLocaleDateString(undefined, { day: 'numeric', month: 'short' }));

  const cards = [
    { label: 'Total revenue', value: formatUgx(stats.revenueUgx), icon: <Wallet size={18} />, color: 'linear-gradient(135deg, var(--success), #10b981)', hint: `${formatUgx(stats.revenue30Ugx)} last 30 days` },
    { label: 'Total orders', value: <CountUp value={stats.orders} />, icon: <ShoppingBag size={18} />, color: 'linear-gradient(135deg, var(--primary), var(--accent-violet))', hint: `${stats.orders30} last 30 days` },
    { label: 'Avg order value', value: formatUgx(stats.avgOrderValueUgx), icon: <TrendingUp size={18} />, color: 'linear-gradient(135deg, var(--accent-violet), var(--accent-pink))', hint: 'Per paid order' },
    { label: 'Followers', value: <CountUp value={stats.followers} />, icon: <Users size={18} />, color: 'linear-gradient(135deg, var(--accent-pink), #ec4899)', hint: `${stats.totalViews.toLocaleString()} product views` },
  ];

  const statusEntries = Object.entries(stats.productsByStatus || {});
  const totalStatusRaw = statusEntries.reduce((s, [, v]) => s + v, 0);
  const totalStatus = totalStatusRaw || 0;
  const totalStatusDisplay = String(totalStatusRaw);
  const statusColors: Record<string, string> = {
    approved: 'var(--success)',
    pending: 'var(--warning)',
    draft: 'var(--text-3)',
    rejected: 'var(--danger)',
    suspended: '#f97316',
  };

  return (
    <>
      <PageHeader title="Store analytics" sub="Your revenue, orders and catalogue health — live from the same endpoint the dashboard uses." />

      <div className="grid grid-4 mb-16 stagger">
        {cards.map((c, i) => (
          <StatCard key={c.label} index={i} label={c.label} value={c.value} icon={c.icon} color={c.color} hint={c.hint} />
        ))}
      </div>

      <div className="grid grid-2 dash-split mt-16">
        <Card title={<span className="card-title"><TrendingUp size={17} /> Revenue — last 14 days</span>}>
          {revenueTrend.some((v) => v > 0) ? (
            <AreaChart points={revenueTrend} labels={labels} valueFormat={formatUgx} height={210} color="var(--success)" />
          ) : (
            <div className="empty"><p className="muted tiny">No paid orders yet — revenue chart appears once orders are paid.</p></div>
          )}
        </Card>
        <Card title={<span className="card-title"><ShoppingBag size={17} /> Orders — last 14 days</span>}>
          {orderTrend.some((v) => v > 0) ? (
            <BarChart data={salesSeries.map((s) => ({ label: new Date(s.date).toLocaleDateString(undefined, { day: 'numeric' }), value: s.orders }))} height={210} />
          ) : (
            <div className="empty"><p className="muted tiny">No orders in the last 14 days.</p></div>
          )}
        </Card>
      </div>

      <div className="grid grid-2 mt-16">
        <Card title={<span className="card-title"><Package size={17} /> Catalogue breakdown</span>}>
          <div className="row" style={{ justifyContent: 'center', gap: 24, flexWrap: 'wrap' }}>
            <Donut
              size={160}
              centerLabel={totalStatusDisplay}
              segments={statusEntries.map(([k, v]) => ({ label: k, value: v, color: statusColors[k] || 'var(--primary)' }))}
            />
            <div className="col" style={{ gap: 8, minWidth: 160 }}>
              {statusEntries.map(([k, v]) => {
                const pct = totalStatus > 0 ? Math.round((v / totalStatus) * 100) : 0;
                return (
                  <div key={k} className="row-between" style={{ gap: 12 }}>
                    <span className="row" style={{ gap: 8 }}>
                      <span style={{ width: 10, height: 10, borderRadius: 3, background: statusColors[k] || 'var(--primary)', display: 'inline-block' }} />
                      <span className="tiny semi" style={{ textTransform: 'capitalize' }}>{k}</span>
                    </span>
                    <span className="row" style={{ gap: 8 }}>
                      <span className="tiny muted">{pct}%</span>
                      <Badge tone={k === 'approved' ? 'green' : k === 'pending' ? 'amber' : k === 'rejected' ? 'red' : 'default'}>{v}</Badge>
                    </span>
                  </div>
                );
              })}
              <p className="tiny muted-2 mt-8">{totalStatusRaw === 0 ? 'No listings yet' : `Total ${totalStatusRaw} listings across all statuses`}</p>
            </div>
          </div>
        </Card>

        <Card title={<span className="card-title"><AlertTriangle size={17} /> Stock health</span>}>
          <div className="col" style={{ gap: 12 }}>
            <div className="row-between">
              <span className="tiny semi">Live listings</span>
              <Badge tone="green">{stats.productsByStatus?.approved ?? 0}</Badge>
            </div>
            <div className="row-between">
              <span className="tiny semi">Low stock (≤5)</span>
              <Badge tone="amber">{stats.lowStock}</Badge>
            </div>
            <div className="row-between">
              <span className="tiny semi">Out of stock</span>
              <Badge tone="red">{stats.outOfStock}</Badge>
            </div>
            <div className="row-between">
              <span className="tiny semi">Awaiting approval</span>
              <Badge tone="amber">{stats.pendingApproval}</Badge>
            </div>
            <div className="row-between">
              <span className="tiny semi">Unread messages</span>
              <Badge tone="primary">{stats.unreadMessages}</Badge>
            </div>
            <div className="mt-12">
              <Link to="/seller/inventory" className="btn btn-sm btn-primary">Manage inventory →</Link>
            </div>
          </div>
        </Card>
      </div>
    </>
  );
}
