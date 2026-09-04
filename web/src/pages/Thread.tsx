/**
 * Conversation thread.
 *
 * Supports everything the backend exposes: text, photos, price offers with
 * accept/decline/withdraw, system events, read receipts, typing indicators,
 * message retraction, pin/mute, and saved quick replies.
 *
 * Polling is deliberate rather than websockets: the API is stateless and the
 * 2.5s cadence is cheap, and it keeps the mobile app and web identical.
 */
import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import {
  ArrowLeft, ArrowDown, BellOff, BellRing, Check, CheckCheck, Copy, HandCoins, ImagePlus,
  Inbox, Pin, PinOff, Reply, Send, ShieldCheck, Trash2, X, Zap,
} from 'lucide-react';
import { chatService } from '../api/services';
import type { ChatMessage, Conversation, QuickReply } from '../api/types';
import { useAuth } from '../store/AuthContext';
import { useToast } from '../store/ToastContext';
import { Avatar, Badge, Btn, ConfirmModal, Empty, ErrorBox, Field, Input, Modal } from '../components/ui';

const money = (minor?: number | null) =>
  minor == null ? '' : `UGX ${(minor / 100).toLocaleString('en-UG')}`;

const DEFAULT_QUICK = ['Is this still available?', 'What is your best price?', 'Do you deliver?', 'Thanks!'];

/** Group consecutive messages by calendar day for the date separators. */
function dayLabel(iso: string): string {
  const d = new Date(iso);
  const now = new Date();
  if (d.toDateString() === now.toDateString()) return 'Today';
  const y = new Date(now);
  y.setDate(now.getDate() - 1);
  if (d.toDateString() === y.toDateString()) return 'Yesterday';
  return d.toLocaleDateString([], { day: 'numeric', month: 'long', year: d.getFullYear() === now.getFullYear() ? undefined : 'numeric' });
}

const clock = (iso: string) =>
  new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

