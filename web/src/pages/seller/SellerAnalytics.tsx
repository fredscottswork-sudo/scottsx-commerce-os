import { useEffect, useState } from 'react';
import { Wallet, ShoppingBag, Package, AlertTriangle } from 'lucide-react';
import { sellerService } from '../../api/services';
import type { SellerDashboardStats } from '../../api/types';
import { formatUgx } from '../../api/types';
import { Card, ErrorBox, Loading, PageHeader } from '../../components/ui';
import { BarChart, Donut } from '../../components/charts';

export default function SellerAnalytics() {
  const [stats, setStats] = useState<SellerDashboardStats | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    sellerService.dashboardStats().then((r) => setStats(r.stats)).catch((e: any) => setError(e.message)).finally(() => setLoading(false));
  }, []);

  if (loading) return <Loading />;
  if (error || !stats) return <ErrorBox message={error} onRetry={() => window.location.reload()} />;

  const cards = [
    { label: 'Revenue', value: formatUgx(stats.revenueUgx), icon: <Wallet size={18} />, color: 'linear-gradient(135deg, var(--success), var(--primary))' },
    { label: 'Orders', value: String(stats.orders), icon: <ShoppingBag size={18} />, color: 'linear-gradient(135deg, var(--primary), var(--purple))' },
    { label: 'Listings', value: String(stats.totalProducts), icon: <Package size={18} />, color: 'linear-gradient(135deg, var(--purple), var(--pink))' },
    { label: 'Low stock', value: String(stats.lowStock), icon: <AlertTriangle size={18} />, color: 'linear-gradient(135deg, var(--warning), var(--danger))' },
  ];

  const barData = [
    { label: 'Listings', value: stats.totalProducts },
    { label: 'Orders', value: stats.orders },
    { label: 'Low stock', value: stats.lowStock },
  ];

  return (
    <>
      <PageHeader title="Analytics" sub="Key figures straight from the backend dashboard endpoint." />
      <div className="grid grid-4 mb-16">
        {cards.map((c) => (
          <Card key={c.label}>
            <div className="row-between">
              <div>
                <div className="stat-label">{c.label}</div>
                <div className="stat-value" style={{ fontSize: 19 }}>{c.value}</div>
              </div>
              <span className="stat-icon" style={{ background: c.color }}>{c.icon}</span>
            </div>
          </Card>
        ))}
      </div>

      <div className="grid grid-2">
        <Card>
          <h3 style={{ marginTop: 0 }} className="mb-16">Store overview</h3>
          <BarChart data={barData} />
        </Card>
        <Card>
          <h3 style={{ marginTop: 0 }} className="mb-16">Inventory mix</h3>
          <div className="row" style={{ justifyContent: 'center' }}>
            <Donut segments={[
              { label: 'Listings', value: Math.max(1, stats.totalProducts), color: 'var(--primary)' },
              { label: 'Orders', value: Math.max(1, stats.orders), color: 'var(--purple)' },
              { label: 'Low stock', value: Math.max(1, stats.lowStock), color: 'var(--warning)' },
            ]} centerLabel={String(stats.totalProducts)} />
          </div>
          <p className="muted" style={{ textAlign: 'center', fontSize: 13 }}>Listings · Orders · Low stock</p>
        </Card>
      </div>
    </>
  );
}
