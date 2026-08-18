import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { chatService } from '../api/services';
import type { Conversation } from '../api/types';
import { Empty, ErrorBox, Loading, PageHeader } from '../components/ui';
import { useAuth } from '../store/AuthContext';

export default function Messages() {
  const { user } = useAuth();
  const [items, setItems] = useState<Conversation[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  async function load() {
    setLoading(true);
    chatService.conversations().then((r) => setItems(r.conversations)).catch((e: any) => setError(e.message)).finally(() => setLoading(false));
  }
  useEffect(() => { load(); const t = setInterval(load, 10000); return () => clearInterval(t); }, []);

  return (
    <>
      <PageHeader title={user?.role === 'seller' ? 'Seller messages' : 'Messages'} sub="Shared inbox — conversations with buyers & sellers sync with the mobile app." />
      {loading ? <Loading /> : error ? <ErrorBox message={error} onRetry={load} /> :
        items.length === 0 ? <Empty emoji="💬" title="No conversations yet" subtitle="Message a seller from any product page to start chatting." /> :
        <div className="card" style={{ padding: 8 }}>
          {items.map((c) => (
            <Link to={`/messages/${c.id}`} key={c.id} className={`chat-list-item ${c.unread > 0 ? '' : ''}`} style={{ textDecoration: 'none', color: 'inherit' }}>
              <span className="avatar">{c.otherParty.name[0]}</span>
              <div className="grow" style={{ minWidth: 0 }}>
                <div className="row-between">
                  <strong style={{ fontWeight: c.unread > 0 ? 700 : 500 }}>{c.otherParty.name}</strong>
                  <span className="muted" style={{ fontSize: 11.5 }}>{new Date(c.lastTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</span>
                </div>
                <div className="row-between">
                  <span className="muted" style={{ fontSize: 13, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{c.lastMessage}</span>
                  {c.unread > 0 && <span className="badge badge-red">{c.unread}</span>}
                </div>
              </div>
            </Link>
          ))}
        </div>}
    </>
  );
}
