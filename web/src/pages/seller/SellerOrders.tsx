import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Receipt, TrendingUp, Truck, CheckCircle2, MessageCircle, Clock, ShoppingBag,
} from 'lucide-react';
import { sellerService, chatService } from '../../api/services';
import type { Order } from '../../api/types';
import { formatUgx } from '../../api/types';
import { useToast } from '../../store/ToastContext';
import {
  Btn, Empty, ErrorBox, PageHeader, SkeletonRows, Table, StatusBadge, Tabs,
  StatCard, CountUp, SearchInput,
} from '../../components/ui';

type TabId = 'new' | 'shipping' | 'completed' | 'all';

export default function SellerOrders() {
  const { toast } = useToast();
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [tab, setTab] = useState<TabId>('new');
  const [q, setQ] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const r = await sellerService.orders();
      setOrders(r.orders);
    } catch (e: any) {
      setError(e?.message || 'Could not load orders');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);

  const groups = useMemo(() => {
    const s = (o: Order) => (o.status || '').toLowerCase();
    return {
      new: orders.filter((o) => ['pending', 'paid', 'processing'].includes(s(o))),
      shipping: orders.filter((o) => s(o) === 'shipped'),
      completed: orders.filter((o) => s(o) === 'delivered'),
      all: orders,
    };
  }, [orders]);

  const rows = useMemo(() => {
    const needle = q.trim().toLowerCase();
    const base = groups[tab];
    if (!needle) return base;
    return base.filter((o) =>
      o.title.toLowerCase().includes(needle) || (o.buyerName || '').toLowerCase().includes(needle)
    );
  }, [groups, tab, q]);

  const revenue = orders
    .filter((o) => ['paid', 'shipped', 'delivered'].includes((o.status || '').toLowerCase()))
    .reduce((s, o) => s + o.amount * (o.quantity || 1), 0);
  const pendingValue = groups.new.reduce((s, o) => s + o.amount * (o.quantity || 1), 0);

  const messageBuyer = async (o: Order) => {
    const buyerId = (o as any).buyerId as string | undefined;
    if (!buyerId) { toast('Buyer contact unavailable for this order', 'warning'); return; }
    try {
      const r = await chatService.open(buyerId);
      window.location.assign(`/messages/${r.conversation.id}`);
    } catch (e: any) {
      toast(e?.message || 'Could not open the chat', 'error');
    }
  };

  return (
    <>
      <PageHeader
        title="Orders"
        sub="Orders placed for your products — identical to what the mobile seller app shows."
        actions={<Btn onClick={load}>Refresh</Btn>}
      />

      <div className="grid grid-4 mb-16 stagger">
        <StatCard index={0} label="Needs action" value={<CountUp value={groups.new.length} />}
          icon={<Clock size={18} />} color="var(--warning)" hint={formatUgx(pendingValue)} />
        <StatCard index={1} label="In transit" value={<CountUp value={groups.shipping.length} />}
          icon={<Truck size={18} />} color="var(--accent-cyan)" />
        <StatCard index={2} label="Delivered" value={<CountUp value={groups.completed.length} />}
          icon={<CheckCircle2 size={18} />} color="var(--accent-emerald)" />
        <StatCard index={3} label="Revenue" value={formatUgx(revenue)}
          icon={<TrendingUp size={18} />} color="var(--accent-violet)" hint={`${orders.length} orders total`} />
      </div>

      <Tabs<TabId>
        active={tab}
        onChange={setTab}
        tabs={[
          { id: 'new', label: 'Needs action', count: groups.new.length },
          { id: 'shipping', label: 'Shipped', count: groups.shipping.length },
          { id: 'completed', label: 'Delivered' },
          { id: 'all', label: 'All' },
        ]}
      />

      <div className="row-between wrap mt-16 mb-12">
        <div style={{ flex: '1 1 260px', maxWidth: 340 }}>
          <SearchInput value={q} onChange={setQ} placeholder="Filter by product or buyer…" />
        </div>
        <span className="tiny muted">{rows.length} shown</span>
      </div>

      {loading ? (
        <SkeletonRows rows={6} height={56} />
      ) : error ? (
        <ErrorBox message={error} onRetry={load} />
      ) : rows.length === 0 ? (
        <Empty
          icon={<Receipt size={28} />}
          title={q ? 'Nothing matched' : tab === 'new' ? 'No orders need action' : 'No orders here'}
          subtitle={q ? 'Try another search term.' : 'Approved listings reach buyers instantly — orders land here.'}
          action={!q ? <Link to="/seller/add-product" className="btn btn-primary">Add a product</Link> : undefined}
        />
      ) : (
        <Table<Order>
          rows={rows}
          keyOf={(o) => o.id}
          columns={[
            {
              key: 'product', header: 'Product',
              render: (o) => (
                <div className="row" style={{ gap: 10 }}>
                  {o.imageUrl && <img src={o.imageUrl} alt="" className="row-thumb" loading="lazy" />}
                  <div style={{ minWidth: 0 }}>
                    <div className="semi ellipsis" style={{ maxWidth: 260 }}>{o.title}</div>
                    <div className="tiny muted">Order #{o.id.slice(0, 8)}</div>
                  </div>
                </div>
              ),
            },
            { key: 'buyer', header: 'Buyer', hideSm: true, render: (o) => o.buyerName || '—' },
            {
              key: 'delivery', header: 'Deliver to', hideSm: true,
              render: (o) => (
                <div className="tiny" style={{ maxWidth: 190 }}>
                  <div className="ellipsis">{o.deliveryAddress || 'Address not provided'}</div>
                  {o.deliveryPhone && <div className="muted">{o.deliveryPhone}</div>}
                  {o.deliveryNote && <div className="muted ellipsis">Note: {o.deliveryNote}</div>}
                </div>
              ),
            },
            { key: 'qty', header: 'Qty', hideSm: true, render: (o) => o.quantity },
            { key: 'amount', header: 'Amount', render: (o) => <span className="semi">{formatUgx(o.amount * (o.quantity || 1))}</span> },
            { key: 'status', header: 'Status', render: (o) => <StatusBadge status={o.status} /> },
            {
              key: 'date', header: 'Placed', hideSm: true,
              render: (o) => <span className="tiny muted">{new Date(o.createdAt).toLocaleDateString()}</span>,
            },
            {
              key: 'actions', header: '',
              render: (o) => (
                <div className="row" style={{ gap: 5, justifyContent: 'flex-end' }}>
                  <Btn size="sm" icon={<MessageCircle size={13} />} onClick={() => messageBuyer(o)} title="Message the buyer" />
                </div>
              ),
            },
          ]}
        />
      )}
    </>
  );
}
