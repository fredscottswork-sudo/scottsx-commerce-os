import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Package, ShoppingBag, Truck, CheckCircle2, XCircle, MessageCircle, Star, Receipt,
} from 'lucide-react';
import { buyerService, chatService, productService } from '../../api/services';
import type { Order } from '../../api/types';
import { formatUgx } from '../../api/types';
import { useToast } from '../../store/ToastContext';
import {
  Btn, Empty, ErrorBox, PageHeader, SkeletonRows, Table, StatusBadge, Tabs,
  StatCard, CountUp, Modal, Field, TextArea,
} from '../../components/ui';

type TabId = 'active' | 'completed' | 'cancelled' | 'all';

const ACTIVE = ['pending', 'paid', 'processing', 'shipped'];
const DONE = ['delivered'];
const DEAD = ['cancelled', 'refunded', 'failed'];

export default function Orders() {
  const { toast } = useToast();
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [tab, setTab] = useState<TabId>('active');

  const [rating, setRating] = useState<Order | null>(null);
  const [stars, setStars] = useState(5);
  const [comment, setComment] = useState('');
  const [saving, setSaving] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const r = await buyerService.orders();
      setOrders(r.orders);
    } catch (e: any) {
      setError(e?.message || 'Could not load your orders');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);

  const groups = useMemo(() => {
    const s = (o: Order) => (o.status || '').toLowerCase();
    return {
      active: orders.filter((o) => ACTIVE.includes(s(o))),
      completed: orders.filter((o) => DONE.includes(s(o))),
      cancelled: orders.filter((o) => DEAD.includes(s(o))),
      all: orders,
    };
  }, [orders]);

  const rows = groups[tab];
  const spent = orders
    .filter((o) => ['paid', 'shipped', 'delivered'].includes((o.status || '').toLowerCase()))
    .reduce((s, o) => s + o.amount * (o.quantity || 1), 0);

  const messageSeller = async (o: Order) => {
    if (!o.sellerId) { toast('Seller unavailable for this order', 'warning'); return; }
    try {
      const r = await chatService.open(o.sellerId);
      window.location.assign(`/messages/${r.conversation.id}`);
    } catch (e: any) {
      toast(e?.message || 'Could not open the chat', 'error');
    }
  };

  const submitRating = async () => {
    if (!rating) return;
    const productId = (rating as any).productId as string | undefined;
    if (!productId) { toast('This order has no linked product to rate', 'warning'); return; }
    setSaving(true);
    try {
      await productService.rate(productId, stars, comment);
      toast('Thanks for the review!', 'success');
      setRating(null);
      setComment('');
      setStars(5);
    } catch (e: any) {
      toast(e?.message || 'Could not save your rating', 'error');
    } finally {
      setSaving(false);
    }
  };

  return (
    <>
      <PageHeader
        title="My orders"
        sub="Everything you've bought — the same list your phone shows, live from one backend."
        actions={<Btn onClick={load}>Refresh</Btn>}
      />

      <div className="grid grid-4 mb-16 stagger">
        <StatCard index={0} label="In progress" value={<CountUp value={groups.active.length} />} icon={<Truck size={18} />} color="var(--accent-cyan)" />
        <StatCard index={1} label="Delivered" value={<CountUp value={groups.completed.length} />} icon={<CheckCircle2 size={18} />} color="var(--accent-emerald)" />
        <StatCard index={2} label="Total orders" value={<CountUp value={orders.length} />} icon={<ShoppingBag size={18} />} />
        <StatCard index={3} label="Total spent" value={formatUgx(spent)} icon={<Receipt size={18} />} color="var(--accent-violet)" />
      </div>

      <Tabs<TabId>
        active={tab}
        onChange={setTab}
        tabs={[
          { id: 'active', label: 'In progress', count: groups.active.length },
          { id: 'completed', label: 'Delivered', count: groups.completed.length },
          { id: 'cancelled', label: 'Cancelled' },
          { id: 'all', label: 'All' },
        ]}
      />

      <div className="mt-16">
        {loading ? (
          <SkeletonRows rows={5} height={62} />
        ) : error ? (
          <ErrorBox message={error} onRetry={load} />
        ) : rows.length === 0 ? (
          <Empty
            icon={<Package size={28} />}
            title={tab === 'active' ? 'No orders in progress' : 'Nothing here'}
            subtitle="When you place an order it appears here with live status updates."
            action={<Link to="/search" className="btn btn-primary">Start shopping</Link>}
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
                      <div className="tiny muted">{o.storeName || 'ScottsTechX seller'}</div>
                    </div>
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
                    <Btn size="sm" icon={<MessageCircle size={13} />} onClick={() => messageSeller(o)} title="Message the seller" />
                    {(o.status || '').toLowerCase() === 'delivered' && (
                      <Btn size="sm" variant="primary" icon={<Star size={13} />}
                        onClick={() => { setRating(o); setStars(5); setComment(''); }}>
                        Rate
                      </Btn>
                    )}
                    {['pending', 'failed'].includes((o.status || '').toLowerCase()) && (
                      <Link to="/buyer/refunds" className="btn btn-sm btn-ghost" title="Report a problem">
                        <XCircle size={13} />
                      </Link>
                    )}
                  </div>
                ),
              },
            ]}
          />
        )}
      </div>

      <Modal
        open={!!rating}
        onClose={() => setRating(null)}
        title={`Rate “${rating?.title ?? ''}”`}
        footer={
          <>
            <Btn onClick={() => setRating(null)}>Cancel</Btn>
            <Btn variant="primary" loading={saving} onClick={submitRating}>Submit review</Btn>
          </>
        }
      >
        <div className="row center mb-16" style={{ gap: 6, justifyContent: 'center' }}>
          {[1, 2, 3, 4, 5].map((n) => (
            <button
              key={n}
              className={`star-btn${n <= stars ? ' on' : ''}`}
              onClick={() => setStars(n)}
              aria-label={`${n} star${n > 1 ? 's' : ''}`}
            >
              <Star size={30} fill={n <= stars ? 'currentColor' : 'none'}
                style={{ color: n <= stars ? 'var(--warning)' : 'var(--text-3)' }} />
            </button>
          ))}
        </div>
        <Field label="Tell other buyers what you think (optional)">
          <TextArea rows={3} value={comment} onChange={(e) => setComment(e.target.value)}
            placeholder="Quality, delivery speed, packaging…" />
        </Field>
      </Modal>
    </>
  );
}
