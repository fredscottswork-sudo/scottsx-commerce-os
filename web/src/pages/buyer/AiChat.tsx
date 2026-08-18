import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Send, Sparkles } from 'lucide-react';
import { aiService, productService } from '../../api/services';
import { useAuth } from '../../store/AuthContext';
import { useToast } from '../../store/ToastContext';

const QUICK = ["What's near me?", 'Cheapest Samsung phone', 'Flash deals right now', 'Recommend a gift under UGX 100,000'];

interface Turn { fromUser: boolean; text: string }

export default function AiChat() {
  const { user } = useAuth();
  const { toast } = useToast();
  const [turns, setTurns] = useState<Turn[]>([{ fromUser: false, text: "Hi! I'm ScottsTechX AI. Ask me about products, prices, nearby sellers or flash deals — I answer from the live catalog." }]);
  const [input, setInput] = useState('');
  const [busy, setBusy] = useState(false);
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => { bottomRef.current?.scrollIntoView({ behavior: 'smooth' }); }, [turns.length, busy]);

  async function send(prompt: string) {
    if (!prompt.trim() || busy) return;
    setInput('');
    setTurns((t) => [...t, { fromUser: true, text: prompt }]);
    setBusy(true);
    try {
      const catalog = await productService.list();
      const brief = catalog.products.slice(0, 40).map((p) => `- ${p.title} (${p.category}) UGX ${p.priceMinor} from ${p.seller.name}`).join('\n');
      const personalised = user?.displayName ? ` (User: ${user.displayName}, based in ${user.city || 'Kampala'})` : '';
      const reply = await aiService.ask(`Live marketplace catalog (${catalog.products.length} products):\n${brief}\n\nUser${personalised}: ${prompt}`, 'web-ai-chat');
      setTurns((t) => [...t, { fromUser: false, text: reply.text }]);
    } catch (e: any) {
      setTurns((t) => [...t, { fromUser: false, text: `⚠️ ${e.message}` }]);
      toast(e.message, 'error');
    } finally {
      setBusy(false);
    }
  }

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    send(input);
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: 'calc(100vh - var(--topbar-h) - 44px)' }}>
      <div className="card card-pad mb-16" style={{ background: 'linear-gradient(135deg, var(--primary-dark), var(--purple))', color: '#fff', border: 'none' }}>
        <div className="row">
          <Sparkles size={22} />
          <div>
            <strong style={{ fontSize: 17 }}>ScottsTechX AI</strong>
            <div style={{ opacity: 0.85, fontSize: 13 }}>Answers from the live catalog — same assistant the mobile app uses.</div>
          </div>
        </div>
      </div>

      <div className="row wrap mb-16">
        {QUICK.map((q) => <button key={q} className="chip" onClick={() => send(q)}>{q}</button>)}
      </div>

      <div className="card grow" style={{ padding: 16, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: 8 }}>
        {turns.map((t, i) => (
          <div key={i} className={t.fromUser ? 'bubble bubble-mine' : 'bubble bubble-other'} style={{ alignSelf: t.fromUser ? 'flex-end' : 'flex-start', whiteSpace: 'pre-wrap' }}>
            {t.text}
          </div>
        ))}
        {busy && <div className="bubble bubble-other" style={{ alignSelf: 'flex-start' }}>…</div>}
        <div ref={bottomRef} />
      </div>

      <form onSubmit={onSubmit} className="row mt-8">
        <input className="input grow" value={input} onChange={(e) => setInput(e.target.value)} placeholder="Ask about products, prices, sellers…" />
        <button type="submit" className="btn btn-primary" disabled={busy || !input.trim()} aria-label="Send"><Send size={16} /></button>
      </form>
    </div>
  );
}
