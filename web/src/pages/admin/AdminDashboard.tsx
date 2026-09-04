import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Users, Package, ShoppingBag, MessageCircle, TrendingUp, Clock, ShieldCheck,
  ArrowRight, Store, UserPlus, AlertTriangle, CheckCircle2, BadgeCheck,
} from 'lucide-react';
import { adminService } from '../../api/services';
import type { AdminStats } from '../../api/types';
import { formatUgx } from '../../api/types';
import {
  Btn, ErrorBox, PageHeader, SkeletonStats, SkeletonRows, StatCard, CountUp,
  Empty, Badge, Avatar, Table,
} from '../../components/ui';
import { AreaChart } from '../../components/charts';

export default function AdminDashboard() {
  const [data, setData] = useState<AdminStats | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      setData(await adminService.stats());
    } catch (e: any) {
      setError(e?.message || 'Could not load platform stats');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);

  if (loading) {
    return (
      <>
        <PageHeader title="Platform overview" />
        <SkeletonStats count={4} />
        <div className="mt-16"><SkeletonRows rows={4} height={100} /></div>
      </>
    );
  }
  if (error) return (<><PageHeader title="Platform overview" /><ErrorBox message={error} onRetry={load} /></>);
  if (!data) return null;

  const { stats, recentUsers, reviewQueue, topSellers, salesSeries } = data;
  const revenue = salesSeries.map((s) => s.revenue);
  const labels = salesSeries.map((s) => new Date(s.date).toLocaleDateString(undefined, { day: 'numeric', month: 'short' }));

  return (
    <>
      <PageHeader
        title="Platform overview"
        sub="Everything happening across ScottsTechX — buyers, sellers, listings and money."
        actions={<Btn onClick={load}>Refresh</Btn>}
      />

      {/* ── Action needed ───────────────────────────────────────────── */}
      <div className="row wrap mb-16" style={{ gap: 9 }}>
        {stats.products.pending > 0 ? (
          <Link to="/admin/queue" className="alert-chip alert-amber">
            <Clock size={14} /> {stats.products.pending} listing{stats.products.pending > 1 ? 's' : ''} awaiting approval
          </Link>
        ) : (
          <span className="alert-chip alert-green"><CheckCircle2 size={14} /> Approval queue is clear</span>
        )}
        {stats.products.low_stock > 0 && (
          <Link to="/admin/products" className="alert-chip alert-red">
            <AlertTriangle size={14} /> {stats.products.low_stock} products low on stock
          </Link>
        )}
        <Link to="/admin/support" className="alert-chip alert-blue">
          <MessageCircle size={14} /> Support desk
        </Link>
      </div>

      {/* ── Headline stats ──────────────────────────────────────────── */}
      <div className="grid grid-4 stagger">
        <StatCard index={0} label="Total users" value={<CountUp value={stats.users.total} />}
          icon={<Users size={18} />}
          hint={`${stats.users.buyers} buyers · ${stats.users.sellers} sellers`} />
        <StatCard index={1} label="Live products" value={<CountUp value={stats.products.approved} />}
          icon={<Package size={18} />} color="var(--accent-emerald)"
          hint={`${stats.products.pending} pending · ${stats.products.rejected} rejected`} />
        <StatCard index={2} label="Revenue" value={formatUgx(stats.orders.revenue_ugx)}
          icon={<TrendingUp size={18} />} color="var(--accent-violet)"
          hint={`${stats.orders.paid} paid of ${stats.orders.total} orders`} />
        <StatCard index={3} label="New this week" value={<CountUp value={stats.users.newThisWeek} />}
          icon={<UserPlus size={18} />} color="var(--accent-pink)"
          hint={`${stats.conversations} conversations`} />
      </div>

      <div className="grid grid-2 dash-split-admin mt-16">
        {/* ── Revenue ───────────────────────────────────────────────── */}
        <section className="card">
          <div className="card-head">
            <h2 className="card-title"><TrendingUp size={17} /> Revenue — last 14 days</h2>
          </div>
          {revenue.some((v) => v > 0) ? (
            <AreaChart points={revenue} labels={labels} valueFormat={formatUgx} height={210} />
          ) : (
            <Empty icon={<TrendingUp size={26} />} title="No paid orders yet"
              subtitle="Platform revenue appears here as orders are paid." />
          )}
        </section>

        {/* ── Catalogue health ──────────────────────────────────────── */}
        <section className="card">
          <h2 className="card-title mb-12"><Package size={17} /> Catalogue health</h2>
          <div className="col">
            {([
              ['Live', stats.products.approved, 'green'],
              ['Pending review', stats.products.pending, 'amber'],
              ['Drafts', stats.products.draft, 'default'],
              ['Rejected', stats.products.rejected, 'red'],
              ['Suspended', stats.products.suspended, 'red'],
              ['Flash deals', stats.products.flash_deals, 'violet'],
            ] as const).map(([label, value, tone], i) => {
              const pct = stats.products.total > 0 ? (value / stats.products.total) * 100 : 0;
              return (
                <div key={label} className="stagger-item" style={{ '--i': i } as React.CSSProperties}>
                  <div className="row-between tiny">
                    <span className="semi">{label}</span>
                    <Badge tone={tone}>{value}</Badge>
                  </div>
                  <div className="rank-bar"><span style={{ width: `${pct}%` }} /></div>
                </div>
              );
            })}
          </div>
          <p className="tiny muted-2 mt-12">Catalogue overview</p>
        </section>
      </div>

      <div className="grid grid-2 mt-16">
        {/* ── Approval queue preview ────────────────────────────────── */}
        <section className="card">
          <div className="card-head">
            <h2 className="card-title"><ShieldCheck size={17} /> Awaiting approval</h2>
            <Link to="/admin/queue" className="link-arrow">Review all <ArrowRight size={14} /></Link>
          </div>
          {reviewQueue.length === 0 ? (
            <Empty icon={<CheckCircle2 size={26} />} title="Queue is clear"
              subtitle="No listings are waiting for review." />
          ) : (
            <div className="col">
              {reviewQueue.slice(0, 5).map((p, i) => (
                <div key={p.id} className="order-row stagger-item" style={{ '--i': i } as React.CSSProperties}>
                  <img src={p.imageUrl} alt="" className="row-thumb" loading="lazy" />
                  <div className="grow" style={{ minWidth: 0 }}>
                    <div className="tiny semi ellipsis">{p.title}</div>
                    <div className="tiny muted ellipsis">{p.sellerName} · {p.category}</div>
                  </div>
                  <span className="tiny semi">{formatUgx(p.priceMinor)}</span>
                </div>
              ))}
            </div>
          )}
        </section>

        {/* ── Top sellers ───────────────────────────────────────────── */}
        <section className="card">
          <div className="card-head">
            <h2 className="card-title"><Store size={17} /> Top sellers</h2>
            <Link to="/admin/users?role=seller" className="link-arrow">All sellers <ArrowRight size={14} /></Link>
          </div>
          {topSellers.length === 0 ? (
            <Empty icon={<Store size={26} />} title="No sellers yet" />
          ) : (
            <div className="col">
              {topSellers.slice(0, 6).map((s, i) => (
                <div key={s.id} className="rank-row stagger-item" style={{ '--i': i } as React.CSSProperties}>
                  <span className="rank-num">{i + 1}</span>
                  <div className="grow" style={{ minWidth: 0 }}>
                    <div className="tiny semi ellipsis">
                      {s.storeName}
                      {s.verified && <BadgeCheck size={12} className="t-success" style={{ verticalAlign: -2, marginLeft: 4 }} />}
                    </div>
                    <div className="tiny muted">{Number(s.rating || 0).toFixed(1)}★ · {s.verified ? 'Verified' : 'Seller'}</div>
                  </div>
                  <span className="tiny semi">{formatUgx(s.revenueUgx)}</span>
                </div>
              ))}
            </div>
          )}
        </section>
      </div>

      {/* ── Newest users ────────────────────────────────────────────── */}
      <section className="card mt-16">
        <div className="card-head">
          <h2 className="card-title"><UserPlus size={17} /> Newest sign-ups</h2>
          <Link to="/admin/users" className="link-arrow">Manage users <ArrowRight size={14} /></Link>
        </div>
        <Table
          rows={recentUsers}
          keyOf={(u) => u.id}
          empty={<Empty icon={<Users size={26} />} title="No users yet" />}
          columns={[
            {
              key: 'user', header: 'User',
              render: (u) => (
                <div className="row" style={{ gap: 9 }}>
                  <Avatar name={u.displayName || u.email} size="sm" />
                  <div style={{ minWidth: 0 }}>
                    <div className="semi ellipsis">{u.displayName || '—'}</div>
                    <div className="tiny muted ellipsis">{u.email}</div>
                  </div>
                </div>
              ),
            },
            { key: 'role', header: 'Role', render: (u) => <Badge tone={u.role === 'admin' ? 'violet' : u.role === 'seller' ? 'cyan' : 'default'}>{u.role}</Badge> },
            {
              key: 'verified', header: 'Email', hideSm: true,
              render: (u) => u.emailVerified
                ? <Badge tone="green">verified</Badge>
                : <Badge tone="amber">unverified</Badge>,
            },
            {
              key: 'joined', header: 'Joined', hideSm: true,
              render: (u) => <span className="tiny muted">{new Date(u.createdAt).toLocaleDateString()}</span>,
            },
          ]}
        />
      </section>
    </>
  );
}