export default function Thread() {
  const { id } = useParams<{ id: string }>();
  const { user } = useAuth();
  const { toast } = useToast();
  const navigate = useNavigate();

  const [conv, setConv] = useState<Conversation | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [otherTyping, setOtherTyping] = useState(false);
  const [input, setInput] = useState('');
  const [sending, setSending] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [quickOpen, setQuickOpen] = useState(false);
  const [quickReplies, setQuickReplies] = useState<QuickReply[]>([]);
  const [offerOpen, setOfferOpen] = useState(false);
  const [offerPrice, setOfferPrice] = useState('');
  const [offerQty, setOfferQty] = useState('1');
  const [photoOpen, setPhotoOpen] = useState(false);
  const [photoUrl, setPhotoUrl] = useState('');
  const [confirmDelete, setConfirmDelete] = useState<ChatMessage | null>(null);
  const [offerBusy, setOfferBusy] = useState<string | null>(null);
  const [replyTarget, setReplyTarget] = useState<ChatMessage | null>(null);
  const [lightbox, setLightbox] = useState<string | null>(null);
  const [scrollOffset, setScrollOffset] = useState(false);

  const bottomRef = useRef<HTMLDivElement>(null);
  const bodyRef = useRef<HTMLDivElement>(null);
  const typingSentAt = useRef(0);
  const lastCount = useRef(0);

  const load = useCallback(async (opts: { quiet?: boolean } = {}) => {
    if (!id) return;
    try {
      const [head, body] = await Promise.all([
        chatService.thread(id),
        chatService.messages(id),
      ]);
      setConv(head.conversation);
      setMessages(body.messages);
      setOtherTyping(body.otherTyping);
      setError('');
    } catch (e: any) {
      if (!opts.quiet) setError(e.message);
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    setLoading(true);
    load();
    chatService.markRead(id!).catch(() => undefined);
    chatService.quickReplies().then((r) => setQuickReplies(r.quickReplies)).catch(() => undefined);
  }, [id, load]);

  // Live polling + mark-as-read so the other side's receipts advance.
  // Pauses while the tab is hidden so a backgrounded chat costs nothing.
  useEffect(() => {
    const t = setInterval(() => {
      if (document.hidden) return;
      load({ quiet: true });
      chatService.markRead(id!).catch(() => undefined);
    }, 2500);
    return () => clearInterval(t);
  }, [id, load]);

  // Only autoscroll when the transcript actually grew (and only when the user
  // is already near the bottom — never yank them away from reading history).
  useEffect(() => {
    if (messages.length !== lastCount.current) {
      lastCount.current = messages.length;
      const el = bodyRef.current;
      const nearBottom = !el || el.scrollHeight - el.scrollTop - el.clientHeight < 180;
      if (nearBottom) bottomRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' });
    }
  }, [messages.length]);

  // Track whether the reader has scrolled up, so the jump-to-latest button
  // appears instead of silently stealing their position.
  useEffect(() => {
    const el = bodyRef.current;
    if (!el) return;
    const onScroll = () => setScrollOffset(el.scrollHeight - el.scrollTop - el.clientHeight > 220);
    el.addEventListener('scroll', onScroll, { passive: true });
    return () => el.removeEventListener('scroll', onScroll);
  }, [loading]);

  const jumpLatest = () => bottomRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' });

  // Typing heartbeat, throttled to one ping per 3s.
  function onInputChange(value: string) {
    setInput(value);
    const now = Date.now();
    if (value && now - typingSentAt.current > 3000) {
      typingSentAt.current = now;
      chatService.typing(id!, true).catch(() => undefined);
    }
  }

  async function send(e?: FormEvent) {
    e?.preventDefault();
    const text = input.trim();
    if (!text || sending) return;
    setInput('');
    setSending(true);
    // Optimistic echo keeps the UI instant; the poll reconciles it.
    const optimistic: ChatMessage = {
      id: `tmp-${Date.now()}`,
      senderId: user!.id,
      text,
      kind: 'text',
      createdAt: new Date().toISOString(),
    };
    setMessages((m) => [...m, optimistic]);
    try {
      await chatService.send(id!, { text, replyToId: replyTarget?.id });
      setReplyTarget(null);
      await load({ quiet: true });
      window.dispatchEvent(new Event('stx:refresh-badges'));
    } catch (err: any) {
      setMessages((m) => m.filter((x) => x.id !== optimistic.id));
      setInput(text);
      toast(err.message, 'error');
    } finally {
      setSending(false);
    }
  }

  async function copyText(m: ChatMessage) {
    const text = m.text || m.attachmentName || '';
    try {
      await navigator.clipboard.writeText(text);
      toast('Copied to clipboard', 'success');
    } catch {
      // Clipboard API can be unavailable outside secure contexts.
      const ta = document.createElement('textarea');
      ta.value = text;
      document.body.appendChild(ta);
      ta.select();
      document.execCommand('copy');
      ta.remove();
      toast('Copied to clipboard', 'success');
    }
  }

  async function sendPhoto() {
    const url = photoUrl.trim();
    if (!/^https?:\/\//.test(url)) { toast('Enter a valid image URL', 'error'); return; }
    try {
      await chatService.send(id!, { kind: 'image', imageUrl: url, attachmentName: url.split('/').pop() });
      setPhotoOpen(false);
      setPhotoUrl('');
      await load({ quiet: true });
    } catch (e: any) { toast(e.message, 'error'); }
  }

  async function sendOffer() {
    const amount = Number(offerPrice);
    const qty = Number(offerQty) || 1;
    if (!amount || amount <= 0) { toast('Enter an offer amount', 'error'); return; }
    try {
      await chatService.send(id!, {
        kind: 'offer',
        offerMinor: Math.round(amount * 100),
        offerQuantity: qty,
      });
      setOfferOpen(false);
      setOfferPrice('');
      setOfferQty('1');
      await load({ quiet: true });
      toast('Offer sent', 'success');
    } catch (e: any) { toast(e.message, 'error'); }
  }

  async function respond(m: ChatMessage, action: 'accept' | 'decline' | 'withdraw') {
    setOfferBusy(m.id);
    try {
      await chatService.respondToOffer(id!, m.id, action);
      await load({ quiet: true });
      toast(action === 'accept' ? 'Offer accepted' : action === 'decline' ? 'Offer declined' : 'Offer withdrawn', 'success');
    } catch (e: any) {
      toast(e.message, 'error');
    } finally {
      setOfferBusy(null);
    }
  }

  async function retract() {
    if (!confirmDelete) return;
    try {
      await chatService.retract(id!, confirmDelete.id);
      setConfirmDelete(null);
      await load({ quiet: true });
    } catch (e: any) { toast(e.message, 'error'); }
  }

  async function toggleState(patch: { pinned?: boolean; muted?: boolean }, label: string) {
    try {
      const r = await chatService.setState(id!, patch);
      setConv((c) => (c ? { ...c, ...r.state } : c));
      toast(label, 'success');
    } catch (e: any) { toast(e.message, 'error'); }
  }

  const quickChips = useMemo(
    () => (quickReplies.length ? quickReplies.map((q) => q.text) : DEFAULT_QUICK),
    [quickReplies]
  );

  if (loading) {
    return (
      <div className="col">
        <div className="skeleton" style={{ height: 58, borderRadius: 'var(--radius)' }} />
        <div className="skeleton" style={{ height: 320, borderRadius: 'var(--radius)' }} />
      </div>
    );
  }
  if (error) return <ErrorBox message={error} onRetry={load} />;

  const other = conv?.otherParty;
  let lastDay = '';

  return (
    <div className="thread-wrap">
      {/* ---------------------------------------------------------- header */}
      <header className="thread-head">
        <button className="icon-btn" onClick={() => navigate('/messages')} aria-label="Back to inbox">
          <ArrowLeft size={18} />
        </button>
        <Avatar name={other?.name} src={other?.photoUrl ?? undefined} />
        <div className="grow" style={{ minWidth: 0 }}>
          <div className="row" style={{ gap: 6 }}>
            <strong className="ellipsis">{other?.name ?? 'Conversation'}</strong>
            {other?.verified && <ShieldCheck size={14} className="chat-verified" />}
          </div>
          <span className="thread-status">
            {otherTyping ? (
              <span className="typing-live">
                typing<span className="typing-dot" /><span className="typing-dot" /><span className="typing-dot" />
              </span>
            ) : (
              <>{other?.role === 'seller' ? 'Seller' : 'Buyer'}{other?.location ? ` · ${other.location}` : ''}</>
            )}
          </span>
        </div>
        <button
          className="icon-btn"
          title={conv?.muted ? 'Unmute notifications' : 'Mute notifications'}
          aria-label={conv?.muted ? 'Unmute conversation' : 'Mute conversation'}
          onClick={() => toggleState({ muted: !conv?.muted }, conv?.muted ? 'Notifications on' : 'Muted')}
        >
          {conv?.muted ? <BellOff size={17} /> : <BellRing size={17} />}
        </button>
        <button
          className="icon-btn"
          title={conv?.pinned ? 'Unpin' : 'Pin conversation'}
          aria-label={conv?.pinned ? 'Unpin conversation' : 'Pin conversation'}
          onClick={() => toggleState({ pinned: !conv?.pinned }, conv?.pinned ? 'Unpinned' : 'Pinned')}
        >
          {conv?.pinned ? <PinOff size={17} /> : <Pin size={17} />}
        </button>
      </header>

      {/* ---------------------------------------------- product context bar */}
      {conv?.productTitle && (
        <Link to={conv.productId ? `/product/${conv.productId}` : '#'} className="thread-product">
          {conv.productImageUrl && <img src={conv.productImageUrl} alt="" loading="lazy" />}
          <div className="grow" style={{ minWidth: 0 }}>
            <span className="ellipsis">{conv.productTitle}</span>
            {conv.productPriceMinor != null && <strong>{money(conv.productPriceMinor)}</strong>}
          </div>
          <Badge tone="primary">View</Badge>
        </Link>
      )}

      {/* ------------------------------------------------------ transcript */}
      <div className="thread-body" ref={bodyRef}>
        {messages.length === 0 ? (
          <Empty icon={<Inbox size={26} />} title="Say hello" subtitle="Ask about price, delivery or availability." />
        ) : (
          messages.map((m, mi) => {
            const mine = m.senderId === user?.id;
            const day = dayLabel(m.createdAt);
            const showDay = day !== lastDay;
            lastDay = day;
            // Tight, WhatsApp-style grouping for consecutive messages from the
            // same person on the same day.
            const prev = messages[mi - 1];
            const adjacent = !!prev && prev.senderId === m.senderId && !prev.deletedAt && !m.deletedAt &&
              dayLabel(prev.createdAt) === day && prev.kind !== 'system' && m.kind !== 'system';
            const replied = m.replyToId ? messages.find((x) => x.id === m.replyToId) : undefined;

            // ---- system events -------------------------------------------
            if (m.kind === 'system') {
              return (
                <div key={m.id}>
                  {showDay && <div className="thread-day">{day}</div>}
                  <div className="thread-system">{m.text}</div>
                </div>
              );
            }

            // ---- price offers --------------------------------------------
            if (m.kind === 'offer') {
              const pending = m.offerStatus === 'pending';
              return (
                <div key={m.id}>
                  {showDay && <div className="thread-day">{day}</div>}
                  <div className={`offer-card${mine ? ' mine' : ''}`}>
                    <div className="row-between">
                      <span className="row" style={{ gap: 6 }}>
                        <HandCoins size={15} />
                        <strong>{mine ? 'Your offer' : 'Offer received'}</strong>
                      </span>
                      <Badge tone={
                        m.offerStatus === 'accepted' ? 'green'
                          : m.offerStatus === 'pending' ? 'amber' : 'red'
                      }>
                        {m.offerStatus}
                      </Badge>
                    </div>
                    <div className="offer-price">{money(m.offerMinor)}</div>
                    {(m.offerQuantity ?? 1) > 1 && (
                      <span className="muted">for {m.offerQuantity} units</span>
                    )}
                    {m.productTitle && <span className="muted ellipsis">on {m.productTitle}</span>}
                    {pending && (
                      <div className="row mt-8" style={{ gap: 8 }}>
                        {mine ? (
                          <Btn size="sm" loading={offerBusy === m.id} onClick={() => respond(m, 'withdraw')}>
                            Withdraw
                          </Btn>
                        ) : (
                          <>
                            <Btn size="sm" variant="primary" loading={offerBusy === m.id} onClick={() => respond(m, 'accept')}>
                              Accept
                            </Btn>
                            <Btn size="sm" loading={offerBusy === m.id} onClick={() => respond(m, 'decline')}>
                              Decline
                            </Btn>
                          </>
                        )}
                      </div>
                    )}
                    <div className="bubble-meta">{clock(m.createdAt)}</div>
                  </div>
                </div>
              );
            }

            // ---- text / image --------------------------------------------
            const retracted = !!m.deletedAt;
            const optimistic = String(m.id).startsWith('tmp-');
            return (
              <div key={m.id}>
                {showDay && <div className="thread-day">{day}</div>}
                <div className={`bubble-row ${mine ? 'user' : 'assistant'}${adjacent ? ' adjacent' : ''}`}>
                  <div className={`bubble ${mine ? 'bubble-mine' : 'bubble-other'}${retracted ? ' bubble-retracted' : ''}`}>
                    {retracted ? (
                      <em>This message was deleted</em>
                    ) : (
                      <>
                        {m.imageUrl && (
                          <img
                            src={m.imageUrl}
                            alt={m.attachmentName ?? 'attachment'}
                            className="bubble-img"
                            loading="lazy"
                            onClick={() => setLightbox(m.imageUrl!)}
                          />
                        )}
                        {replied && (
                          <span className="bubble-quote">
                            <Reply size={11} />
                            <span className="ellipsis">{replied.text || (replied.kind === 'offer' ? 'An offer' : 'Photo')}</span>
                          </span>
                        )}
                        {m.text && <span>{m.text}</span>}
                      </>
                    )}
                    <div className="bubble-meta">
                      <span>{clock(m.createdAt)}</span>
                      {optimistic && <span className="bubble-sending">Sending…</span>}
                      {mine && !retracted && !optimistic && (
                        m.readByOther ? <CheckCheck size={13} className="receipt-read" /> : <Check size={13} />
                      )}
                      {mine && !retracted && !optimistic && (
                        <button
                          className="bubble-del"
                          title="Delete message"
                          aria-label="Delete message"
                          onClick={() => setConfirmDelete(m)}
                        >
                          <Trash2 size={12} />
                        </button>
                      )}
                      {!retracted && (
                        <>
                          <button className="bubble-del" title="Reply" aria-label="Reply" onClick={() => setReplyTarget(m)}>
                            <Reply size={12} />
                          </button>
                          <button className="bubble-del" title="Copy" aria-label="Copy message" onClick={() => void copyText(m)}>
                            <Copy size={12} />
                          </button>
                        </>
                      )}
                    </div>
                  </div>
                </div>
              </div>
            );
          })
        )}

        {scrollOffset && messages.length > 0 && (
          <button className="thread-jump" onClick={jumpLatest} aria-label="Jump to latest">
            <ArrowDown size={16} />
          </button>
        )}

        {otherTyping && messages.length > 0 && (
          <div className="bubble-row assistant">
            <div className="bubble bubble-other typing-bubble">
              <span className="typing-dot" /><span className="typing-dot" /><span className="typing-dot" />
            </div>
          </div>
        )}
        <div ref={bottomRef} />
      </div>

      {/* --------------------------------------------------- quick replies */}
      {quickOpen && (
        <div className="chip-row thread-quick">
          {quickChips.map((q) => (
            <button key={q} className="chip" onClick={() => { setInput(q); setQuickOpen(false); }}>
              {q}
            </button>
          ))}
        </div>
      )}

      {/* -------------------------------------------------- reply preview */}
      {replyTarget && (
        <div className="thread-reply-bar">
          <Reply size={14} />
          <span className="grow ellipsis">
            Replying to <strong>{replyTarget.senderId === user?.id ? 'yourself' : other?.name}</strong>:
            {replyTarget.text || (replyTarget.kind === 'offer' ? ' an offer' : ' a photo')}
          </span>
          <button type="button" className="icon-btn" aria-label="Cancel reply" onClick={() => setReplyTarget(null)}>
            <X size={14} />
          </button>
        </div>
      )}

      {/* ---------------------------------------------------- composer bar */}
      <form onSubmit={send} className="thread-composer">
        <button
          type="button"
          className={`icon-btn${quickOpen ? ' active' : ''}`}
          title="Quick replies"
          aria-label="Quick replies"
          onClick={() => setQuickOpen((v) => !v)}
        >
          <Zap size={17} />
        </button>
        <button type="button" className="icon-btn" title="Send a photo" aria-label="Send a photo" onClick={() => setPhotoOpen(true)}>
          <ImagePlus size={17} />
        </button>
        {conv?.mySide === 'buyer' && conv?.productId && (
          <button type="button" className="icon-btn" title="Make an offer" aria-label="Make an offer" onClick={() => setOfferOpen(true)}>
            <HandCoins size={17} />
          </button>
        )}
        <input
          className="input grow"
          value={input}
          onChange={(e) => onInputChange(e.target.value)}
          placeholder="Type a message…"
          aria-label="Message"
        />
        <button type="submit" className="btn btn-primary" disabled={!input.trim() || sending} aria-label="Send">
          <Send size={16} />
        </button>
      </form>

      {/* -------------------------------------------------------- dialogs */}
      <Modal open={photoOpen} title="Send a photo" onClose={() => setPhotoOpen(false)}
        footer={<><Btn onClick={() => setPhotoOpen(false)}>Cancel</Btn><Btn variant="primary" onClick={sendPhoto}>Send</Btn></>}>
        <Field label="Image URL" hint="Paste a link to the photo you want to share.">
          <Input value={photoUrl} onChange={(e) => setPhotoUrl(e.target.value)} placeholder="https://…" />
        </Field>
      </Modal>

      <Modal open={offerOpen} title="Make an offer" onClose={() => setOfferOpen(false)}
        footer={<><Btn onClick={() => setOfferOpen(false)}>Cancel</Btn><Btn variant="primary" onClick={sendOffer}>Send offer</Btn></>}>
        {conv?.productPriceMinor != null && (
          <p className="muted mb-16">Listed at {money(conv.productPriceMinor)}. Offers are negotiable — the seller can accept or decline.</p>
        )}
        <Field label="Your price per unit (UGX)">
          <Input type="number" min="1" value={offerPrice} onChange={(e) => setOfferPrice(e.target.value)} placeholder="e.g. 450000" />
        </Field>
        <Field label="Quantity">
          <Input type="number" min="1" value={offerQty} onChange={(e) => setOfferQty(e.target.value)} />
        </Field>
      </Modal>

      <ConfirmModal
        open={!!confirmDelete}
        title="Delete this message?"
        message="It will be removed for everyone in this conversation. This cannot be undone."
        confirmLabel="Delete"
        danger
        onCancel={() => setConfirmDelete(null)}
        onConfirm={retract}
      />

      {/* ------------------------------------------------- image lightbox */}
      <Modal open={!!lightbox} onClose={() => setLightbox(null)} title="Photo" footer={<Btn onClick={() => setLightbox(null)}>Close</Btn>}>
        {lightbox && <img src={lightbox} alt="Full size" className="lightbox-img" />}
      </Modal>
    </div>
  );
}
