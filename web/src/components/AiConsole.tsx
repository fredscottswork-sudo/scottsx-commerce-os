import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  Sparkles, ArrowUp, RotateCcw, Zap, AlertCircle, Square,
  ShoppingBag, Tag, LifeBuoy, TrendingUp, Compass, Bot, ImagePlus, X,
  Copy, Share2, BookOpen, Store, Flame, Wallet, MapPin, Truck, ChevronLeft,
  History, Plus, MessageSquare, Trash2, PanelLeftClose,
} from 'lucide-react';

/** Composer watermarks — rotate continuously so the empty field keeps
 *  suggesting what the assistant can do for a shopper / a seller. */
const BUYER_WATERMARKS = [
  'Ask anything about the market…',
  'Cheapest phones under 1.5M…',
  'Compare two products for me…',
  'What can I buy near me now?',
  'Attach a photo — I’ll find it…',
  'Best sneakers under 200k…',
  'Which seller is verified for TVs?',
  'Show me today’s flash deals…',
] as const;
const BUYER_WATERMARKS_SHORT = [
  'Ask anything…',
  'Phones under 1.5M…',
  'Compare two items…',
  'What’s near me?',
  'Snap a photo…',
  'Sneakers under 200k…',
  'Today’s deals…',
] as const;
const SELLER_WATERMARKS = [
  'Ask about your store…',
  'What should I stock next?',
  'Write a listing for this photo…',
  'How do I price against the market?',
  'Which of my products sell best?',
  'Draft a reply to a buyer…',
] as const;
import { aiService } from '../api/services';
import { type AiAgent, type AiAnswer, type Product } from '../api/types';
import { compressImage } from '../lib/imageSearch';
import { useToast } from '../store/ToastContext';
import { useCart } from '../store/CartContext';
import { ProductGrid } from './ProductCard';
import { Btn, RichText, Badge } from './ui';
import { BrandMark } from './BrandLogo';
import { useRotatingPlaceholder } from '../hooks/useRotatingPlaceholder';

/**
 * The API describes each agent with a lucide icon NAME ('shopping-bag',
 * 'tag', ...). Rendering `a.icon` directly printed those slugs as visible
 * text, so the agent chips on /ai literally read "shopping-bag Shopping".
 * Map the names to components, and fall back to a generic bot for any agent
 * the backend adds later so a new id degrades to an icon, never to a slug.
 */
const AGENT_ICONS: Record<string, typeof ShoppingBag> = {
  'shopping-bag': ShoppingBag,
  tag: Tag,
  'life-buoy': LifeBuoy,
  sparkles: Sparkles,
  'trending-up': TrendingUp,
  compass: Compass,
};

function AgentIcon({ name }: { name?: string }) {
  const Icon = (name && AGENT_ICONS[name]) || Bot;
  return <Icon size={17} aria-hidden />;
}

/**
 * True on phone-width screens. Used to swap in shorter copy where the long
 * form would wrap past the space available and be clipped.
 */
function useNarrowScreen(maxWidth = 480): boolean {
  const [narrow, setNarrow] = useState(
    () => typeof window !== 'undefined' && window.matchMedia(`(max-width: ${maxWidth}px)`).matches
  );
  useEffect(() => {
    const mq = window.matchMedia(`(max-width: ${maxWidth}px)`);
    const onChange = () => setNarrow(mq.matches);
    onChange();
    mq.addEventListener('change', onChange);
    return () => mq.removeEventListener('change', onChange);
  }, [maxWidth]);
  return narrow;
}

/* ── Chat history (on-device) ─────────────────────────────────────────────
   Conversations are kept in localStorage per audience: simple, private, and
   works for guests. Photos are stripped (they are large data URLs). */
interface SavedChat {
  id: string;
  title: string;
  updatedAt: number;
  turns: Turn[];
}
const HISTORY_MAX = 30;
function historyKey(audience: string) { return `stx.ai.history.${audience}`; }
function loadHistory(audience: string): SavedChat[] {
  try {
    const raw = localStorage.getItem(historyKey(audience));
    const list = raw ? (JSON.parse(raw) as SavedChat[]) : [];
    return Array.isArray(list) ? list : [];
  } catch { return []; }
}
function persistHistory(audience: string, list: SavedChat[]) {
  try { localStorage.setItem(historyKey(audience), JSON.stringify(list.slice(0, HISTORY_MAX))); } catch { /* quota */ }
}
function stripTurns(turns: Turn[]): Turn[] {
  return turns
    .filter((t) => !t.pending)
    .map(({ photo, streaming, ...rest }) => ({ ...rest, photo: photo ? 'attached' : undefined }));
}
function titleFor(turns: Turn[]): string {
  const first = turns.find((t) => t.role === 'user')?.content ?? 'New chat';
  return first.length > 48 ? `${first.slice(0, 46).trim()}…` : first;
}
function relTime(ts: number): string {
  const d = Date.now() - ts;
  if (d < 60_000) return 'now';
  if (d < 3_600_000) return `${Math.floor(d / 60_000)}m`;
  if (d < 86_400_000) return `${Math.floor(d / 3_600_000)}h`;
  return `${Math.floor(d / 86_400_000)}d`;
}

