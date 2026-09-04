import { useCallback, useEffect, useRef, useState } from 'react';
import { Sparkles, Send, RotateCcw, Zap, AlertCircle, Mic, MicOff } from 'lucide-react';
import { aiService } from '../api/services';
import type { AiAgent, Product } from '../api/types';
import { useToast } from '../store/ToastContext';
import { useCart } from '../store/CartContext';
import { ProductGrid } from './ProductCard';
import { Btn, RichText, Badge } from './ui';

/** Minimal typing for the vendor-prefixed Web Speech API. */
type SpeechLike = {
  lang: string; continuous: boolean; interimResults: boolean;
  start: () => void; stop: () => void;
  onresult: ((e: any) => void) | null;
  onerror: ((e: any) => void) | null;
  onend: (() => void) | null;
};

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
  audience, screen, title, subtitle,
}: {
  audience: 'buyer' | 'seller';
  screen: string;
  title: string;
  subtitle: string;
}) {
  const { toast } = useToast();
  const { add, favoriteSellerIds, toggleFavoriteSeller } = useCart();

  const [agents, setAgents] = useState<AiAgent[]>([]);
  const [agentId, setAgentId] = useState('');
  const [turns, setTurns] = useState<Turn[]>([]);
  const [input, setInput] = useState('');
  const [busy, setBusy] = useState(false);
  const [status, setStatus] = useState<{ provider: string; model?: string; grounded: boolean; configured: boolean } | null>(null);
  const [listening, setListening] = useState(false);
  const recognitionRef = useRef<SpeechLike | null>(null);

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
  useEffect(() => () => { recognitionRef.current?.stop(); }, []);

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

  /** Speak the request the same way the search page does (Web Speech API). */
  const toggleVoice = useCallback(() => {
    const Ctor = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;
    if (!Ctor) { toast('Voice input needs Chrome or Edge on this device', 'warning'); return; }
    if (listening) { recognitionRef.current?.stop(); setListening(false); return; }

    const rec: SpeechLike = new Ctor();
    rec.lang = 'en-UG';
    rec.continuous = false;
    rec.interimResults = false;
    rec.onresult = (e: any) => {
      const transcript = e.results?.[0]?.[0]?.transcript ?? '';
      setInput(transcript);
      setListening(false);
      if (transcript) void send(transcript);
    };
    rec.onerror = () => { setListening(false); toast('Could not hear you — try again', 'error'); };
    rec.onend = () => setListening(false);
    recognitionRef.current = rec;
    try {
      rec.start();
      setListening(true);
    } catch {
      setListening(false);
      toast('Voice input could not start — try again', 'error');
    }
  }, [listening, send, toast]);

  return (
    <div className="ai-console">
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
              <span className="agent-emoji">{a.icon || '🤖'}</span>
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
            {status.model && status.model !== 'catalog-grounded' && (
              <> · <span className="mono">{status.model}</span></>
            )}
            {!status.configured && ' · local catalogue mode'}
          </p>
        )}
      </aside>

      {/* ── Conversation ────────────────────────────────────────────── */}
      <div className="card ai-chat card-flush">
        <div className="ai-chat-head">
          <div style={{ minWidth: 0 }}>
            <h2 className="card-title">{activeAgent?.icon} {activeAgent?.name || title}</h2>
            <p className="tiny muted ellipsis">{activeAgent?.tagline || subtitle}</p>
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
              <p className="muted mt-4" style={{ maxWidth: 460 }}>{subtitle}</p>

              <div className="row wrap center mt-16" style={{ gap: 8, justifyContent: 'center' }}>
                {(activeAgent?.starters ?? []).map((s) => (
                  <button key={s} className="chip" onClick={() => void send(s)}>{s}</button>
                ))}
              </div>
            </div>
          ) : (
            turns.map((t, i) => (
              <div key={i} className={`bubble-row ${t.role}`}>
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
                      {!!t.products?.length && (
                        <div className="mt-12">
                          <p className="tiny semi muted mb-8">
                            <Zap size={12} style={{ verticalAlign: -2 }} /> {t.products.length} matching product{t.products.length > 1 ? 's' : ''}
                          </p>
                          <ProductGrid
                            products={t.products.slice(0, 6)}
                            onAddToCart={audience === 'buyer' ? (p) => void add(p) : undefined}
                            onToggleFavorite={audience === 'buyer'
                              ? (p) => {
                                  if (p.seller?.id) void toggleFavoriteSeller(p.seller.id, p.seller.name);
                                }
                              : undefined}
                            favoriteSellerIds={favoriteSellerIds}
                          />
                        </div>
                      )}
                    </>
                  )}
                </div>
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
          <button
            type="button"
            className={`btn btn-icon ${listening ? 'btn-danger' : ''}`}
            onClick={toggleVoice}
            title={listening ? 'Stop listening' : 'Speak your request'}
            aria-label={listening ? 'Stop listening' : 'Speak your request'}
          >
            {listening ? <MicOff size={16} /> : <Mic size={16} />}
          </button>
          <Btn variant="primary" type="submit" loading={busy} disabled={!input.trim()} icon={<Send size={15} />}>
            Send
          </Btn>
        </form>
      </div>
    </div>
  );
}
