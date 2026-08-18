import { useCallback, useEffect, useState } from 'react';
import {
  Headphones, Send, CheckCircle2, Clock, Bot, User as UserIcon, ShieldCheck, MessageSquare,
} from 'lucide-react';
import { adminService } from '../../api/services';
import type { SupportThread, SupportReply } from '../../api/types';
import { useToast } from '../../store/ToastContext';
import {
  Btn, Empty, ErrorBox, PageHeader, SkeletonRows, Tabs, Badge, TextArea, Switch, RichText, Avatar,
} from '../../components/ui';

type Filter = 'open' | 'answered' | 'closed' | 'all';

export default function AdminSupport() {
  const { toast } = useToast();
  const [filter, setFilter] = useState<Filter>('open');
  const [threads, setThreads] = useState<SupportThread[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [activeId, setActiveId] = useState('');
  const [detail, setDetail] = useState<{ thread: SupportThread; replies: SupportReply[] } | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [body, setBody] = useState('');
  const [closeOnSend, setCloseOnSend] = useState(false);
  const [sending, setSending] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const r = await adminService.tickets(filter === 'all' ? undefined : filter);
      setThreads(r.tickets);
      if (r.tickets.length > 0 && !r.tickets.some((t) => t.id === activeId)) {
        setActiveId(r.tickets[0].id);
      }
      if (r.tickets.length === 0) { setActiveId(''); setDetail(null); }
    } catch (e: any) {
      setError(e?.message || 'Could not load tickets');
    } finally {
      setLoading(false);
    }
  // activeId intentionally excluded — we only re-pick when the list reloads.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filter]);

  useEffect(() => { void load(); }, [load]);

  useEffect(() => {
    if (!activeId) { setDetail(null); return; }
    let alive = true;
    setDetailLoading(true);
    adminService.ticket(activeId)
      .then((d) => { if (alive) setDetail({ thread: d.ticket, replies: d.replies }); })
      .catch((e) => { if (alive) toast(e?.message || 'Could not open ticket', 'error'); })
      .finally(() => { if (alive) setDetailLoading(false); });
    return () => { alive = false; };
  }, [activeId, toast]);

  const send = async () => {
    if (!body.trim() || !activeId) return;
    setSending(true);
    try {
      await adminService.replyTicket(activeId, body.trim(), closeOnSend);
      setBody('');
      const d = await adminService.ticket(activeId);
      setDetail({ thread: d.ticket, replies: d.replies });
      toast(closeOnSend ? 'Replied and closed' : 'Reply sent to the customer', 'success');
      if (closeOnSend) { setCloseOnSend(false); void load(); }
      else {
        setThreads((ts) => ts.map((t) => (t.id === activeId ? { ...t, status: 'answered' } : t)));
      }
    } catch (e: any) {
      toast(e?.message || 'Could not send the reply', 'error');
    } finally {
      setSending(false);
    }
  };

  const counts = {
    open: threads.filter((t) => t.status === 'open').length,
  };

  return (
    <>
      <PageHeader
        title="Support desk"
        sub="Every buyer and seller ticket. AI handles the easy ones — escalations land here."
        actions={<Btn onClick={load}>Refresh</Btn>}
      />

      <Tabs<Filter>
        active={filter}
        onChange={setFilter}
        tabs={[
          { id: 'open', label: 'Needs a human', count: filter === 'open' ? counts.open : undefined },
          { id: 'answered', label: 'Answered' },
          { id: 'closed', label: 'Closed' },
          { id: 'all', label: 'All' },
        ]}
      />

      {loading ? (
        <SkeletonRows rows={5} height={78} />
      ) : error ? (
        <ErrorBox message={error} onRetry={load} />
      ) : threads.length === 0 ? (
        <Empty
          icon={<CheckCircle2 size={28} />}
          title="Nothing waiting"
          subtitle={filter === 'open' ? 'No tickets need a human right now — the AI agent is handling them.' : 'No tickets in this view.'}
        />
      ) : (
        <div className="support-layout mt-16">
          {/* ── Ticket list ─────────────────────────────────────────── */}
          <aside className="card card-flush ticket-list">
            {threads.map((t) => (
              <button
                key={t.id}
                className={`ticket-row ${activeId === t.id ? 'active' : ''}`}
                onClick={() => setActiveId(t.id)}
              >
                <div className="row-between" style={{ gap: 8 }}>
                  <strong className="ellipsis tiny">{t.subject}</strong>
                  <Badge tone={t.status === 'open' ? 'amber' : t.status === 'closed' ? 'default' : 'green'}>
                    {t.status}
                  </Badge>
                </div>
                <p className="tiny muted clamp-2 mt-4">{t.lastReply || t.message}</p>
                <div className="row tiny muted-2 mt-4" style={{ gap: 7 }}>
                  {t.mode === 'ai' ? <Bot size={11} /> : <ShieldCheck size={11} />}
                  <span className="ellipsis">{t.userName || t.userEmail}</span>
                  <span className="grow" />
                  <Clock size={10} /> {new Date(t.updatedAt).toLocaleDateString()}
                </div>
              </button>
            ))}
          </aside>

          {/* ── Conversation ────────────────────────────────────────── */}
          <div className="card card-flush ticket-detail">
            {detailLoading ? (
              <div style={{ padding: 18 }}><SkeletonRows rows={3} height={60} /></div>
            ) : !detail ? (
              <Empty icon={<Headphones size={28} />} title="Pick a ticket" subtitle="Choose a conversation on the left." />
            ) : (
              <>
                <div className="ai-chat-head">
                  <div style={{ minWidth: 0 }}>
                    <h2 className="card-title ellipsis">{detail.thread.subject}</h2>
                    <p className="tiny muted">
                      {detail.thread.userName} · {detail.thread.userRole} · {detail.thread.userEmail}
                    </p>
                  </div>
                  <Badge tone={detail.thread.status === 'open' ? 'amber' : detail.thread.status === 'closed' ? 'default' : 'green'}>
                    {detail.thread.status}
                  </Badge>
                </div>

                <div className="ai-chat-body">
                  <div className="bubble-row assistant">
                    <Avatar name={detail.thread.userName} size="sm" />
                    <div className="bubble bubble-other">
                      <p className="tiny semi muted mb-4">
                        {detail.thread.userName} · {new Date(detail.thread.createdAt).toLocaleString()}
                      </p>
                      <RichText text={detail.thread.message} />
                    </div>
                  </div>

                  {detail.replies.map((r) => (
                    <div key={r.id} className={`bubble-row ${r.authorRole === 'user' ? 'assistant' : 'user'}`}>
                      {r.authorRole === 'user' && <Avatar name={r.authorName} size="sm" />}
                      <div className={`bubble ${
                        r.authorRole === 'user' ? 'bubble-other' : r.authorRole === 'ai' ? 'bubble-ai' : 'bubble-user'
                      }`}>
                        <p className="tiny semi mb-4" style={{ opacity: 0.8 }}>
                          {r.authorRole === 'ai' ? <Bot size={11} style={{ verticalAlign: -1 }} />
                            : r.authorRole === 'admin' ? <ShieldCheck size={11} style={{ verticalAlign: -1 }} />
                            : <UserIcon size={11} style={{ verticalAlign: -1 }} />}
                          {' '}{r.authorName} · {new Date(r.createdAt).toLocaleString()}
                        </p>
                        <RichText text={r.body} />
                      </div>
                    </div>
                  ))}
                </div>

                {detail.thread.status !== 'closed' && (
                  <div className="ai-chat-input" style={{ flexDirection: 'column', alignItems: 'stretch', gap: 10 }}>
                    <TextArea
                      rows={3}
                      value={body}
                      onChange={(e) => setBody(e.target.value)}
                      placeholder="Reply as the ScottsTechX support team…"
                    />
                    <div className="row-between">
                      <Switch checked={closeOnSend} onChange={setCloseOnSend} label="Close ticket after sending" />
                      <Btn variant="primary" loading={sending} disabled={!body.trim()} icon={<Send size={15} />} onClick={send}>
                        Send reply
                      </Btn>
                    </div>
                  </div>
                )}

                {detail.thread.status === 'closed' && (
                  <div className="ai-chat-input center tiny muted">
                    <MessageSquare size={13} style={{ verticalAlign: -2 }} /> This ticket is closed.
                  </div>
                )}
              </>
            )}
          </div>
        </div>
      )}
    </>
  );
}