interface Turn {
  role: 'user' | 'assistant';
  content: string;
  products?: Product[];
  agent?: string;
  provider?: string;
  model?: string;
  grounded?: boolean;
  pending?: boolean;
  /** Assistant turn still receiving streamed deltas (content is partial). */
  streaming?: boolean;
  llmError?: string;
  /** Photo attached to this user turn (compressed data URL) + its filename. */
  photo?: string;
  photoName?: string;
  /** Assistant: what the vision pipeline saw in the attached photo. */
  photoAnalysis?: { detected: string; matchCount: number; error?: string };
}

type HistoryItem = { role: 'user' | 'assistant'; content: string };

/** Serialize prior turns for the backend (pending turns excluded). A photo's
 *  analysis stays attached to the answer it produced, so follow-ups still
 *  have the photo in context. Pure module function so "regenerate" reuses
 *  exactly the same history the send button builds. */
function historyFromTurns(list: Turn[], max = 8): HistoryItem[] {
  return list
    .filter((t) => !t.pending)
    .slice(-max)
    .map((t) => ({
      role: t.role,
      content:
        t.role === 'assistant' && t.photoAnalysis?.detected
          ? `${t.content}\n[Photo from earlier: ${t.photoAnalysis.detected} — ${t.photoAnalysis.matchCount} live matches]`
          : t.content,
    }));
}

/** Copy any text with a legacy fallback (clipboard API can be absent in
 *  embedded web views and older browsers). Resolves to the copied text. */
async function copyText(text: string): Promise<boolean> {
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text);
      return true;
    }
  } catch { /* fall through to the DOM fallback */ }
  try {
    const ta = document.createElement('textarea');
    ta.value = text;
    ta.style.position = 'fixed';
    ta.style.opacity = '0';
    document.body.appendChild(ta);
    ta.select();
    const ok = document.execCommand('copy');
    ta.remove();
    return ok;
  } catch {
    return false;
  }
}

/** Build a share-ready text block for one assistant answer. */
function answerShareText(t: Turn): string {
  const lines = [t.content];
  if (t.products?.length) {
    lines.push('', 'Matching products:');
    for (const p of t.products.slice(0, 6)) lines.push(`• ${p.title} — ${window.location.origin}/product/${p.id}`);
  }
  lines.push('', '— answered by ScottsTechX AI');
  return lines.join('\n');
}

/**
 * The shared AI surface. Buyers and sellers get the same engine; only the
 * agent roster differs (filtered server-side by audience).
 */
