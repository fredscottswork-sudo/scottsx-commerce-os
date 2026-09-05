/**
 * Conversation thread — WhatsApp-style trading chat.
 *
 * Full-height surface (the shell hides the search bar, category nav and
 * bottom nav on this route): header, product strip, transcript, composer.
 *
 * Capabilities: text, photos (picked from the device, compressed on-device,
 * uploaded, with a caption), price offers with accept/decline/withdraw,
 * counter-offers, reply-to, copy, delete, read receipts, typing indicators,
 * pin/mute, quick replies, product card in-chat, jump-to-latest, lightbox,
 * date separators, message grouping.
 *
 * Polling (5s, paused when hidden) — the API is stateless and this keeps the
 * mobile app and web identical.
 */
import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import {
  ArrowDown, ArrowLeft, BellOff, BellRing, Check, CheckCheck, ChevronRight, Copy, HandCoins, ImagePlus,
  Inbox, Loader2, MoreVertical, Package, Pin, PinOff, Reply, Send, ShieldCheck, ShoppingCart, Store,
  Trash2, X, Zap,
} from 'lucide-react';
import { chatService } from '../api/services';
import { multipart } from '../api/client';
import type { ChatMessage, Conversation, QuickReply } from '../api/types';
import { formatUgx } from '../api/types';
import { compressImage } from '../lib/imageSearch';
import { useAuth } from '../store/AuthContext';
import { useToast } from '../store/ToastContext';
import { Avatar, Badge, Btn, ConfirmModal, Field, Input, Modal } from '../components/ui';

const money = (minor?: number | null) =>
  minor == null ? '' : `UGX ${(minor / 100).toLocaleString('en-UG')}`;
const productMoney = (major?: number | null) => (major == null ? '' : formatUgx(major));

const DEFAULT_QUICK_BUYER = ['Is this still available?', 'What is your best price?', 'Do you deliver?', 'Can I see more photos?', 'Thanks!'];
const DEFAULT_QUICK_SELLER = ['Yes, it is available.', 'Delivery is available within Kampala.', 'Cash on delivery or mobile money.', 'I can hold it for you until tomorrow.', 'Thank you for your order!'];

