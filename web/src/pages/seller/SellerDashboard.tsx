import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  TrendingUp, ShoppingBag, Package, AlertTriangle, Eye, Users, MessageCircle,
  PlusCircle, Clock, ArrowRight, LocateFixed, LocateOff, Store, Sparkles,
} from 'lucide-react';
import { sellerService } from '../../api/services';
import type { SellerDashboard as Dash } from '../../api/types';
import { formatUgx } from '../../api/types';
import { useToast } from '../../store/ToastContext';
import { useAuth } from '../../store/AuthContext';
import {
  Btn, ErrorBox, PageHeader, SkeletonStats, SkeletonRows, StatCard, CountUp,
  StatusBadge, Empty, Switch, Badge,
} from '../../components/ui';
import { AreaChart } from '../../components/charts';

export default function SellerDashboard() {
  const { toast } = useToast();
  const { user } = useAuth();
  const nav = useNavigate();
  const [data, setData] = useState<Dash | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [sharing, setSharing] = useState(false);
  const [isOpen, setIsOpen] = useState(true);
  const [locBusy, setLocBusy] = useState(false);
  const [lastFix, setLastFix] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      setData(await sellerService.dashboard());
    } catch (e: any) {
      setError(e?.message || 'Could not load your dashboard');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);

  useEffect(() => {
    sellerService.location()
      .then((r) => {
        setSharing(!!r.location?.sharing);
        setIsOpen(r.location?.isOpen ?? true);
        setLastFix(r.location?.updatedAt ?? null);
      })
      .catch(() => undefined);
  }, []);

  // ── Live location: publish a fix, or turn sharing off (pin stays put) ────
  const toggleSharing = async (next: boolean) => {
    setLocBusy(true);
    try {
      if (!next) {
        const r = await sellerService.stopSharingLocation();
        setSharing(false);
        toast(r.message, 'info');
      } else {
        if (!navigator.geolocation) {
          toast('This browser cannot share location', 'error');
          return;
        }
        let pos: GeolocationPosition;
        try {
          pos = await new Promise<GeolocationPosition>((res, rej) =>
            navigator.geolocation.getCurrentPosition(res, rej, { enableHighAccuracy: true, timeout: 20000 })
          );
        } catch (geoErr: any) {
          const msg = geoErr?.message || 'Location permission denied';
          toast(msg, 'error');
          return;
        }
        const r = await sellerService.publishLocation(pos.coords.latitude, pos.coords.longitude);
        setSharing(true);
        setLastFix(r.location.updatedAt);
        toast('Live location on — buyers see you move in real time', 'success');
      }
    } catch (e: any) {
      toast(e?.message || 'Could not update your location', 'error');
    } finally {
      setLocBusy(false);
    }
  };

  const toggleOpen = async (next: boolean) => {
    setIsOpen(next);
    try {
      await sellerService.setOpenState(next);
      toast(next ? 'Store marked open' : 'Store marked closed', 'success');
    } catch (e: any) {
      setIsOpen(!next);
      toast(e?.message || 'Could not update store state', 'error');
    }
  };

  if (loading) {
    return (
      <>
        <PageHeader title="Seller dashboard" />
        <SkeletonStats count={4} />
        <div className="mt-16"><SkeletonRows rows={3} height={110} /></div>
      </>
    );
  }
  if (error) return (<><PageHeader title="Seller dashboard" /><ErrorBox message={error} onRetry={load} /></>);
  if (!data) return null;

  const { stats, topProducts, recentOrders, salesSeries } = data;
  const revenueTrend = salesSeries.map((s) => s.revenue);
  const labels = salesSeries.map((s) => new Date(s.date).toLocaleDateString(undefined, { day: 'numeric', month: 'short' }));

  return (
    <>
      <PageHeader
        title={`Welcome back, ${(user?.displayName || 'seller').split(' ')[0]}`}
        sub="Your store at a glance — revenue, stock health and what needs attention today."
        actions={
          <>
            <Btn icon={<Sparkles size={15} />} onClick={() => nav('/seller/ai')}>AI copilot</Btn>
            <Btn variant="primary" icon={<PlusCircle size={15} />} onClick={() => nav('/seller/add-product')}>
              Add product
            </Btn>
          </>
        }
      />

      {/* ── Things that need attention ──────────────────────────────── */}
      {(stats.pendingApproval > 0 || stats.lowStock > 0 || stats.outOfStock > 0) && (
        <div className="row wrap mb-16" style={{ gap: 9 }}>
          {stats.pendingApproval > 0 && (
            <Link to="/seller/inventory?status=pending" className="alert-chip alert-amber">
              <Clock size={14} /> {stats.pendingApproval} awaiting admin approval
            </Link>
          )}
          {stats.outOfStock > 0 && (
            <Link to="/seller/inventory" className="alert-chip alert-red">
              <AlertTriangle size={14} /> {stats.outOfStock} out of stock
            </Link>
          )}
          {stats.lowStock > 0 && (
            <Link to="/seller/inventory" className="alert-chip alert-amber">
              <Package size={14} /> {stats.lowStock} low on stock
            </Link>
          )}
          {stats.unreadMessages > 0 && (
            <Link to="/messages" className="alert-chip alert-blue">
              <MessageCircle size={14} /> {stats.unreadMessages} unread message{stats.unreadMessages > 1 ? 's' : ''}
            </Link>
          )}
        </div>
      )}

      {/* ── Headline stats ──────────────────────────────────────────── */}
      <div className="grid grid-4 stagger">
        <StatCard index={0} label="Revenue (all time)" value={formatUgx(stats.revenueUgx)}
          icon={<TrendingUp size={18} />} color="var(--accent-emerald)"
          hint={`${formatUgx(stats.revenue30Ugx)} in last 30 days`} />
        <StatCard index={1} label="Orders" value={<CountUp value={stats.orders} />}
          icon={<ShoppingBag size={18} />} hint={`${stats.orders30} in last 30 days`} />
        <StatCard index={2} label="Avg order value" value={formatUgx(stats.avgOrderValueUgx)}
          icon={<Package size={18} />} color="var(--accent-violet)" hint="Per order average" />
        <StatCard index={3} label="Followers" value={<CountUp value={stats.followers} />}
          icon={<Users size={18} />} color="var(--accent-pink)"
          hint={`${stats.totalViews.toLocaleString()} views`} />
      </div>

      <div className="grid grid-2 dash-split mt-16">
        {/* ── Revenue chart ─────────────────────────────────────────── */}
        <section className="card">
          <div className="card-head">
            <h2 className="card-title"><TrendingUp size={17} /> Last 14 days</h2>
            <Link to="/seller/analytics" className="link-arrow">Full analytics <ArrowRight size={14} /></Link>
          </div>
          {revenueTrend.some((v) => v > 0) ? (
            <AreaChart points={revenueTrend} labels={labels} valueFormat={formatUgx} height={200} />
          ) : (
            <Empty icon={<TrendingUp size={26} />} title="No sales yet"
              subtitle="Once orders come in, your daily revenue curve shows up here." />
          )}
        </section>

        {/* ── Store controls ────────────────────────────────────────── */}
        <section className="card">
          <h2 className="card-title mb-12"><Store size={17} /> Store controls</h2>

          <div className="control-row">
            <div style={{ minWidth: 0 }}>
              <div className="semi tiny">Accepting orders</div>
              <p className="tiny muted">Closed stores stay listed but marked closed.</p>
            </div>
            <Switch checked={isOpen} onChange={toggleOpen} label="" />
          </div>

          <div className="control-row">
            <div style={{ minWidth: 0 }}>
              <div className="semi tiny row" style={{ gap: 6 }}>
                {sharing ? <LocateFixed size={13} className="t-success" /> : <LocateOff size={13} className="muted-2" />}
                Live location
              </div>
              <p className="tiny muted">
                {sharing
                  ? 'Buyers see your store move in real time.'
                  : 'Off — your store stays pinned at its last known position.'}
              </p>
              {lastFix && <p className="tiny muted-2 mt-4">Last fix: {new Date(lastFix).toLocaleString()}</p>}
            </div>
            <Btn size="sm" loading={locBusy} variant={sharing ? 'danger' : 'primary'}
              onClick={() => toggleSharing(!sharing)}>
              {sharing ? 'Turn off' : 'Turn on'}
            </Btn>
          </div>

          <div className="control-row" style={{ borderBottom: 'none' }}>
            <div style={{ minWidth: 0 }}>
              <div className="semi tiny">Listing status</div>
              <div className="row wrap mt-4" style={{ gap: 6 }}>
                {Object.entries(stats.productsByStatus || {}).map(([k, v]) => (
                  <Badge key={k} tone={k === 'approved' ? 'green' : k === 'pending' ? 'amber' : k === 'rejected' ? 'red' : 'default'}>
                    {v} {k}
                  </Badge>
                ))}
              </div>
            </div>
          </div>
        </section>
      </div>

      <div className="grid grid-2 mt-16">
        {/* ── Best sellers ──────────────────────────────────────────── */}
        <section className="card">
          <div className="card-head">
            <h2 className="card-title"><Eye size={17} /> Best sellers</h2>
            <Link to="/seller/inventory" className="link-arrow">Inventory <ArrowRight size={14} /></Link>
          </div>
          {topProducts.length === 0 ? (
            <Empty icon={<Package size={26} />} title="No sales yet"
              subtitle="Your top-moving products will be ranked here." />
          ) : (
            <div className="col">
              {topProducts.map((p, i) => {
                const max = Math.max(...topProducts.map((x) => x.sold), 1);
                return (
                  <div key={`${p.title}-${i}`} className="rank-row stagger-item" style={{ '--i': i } as React.CSSProperties}>
                    <span className="rank-num">{i + 1}</span>
                    <div className="grow" style={{ minWidth: 0 }}>
                      <div className="tiny semi ellipsis">{p.title}</div>
                      <div className="rank-bar"><span style={{ width: `${(p.sold / max) * 100}%` }} /></div>
                    </div>
                    <span className="tiny semi">{p.sold} sold</span>
                  </div>
                );
              })}
            </div>
          )}
        </section>

        {/* ── Recent orders ─────────────────────────────────────────── */}
        <section className="card">
          <div className="card-head">
            <h2 className="card-title"><ShoppingBag size={17} /> Recent orders</h2>
            <Link to="/seller/orders" className="link-arrow">All orders <ArrowRight size={14} /></Link>
          </div>
          {recentOrders.length === 0 ? (
            <Empty icon={<ShoppingBag size={26} />} title="No orders yet"
              subtitle="Approved listings reach buyers instantly — orders land here." />
          ) : (
            <div className="col">
              {recentOrders.slice(0, 6).map((o, i) => (
                <div key={o.id} className="order-row stagger-item" style={{ '--i': i } as React.CSSProperties}>
                  <div className="grow" style={{ minWidth: 0 }}>
                    <div className="tiny semi ellipsis">{o.productTitle}</div>
                    <div className="tiny muted">{o.buyerName} · {new Date(o.createdAt).toLocaleDateString()}</div>
                  </div>
                  <div style={{ textAlign: 'right' }}>
                    <div className="tiny semi">{formatUgx(o.amount * o.quantity)}</div>
                    <StatusBadge status={o.status} />
                  </div>
                </div>
              ))}
            </div>
          )}
        </section>
      </div>
    </>
  );
}
