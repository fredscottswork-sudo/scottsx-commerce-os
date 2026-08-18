import { useEffect, useState } from 'react';
import { Bell, CheckCheck } from 'lucide-react';
import { buyerService } from '../api/services';
import type { AppNotification } from '../api/types';
import { Btn, Card, Empty, ErrorBox, Loading, PageHeader } from '../components/ui';
import { useToast } from '../store/ToastContext';

export default function Notifications() {
  const { toast } = useToast();
  const [items, setItems] = useState<AppNotification[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  async function load() {
    setLoading(true);
    buyerService.notifications().then((r) => setItems(r.notifications)).catch((e: any) => setError(e.message)).finally(() => setLoading(false));
  }
  useEffect(() => { load(); const t = setInterval(load, 15000); return () => clearInterval(t); }, []);

  async function markAll() {
    await buyerService.markAllNotificationsRead();
    toast('All marked read', 'success');
    load();
  }

  async function markOne(n: AppNotification) {
    if (n.read) return;
    await buyerService.markNotificationRead(n.id);
    load();
  }

  return (
    <>
      <PageHeader title="Notifications" sub="Read/unread state syncs with the mobile app."
        actions={items.some((n) => !n.read) ? <Btn size="sm" onClick={markAll}><CheckCheck size={15} /> Mark all read</Btn> : undefined} />
      {loading ? <Loading /> : error ? <ErrorBox message={error} onRetry={load} /> :
        items.length === 0 ? <Empty emoji="🔔" title="No notifications" /> : (
        <div className="grid" style={{ gap: 10 }}>
          {items.map((n) => (
            <Card key={n.id} className={n.read ? '' : ''} pad={false}>
              <div className="row" style={{ padding: '13px 16px', cursor: n.read ? 'default' : 'pointer', opacity: n.read ? 0.72 : 1 }} onClick={() => markOne(n)}>
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
