/**
 * Inbox — the list side of the messaging experience.
 *
 * Filters (all / unread / pinned / offers / archived) come straight from the
 * API so the badge counts always describe the whole inbox rather than the
 * currently filtered slice. Rows can be pinned, muted and archived inline.
 */
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  Archive, ArchiveRestore, BellOff, HandCoins, Inbox, MessageCircle,
  Pin, PinOff, Search as SearchIcon, ShieldCheck,
} from 'lucide-react';
import { chatService } from '../api/services';
import type { Conversation, InboxCounts, InboxFilter } from '../api/types';
import { Avatar, Badge, Empty, ErrorBox, PageHeader, SkeletonRows } from '../components/ui';
import { useAuth } from '../store/AuthContext';
import { useToast } from '../store/ToastContext';

const FILTERS: { key: InboxFilter; label: string }[] = [
  { key: 'all', label: 'All' },
  { key: 'unread', label: 'Unread' },
  { key: 'offers', label: 'Offers' },
  { key: 'pinned', label: 'Pinned' },
  { key: 'archived', label: 'Archived' },
];

/** Compact, WhatsApp-style relative timestamp. */
function whenLabel(iso?: string | null): string {
  if (!iso) return '';
  const then = new Date(iso);
  const now = new Date();
  const sameDay = then.toDateString() === now.toDateString();
  if (sameDay) return then.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  const yesterday = new Date(now);
  yesterday.setDate(now.getDate() - 1);
  if (then.toDateString() === yesterday.toDateString()) return 'Yesterday';
  const days = Math.floor((now.getTime() - then.getTime()) / 86400000);
  if (days < 7) return then.toLocaleDateString([], { weekday: 'short' });
  return then.toLocaleDateString([], { day: 'numeric', month: 'short' });
}

