import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Bell, CheckCheck } from 'lucide-react';
import { buyerService } from '../api/services';
import type { AppNotification } from '../api/types';
import { Btn, Card, Empty, ErrorBox, Loading, PageHeader } from '../components/ui';
import { useToast } from '../store/ToastContext';
import { useAuth } from '../store/AuthContext';

function targetForNotification(n: AppNotification, role?: string): string | null {
  const screen = n.data?.screen;
  const id = n.data?.id;
  const conversationId = n.data?.conversationId;
  if (conversationId) return `/messages/${encodeURIComponent(conversationId)}`;
  if (screen === 'product' && id) return `/product/${encodeURIComponent(id)}`;
  if (screen === 'admin_products') return '/admin/queue';
  if (screen === 'admin_support') return '/admin/support';
  if (screen === 'order' && id) return role === 'seller' ? '/seller/orders' : '/buyer/orders';
  if (screen === 'support') return role === 'admin' ? '/admin/support' : '/buyer/support';
  return null;
}

export default function Notifications() {
  const { toast } = useToast();
  const { user } = useAuth();
  const navigate = useNavigate();
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
  useEffect(() => { void load(); const t = setInterval(() => void load(), 15000); return () => clearInterval(t); }, []);

  async function markAll() {
    try {
      await buyerService.markAllNotificationsRead();
      toast('All marked read', 'success');
      await load();
    } catch (e: any) {
      toast(e?.message || 'Could not mark notifications read', 'error');
    }
  }

  async function openNotification(n: AppNotification) {
    try {
      if (!n.read) await buyerService.markNotificationRead(n.id);
      const target = targetForNotification(n, user?.role);
      if (target) navigate(target);
      else if (!n.read) await load();
    } catch (e: any) {
      toast(e?.message || 'Could not open notification', 'error');
    }
  }

  return (
    <>
      <PageHeader title="Notifications" sub="Read/unread state syncs with the mobile app."
        actions={items.some((n) => !n.read) ? <Btn size="sm" onClick={markAll}><CheckCheck size={15} /> Mark all read</Btn> : undefined} />
      {loading ? <Loading /> : error ? <ErrorBox message={error} onRetry={load} /> :
        items.length === 0 ? <Empty icon={<Bell size={28} />} title="No notifications" /> : (
        <div className="grid" style={{ gap: 10 }}>
          {items.map((n) => (
            <Card key={n.id} className={n.read ? '' : ''} flush>
              <div
                className="row"
                style={{ padding: '13px 16px', cursor: targetForNotification(n, user?.role) || !n.read ? 'pointer' : 'default', opacity: n.read ? 0.72 : 1 }}
                onClick={() => void openNotification(n)}
                onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); void openNotification(n); } }}
                role={targetForNotification(n, user?.role) || !n.read ? 'button' : undefined}
                tabIndex={targetForNotification(n, user?.role) || !n.read ? 0 : undefined}
              >
                <span className="stat-icon" style={{ background: n.read ? 'var(--surface-3)' : 'linear-gradient(135deg, var(--primary), var(--purple))', color: n.read ? 'var(--text-2)' : '#fff' }}>
                  <Bell size={17} />
                </span>
                <div className="grow">
                  <div className="row-between">
                    <strong>{n.title}</strong>
                    {!n.read && <span className="badge badge-blue">New</span>}
                  </div>
                  <div className="muted" style={{ fontSize: 13.5 }}>{n.body}</div>
                  <div className="muted" style={{ fontSize: 11.5 }}>{new Date(n.createdAt).toLocaleString()}</div>
                </div>
              </div>
            </Card>
          ))}
        </div>
      )}
    </>
  );
}
