/**
 * DashboardGuide — a small floating "?" helper on every dashboard page.
 *
 * It talks to the role-scoped `guide` agent: a buyer only hears about the
 * buyer dashboard, a seller about the seller dashboard, an admin about the
 * admin console. It never answers as a shopping assistant and never shows
 * product cards — it explains the screens in front of the user.
 */
import { useEffect, useRef, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { CircleHelp, Loader2, Send, Sparkles, X } from 'lucide-react';
import { aiService } from '../api/services';
import { useAuth } from '../store/AuthContext';

type Turn = { role: 'user' | 'assistant'; content: string };

const STARTERS: Record<string, string[]> = {
  buyer: ['What can I do on this page?', 'Where do I see my orders?', 'How do I make an offer to a seller?', 'How do I change my address?'],
  seller: ['What can I do on this page?', 'Why is my product still pending?', 'How do I add a product with AI?', 'How do I mark an order as shipped?'],
  admin: ['What can I do on this page?', 'How do I approve a product?', 'How do I verify a seller?', 'Where is the locations map?'],
};

function md(text: string) {
  // Tiny inline renderer: **bold**, bullets, line breaks. Nothing else.
  return text.split('\n').map((line, i) => {
    const html = line.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');
    const bullet = /^\s*[•\-*]\s+/.test(line);
    return <p key={i} className={bullet ? 'dg-li' : undefined} dangerouslySetInnerHTML={{ __html: bullet ? html.replace(/^\s*[•\-*]\s+/, '') : html }} />;
  });
}

export function DashboardGuide() {
  const { user } = useAuth();
  const location = useLocation();
  const [open, setOpen] = useState(false);
  const [input, setInput] = useState('');
  const [busy, setBusy] = useState(false);
  const [turns, setTurns] = useState<Turn[]>([]);
  const bodyRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  const role = user?.role ?? 'buyer';
  const starters = STARTERS[role] ?? STARTERS.buyer;

  useEffect(() => { if (open) setTimeout(() => inputRef.current?.focus(), 60); }, [open]);
  useEffect(() => { bodyRef.current?.scrollTo({ top: bodyRef.current.scrollHeight, behavior: 'smooth' }); }, [turns, busy]);

  if (!user) return null;

  const ask = async (q: string) => {
    const prompt = q.trim();
    if (!prompt || busy) return;
    setInput('');
    setTurns((t) => [...t, { role: 'user', content: prompt }]);
    setBusy(true);
    try {
      const r = await aiService.ask(prompt, {
        agent: 'guide',
        screen: `dashboard:${role}:${location.pathname}`,
        history: turns.slice(-6),
      });
      setTurns((t) => [...t, { role: 'assistant', content: r.text || 'I could not find that in your dashboard.' }]);
    } catch (e: any) {
      setTurns((t) => [...t, { role: 'assistant', content: e?.message || 'The guide is unavailable right now.' }]);
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <button
        type="button"
        className={`dg-fab${open ? ' open' : ''}`}
        onClick={() => setOpen((v) => !v)}
        aria-label={open ? 'Close dashboard help' : 'Dashboard help'}
        title="Need help with this dashboard?"
        data-testid="dashboard-guide-fab"
      >
        {open ? <X size={20} /> : <CircleHelp size={22} />}
      </button>

      {open && (
        <section className="dg-panel" role="dialog" aria-label="Dashboard guide" data-testid="dashboard-guide">
          <header className="dg-head">
            <span className="dg-head-ico"><Sparkles size={15} /></span>
            <span className="grow">
              <strong>Dashboard guide</strong>
              <small>Explains your {role} dashboard</small>
            </span>
            <button type="button" className="icon-btn" aria-label="Close" onClick={() => setOpen(false)}><X size={16} /></button>
          </header>

          <div className="dg-body" ref={bodyRef}>
            {turns.length === 0 && (
              <div className="dg-intro">
                <p>Hi{user.displayName ? ` ${user.displayName.split(' ')[0]}` : ''} 👋 Ask me anything about the pages and buttons in your dashboard.</p>
                <div className="dg-starters">
                  {starters.map((s) => (
                    <button key={s} type="button" className="chip" onClick={() => void ask(s)}>{s}</button>
                  ))}
                </div>
              </div>
            )}
            {turns.map((t, i) => (
              <div key={i} className={`dg-msg dg-msg--${t.role}`}>{t.role === 'assistant' ? md(t.content) : <p>{t.content}</p>}</div>
            ))}
            {busy && <div className="dg-msg dg-msg--assistant dg-msg--busy"><Loader2 size={14} className="anim-spin" /> Looking at your dashboard…</div>}
          </div>

          <form className="dg-form" onSubmit={(e) => { e.preventDefault(); void ask(input); }}>
            <input
              ref={inputRef}
              value={input}
              onChange={(e) => setInput(e.target.value)}
              placeholder="Ask how to do something…"
              aria-label="Ask the dashboard guide"
            />
            <button type="submit" className="dg-send" disabled={!input.trim() || busy} aria-label="Ask">
              <Send size={16} />
            </button>
          </form>
        </section>
      )}
    </>
  );
}
