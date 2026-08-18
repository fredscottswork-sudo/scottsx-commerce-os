import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Send, Star } from 'lucide-react';
import { chatService } from '../api/services';
import type { ChatMessage, Conversation } from '../api/types';
import { useAuth } from '../store/AuthContext';
import { useToast } from '../store/ToastContext';
import { Empty, Loading } from '../components/ui';

const QUICK = ['Got it', 'On the way', 'Thanks!', 'Will check'];

export default function Thread() {
  const { id } = useParams<{ id: string }>();
  const { user } = useAuth();
  const { toast } = useToast();
  const [conv, setConv] = useState<Conversation | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState('');
  const [showQuick, setShowQuick] = useState(false);
  const [loading, setLoading] = useState(true);
  const bottomRef = useRef<HTMLDivElement>(null);

  async function load() {
    const c = await chatService.conversations();
    setConv(c.conversations.find((x) => x.id === id) ?? null);
    const m = await chatService.messages(id!);
    setMessages(m.messages);
    setLoading(false);
    chatService.markRead(id!).catch(() => undefined);
  }
  useEffect(() => {
    load();
    const t = setInterval(load, 3000);
    return () => clearInterval(t);
  }, [id]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages.length]);

  async function send(e?: FormEvent) {
    e?.preventDefault();
    const text = input.trim();
    if (!text) return;
    setInput('');
    try {
      await chatService.send(id!, text);
      load();
    } catch (err: any) {
      toast(err.message, 'error');
    }
  }

  if (loading) return <Loading />;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: 'calc(100vh - var(--topbar-h) - 44px)' }}>
      <div className="row-between mb-16">
        <div className="row">
          <Link to="/messages" className="muted">← Inbox</Link>
          <strong>{conv?.otherParty.name ?? 'Conversation'}</strong>
        </div>
        {conv?.productTitle && <span className="badge badge-blue">🛒 {conv.productTitle}</span>}
      </div>

      <div className="card grow" style={{ padding: 16, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: 8 }}>
        {messages.length === 0 ? <Empty emoji="👋" title="Say hello" subtitle="Start the conversation with the seller." /> :
          messages.map((m) => {
            const mine = m.senderId === user?.id;
            return (
              <div key={m.id} className={mine ? 'bubble bubble-mine' : 'bubble bubble-other'} style={{ alignSelf: mine ? 'flex-end' : 'flex-start' }}>
                {m.text}
                <div style={{ fontSize: 10.5, opacity: 0.75, marginTop: 3, textAlign: 'right' }}>
                  {mine ? '✓ Delivered' : new Date(m.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                </div>
              </div>
            );
          })}
        <div ref={bottomRef} />
      </div>

      {showQuick && (
        <div className="row wrap mt-8">
          {QUICK.map((q) => (
            <button key={q} className="chip" onClick={() => { setInput(q); setShowQuick(false); }}>{q}</button>
          ))}
        </div>
      )}

      <form onSubmit={send} className="row mt-8">
        <button type="button" className="btn" title="Quick replies" onClick={() => setShowQuick(!showQuick)}><Star size={16} /></button>
        <input className="input grow" value={input} onChange={(e) => setInput(e.target.value)} placeholder="Type a message…" />
        <button type="submit" className="btn btn-primary" disabled={!input.trim()} aria-label="Send">
          <Send size={16} />
        </button>
      </form>
    </div>
  );
}