export function AiConsole({
  audience, screen, title, subtitle, fullHeight = false,
}: {
  audience: 'buyer' | 'seller';
  screen: string;
  title: string;
  subtitle: string;
  /**
   * Let the console own the full height of the content area. Used by the
   * dedicated AI pages, which no longer render a PageHeader above the chat:
   * that heading cost ~90px of vertical space on a phone and pushed the
   * conversation into a small box. The same wording now appears in the chat
   * header and the welcome panel instead.
   */
  fullHeight?: boolean;
}) {
  const { toast } = useToast();
  const { add, favoriteSellerIds, toggleFavoriteSeller } = useCart();

  const [agents, setAgents] = useState<AiAgent[]>([]);
  const [agentId, setAgentId] = useState('');
  const [turns, setTurns] = useState<Turn[]>([]);
  const [input, setInput] = useState('');
  const [busy, setBusy] = useState(false);
  /** Live status chip while the answer streams ("Searching the catalogue…"). */
  const [stage, setStage] = useState('');
  /** Aborts the in-flight SSE request (Stop button). */
  const abortRef = useRef<AbortController | null>(null);
  /** Drag-over highlight on the composer (a photo is being dragged onto it). */
  const [dragOver, setDragOver] = useState(false);
  /** Photo attached to the NEXT message (compressed on-device, ~200-400KB). */
  const [photo, setPhoto] = useState<{ dataUrl: string; name: string } | null>(null);
  const fileRef = useRef<HTMLInputElement>(null);
  const [status, setStatus] = useState<{ provider: string; grounded: boolean; configured: boolean; visionProvider?: 'roboflow' | 'nvidia' | 'llm' | 'none'; visionConfigured?: boolean; nvidiaVisionConfigured?: boolean } | null>(null);
  const [openSources, setOpenSources] = useState<number | null>(null);
  const [copied, setCopied] = useState<number | null>(null);

  const scrollRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const rootRef = useRef<HTMLDivElement>(null);
  const narrow = useNarrowScreen();
  /** True while the on-screen keyboard is up (phones). */
  const [keyboardOpen, setKeyboardOpen] = useState(false);
  const navigate = useNavigate();
  const [history, setHistory] = useState<SavedChat[]>(() => loadHistory(audience));
  const [chatId, setChatId] = useState<string>(() => `${Date.now().toString(36)}`);
  const [sidebarOpen, setSidebarOpen] = useState(false);

  /** Persist the current conversation whenever a complete answer lands. */
  useEffect(() => {
    if (!turns.length || turns.some((t) => t.pending)) return;
    setHistory((prev) => {
      const entry: SavedChat = { id: chatId, title: titleFor(turns), updatedAt: Date.now(), turns: stripTurns(turns) };
      const next = [entry, ...prev.filter((c) => c.id !== chatId)];
      persistHistory(audience, next);
      return next;
    });
  }, [turns, chatId, audience]);

  const newChat = useCallback(() => {
    abortRef.current?.abort();
    setTurns([]);
    setPhoto(null);
    setInput('');
    setChatId(`${Date.now().toString(36)}`);
    setSidebarOpen(false);
  }, []);
  const openChat = useCallback((c: SavedChat) => {
    abortRef.current?.abort();
    setTurns(c.turns.map((t) => ({ ...t, photo: undefined })));
    setChatId(c.id);
    setSidebarOpen(false);
  }, []);
  const deleteChat = useCallback((id: string) => {
    setHistory((prev) => { const next = prev.filter((c) => c.id !== id); persistHistory(audience, next); return next; });
    if (id === chatId) { setTurns([]); setChatId(`${Date.now().toString(36)}`); }
  }, [audience, chatId]);
  const watermark = useRotatingPlaceholder(
    audience === 'buyer' ? (narrow ? BUYER_WATERMARKS_SHORT : BUYER_WATERMARKS) : SELLER_WATERMARKS,
    2800
  );

  /**
   * Mobile keyboard. When the soft keyboard opens, the layout viewport does
   * not shrink on iOS (and only sometimes on Android): the page is scrolled
   * behind the keyboard, the composer disappears under it and pressing Send
   * jumps the screen around. Track the *visual* viewport instead and size the
   * surface to it, so the composer always sits just above the keyboard and
   * the transcript keeps scrolling inside.
   */
  useEffect(() => {
    if (!fullHeight) return;
    const vv = window.visualViewport;
    if (!vv) return;
    const root = document.documentElement;
    let raf = 0;
    const apply = () => {
      raf = 0;
      const kb = Math.max(0, Math.round(window.innerHeight - vv.height - vv.offsetTop));
      const open = kb > 120;
      root.style.setProperty('--kbd-h', `${open ? kb : 0}px`);
      setKeyboardOpen(open);
      // Keep the document pinned: the browser likes to scroll the page so the
      // focused field is visible, which drags the header off-screen.
      if (open && window.scrollY !== 0) window.scrollTo(0, 0);
      if (open) scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight });
    };
    const onChange = () => { if (!raf) raf = requestAnimationFrame(apply); };
    vv.addEventListener('resize', onChange);
    vv.addEventListener('scroll', onChange);
    apply();
    return () => {
      vv.removeEventListener('resize', onChange);
      vv.removeEventListener('scroll', onChange);
      if (raf) cancelAnimationFrame(raf);
      root.style.removeProperty('--kbd-h');
    };
  }, [fullHeight]);

  /** Auto-grow the textarea to its content (up to the CSS max-height). */
  const autosize = useCallback(() => {
    const el = inputRef.current;
    if (!el) return;
    el.style.height = '0px';
    // Grows with the text between the CSS min-height and this cap; the
    // container adds the toolbar row beneath.
    const cap = window.matchMedia('(max-width: 620px)').matches ? 220 : 320;
    el.style.height = `${Math.min(el.scrollHeight, cap)}px`;
  }, []);
  useEffect(() => { autosize(); }, [input, autosize]);

  useEffect(() => {
    aiService.agents()
      .then((r) => {
        const mine = r.agents.filter((a) => a.audience === audience || a.audience === 'both');
        setAgents(mine);
        setAgentId(mine[0]?.id ?? '');
      })
      .catch(() => undefined);
    aiService.status().then(setStatus).catch(() => undefined);
  }, [audience]);

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: 'smooth' });
  }, [turns]);

  /**
   * On the dedicated AI routes the conversation is the whole app surface, the
   * way it is on Alibaba's or ChatGPT's assistant pages: the page itself must
   * not scroll, only the transcript inside it. A class on <body> is the only
   * place that can express that, because the scrolling document is an
   * ancestor of this component. Removed on unmount so every other route
   * scrolls normally again.
   */
  useEffect(() => {
    if (!fullHeight) return;
    document.body.classList.add('ai-immersive');

    // How much chrome sits above the surface cannot be hardcoded: the public
    // layout stacks a header AND a 45px category bar (158px measured), while
    // the signed-in layout differs again. --topbar-h is only 62px, and using
    // it pushed the composer 40px below the fold on a 360px phone. So measure
    // the real offset and re-measure whenever the chrome can change.
    const root = document.documentElement;
    const surface =
      (document.querySelector('.public-content') as HTMLElement | null) ??
      (document.querySelector('.content') as HTMLElement | null);

    const measure = () => {
      if (!surface) return;
      // Distance from the top of the viewport to the top of the surface.
      const top = Math.max(0, Math.round(surface.getBoundingClientRect().top));
      root.style.setProperty('--ai-surface-top', `${top}px`);

      const nav = document.querySelector('.bottomnav') as HTMLElement | null;
      const navVisible = nav && getComputedStyle(nav).display !== 'none';
      root.style.setProperty(
        '--bottom-chrome',
        navVisible ? `${Math.round(nav!.getBoundingClientRect().height)}px` : '0px'
      );
    };

    measure();
    // Orientation changes, the URL bar collapsing, and the breakpoint that
    // shows/hides the bottom nav all move these numbers.
    window.addEventListener('resize', measure);
    window.addEventListener('orientationchange', measure);

    return () => {
      window.removeEventListener('resize', measure);
      window.removeEventListener('orientationchange', measure);
      document.body.classList.remove('ai-immersive');
      root.style.removeProperty('--ai-surface-top');
      root.style.removeProperty('--bottom-chrome');
    };
  }, [fullHeight]);

  const activeAgent = agents.find((a) => a.id === agentId);

  const send = useCallback(async (text: string, imageData?: string, historyOverride?: HistoryItem[]) => {
    const prompt = text.trim();
    if (!prompt || busy) return;

    // Snapshot the history *before* adding this turn — the backend wants the
    // prior context, not the message it is about to answer. Regenerate passes
    // an explicit override (the turns to the user message are already gone).
    const history = historyOverride ?? historyFromTurns(turns);

    setTurns((t) => [
      ...t,
      { role: 'user', content: prompt, photo: imageData, photoName: photo?.name },
      { role: 'assistant', content: '', pending: true },
    ]);
    setInput('');
    setPhoto(null);
    setBusy(true);
    setStage(imageData ? 'Analyzing your photo…' : 'Searching the catalogue…');

    // Finalize = replace the still-pending last turn with the completed answer.
    const finalize = (r: AiAnswer) => {
      setTurns((t) => {
        const next = [...t];
        next[next.length - 1] = {
          role: 'assistant',
          content: r.text,
          products: r.products,
          agent: r.agent?.name,
          provider: r.provider,
          model: r.model,
          grounded: r.grounded,
          llmError: (r as any).llmError,
          photoAnalysis: (r as any).photoAnalysis,
        };
        return next;
      });
      // The router may have switched agents based on the prompt.
      if (r.agent?.id && r.agent.id !== agentId && agents.some((a) => a.id === r.agent.id)) {
        setAgentId(r.agent.id);
      }
    };

    const ctrl = new AbortController();
    abortRef.current = ctrl;
    try {
      // Stream the answer; if the SSE transport dies (proxy timeout, network
      // drop, Render waking up) fall back to the plain JSON ask — the user
      // still gets the full answer, just without the typewriter effect.
      let r: AiAnswer;
      try {
        r = await aiService.askStream(prompt, {
          screen,
          agent: agentId || undefined,
          history,
          imageData,
          signal: ctrl.signal,
          onEvent: (ev) => {
          if (ev.type === 'stage') {
            setStage(ev.text);
            return;
          }
          if (ev.type === 'reasoning') {
            // Thinking deltas are private chain-of-thought — the user sees a
            // pulsing "Thinking…" state, never the raw reasoning.
            setStage('Thinking…');
            return;
          }
          if (ev.type === 'delta') {
            const delta = ev.text;
            setStage('');
            setTurns((prev) => {
              const next = [...prev];
              const last = next[next.length - 1];
              if (!last || last.role !== 'assistant' || !last.pending) return prev;
              next[next.length - 1] = { ...last, content: last.content + delta, streaming: true };
              return next;
            });
            return;
          }
          if (ev.type === 'error' && ev.message) {
            // The backend emits this just before its own fallback answer; a
            // death of the stream AFTER the error is reported by the throw
            // below. Never paint it over live content.
            setStage('');
          }
        },
        });
      } catch (streamErr) {
        if (ctrl.signal.aborted) throw streamErr;
        // SSE transport died (proxy timeout, network drop, cold start) —
        // retry once through the plain JSON ask so the user still gets the
        // full answer instead of a "Failed to fetch" bubble.
        r = await aiService.ask(prompt, { screen, agent: agentId || undefined, history, imageData });
      }
      finalize(r);
    } catch (e: any) {
      if (ctrl.signal.aborted) {
        // User pressed Stop — keep whatever streamed so far, mark it done.
        setTurns((t) => {
          const next = [...t];
          const last = next[next.length - 1];
          if (last?.pending) {
            next[next.length - 1] = {
              ...last,
              pending: false,
              streaming: false,
              content: (last.content || '⏹️ Stopped.').trim(),
              agent: 'ScottsTechX AI',
            };
          }
          return next;
        });
      } else {
        setTurns((t) => {
          const next = [...t];
          next[next.length - 1] = {
            role: 'assistant',
            content: `⚠️ ${e?.message || 'The assistant is unavailable right now. Please try again.'}`,
          };
          return next;
        });
        toast(e?.message || 'AI request failed', 'error');
      }
    } finally {
      setBusy(false);
      setStage('');
      abortRef.current = null;
      // Only pull focus back on desktop — on a phone that would pop the
      // keyboard open again over the answer the user is trying to read.
      if (!window.matchMedia('(pointer: coarse)').matches) inputRef.current?.focus();
    }
  }, [agentId, agents, busy, photo, screen, toast, turns]);

  /** Attach a photo to the next CHAT message (compressed on-device first). */
  const attachFile = useCallback(
    async (file: File) => {
      if (!file.type.startsWith('image/')) return;
      try {
        const c = await compressImage(file);
        if (c.dataUrl.length > 7 * 1024 * 1024) {
          toast('That photo is still too large — try a smaller one.', 'warning');
          return;
        }
        setPhoto({ dataUrl: c.dataUrl, name: c.filename });
        toast('Photo attached — ask me about it!', 'success');
      } catch (err: any) {
        toast(err?.message || 'Could not read that image.', 'error');
      }
    },
    [toast]
  );

  const onAttachFile = useCallback(
    async (e: React.ChangeEvent<HTMLInputElement>) => {
      const file = e.target.files?.[0];
      e.target.value = '';
      if (file) await attachFile(file);
    },
    [attachFile]
  );

  /** Paste an image straight into the composer (Ctrl/Cmd+V). */
  const onPastePhoto = useCallback(
    async (e: React.ClipboardEvent) => {
      const file = Array.from(e.clipboardData?.files ?? []).find((f) => f.type.startsWith('image/'));
      if (file) {
        e.preventDefault();
        await attachFile(file);
      }
    },
    [attachFile]
  );

  /** Drag & drop a photo onto the composer. */
  const onDropPhoto = useCallback(
    async (e: React.DragEvent) => {
      e.preventDefault();
      setDragOver(false);
      const file = Array.from(e.dataTransfer?.files ?? []).find((f) => f.type.startsWith('image/'));
      if (file) await attachFile(file);
    },
    [attachFile]
  );

  /** Copy one assistant answer to the clipboard. */
  const copyAnswer = useCallback(async (t: Turn, i: number) => {
    const ok = await copyText(t.content);
    setCopied(i);
    window.setTimeout(() => setCopied((c) => (c === i ? null : c)), 1600);
    toast(ok ? 'Answer copied' : 'Could not copy the answer', ok ? 'success' : 'error');
  }, [toast]);

  /** Share one assistant answer (native share sheet, or copy as fallback). */
  const shareAnswer = useCallback(async (t: Turn, i: number) => {
    const text = answerShareText(t);
    const nav = navigator as any;
    if (nav.share) {
      try {
        await nav.share({ title: 'ScottsTechX AI', text });
        return;
      } catch (e: any) {
        if (e?.name === 'AbortError') return; // user closed the sheet — not an error
      }
    }
    const ok = await copyText(text);
    toast(ok ? 'Answer copied — paste it anywhere to share' : 'Could not share the answer', ok ? 'success' : 'error');
  }, [toast]);

  /** Copy the store link of a matched product. */
  const copyShop = useCallback(async (p: Product) => {
    const url = `${window.location.origin}/seller/${p.seller.id}`;
    const ok = await copyText(`${p.seller.name} — ${url}`);
    toast(ok ? `${p.seller.name} shop link copied` : 'Could not copy the shop link', ok ? 'success' : 'error');
  }, [toast]);

  /** Regenerate the last answer: drop its turn plus the user message and ask
   *  again with the same prior context (history override, exactly as send). */
  const regenerate = useCallback(async () => {
    if (busy) return;
    let idx = -1;
    for (let i = turns.length - 1; i >= 0; i--) {
      if (turns[i].role === 'user') { idx = i; break; }
    }
    if (idx < 0) return;
    const prior = historyFromTurns(turns.slice(0, idx));
    const userTurn = turns[idx];
    setTurns((t) => t.slice(0, idx));
    await send(userTurn.content, userTurn.photo, prior);
  }, [busy, turns, send]);

  return (
    <div
      ref={rootRef}
      className={`ai-console${fullHeight ? ' ai-console-full ai-console--noagents' : ''}${keyboardOpen ? ' ai-console--kbd' : ''}`}
    >
      <div className="ai-console-inner">
      {/* Agent picker removed for extraordinary full-screen chat — user asked to remove agent words above */}
      {!fullHeight && (
        <aside className="card ai-agents">
          <div className="row-between mb-12">
            <h3 className="card-title"><Sparkles size={16} /> Agents</h3>
            {status && (
              <Badge tone={status.grounded ? 'green' : 'amber'}>
                {status.grounded ? 'Store-aware' : 'Limited'}
              </Badge>
            )}
          </div>

          <div className="col" style={{ gap: 7 }}>
            {agents.map((a) => (
              <button
                key={a.id}
                className={`agent-card ${agentId === a.id ? 'active' : ''}`}
                onClick={() => setAgentId(a.id)}
              >
                <span className="agent-emoji"><AgentIcon name={a.icon} /></span>
                <span style={{ minWidth: 0 }}>
                  <span className="agent-name">{a.name}</span>
                  <span className="agent-tag">{a.tagline}</span>
                </span>
              </button>
            ))}
          </div>

          {status && (
            <p className="tiny muted-2 mt-16">
              Engine: <strong>{status.provider}</strong>
              {!status.configured && ' · local catalogue mode'}
              {status.visionProvider === 'roboflow' && (
                <span className="t-success"> · Vision: Roboflow + {status.nvidiaVisionConfigured ? 'NVIDIA' : 'LLM'} captions</span>
              )}
              {status.visionProvider === 'nvidia' && (
                <span className="t-success"> · Vision: NVIDIA captions</span>
              )}
              {status.visionProvider === 'llm' && <> · Vision: LLM</>}
              {status.visionProvider === 'none' && status.visionConfigured === false && (
                <span className="muted-2"> · Vision not configured</span>
              )}
            </p>
          )}
        </aside>
      )}

      {/* ── Conversation — now full, no agent words above, bigger & flexible ── */}
      <div className="card ai-chat card-flush ai-chat--full">
        {/* ── History drawer ─────────────────────────────────────────────── */}
        {sidebarOpen && <button type="button" className="ai-drawer-scrim" aria-label="Close history" onClick={() => setSidebarOpen(false)} />}
        <aside className={`ai-drawer ${sidebarOpen ? 'ai-drawer--open' : ''}`} aria-label="Chat history" aria-hidden={!sidebarOpen}>
          <div className="ai-drawer-head">
            <strong>Chats</strong>
            <button type="button" className="ai-cap ai-cap--icon" onClick={() => setSidebarOpen(false)} aria-label="Close">
              <PanelLeftClose size={16} />
            </button>
          </div>
          <button type="button" className="ai-drawer-new" onClick={newChat}>
            <Plus size={16} /> New chat
          </button>
          <div className="ai-drawer-list">
            {history.length === 0 && (
              <p className="ai-drawer-empty">Your conversations will appear here. They stay on this device.</p>
            )}
            {history.map((c) => (
              <div key={c.id} className={`ai-drawer-item ${c.id === chatId ? 'is-active' : ''}`}>
                <button type="button" className="ai-drawer-open" onClick={() => openChat(c)} title={c.title}>
                  <MessageSquare size={14} />
                  <span className="ai-drawer-title">{c.title}</span>
                  <span className="ai-drawer-time">{relTime(c.updatedAt)}</span>
                </button>
                <button type="button" className="ai-drawer-del" onClick={() => deleteChat(c.id)} aria-label="Delete chat" title="Delete">
                  <Trash2 size={13} />
                </button>
              </div>
            ))}
          </div>
        </aside>

        <div className="ai-main">
        <div className="ai-chat-head ai-chat-head--brand">
          {fullHeight && (
            <button
              type="button"
              className="ai-back"
              onClick={() => (window.history.length > 1 ? navigate(-1) : navigate('/'))}
              aria-label="Back"
              title="Back"
            >
              <ChevronLeft size={20} />
            </button>
          )}
          <div className="ai-brand" aria-label="ScottsTechX AI">
            <span className="ai-brand-orb" aria-hidden="true"><BrandMark size={30} className="ai-brand-mark" /></span>
            <div className="ai-brand-copy">
              <strong className="ai-brand-name">ScottsTechX <em>AI</em></strong>
              <span className="ai-brand-sub">
                <span className={`ai-live-dot ${status?.configured ? 'ai-live-dot--on' : ''}`} aria-hidden="true" />
                {status?.configured ? 'Live · marketplace assistant' : 'Catalogue mode'}
              </span>
            </div>
          </div>
          <div className="ai-cap-row">
            <button
              type="button"
              className={`ai-cap ai-cap--icon ${sidebarOpen ? 'ai-cap--active' : ''}`}
              onClick={() => setSidebarOpen((v) => !v)}
              title="Chat history"
              aria-label="Chat history"
              aria-expanded={sidebarOpen}
            >
              <History size={17} />
              {history.length > 0 && <span className="ai-cap-count">{history.length > 9 ? '9+' : history.length}</span>}
            </button>
            <button
              type="button"
              className="ai-cap ai-cap--icon"
              onClick={newChat}
              title="New chat"
              aria-label="New chat"
            >
              <Plus size={18} />
            </button>
          </div>
        </div>

        <div className="ai-chat-body" ref={scrollRef}>
          {turns.length === 0 ? (
            <div className={`ai-welcome ${fullHeight ? 'ai-welcome--bare' : ''}`}>
              {!fullHeight ? (
                <>
                  <div className="ai-avatar" style={{ width: 44, height: 44, borderRadius: 12 }}>
                    <Sparkles size={20} />
                  </div>
                  <h3 className="mt-10" style={{ fontFamily: 'var(--font-display)', fontSize: 15 }}>{title}</h3>
                  <p className="muted mt-4" style={{ maxWidth: 'min(460px, 100%)' }}>{subtitle}</p>
                </>
              ) : (
                <div className="ai-welcome-bare">
                  <span className="ai-welcome-orb" aria-hidden="true">
                    <BrandMark size={56} className="ai-brand-mark" />
                    <i className="ai-welcome-shadow" />
                  </span>
                  <h2 className="ai-welcome-title">What are you looking for?</h2>
                  <p className="ai-welcome-sub">Products, prices and sellers across the marketplace — or attach a photo and I’ll find it.</p>
                </div>
              )}

              <div className="row wrap center mt-14" style={{ gap: 6, justifyContent: 'center' }}>
                {(activeAgent?.starters ?? []).map((s) => (
                  <button key={s} className="chip" style={{ fontSize: 12, padding: '6px 12px' }} onClick={() => void send(s)}>{s}</button>
                ))}
              </div>
            </div>
          ) : (
            turns.map((t, i) => (
              // A turn is the bubble PLUS, for the assistant, any products it
              // found. The products used to live inside the bubble, which is
              // capped at 88% of the row and indented past the avatar — on a
              // 360px phone that left ~202px, so a two-column grid rendered
              // 95px-wide cards: images the size of a thumbnail and truncated
              // titles. Product results are the answer on a shopping
              // assistant, so they now sit OUTSIDE the bubble and take the
              // full width of the conversation.
              <div key={i} className="ai-turn">
                <div className={`bubble-row ${t.role}`}>
                  {t.role === 'assistant' && (
                    <span className="ai-avatar" style={{ width: 28, height: 28 }}><Sparkles size={13} /></span>
                  )}
                  <div className={`bubble ${t.role === 'user' ? 'bubble-user' : 'bubble-ai'}`}>
                    {t.pending && !t.content && (
                      <span className="ai-stream-state">
                        {stage && <span className="ai-stage">{stage}</span>}
                        <span className="typing"><i /><i /><i /></span>
                      </span>
                    )}
                    {t.pending && t.content && stage && (
                      <span className="ai-stage ai-stage--inline">{stage}</span>
                    )}
                    {(!t.pending || t.content) && (
                      <>
                        {t.role === 'user' && t.photo && (
                          <img
                            src={t.photo}
                            alt={t.photoName || 'attached photo'}
                            style={{
                              display: 'block', maxWidth: 160, maxHeight: 120,
                              objectFit: 'cover', borderRadius: 10, marginBottom: 6,
                            }}
                          />
                        )}
                        <RichText text={t.content} />
                        {t.role === 'assistant' && t.photoAnalysis && (
                          <p className="tiny muted-2" style={{ margin: '8px 0 0' }}>
                            {t.photoAnalysis.error ? (
                              <>
                                ⚠️ Could not identify the photo
                                {t.photoAnalysis.error ? ` — ${t.photoAnalysis.error.slice(0, 90)}` : ''}
                              </>
                            ) : (
                              <>
                                🔍 Photo analyzed: <strong>{t.photoAnalysis.detected.slice(0, 90)}</strong>
                                {t.photoAnalysis.matchCount > 0
                                  ? ` · ${t.photoAnalysis.matchCount} matching listing${t.photoAnalysis.matchCount === 1 ? '' : 's'}`
                                  : ' · no live matches yet'}
                              </>
                            )}
                          </p>
                        )}
                        {t.role === 'assistant' && (
                          <div className="ai-answer-meta">
                            <span className="ai-answer-wm" title="AI-generated content">
                              <Sparkles size={10} /> ScottsTechX AI
                              {t.agent ? ` · ${t.agent}` : ''}
                              {t.provider ? ` · ${t.provider}` : ''}
                              {t.llmError ? ` · ${t.llmError.slice(0, 90)}` : ''}
                            </span>
                            <span className="ai-answer-actions">
                              <button
                                type="button"
                                className="ai-answer-act"
                                title="Regenerate this answer"
                                aria-label="Regenerate this answer"
                                disabled={busy || i !== turns.length - 1}
                                onClick={() => void regenerate()}
                              >
                                <RotateCcw size={12} /> Regenerate
                              </button>
                              <button
                                type="button"
                                className="ai-answer-act"
                                title="Copy answer"
                                aria-label="Copy answer"
                                onClick={() => void copyAnswer(t, i)}
                              >
                                {copied === i ? '✓ Copied' : <><Copy size={12} /> Copy</>}
                              </button>
                              <button
                                type="button"
                                className="ai-answer-act"
                                title="Share answer"
                                aria-label="Share answer"
                                onClick={() => void shareAnswer(t, i)}
                              >
                                <Share2 size={12} /> Share
                              </button>
                              {!!t.products?.length && (
                                <button
                                  type="button"
                                  className="ai-answer-act"
                                  title="Show sources"
                                  aria-label="Show sources"
                                  onClick={() => setOpenSources((s) => (s === i ? null : i))}
                                >
                                  <BookOpen size={12} /> {openSources === i ? 'Hide sources' : `Sources (${t.products.length})`}
                                </button>
                              )}
                            </span>
                          </div>
                        )}
                        {t.role === 'assistant' && t.grounded === false && (
                          <p className="tiny muted-2 mt-8">
                            <AlertCircle size={11} style={{ verticalAlign: -1 }} /> Answered without live catalogue data.
                          </p>
                        )}
                        {t.streaming && <span className="stream-caret" aria-hidden="true" />}
                      </>
                    )}
                  </div>
                </div>

                {!t.pending && !!t.products?.length && (
                  <div className="ai-results">
                    <p className="tiny semi muted ai-results-head">
                      <Zap size={12} style={{ verticalAlign: -2 }} /> Matching products
                    </p>
                    <ProductGrid
                      products={t.products.slice(0, 6)}
                      onAddToCart={audience === 'buyer' ? (p) => void add(p) : undefined}
                      onToggleFavorite={audience === 'buyer'
                        ? (p) => void toggleFavoriteSeller(p.seller.id, p.seller.name)
                        : undefined}
                      favoriteSellerIds={favoriteSellerIds}
                    />
                    {openSources === i && (
                      <div className="ai-sources">
                        <p className="tiny semi muted-2" style={{ marginBottom: 6 }}>
                          <BookOpen size={11} style={{ verticalAlign: -2 }} /> Sources — live catalogue matches
                        </p>
                        <div className="col" style={{ gap: 4 }}>
                          {t.products.slice(0, 8).map((p) => (
                            <div key={p.id} className="ai-source-row">
                              <Link to={`/product/${p.id}`} className="ai-source-link">
                                {p.title}
                              </Link>
                              <button
                                type="button"
                                className="ai-answer-act"
                                title="Copy shop link"
                                aria-label={`Copy ${p.seller.name} shop link`}
                                onClick={() => void copyShop(p)}
                              >
                                <Store size={11} /> Copy shop
                              </button>
                            </div>
                          ))}
                        </div>
                      </div>
                    )}
                  </div>
                )}
              </div>
            ))
          )}
        </div>

        {/* Market quick actions — step-in tools that make the assistant feel
            like the marketplace's own, not a generic chat bot. */}
        {turns.length > 0 && (
          <div className="ai-quickbar" role="toolbar" aria-label="Quick actions">
            <button
              type="button"
              className="ai-quick"
              disabled={busy}
              onClick={() => void send("Show me today's flash deals and the hottest prices.")}
            >
              <Flame size={13} /> Flash deals
            </button>
            <button
              type="button"
              className="ai-quick"
              disabled={busy}
              onClick={() => void send('Help me compare prices for what I describe.')}
            >
              <Wallet size={13} /> Price check
            </button>
            <button
              type="button"
              className="ai-quick"
              disabled={busy}
              onClick={() => void send('What can I buy near me right now? Tell me the closest sellers with stock.')}
            >
              <MapPin size={13} /> Near me
            </button>
            <button
              type="button"
              className="ai-quick"
              disabled={busy}
              onClick={() => void send('How do I track my order or get help with delivery?')}
            >
              <Truck size={13} /> Track order
            </button>
          </div>
        )}

        <form
          className={`ai-chat-input${dragOver ? ' ai-chat-input--drop' : ''}${photo ? ' ai-chat-input--photo' : ''}`}
          onSubmit={(e) => {
            e.preventDefault();
            const text = input.trim() || (photo ? 'What can you tell me from this photo?' : '');
            if (!text) return;
            const img = photo?.dataUrl;
            setPhoto(null);
            void send(text, img);
          }}
          onPaste={(e) => void onPastePhoto(e)}
          onDrop={(e) => void onDropPhoto(e)}
          onDragOver={(e) => {
            if (Array.from(e.dataTransfer?.items ?? []).some((i) => i.kind === 'file')) {
              e.preventDefault();
              setDragOver(true);
            }
          }}
          onDragLeave={() => setDragOver(false)}
          title={dragOver ? 'Drop the photo to attach it' : undefined}
        >
          {/* Attached photo lives ABOVE the text row as a small chip, so the
              text field keeps its full width — before, the preview sat in the
              same row and left almost no room to type on a phone. */}
          {photo && (
            <div className="ai-attach">
              <span className="ai-attach-thumb">
                <img src={photo.dataUrl} alt={photo.name} />
                <button
                  type="button"
                  className="ai-attach-x"
                  title="Remove photo"
                  aria-label="Remove photo"
                  onClick={() => { setPhoto(null); inputRef.current?.focus(); }}
                >
                  <X size={11} />
                </button>
              </span>
              <span className="ai-attach-meta">
                <strong>Photo attached</strong>
                <span>{narrow ? 'Describe what you want to know' : 'Add a question, or just send to identify it'}</span>
              </span>
            </div>
          )}
          <div className="ai-composer" onClick={(e) => { if (e.target === e.currentTarget) inputRef.current?.focus(); }}>
            <input
              ref={fileRef}
              type="file"
              accept="image/*"
              style={{ display: 'none' }}
              onChange={(e) => void onAttachFile(e)}
              aria-hidden
              tabIndex={-1}
            />
            <div className="ai-composer-field">
            {!input && !photo && (
              <span className="ai-wm" aria-hidden="true" key={watermark}>{watermark}</span>
            )}
            <textarea
              ref={inputRef}
              rows={1}
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={(e) => {
                // Enter sends on desktop only. On a phone Enter should make a
                // new line — the Send button is right there and this keeps
                // the keyboard from being dismissed mid-thought.
                if (e.key === 'Enter' && !e.shiftKey && !window.matchMedia('(pointer: coarse)').matches) {
                  e.preventDefault();
                  const text = input.trim() || (photo ? 'What can you tell me from this photo?' : '');
                  if (!text) return;
                  const img = photo?.dataUrl;
                  setPhoto(null);
                  void send(text, img);
                }
              }}
              enterKeyHint="send"
              placeholder={photo ? 'Ask about this photo…' : ''}
              aria-label="Message the assistant"
            />
            </div>
            <div className="ai-composer-bar">
            <button
              type="button"
              className="ai-chat-img"
              onClick={() => fileRef.current?.click()}
              title="Attach a photo for the assistant to analyze"
              aria-label="Attach a photo"
            >
              <ImagePlus size={18} />
            </button>
              <span className="ai-composer-hint" aria-hidden="true">
                {narrow ? '' : 'Enter to send · Shift+Enter for a new line'}
              </span>
            <button
              type={busy ? 'button' : 'submit'}
              className={`ai-send${busy ? ' ai-send--stop' : ''}${!busy && (input.trim() || photo) ? ' ai-send--ready' : ''}`}
              disabled={!busy && !input.trim() && !photo}
              aria-label={busy ? 'Stop generating' : 'Send message'}
              title={busy ? 'Stop' : 'Send'}
              // Keep focus (and the keyboard) where it is: a mousedown on the
              // button would blur the textarea and the keyboard would collapse
              // and re-open, which is the "jumping" people see on phones.
              onMouseDown={(e) => e.preventDefault()}
              onTouchStart={() => { /* noop — enables :active on iOS */ }}
              onClick={busy ? () => abortRef.current?.abort() : undefined}
            >
              {busy ? <Square size={15} /> : <ArrowUp size={18} strokeWidth={2.5} />}
            </button>
            </div>
          </div>
        </form>
        <p className="ai-disclaimer">
          AI answers are generated — double-check prices and availability with the seller before paying.
        </p>
        </div>
      </div>
      </div>
    </div>
  );
}