export default function Messages() {
  const { user } = useAuth();
  const { toast } = useToast();
  const navigate = useNavigate();

  const [items, setItems] = useState<Conversation[]>([]);
  const [counts, setCounts] = useState<InboxCounts>({ all: 0, unread: 0, pinned: 0, archived: 0, offers: 0 });
  const [filter, setFilter] = useState<InboxFilter>('all');
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [busyId, setBusyId] = useState<string | null>(null);

  // Keep the latest filter/search in refs so the poll interval never goes stale.
  const filterRef = useRef(filter);
  const searchRef = useRef(search);
  filterRef.current = filter;
  searchRef.current = search;

  const load = useCallback(async (opts: { quiet?: boolean } = {}) => {
    if (!opts.quiet) setLoading(true);
    try {
      const r = await chatService.conversations({ filter: filterRef.current, q: searchRef.current });
      setItems(r.conversations);
      setCounts(r.counts);
      setError('');
    } catch (e: any) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }, []);

  // Initial load + filter change
  useEffect(() => { load(); }, [filter, load]);

  // Live poll — 20s, paused when hidden
  useEffect(() => {
    const tick = () => { if (!document.hidden) load({ quiet: true }); };
    const t = setInterval(tick, 20000);
    const onVis = () => { if (!document.hidden) tick(); };
    document.addEventListener('visibilitychange', onVis);
    return () => { clearInterval(t); document.removeEventListener('visibilitychange', onVis); };
  }, [load]);

  // Debounce search — skip first render (filter effect already loaded)
  const firstSearch = useRef(true);
  useEffect(() => {
    if (firstSearch.current) { firstSearch.current = false; return; }
    const t = setTimeout(() => load({ quiet: true }), 280);
    return () => clearTimeout(t);
  }, [search, load]);

  async function patch(
    conv: Conversation,
    change: { pinned?: boolean; archived?: boolean; muted?: boolean },
    label: string
  ) {
    setBusyId(conv.id);
    try {
      await chatService.setState(conv.id, change);
      toast(label, 'success');
      await load({ quiet: true });
      window.dispatchEvent(new Event('stx:refresh-badges'));
    } catch (e: any) {
      toast(e.message, 'error');
    } finally {
      setBusyId(null);
    }
  }

  const emptyCopy = useMemo(() => {
    switch (filter) {
      case 'unread': return { title: 'Nothing unread', subtitle: 'You are all caught up.' };
      case 'offers': return { title: 'No open offers', subtitle: 'Price offers awaiting a reply appear here.' };
      case 'pinned': return { title: 'No pinned chats', subtitle: 'Pin important conversations to keep them on top.' };
      case 'archived': return { title: 'Archive is empty', subtitle: 'Archived chats return here when a new message arrives.' };
      default:
        return user?.role === 'seller'
          ? { title: 'No buyer messages yet', subtitle: 'Buyers who message your store will show up here.' }
          : { title: 'No conversations yet', subtitle: 'Message a seller from any product page to start chatting.' };
    }
  }, [filter, user?.role]);

  return (
    <>
      <PageHeader
        title={user?.role === 'seller' ? 'Buyer messages' : 'Messages'}
        sub="One shared inbox — everything syncs with the mobile app in real time."
      />

      <div className="inbox-toolbar">
        <div className="chip-row">
          {FILTERS.map((f) => {
            const n = counts[f.key];
            return (
              <button
                key={f.key}
                className={`chip${filter === f.key ? ' active' : ''}`}
                onClick={() => setFilter(f.key)}
              >
                {f.label}
                {n > 0 && <span className="chip-count">{n}</span>}
              </button>
            );
          })}
        </div>
        <div className="inbox-search">
          <SearchIcon size={15} />
          <input
            className="input"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search people, products or messages…"
            aria-label="Search conversations"
          />
        </div>
      </div>

      {loading ? (
        <SkeletonRows rows={5} />
      ) : error ? (
        <ErrorBox message={error} onRetry={load} />
      ) : items.length === 0 ? (
        <Empty icon={<MessageCircle size={28} />} title={emptyCopy.title} subtitle={emptyCopy.subtitle} />
      ) : (
        <div className="card card-flush stagger">
          {items.map((c, i) => (
            <div
              key={c.id}
              className={`chat-list-item stagger-item${c.unread > 0 ? ' unread' : ''}`}
              style={{ '--i': i } as React.CSSProperties}
              role="button"
              tabIndex={0}
              onClick={() => navigate(`/messages/${c.id}`)}
              onKeyDown={(e) => { if (e.key === 'Enter') navigate(`/messages/${c.id}`); }}
            >
              <Avatar name={c.otherParty.name} src={c.otherParty.photoUrl ?? undefined} />

              <div className="grow" style={{ minWidth: 0 }}>
                <div className="row-between" style={{ gap: 8 }}>
                  <span className="chat-name">
                    {c.pinned && <Pin size={12} className="chat-pin-mark" />}
                    {c.otherParty.name}
                    {c.otherParty.verified && <ShieldCheck size={13} className="chat-verified" />}
                  </span>
                  <span className="chat-time">{whenLabel(c.lastTime)}</span>
                </div>

                <div className="row-between" style={{ gap: 8 }}>
                  <span className="chat-preview">
                    {c.muted && <BellOff size={11} style={{ verticalAlign: -1, marginRight: 4 }} />}
                    {c.lastSenderId && c.lastSenderId === user?.id ? 'You: ' : ''}
                    {c.lastMessage || 'No messages yet'}
                  </span>
                  <span className="row" style={{ gap: 6 }}>
                    {(c.pendingOffers ?? 0) > 0 && (
                      <Badge tone="amber"><HandCoins size={11} /> {c.pendingOffers}</Badge>
                    )}
                    {c.unread > 0 && <span className="chat-unread">{c.unread}</span>}
                  </span>
                </div>

                {c.productTitle && <span className="chat-context">🛍 {c.productTitle}</span>}
              </div>

              <div className="chat-row-actions" onClick={(e) => e.stopPropagation()}>
                <button
                  className="icon-btn"
                  disabled={busyId === c.id}
                  title={c.pinned ? 'Unpin' : 'Pin to top'}
                  aria-label={c.pinned ? 'Unpin conversation' : 'Pin conversation'}
                  onClick={() => patch(c, { pinned: !c.pinned }, c.pinned ? 'Unpinned' : 'Pinned to top')}
                >
                  {c.pinned ? <PinOff size={15} /> : <Pin size={15} />}
                </button>
                <button
                  className="icon-btn"
                  disabled={busyId === c.id}
                  title={c.archived ? 'Move to inbox' : 'Archive'}
                  aria-label={c.archived ? 'Unarchive conversation' : 'Archive conversation'}
                  onClick={() => patch(c, { archived: !c.archived }, c.archived ? 'Moved to inbox' : 'Archived')}
                >
                  {c.archived ? <ArchiveRestore size={15} /> : <Archive size={15} />}
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {!loading && !error && items.length > 0 && (
        <p className="muted mt-16" style={{ fontSize: 'var(--fs-xs)', textAlign: 'center' }}>
          <Inbox size={12} style={{ verticalAlign: -2 }} /> {filter !== 'all' ? `${FILTERS.find(f=>f.key===filter)?.label} · ` : ''}{items.length} conversation{items.length === 1 ? '' : 's'}
          {user?.role === 'buyer' && (
            <>
              {' · '}
              <Link to="/buyer/support">Need help? Contact support</Link>
            </>
          )}
        </p>
      )}
    </>
  );
}
