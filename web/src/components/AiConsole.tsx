import { useCallback, useEffect, useRef, useState } from 'react';
import {
  Sparkles, Send, RotateCcw, Zap, AlertCircle,
  ShoppingBag, Tag, LifeBuoy, TrendingUp, Compass, Bot,
} from 'lucide-react';
import { aiService } from '../api/services';
import type { AiAgent, Product } from '../api/types';
import { useToast } from '../store/ToastContext';
import { useCart } from '../store/CartContext';
import { ProductGrid } from './ProductCard';
import { Btn, RichText, Badge, Empty } from './ui';

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
  const [status, setStatus] = useState<{ provider: string; grounded: boolean; configured: boolean } | null>(null);

  const scrollRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);

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

  return (
    <div className={`ai-console${fullHeight ? ' ai-console-full' : ''}`}>
      {/* ── Agent picker ────────────────────────────────────────────── */}
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

      {/* ── Conversation ────────────────────────────────────────────── */}
      <div className="card ai-chat card-flush">
        <div className="ai-chat-head">
          <div style={{ minWidth: 0 }}>
            {/* The page heading lives here now that the AI pages render no
                PageHeader. The active agent becomes the secondary line, so
                nothing that used to be shown above the chat is lost. */}
            <h2 className="card-title ellipsis">{title}</h2>
            <p className="tiny muted ellipsis">
              {activeAgent ? (
                /* Same slug leak as the agent list: interpolating the icon
                   name into the template string printed "shopping-bag
                   Shopping Assistant" under the chat title. */
                <span className="row" style={{ gap: 5 }}>
                  <AgentIcon name={activeAgent.icon} />
                  {activeAgent.name}
                </span>
              ) : subtitle}
            </p>
          </div>
          {turns.length > 0 && (
            <Btn size="sm" variant="ghost" icon={<RotateCcw size={14} />} onClick={() => setTurns([])}>
              New chat
            </Btn>
          )}
        </div>

        <div className="ai-chat-body" ref={scrollRef}>
          {turns.length === 0 ? (
            <div className="ai-welcome">
              <div className="ai-avatar" style={{ width: 46, height: 46, borderRadius: 14 }}>
                <Sparkles size={22} />
              </div>
              <h3 className="mt-12" style={{ fontFamily: 'var(--font-display)' }}>{title}</h3>
              <p className="muted mt-4" style={{ maxWidth: 'min(460px, 100%)' }}>{subtitle}</p>

              <div className="row wrap center mt-16" style={{ gap: 8, justifyContent: 'center' }}>
                {(activeAgent?.starters ?? []).map((s) => (
                  <button key={s} className="chip" onClick={() => void send(s)}>{s}</button>
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
                      <Zap size={12} style={{ verticalAlign: -2 }} /> {t.products.length} matching product{t.products.length > 1 ? 's' : ''}
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
              audience === 'buyer'
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
    </div>
  );
}
