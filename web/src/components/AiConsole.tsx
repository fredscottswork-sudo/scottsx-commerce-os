import { useCallback, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Sparkles, Send, RotateCcw, Zap, AlertCircle,
  ShoppingBag, Tag, LifeBuoy, TrendingUp, Compass, Bot, Mic, Camera, ImagePlus, X,
  Copy, Share2, BookOpen, Store,
} from 'lucide-react';
import { aiService } from '../api/services';
import type { AiAgent, AiSearchResult, Product } from '../api/types';
import { compressImage } from '../lib/imageSearch';
import { useToast } from '../store/ToastContext';
import { useCart } from '../store/CartContext';
import { ProductGrid } from './ProductCard';
import { Btn, RichText, Badge, Empty, Modal } from './ui';
import { VisualSearch } from './VisualSearch';

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

interface Turn {
  role: 'user' | 'assistant';
  content: string;
  products?: Product[];
  agent?: string;
  provider?: string;
  model?: string;
  grounded?: boolean;
  pending?: boolean;
  llmError?: string;
  /** Photo attached to this user turn (compressed data URL) + its filename. */
  photo?: string;
  photoName?: string;
  /** Assistant: what the vision pipeline saw in the attached photo. */
  photoAnalysis?: { detected: string; matchCount: number };
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
  const [listening, setListening] = useState(false);
  const [imgOpen, setImgOpen] = useState(false);
  /** Photo attached to the NEXT message (compressed on-device, ~200-400KB). */
  const [photo, setPhoto] = useState<{ dataUrl: string; name: string } | null>(null);
  const fileRef = useRef<HTMLInputElement>(null);
  const [status, setStatus] = useState<{ provider: string; grounded: boolean; configured: boolean; visionProvider?: 'roboflow' | 'nvidia' | 'llm' | 'none'; visionConfigured?: boolean; nvidiaVisionConfigured?: boolean } | null>(null);
  const [openSources, setOpenSources] = useState<number | null>(null);
  const [copied, setCopied] = useState<number | null>(null);

  const scrollRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const narrow = useNarrowScreen();

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

  const send = useCallback(async (text: string, imageData?: string) => {
    const prompt = text.trim();
    if (!prompt || busy) return;

    // Snapshot the history *before* adding this turn — the backend wants the
    // prior context, not the message it is about to answer. A photo's analysis
    // stays attached to the answer it produced, so follow-up questions
    // ("and how much is delivery?") still have the photo in context.
    const history = turns
      .filter((t) => !t.pending)
      .slice(-8)
      .map((t) => ({
        role: t.role,
        content:
          t.role === 'assistant' && t.photoAnalysis?.detected
            ? `${t.content}\n[Photo from earlier: ${t.photoAnalysis.detected} — ${t.photoAnalysis.matchCount} live matches]`
            : t.content,
      }));

    setTurns((t) => [
      ...t,
      { role: 'user', content: prompt, photo: imageData, photoName: photo?.name },
      { role: 'assistant', content: '', pending: true },
    ]);
    setInput('');
    setPhoto(null);
    setBusy(true);

    try {
      const r = await aiService.ask(prompt, { screen, agent: agentId || undefined, history, imageData });
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
    } catch (e: any) {
      setTurns((t) => {
        const next = [...t];
        next[next.length - 1] = {
          role: 'assistant',
          content: `⚠️ ${e?.message || 'The assistant is unavailable right now. Please try again.'}`,
        };
        return next;
      });
      toast(e?.message || 'AI request failed', 'error');
    } finally {
      setBusy(false);
      inputRef.current?.focus();
    }
  }, [agentId, agents, busy, photo, screen, toast, turns]);

  /** Voice — speak your request, the assistant answers. */
  const startVoice = useCallback(() => {
    const Ctor = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;
    if (!Ctor) {
      toast('Voice input needs Chrome or Edge on this device', 'warning');
      return;
    }
    if (listening) return;
    const rec: any = new Ctor();
    rec.lang = 'en-UG';
    rec.continuous = false;
    rec.interimResults = false;
    rec.onresult = (e: any) => {
      const heard = e.results?.[0]?.[0]?.transcript ?? '';
      setListening(false);
      if (heard.trim()) void send(heard);
    };
    rec.onerror = () => { setListening(false); toast('Could not hear you — try again', 'error'); };
    rec.onend = () => setListening(false);
    rec.start();
    setListening(true);
  }, [listening, send, toast]);

  /** Photo — find products by image, then show them as a grounded answer. */
  const onPhotoResults = useCallback((r: AiSearchResult) => {
    setImgOpen(false);
    setTurns((t) => [
      ...t,
      { role: 'user', content: 'Find this in the marketplace 📷' },
      {
        role: 'assistant',
        content: r.explanation || 'Here is what matches the photo.',
        products: r.products,
        grounded: true,
        agent: 'Photo search',
        provider: status?.provider,
        photoAnalysis: { detected: r.detected ?? '', matchCount: r.products?.length ?? 0 },
      },
    ]);
  }, [status]);