function dayLabel(iso: string): string {
  const d = new Date(iso);
  const now = new Date();
  if (d.toDateString() === now.toDateString()) return 'Today';
  const y = new Date(now);
  y.setDate(now.getDate() - 1);
  if (d.toDateString() === y.toDateString()) return 'Yesterday';
  return d.toLocaleDateString([], { day: 'numeric', month: 'long', year: d.getFullYear() === now.getFullYear() ? undefined : 'numeric' });
}
const clock = (iso: string) => new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
const relative = (iso?: string | null) => {
  if (!iso) return '';
  const s = Math.max(0, (Date.now() - new Date(iso).getTime()) / 1000);
  if (s < 60) return 'just now';
  if (s < 3600) return `${Math.floor(s / 60)} min ago`;
  if (s < 86400) return `${Math.floor(s / 3600)} h ago`;
  return new Date(iso).toLocaleDateString([], { day: 'numeric', month: 'short' });
};

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
  const [counterOf, setCounterOf] = useState<ChatMessage | null>(null);
  const [photo, setPhoto] = useState<{ file: File; dataUrl: string; name: string } | null>(null);
  const [uploading, setUploading] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState<ChatMessage | null>(null);
  const [offerBusy, setOfferBusy] = useState<string | null>(null);
  const [replyTarget, setReplyTarget] = useState<ChatMessage | null>(null);
  const [lightbox, setLightbox] = useState<string | null>(null);
  const [scrollOffset, setScrollOffset] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);
  const [actionsFor, setActionsFor] = useState<string | null>(null);
  const [dragOver, setDragOver] = useState(false);
  const [newBelow, setNewBelow] = useState(0);

  const bottomRef = useRef<HTMLDivElement>(null);
  const bodyRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const fileRef = useRef<HTMLInputElement>(null);
  const typingSentAt = useRef(0);
  const lastCount = useRef(0);
  const typingTimeout = useRef<number | null>(null);
  const longPress = useRef<number | null>(null);

  const load = useCallback(async (opts: { quiet?: boolean } = {}) => {
    if (!id) return;
    try {
      const [head, body] = await Promise.all([chatService.thread(id), chatService.messages(id)]);
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
    lastCount.current = 0;
    load();
    chatService.markRead(id!).catch(() => undefined);
    chatService.quickReplies().then((r) => setQuickReplies(r.quickReplies)).catch(() => undefined);
  }, [id, load]);

  // Live polling — 5s (2s while the other side is typing), paused when hidden.
  useEffect(() => {
    let timer: number;
    const tick = () => {
      if (!document.hidden) {
        load({ quiet: true }).catch(() => undefined);
        chatService.markRead(id!).catch(() => undefined);
      }
      timer = window.setTimeout(tick, otherTyping ? 2000 : 5000);
    };
    timer = window.setTimeout(tick, otherTyping ? 2000 : 5000);
    const onVis = () => { if (!document.hidden) tick(); };
    document.addEventListener('visibilitychange', onVis);
    return () => { clearTimeout(timer); document.removeEventListener('visibilitychange', onVis); };
  }, [id, load, otherTyping]);

  // Autoscroll only when the transcript grew AND the reader was near the bottom;
  // otherwise count the new messages for the jump pill.
  useEffect(() => {
    if (messages.length === lastCount.current) return;
    const grew = messages.length - lastCount.current;
    const first = lastCount.current === 0;
    lastCount.current = messages.length;
    const el = bodyRef.current;
    const nearBottom = !el || el.scrollHeight - el.scrollTop - el.clientHeight < 160;
    if (first) {
      bottomRef.current?.scrollIntoView({ block: 'end' });
    } else if (nearBottom) {
      bottomRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' });
    } else {
      const lastMsg = messages[messages.length - 1];
      if (lastMsg && lastMsg.senderId !== user?.id) setNewBelow((n) => n + grew);
    }
  }, [messages, user?.id]);

  useEffect(() => {
    const el = bodyRef.current;
    if (!el) return;
    const onScroll = () => {
      const away = el.scrollHeight - el.scrollTop - el.clientHeight > 220;
      setScrollOffset(away);
      if (!away) setNewBelow(0);
    };
    el.addEventListener('scroll', onScroll, { passive: true });
    return () => el.removeEventListener('scroll', onScroll);
  }, [loading]);

  // The shell's topbar wraps on phones, so its real height is measured and
  // the thread fills exactly what is left (no page scroll, composer pinned).
  useEffect(() => {
    const bar = document.querySelector<HTMLElement>('.topbar');
    const set = () => document.documentElement.style.setProperty('--thread-top', `${bar?.offsetHeight ?? 62}px`);
    set();
    const ro = typeof ResizeObserver !== 'undefined' && bar ? new ResizeObserver(set) : null;
    ro?.observe(bar!);
    window.addEventListener('resize', set);
    return () => { ro?.disconnect(); window.removeEventListener('resize', set); document.documentElement.style.removeProperty('--thread-top'); };
  }, []);

  const jumpLatest = () => { bottomRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' }); setNewBelow(0); };

  // Auto-grow textarea (1–5 lines).
  const growInput = () => {
    const ta = inputRef.current;
    if (!ta) return;
    ta.style.height = 'auto';
    ta.style.height = `${Math.min(ta.scrollHeight, 132)}px`;
  };
  useEffect(growInput, [input]);

  async function copyText(m: ChatMessage) {
    const text = m.text || m.attachmentName || '';
    try { await navigator.clipboard.writeText(text); } catch {
      const ta = document.createElement('textarea');
      ta.value = text; ta.style.position = 'fixed'; ta.style.opacity = '0';
      document.body.appendChild(ta); ta.select(); document.execCommand('copy'); document.body.removeChild(ta);
    }
    toast('Copied', 'success');
    setActionsFor(null);
  }

  function onInputChange(value: string) {
    setInput(value);
    const now = Date.now();
    if (!value.trim()) {
      if (typingTimeout.current) window.clearTimeout(typingTimeout.current);
      typingTimeout.current = null;
      typingSentAt.current = 0;
      chatService.typing(id!, false).catch(() => undefined);
      return;
    }
    if (now - typingSentAt.current > 3000) {
      typingSentAt.current = now;
      chatService.typing(id!, true).catch(() => undefined);
    }
    if (typingTimeout.current) window.clearTimeout(typingTimeout.current);
    typingTimeout.current = window.setTimeout(() => {
      chatService.typing(id!, false).catch(() => undefined);
    }, 4000) as unknown as number;
  }

  useEffect(() => () => {
    if (typingTimeout.current) window.clearTimeout(typingTimeout.current);
    if (id) chatService.typing(id, false).catch(() => undefined);
  }, [id]);

  // ── Photos: pick / paste / drop → compress → preview → upload on send ────
  const attachPhoto = useCallback(async (file: File | null | undefined) => {
    if (!file) return;
    if (!file.type.startsWith('image/')) { toast('Please choose a photo', 'warning'); return; }
    try {
      const c = await compressImage(file);
      setPhoto({ file: new File([c.blob], c.filename, { type: 'image/jpeg' }), dataUrl: c.dataUrl, name: c.filename });
      inputRef.current?.focus();
    } catch (e: any) {
      toast(e?.message || 'Could not read that photo', 'error');
    }
  }, [toast]);

  async function send(e?: FormEvent) {
    e?.preventDefault();
    const text = input.trim();
    if ((!text && !photo) || sending) return;
    const attached = photo;
    setInput('');
    setPhoto(null);
    if (typingTimeout.current) window.clearTimeout(typingTimeout.current);
    typingTimeout.current = null;
    typingSentAt.current = 0;
    chatService.typing(id!, false).catch(() => undefined);
    setSending(true);
    const optimistic: ChatMessage = {
      id: `tmp-${Date.now()}`,
      senderId: user!.id,
      text,
      imageUrl: attached?.dataUrl ?? null,
      kind: attached ? 'image' : 'text',
      replyToId: replyTarget?.id ?? null,
      createdAt: new Date().toISOString(),
    };
    setMessages((m) => [...m, optimistic]);
    const reply = replyTarget;
    setReplyTarget(null);
    try {
      let imageUrl: string | undefined;
      if (attached) {
        setUploading(true);
        const form = new FormData();
        form.append('image', attached.file, attached.name);
        form.append('purpose', 'chat');
        const up = (await multipart('/uploads/images', form)) as { url: string };
        imageUrl = up.url;
        setUploading(false);
      }
      await chatService.send(id!, {
        text: text || undefined,
        kind: imageUrl ? 'image' : 'text',
        imageUrl,
        attachmentName: attached?.name,
        replyToId: reply?.id,
      });
      await load({ quiet: true });
      window.dispatchEvent(new Event('stx:refresh-badges'));
    } catch (err: any) {
      setMessages((m) => m.filter((x) => x.id !== optimistic.id));
      setInput(text);
      if (attached) setPhoto(attached);
      if (reply) setReplyTarget(reply);
      toast(err.message || 'Message not sent', 'error');
    } finally {
      setUploading(false);
      setSending(false);
    }
  }

  async function sendOffer() {
    const amount = Number(offerPrice);
    const qty = Number(offerQty) || 1;
    if (!amount || amount <= 0) { toast('Enter an offer amount', 'error'); return; }
    try {
      if (counterOf && counterOf.senderId !== user?.id && counterOf.offerStatus === 'pending') {
        await chatService.respondToOffer(id!, counterOf.id, 'decline').catch(() => undefined);
      }
      await chatService.send(id!, {
        kind: 'offer',
        offerMinor: Math.round(amount * 100),
        offerQuantity: qty,
        replyToId: counterOf?.id,
      });
      setOfferOpen(false);
      setCounterOf(null);
      setOfferPrice('');
      setOfferQty('1');
      await load({ quiet: true });
      toast(counterOf ? 'Counter-offer sent' : 'Offer sent', 'success');
    } catch (e: any) { toast(e.message, 'error'); }
  }

  async function respond(m: ChatMessage, action: 'accept' | 'decline' | 'withdraw') {
    setOfferBusy(m.id);
    try {
      await chatService.respondToOffer(id!, m.id, action);
      await load({ quiet: true });
      toast(action === 'accept' ? 'Offer accepted 🎉' : action === 'decline' ? 'Offer declined' : 'Offer withdrawn', action === 'accept' ? 'success' : 'info');
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
    setMenuOpen(false);
    try {
      const r = await chatService.setState(id!, patch);
      setConv((c) => (c ? { ...c, ...r.state } : c));
      toast(label, 'success');
    } catch (e: any) { toast(e.message, 'error'); }
  }

  const isBuyer = conv?.mySide === 'buyer';
  const quickChips = useMemo(
    () => (quickReplies.length ? quickReplies.map((q) => q.text) : isBuyer ? DEFAULT_QUICK_BUYER : DEFAULT_QUICK_SELLER),
    [quickReplies, isBuyer]
  );

  const acceptedOffer = useMemo(
    () => [...messages].reverse().find((m) => m.kind === 'offer' && m.offerStatus === 'accepted'),
    [messages]
  );

  const openOffer = (counter?: ChatMessage) => {
    setCounterOf(counter ?? null);
    if (counter?.offerMinor) setOfferPrice(String(Math.round(counter.offerMinor / 100)));
    else if (conv?.productPriceMinor) setOfferPrice(String(Math.round(conv.productPriceMinor * 0.9)));
    if (counter?.offerQuantity) setOfferQty(String(counter.offerQuantity));
    setOfferOpen(true);
  };

  // Long-press / right-click a bubble for its actions on touch screens.
  const pressStart = (mid: string) => {
    longPress.current = window.setTimeout(() => setActionsFor(mid), 420) as unknown as number;
  };
  const pressEnd = () => { if (longPress.current) { clearTimeout(longPress.current); longPress.current = null; } };

  if (loading) {
    return (
      <div className="thread-wrap thread-wrap--loading">
        <div className="skeleton" style={{ height: 58 }} />
        <div className="skeleton grow" />
        <div className="skeleton" style={{ height: 64 }} />
      </div>
    );
  }
  if (error) {
    return (
      <div className="thread-wrap">
        <div className="thread-error">
          <p>{error}</p>
          <Btn variant="primary" onClick={() => load()}>Try again</Btn>
        </div>
      </div>
    );
  }

  const other = conv?.otherParty;
  let lastDay = '';
  const canSend = !!(input.trim() || photo) && !sending;

  return (
    <div
      className={`thread-wrap${dragOver ? ' thread-wrap--drop' : ''}`}
      onDragOver={(e) => { if (e.dataTransfer?.types?.includes('Files')) { e.preventDefault(); setDragOver(true); } }}
      onDragLeave={() => setDragOver(false)}
      onDrop={(e) => { e.preventDefault(); setDragOver(false); void attachPhoto(Array.from(e.dataTransfer?.files ?? []).find((f) => f.type.startsWith('image/'))); }}
      onClick={() => { if (menuOpen) setMenuOpen(false); if (actionsFor) setActionsFor(null); }}
    >
      {/* ── Header ─────────────────────────────────────────────────────── */}
      <header className="thread-head">
        <button className="icon-btn" onClick={() => navigate('/messages')} aria-label="Back to inbox">
          <ArrowLeft size={20} />
        </button>
        <Link to={other?.role === 'seller' ? `/seller/${other.id}` : '#'} className="thread-head-id" onClick={(e) => { if (other?.role !== 'seller') e.preventDefault(); }}>
          <span className="thread-head-avatar">
            <Avatar name={other?.name} src={other?.photoUrl ?? undefined} />
            {otherTyping && <span className="thread-head-live" aria-hidden />}
          </span>
          <span className="grow" style={{ minWidth: 0 }}>
            <span className="row" style={{ gap: 5 }}>
              <strong className="ellipsis">{other?.name ?? 'Conversation'}</strong>
              {other?.verified && <ShieldCheck size={14} className="chat-verified" />}
            </span>
            <span className="thread-status">
              {otherTyping ? (
                <span className="typing-live">typing<span className="typing-dot" /><span className="typing-dot" /><span className="typing-dot" /></span>
              ) : (
                <>
                  {other?.role === 'seller' ? <Store size={11} /> : null}
                  {other?.role === 'seller' ? 'Seller' : 'Buyer'}
                  {other?.location ? ` · ${other.location}` : ''}
                  {conv?.otherLastReadAt ? ` · seen ${relative(conv.otherLastReadAt)}` : ''}
                </>
              )}
            </span>
          </span>
        </Link>
        {conv?.pinned && <Pin size={14} className="thread-head-pin" aria-label="Pinned" />}
        {conv?.muted && <BellOff size={14} className="thread-head-pin" aria-label="Muted" />}
        <div className="thread-menu-wrap" onClick={(e) => e.stopPropagation()}>
          <button className="icon-btn" aria-label="Conversation menu" aria-expanded={menuOpen} onClick={() => setMenuOpen((v) => !v)}>
            <MoreVertical size={19} />
          </button>
          {menuOpen && (
            <div className="thread-menu" role="menu">
              {conv?.productId && (
                <Link role="menuitem" to={`/product/${conv.productId}`} className="thread-menu-item" onClick={() => setMenuOpen(false)}>
                  <Package size={15} /> View product
                </Link>
              )}
              {other?.role === 'seller' && (
                <Link role="menuitem" to={`/seller/${other.id}`} className="thread-menu-item" onClick={() => setMenuOpen(false)}>
                  <Store size={15} /> Visit store
                </Link>
              )}
              <button role="menuitem" className="thread-menu-item" onClick={() => toggleState({ pinned: !conv?.pinned }, conv?.pinned ? 'Unpinned' : 'Pinned to top')}>
                {conv?.pinned ? <PinOff size={15} /> : <Pin size={15} />} {conv?.pinned ? 'Unpin' : 'Pin conversation'}
              </button>
              <button role="menuitem" className="thread-menu-item" onClick={() => toggleState({ muted: !conv?.muted }, conv?.muted ? 'Notifications on' : 'Muted')}>
                {conv?.muted ? <BellRing size={15} /> : <BellOff size={15} />} {conv?.muted ? 'Unmute' : 'Mute notifications'}
              </button>
            </div>
          )}
        </div>
      </header>

      {/* ── Product strip ──────────────────────────────────────────────── */}
      {conv?.productTitle && (
        <Link to={conv.productId ? `/product/${conv.productId}` : '#'} className="thread-product">
          {conv.productImageUrl ? (
            <img src={conv.productImageUrl} alt="" loading="lazy" />
          ) : (
            <span className="thread-product-ph"><Package size={16} /></span>
          )}
          <span className="grow" style={{ minWidth: 0 }}>
            <span className="thread-product-label">About this item</span>
            <span className="ellipsis thread-product-title">{conv.productTitle}</span>
          </span>
          <span className="thread-product-price">
            {acceptedOffer?.offerMinor ? (
              <>
                <strong>{money(acceptedOffer.offerMinor)}</strong>
                <small>agreed</small>
              </>
            ) : conv.productPriceMinor != null ? (
              <strong>{productMoney(conv.productPriceMinor)}</strong>
            ) : null}
          </span>
          <ChevronRight size={16} className="muted-2" />
        </Link>
      )}

      {/* ── Transcript ─────────────────────────────────────────────────── */}
      <div className="thread-body" ref={bodyRef}>
        {messages.length === 0 ? (
          <div className="thread-empty">
            <div className="thread-empty-ico"><Inbox size={26} /></div>
            <strong>Say hello to {other?.name ?? 'them'}</strong>
            <span>Ask about price, delivery or availability. Messages are private between you two.</span>
            <div className="chip-row" style={{ justifyContent: 'center' }}>
              {quickChips.slice(0, 3).map((q) => (
                <button key={q} className="chip" onClick={() => { setInput(q); inputRef.current?.focus(); }}>{q}</button>
              ))}
            </div>
          </div>
        ) : (
          messages.map((m, mi) => {
            const mine = m.senderId === user?.id;
            const day = dayLabel(m.createdAt);
            const showDay = day !== lastDay;
            lastDay = day;
            const prev = messages[mi - 1];
            const next = messages[mi + 1];
            const sameAsPrev = !!prev && prev.senderId === m.senderId && dayLabel(prev.createdAt) === day && prev.kind !== 'system' && m.kind !== 'system';
            const sameAsNext = !!next && next.senderId === m.senderId && dayLabel(next.createdAt) === day && next.kind !== 'system' && m.kind !== 'system';
            const replied = m.replyToId ? messages.find((x) => x.id === m.replyToId) : undefined;

            if (m.kind === 'system') {
              return (
                <div key={m.id} className="thread-item">
                  {showDay && <div className="thread-day"><span>{day}</span></div>}
                  <div className="thread-system">{m.text}</div>
                </div>
              );
            }

            if (m.kind === 'offer') {
              const pending = m.offerStatus === 'pending';
              const tone = m.offerStatus === 'accepted' ? 'accepted' : pending ? 'pending' : 'closed';
              return (
                <div key={m.id} className="thread-item">
                  {showDay && <div className="thread-day"><span>{day}</span></div>}
                  <div className={`msg-row${mine ? ' mine' : ''}`}>
                    <div className={`offer-card offer-card--${tone}${mine ? ' mine' : ''}`}>
                      <div className="offer-head">
                        <span className="offer-kind"><HandCoins size={14} /> {mine ? 'Your offer' : `${other?.name?.split(' ')[0] ?? 'Their'} offer`}</span>
                        <Badge tone={m.offerStatus === 'accepted' ? 'green' : pending ? 'amber' : 'red'}>{m.offerStatus}</Badge>
                      </div>
                      <div className="offer-price">{money(m.offerMinor)}</div>
                      <div className="offer-sub">
                        {(m.offerQuantity ?? 1) > 1 ? `for ${m.offerQuantity} units` : 'per unit'}
                        {conv?.productPriceMinor != null && m.offerMinor != null && (
                          <> · {Math.round((1 - m.offerMinor / 100 / conv.productPriceMinor) * 100) > 0
                            ? `${Math.round((1 - m.offerMinor / 100 / conv.productPriceMinor) * 100)}% below list`
                            : 'at list price'}</>
                        )}
                      </div>
                      {pending && (
                        <div className="offer-actions">
                          {mine ? (
                            <Btn size="sm" loading={offerBusy === m.id} onClick={() => respond(m, 'withdraw')}>Withdraw</Btn>
                          ) : (
                            <>
                              <Btn size="sm" variant="primary" loading={offerBusy === m.id} onClick={() => respond(m, 'accept')}>Accept</Btn>
                              <Btn size="sm" onClick={() => openOffer(m)}>Counter</Btn>
                              <Btn size="sm" variant="ghost" loading={offerBusy === m.id} onClick={() => respond(m, 'decline')}>Decline</Btn>
                            </>
                          )}
                        </div>
                      )}
                      {m.offerStatus === 'accepted' && isBuyer && conv?.productId && (
                        <Link to={`/product/${conv.productId}`} className="btn btn-sm btn-primary offer-cta">
                          <ShoppingCart size={14} /> Order at this price
                        </Link>
                      )}
                      <div className="bubble-meta"><span>{clock(m.createdAt)}</span></div>
                    </div>
                  </div>
                </div>
              );
            }

            const retracted = !!m.deletedAt;
            const optimistic = String(m.id).startsWith('tmp-');
            const showActions = actionsFor === m.id;
            return (
              <div key={m.id} className="thread-item">
                {showDay && <div className="thread-day"><span>{day}</span></div>}
                <div className={`msg-row${mine ? ' mine' : ''}${sameAsPrev ? ' cont' : ''}${sameAsNext ? ' more' : ''}`}>
                  <div
                    className={`bubble ${mine ? 'bubble-mine' : 'bubble-other'}${retracted ? ' bubble-retracted' : ''}${m.imageUrl ? ' bubble-has-img' : ''}${showActions ? ' bubble-focus' : ''}`}
                    onContextMenu={(e) => { if (!retracted) { e.preventDefault(); setActionsFor(m.id); } }}
                    onTouchStart={() => !retracted && pressStart(m.id)}
                    onTouchEnd={pressEnd}
                    onTouchMove={pressEnd}
                    onClick={(e) => e.stopPropagation()}
                  >
                    {!sameAsPrev && !mine && <span className="bubble-tail" aria-hidden />}
                    {retracted ? (
                      <em className="bubble-deleted"><Trash2 size={12} /> This message was deleted</em>
                    ) : (
                      <>
                        {replied && (
                          <button type="button" className="bubble-quote" onClick={() => document.getElementById(`m-${replied.id}`)?.scrollIntoView({ behavior: 'smooth', block: 'center' })}>
                            <span className="bubble-quote-who">{replied.senderId === user?.id ? 'You' : other?.name}</span>
                            <span className="ellipsis">{replied.text || (replied.kind === 'offer' ? `Offer ${money(replied.offerMinor)}` : '📷 Photo')}</span>
                          </button>
                        )}
                        {m.imageUrl && (
                          <button type="button" className="bubble-img-wrap" onClick={() => setLightbox(m.imageUrl!)}>
                            <img src={m.imageUrl} alt={m.attachmentName ?? 'Photo'} className="bubble-img" loading="lazy" />
                            {optimistic && uploading && <span className="bubble-img-busy"><Loader2 size={18} className="anim-spin" /></span>}
                          </button>
                        )}
                        {m.text && <span className="bubble-text" id={`m-${m.id}`}>{m.text}</span>}
                      </>
                    )}
                    <span className="bubble-meta">
                      <span>{clock(m.createdAt)}</span>
                      {optimistic ? (
                        <span className="bubble-clock" aria-label="Sending" />
                      ) : mine && !retracted ? (
                        m.readByOther ? <CheckCheck size={14} className="receipt-read" aria-label="Read" /> : <Check size={14} aria-label="Sent" />
                      ) : null}
                    </span>
                    {!retracted && !optimistic && (
                      <span className={`bubble-actions${showActions ? ' open' : ''}`} onClick={(e) => e.stopPropagation()}>
                        <button title="Reply" aria-label="Reply" onClick={() => { setReplyTarget(m); setActionsFor(null); inputRef.current?.focus(); }}><Reply size={14} /></button>
                        {(m.text || m.attachmentName) && <button title="Copy" aria-label="Copy message" onClick={() => void copyText(m)}><Copy size={14} /></button>}
                        {mine && <button title="Delete" aria-label="Delete message" className="danger" onClick={() => { setConfirmDelete(m); setActionsFor(null); }}><Trash2 size={14} /></button>}
                      </span>
                    )}
                  </div>
                </div>
              </div>
            );
          })
        )}

        {otherTyping && messages.length > 0 && (
          <div className="msg-row">
            <div className="bubble bubble-other typing-bubble">
              <span className="typing-dot" /><span className="typing-dot" /><span className="typing-dot" />
            </div>
          </div>
        )}
        <div ref={bottomRef} />
      </div>

      {scrollOffset && messages.length > 0 && (
        <button className={`thread-jump${newBelow ? ' has-new' : ''}`} onClick={jumpLatest} aria-label="Jump to latest">
          {newBelow ? <span className="thread-jump-count">{newBelow}</span> : null}
          <ArrowDown size={16} />
        </button>
      )}

      {/* ── Composer ───────────────────────────────────────────────────── */}
      <div className="thread-foot" onClick={(e) => e.stopPropagation()}>
        {quickOpen && (
          <div className="thread-quick">
            {conv?.productId && (
              <button type="button" className="chip chip-primary" onClick={() => { setQuickOpen(false); openOffer(); }}>
                <HandCoins size={13} /> {isBuyer ? 'Make an offer' : 'Propose a price'}
              </button>
            )}
            {quickChips.map((q) => (
              <button key={q} type="button" className="chip" onClick={() => { setInput(q); setQuickOpen(false); inputRef.current?.focus(); }}>
                {q}
              </button>
            ))}
          </div>
        )}

        {replyTarget && (
          <div className="thread-reply-bar">
            <span className="thread-reply-line" />
            <span className="grow" style={{ minWidth: 0 }}>
              <span className="thread-reply-who">{replyTarget.senderId === user?.id ? 'You' : other?.name}</span>
              <span className="ellipsis">{replyTarget.text || (replyTarget.kind === 'offer' ? `Offer ${money(replyTarget.offerMinor)}` : '📷 Photo')}</span>
            </span>
            <button type="button" className="icon-btn" aria-label="Cancel reply" onClick={() => setReplyTarget(null)}><X size={15} /></button>
          </div>
        )}

        {photo && (
          <div className="thread-attach">
            <img src={photo.dataUrl} alt="" />
            <span className="grow" style={{ minWidth: 0 }}>
              <strong>Photo ready</strong>
              <span className="muted-2">Add a caption or just send</span>
            </span>
            <button type="button" className="icon-btn" aria-label="Remove photo" onClick={() => setPhoto(null)}><X size={15} /></button>
          </div>
        )}

        <form onSubmit={send} className="thread-composer" onPaste={(e) => { const f = Array.from(e.clipboardData?.files ?? []).find((x) => x.type.startsWith('image/')); if (f) { e.preventDefault(); void attachPhoto(f); } }}>
          <input ref={fileRef} type="file" accept="image/*" style={{ display: 'none' }} aria-hidden tabIndex={-1}
            onChange={(e) => { const f = e.target.files?.[0]; e.target.value = ''; void attachPhoto(f); }} />
          <div className="thread-composer-box">
            <button type="button" className={`icon-btn${quickOpen ? ' active' : ''}`} title="Quick replies" aria-label="Quick replies" onClick={() => setQuickOpen((v) => !v)}>
              <Zap size={19} />
            </button>
            <textarea
              ref={inputRef}
              rows={1}
              value={input}
              onChange={(e) => onInputChange(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' && !e.shiftKey && !window.matchMedia('(pointer: coarse)').matches) { e.preventDefault(); void send(); }
              }}
              placeholder={photo ? 'Add a caption…' : 'Message'}
              aria-label="Message"
              enterKeyHint="send"
            />
            <button type="button" className="icon-btn" title="Send a photo" aria-label="Send a photo" onClick={() => fileRef.current?.click()}>
              <ImagePlus size={19} />
            </button>
            {conv?.productId && (
              <button type="button" className="icon-btn thread-offer-btn" title={isBuyer ? 'Make an offer' : 'Propose a price'} aria-label="Make an offer" onClick={() => openOffer()}>
                <HandCoins size={19} />
              </button>
            )}
          </div>
          <button type="submit" className={`thread-send${canSend ? ' ready' : ''}`} disabled={!canSend} aria-label="Send">
            {sending ? <Loader2 size={18} className="anim-spin" /> : <Send size={18} />}
          </button>
        </form>
      </div>

      {/* ── Dialogs ────────────────────────────────────────────────────── */}
      <Modal open={offerOpen} title={counterOf ? 'Counter-offer' : isBuyer ? 'Make an offer' : 'Propose a price'} onClose={() => { setOfferOpen(false); setCounterOf(null); }}
        footer={<><Btn onClick={() => { setOfferOpen(false); setCounterOf(null); }}>Cancel</Btn><Btn variant="primary" onClick={sendOffer} icon={<HandCoins size={15} />}>{counterOf ? 'Send counter' : 'Send offer'}</Btn></>}>
        {conv?.productPriceMinor != null && (
          <p className="muted mb-12">Listed at <strong>{productMoney(conv.productPriceMinor)}</strong>. {isBuyer ? 'The seller can accept, counter or decline.' : 'The buyer can accept, counter or decline.'}</p>
        )}
        {counterOf?.offerMinor != null && (
          <p className="muted mb-12">Countering <strong>{money(counterOf.offerMinor)}</strong>{(counterOf.offerQuantity ?? 1) > 1 ? ` × ${counterOf.offerQuantity}` : ''}.</p>
        )}
        {conv?.productPriceMinor != null && (
          <div className="chip-row mb-12">
            {[0.95, 0.9, 0.85, 0.8].map((f) => (
              <button key={f} type="button" className="chip" onClick={() => setOfferPrice(String(Math.round(conv.productPriceMinor! * f)))}>
                −{Math.round((1 - f) * 100)}% · {formatUgx(Math.round(conv.productPriceMinor! * f))}
              </button>
            ))}
          </div>
        )}
        <Field label="Price per unit (UGX)">
          <Input type="number" min="1" inputMode="numeric" value={offerPrice} onChange={(e) => setOfferPrice(e.target.value)} placeholder="e.g. 450000" autoFocus />
        </Field>
        <Field label="Quantity">
          <Input type="number" min="1" inputMode="numeric" value={offerQty} onChange={(e) => setOfferQty(e.target.value)} />
        </Field>
        {Number(offerPrice) > 0 && (
          <p className="tiny muted">Total: <strong>{formatUgx(Number(offerPrice) * (Number(offerQty) || 1))}</strong></p>
        )}
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

      {lightbox && (
        <div className="thread-lightbox" role="dialog" aria-label="Photo" onClick={() => setLightbox(null)}>
          <button type="button" className="thread-lightbox-x" aria-label="Close"><X size={20} /></button>
          <img src={lightbox} alt="Full size" onClick={(e) => e.stopPropagation()} />
        </div>
      )}
    </div>
  );
}
