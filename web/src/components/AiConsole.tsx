import { useCallback, useEffect, useRef, useState } from 'react';
import {
  Sparkles, Send, RotateCcw, Zap, AlertCircle,
  ShoppingBag, Tag, LifeBuoy, TrendingUp, Compass, Bot, Mic, Camera,
} from 'lucide-react';
import { aiService } from '../api/services';
import type { AiAgent, AiSearchResult, Product } from '../api/types';
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
  grounded?: boolean;
  pending?: boolean;
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
  const [status, setStatus] = useState<{ provider: string; grounded: boolean; configured: boolean } | null>(null);

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

  const send = useCallback(async (text: string) => {
    const prompt = text.trim();
    if (!prompt || busy) return;

    // Snapshot the history *before* adding this turn — the backend wants the
    // prior context, not the message it is about to answer.
    const history = turns
      .filter((t) => !t.pending)
      .slice(-8)
      .map((t) => ({ role: t.role, content: t.content }));

    setTurns((t) => [...t, { role: 'user', content: prompt }, { role: 'assistant', content: '', pending: true }]);
    setInput('');
    setBusy(true);

    try {
      const r = await aiService.ask(prompt, { screen, agent: agentId || undefined, history });
      setTurns((t) => {
        const next = [...t];
        next[next.length - 1] = {
          role: 'assistant',
          content: r.text,
          products: r.products,
          agent: r.agent?.name,
          grounded: r.grounded,
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
  }, [agentId, agents, busy, screen, toast, turns]);

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
      },
    ]);
  }, []);

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
            <button
              type="button"
              className="ai-cap"
              onClick={() => setImgOpen(true)}
              title="Search by photo"
              aria-label="Search by photo"
            >
              <Camera size={15} />
              Photo
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
                        <RichText text={t.content} />
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
                  </div>
                )}
              </div>
            ))
          )}
        </div>

        <form
          className="ai-chat-input"
          onSubmit={(e) => { e.preventDefault(); void send(input); }}
        >
          <textarea
            ref={inputRef}
            rows={1}
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); void send(input); }
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
          <Btn variant="primary" type="submit" loading={busy} disabled={!input.trim()} icon={<Send size={15} />}>
            Send
          </Btn>
        </form>
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
