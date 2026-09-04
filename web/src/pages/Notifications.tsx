import { useEffect, useState } from 'react';
import { Bell, CheckCheck, Package, ShoppingBag, MessageCircle, BadgeCheck, AlertTriangle, Headphones, Tag, Sparkles } from 'lucide-react';
import { buyerService } from '../api/services';
import type { AppNotification } from '../api/types';
import { Btn, Card, Empty, ErrorBox, Loading, PageHeader, Badge } from '../components/ui';
import { useToast } from '../store/ToastContext';
import { Link } from 'react-router-dom';

function iconForType(type?: string, read?: boolean) {
  const color = read ? 'var(--text-2)' : '#fff';
  const bg = read ? 'var(--surface-3)' : 'linear-gradient(135deg, var(--primary), var(--accent-violet))';
  const props = { size: 17, color } as const;
  let Icon = Bell;
  if (!type) Icon = Bell;
  else if (type.includes('product_approved')) Icon = BadgeCheck;
  else if (type.includes('product_rejected')) Icon = AlertTriangle;
  else if (type.includes('product_pending')) Icon = Package;
  else if (type.includes('new_product')) Icon = Sparkles;
  else if (type.includes('order')) Icon = ShoppingBag;
  else if (type.includes('message')) Icon = MessageCircle;
  else if (type.includes('support')) Icon = Headphones;
  else if (type.includes('price_drop')) Icon = Tag;

  return { Icon, bg, color, props };
}

export default function Notifications() {
  const { toast } = useToast();
  const [items, setItems] = useState<AppNotification[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  async function load() {
    setLoading(true);
    setError('');
    try {
      const r = await buyerService.notifications();
      setItems(r.notifications);
    } catch (e: any) {
      setError(e?.message || 'Could not load notifications');
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => {
    void load();
    const tick = () => { if (!document.hidden) load(); };
    const t = setInterval(tick, 30000);
    const onVis = () => { if (!document.hidden) tick(); };
    document.addEventListener('visibilitychange', onVis);
    return () => { clearInterval(t); document.removeEventListener('visibilitychange', onVis); };
  }, []);

  async function markAll() {
    try {
      await buyerService.markAllNotificationsRead();
      toast('All marked read', 'success');
      setItems((xs) => xs.map((n) => ({ ...n, read: true })));
      window.dispatchEvent(new Event('stx:refresh-badges'));
    } catch (e: any) {
      toast(e?.message || 'Could not mark read', 'error');
    }
  }

  async function markOne(n: AppNotification) {
    if (n.read) return;
    try {
      await buyerService.markNotificationRead(n.id);
      setItems((xs) => xs.map((x) => x.id === n.id ? { ...x, read: true } : x));
      window.dispatchEvent(new Event('stx:refresh-badges'));
    } catch {
      // optimistic already done, reload on error
      void load();
    }
  }

  return (
    <>
      <PageHeader title="Notifications" sub="Tap to mark read — state syncs with the mobile app."
        actions={items.some((n) => !n.read) ? <Btn size="sm" variant="primary" icon={<CheckCheck size={15} />} onClick={markAll}>Mark all read</Btn> : undefined} />
      {loading ? <Loading /> : error ? <ErrorBox message={error} onRetry={load} /> :
        items.length === 0 ? <Empty icon={<Bell size={28} />} title="No notifications" subtitle="Orders, messages, product approvals and support replies appear here." /> : (
        <div className="grid" style={{ gap: 10 }}>
          {items.map((n, i) => {
            const { Icon, bg, props } = iconForType(n.type, n.read);
            return (
              <Card key={n.id} flush hover className="stagger-item" style={{ '--i': i } as React.CSSProperties}>
                <div className="row" style={{ padding: '13px 16px', cursor: n.read ? 'default' : 'pointer', opacity: n.read ? 0.72 : 1, gap: 12 }} onClick={() => markOne(n)}>
                  <span className="stat-icon" style={{ background: bg, flex: '0 0 40px' }}>
                    <Icon {...props} />
                  </span>
                  <div className="grow" style={{ minWidth: 0 }}>
                    <div className="row-between" style={{ gap: 8 }}>
                      <strong className="ellipsis" style={{ fontSize: 13.5 }}>{n.title}</strong>
                      <span className="row" style={{ gap: 6, flexShrink: 0 }}>
                        {!n.read && <Badge tone="primary">New</Badge>}
                        <span className="tiny muted-2">{new Date(n.createdAt).toLocaleDateString()}</span>
                      </span>
                    </div>
                    <div className="muted" style={{ fontSize: 13, lineHeight: 1.45, marginTop: 2 }}>{n.body}</div>
                    <div className="row wrap mt-8" style={{ gap: 6 }}>
                      {n.type && <Badge tone="default">{n.type.replace(/_/g,' ')}</Badge>}
                      <span className="tiny muted-2">{new Date(n.createdAt).toLocaleString()}</span>
                      {n.data?.id && n.data?.screen === 'product' && (
                        <Link to={`/product/${n.data.id}`} className="tiny" onClick={(e) => e.stopPropagation()}>View product →</Link>
                      )}
                      {n.data?.id && n.data?.screen === 'admin_products' && (
                        <Link to="/admin/queue" className="tiny" onClick={(e) => e.stopPropagation()}>Open queue →</Link>
                      )}
                    </div>
                  </div>
                </div>
              </Card>
            );
          })}
        </div>
      )}
    </>
  );
}