  /** Attach a photo to the next CHAT message (compressed on-device first). */
  const onAttachFile = useCallback(
    async (e: React.ChangeEvent<HTMLInputElement>) => {
      const file = e.target.files?.[0];
      e.target.value = '';
      if (!file) return;
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

  return (
    <div className={`ai-console${fullHeight ? ' ai-console-full ai-console--noagents' : ''}`}>
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
        <div className="ai-chat-head ai-chat-head--brand">
          <div className="ai-brand" aria-label="ScottsTechX AI">
            <span className="ai-brand-orb" aria-hidden="true"><Sparkles size={18} /></span>
            <div className="ai-brand-copy">
              <strong className="ai-brand-name">ScottsTechX <em>AI</em></strong>
              <span className="ai-brand-sub">{title} · grounded in the live marketplace</span>
            </div>
            <span className={`ai-live ${status?.configured ? 'ai-live--on' : ''}`}>
              <span className="ai-live-dot" aria-hidden="true" />
              {status?.configured ? 'Live' : 'Catalogue mode'}
            </span>
          </div>
          <div className="ai-cap-row">
            <button
              type="button"
              className={`ai-cap ${listening ? 'ai-cap--active' : ''}`}
              onClick={startVoice}
              title="Ask by voice"
              aria-label="Ask by voice"
            >
              <Mic size={15} />
              {listening ? 'Listening…' : 'Voice'}
            </button>
            {turns.length > 0 && (
              <Btn size="sm" variant="ghost" icon={<RotateCcw size={14} />} onClick={() => setTurns([])}>
                New chat
              </Btn>
            )}
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
                  <Sparkles size={18} className="muted-2" />
                  <p className="muted" style={{ fontSize: 13, margin: 0 }}>Ask anything — products, prices, sellers</p>
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
                    {t.pending ? (
                      <span className="typing"><i /><i /><i /></span>
                    ) : (
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
                        {t.role === 'assistant' && t.photoAnalysis?.detected && (
                          <p className="tiny muted-2" style={{ margin: '8px 0 0' }}>
                            🔍 Photo analyzed: <strong>{t.photoAnalysis.detected.slice(0, 90)}</strong>
                            {t.photoAnalysis.matchCount > 0
                              ? ` · ${t.photoAnalysis.matchCount} matching listing${t.photoAnalysis.matchCount === 1 ? '' : 's'}`
                              : ' · no live matches yet'}
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

        <form
          className="ai-chat-input"
          onSubmit={(e) => {
            e.preventDefault();
            const text = input.trim() || (photo ? 'What can you tell me from this photo?' : '');
            if (!text) return;
            const img = photo?.dataUrl;
            setPhoto(null);
            void send(text, img);
          }}
        >
          {photo && (
            <div className="ai-chat-photo" style={{ display: 'flex', alignItems: 'center', gap: 8, width: '100%' }}>
              <img
                src={photo.dataUrl}
                alt={photo.name}
                style={{ width: 44, height: 44, objectFit: 'cover', borderRadius: 8, border: '1px solid var(--border, #ddd)' }}
              />
              <span className="tiny muted-2" style={{ minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                {photo.name} — attached to your message
              </span>
              <button
                type="button"
                className="ai-answer-act"
                title="Remove photo"
                aria-label="Remove photo"
                onClick={() => setPhoto(null)}
              >
                <X size={13} />
              </button>
            </div>
          )}
          <input
            ref={fileRef}
            type="file"
            accept="image/*"
            style={{ display: 'none' }}
            onChange={(e) => void onAttachFile(e)}
            aria-hidden
            tabIndex={-1}
          />
          <button
            type="button"
            className="ai-chat-img"
            onClick={() => fileRef.current?.click()}
            title="Attach a photo for the assistant to analyze"
            aria-label="Attach a photo"
          >
            <ImagePlus size={16} />
          </button>
          <button
            type="button"
            className="ai-chat-img"
            onClick={() => setImgOpen(true)}
            title="Search the catalogue by photo"
            aria-label="Search by photo"
          >
            <Camera size={16} />
          </button>
          <textarea
            ref={inputRef}
            rows={1}
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                const text = input.trim() || (photo ? 'What can you tell me from this photo?' : '');
                if (!text) return;
                const img = photo?.dataUrl;
                setPhoto(null);
                void send(text, img);
              }
            }}
            placeholder={
              /* The long examples wrap to five lines in a 320px composer and
                 get clipped, so the hint is unreadable exactly where space is
                 tightest. Show the short form on narrow screens. */
              narrow
                ? (audience === 'buyer' ? 'Ask anything…' : 'Ask about your store…')
                : audience === 'buyer'
                  ? 'Ask anything — “cheapest phones under 2M”, “compare these two”…'
                  : 'Ask about pricing, listings, buyers — “what should I stock next?”'
            }
            aria-label="Message the assistant"
          />
          <Btn
            variant="primary"
            type="submit"
            loading={busy}
            disabled={!input.trim() && !photo}
            icon={<Send size={15} />}
          >
            Send
          </Btn>
        </form>
        <p className="ai-disclaimer">
          AI answers are generated — double-check prices and availability with the seller before paying.
        </p>
      </div>

      <Modal
        open={imgOpen}
        onClose={() => setImgOpen(false)}
        title="Search by photo"
        footer={<Btn onClick={() => setImgOpen(false)}>Close</Btn>}
      >
        <VisualSearch compact onResults={onPhotoResults} />
      </Modal>
    </div>
  );
}
