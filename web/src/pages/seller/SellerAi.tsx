import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Send, Sparkles, BarChart3, PackageSearch, Tag, Megaphone } from 'lucide-react';
import { aiService, productService } from '../../api/services';

interface Turn { fromUser: boolean; text: string }

const TOOLS = [
  { title: 'Sales analytics', sub: 'Weekly revenue, orders, top sellers', icon: <BarChart3 size={20} />, prompt: 'Give me a sales analytics report: weekly revenue, order volume and top-selling products, based on the catalog. Use bullet points.' },
  { title: 'Low stock', sub: 'Refill alerts + demand signals', icon: <PackageSearch size={20} />, prompt: 'Which of my products are low on stock? List refill alerts and demand signals from the catalog.' },
  { title: 'Pricing tips', sub: 'Competitor comparison, margin lift', icon: <Tag size={20} />, prompt: 'Give me pricing tips: compare my prices with similar items and suggest small margin lifts.' },
  { title: 'Marketing ideas', sub: 'Campaigns, promos, retention', icon: <Megaphone size={20} />, prompt: 'Suggest marketing ideas: campaigns, promos and retention tactics for a Ugandan marketplace store.' },
];

export default function SellerAi() {
  const [turns, setTurns] = useState<Turn[]>([{ fromUser: false, text: "Hello seller! Use a tool below or ask me anything about your store, inventory or pricing." }]);
  const [input, setInput] = useState('');
  const [busy, setBusy] = useState(false);
  const bottomRef = useRef<HTMLDivElement>(null);
  useEffect(() => { bottomRef.current?.scrollIntoView({ behavior: 'smooth' }); }, [turns.length, busy]);

  async function ask(prompt: string) {
    if (busy) return;
    setTurns((t) => [...t, { fromUser: true, text: prompt }]);
    setBusy(true);
    try {
      const catalog = await productService.list();
      const brief = catalog.products.slice(0, 40).map((p) => `- ${p.title} (${p.category}) UGX ${p.priceMinor} from ${p.seller.name}`).join('\n');
      const reply = await aiService.ask(`Live marketplace catalog (${catalog.products.length} products):\n${brief}\n\nUser (seller): ${prompt}`, 'web-seller-ai');
      setTurns((t) => [...t, { fromUser: false, text: reply.text }]);
    } catch (e: any) {
      setTurns((t) => [...t, { fromUser: false, text: `⚠️ ${e.message}` }]);
    } finally {
      setBusy(false);
    }
  }

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    const text = input.trim();
    if (!text) return;
    setInput('');
    ask(text);
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: 'calc(100vh - var(--topbar-h) - 44px)' }}>
      <div className="card card-pad mb-16" style={{ background: 'linear-gradient(135deg, var(--primary-dark), var(--purple), var(--pink))', color: '#fff', border: 'none' }}>
        <div className="row"><Sparkles size={22} /><strong style={{ fontSize: 17 }}>Seller AI Assistant</strong></div>
        <div style={{ opacity: 0.85, fontSize: 13 }}>Analytics · stock · pricing · marketing — from the live catalog.</div>
      </div>

      <div className="grid grid-4 mb-16">
        {TOOLS.map((t) => (
          <button key={t.title} className="card card-pad" style={{ cursor: 'pointer', textAlign: 'left', border: '1px solid var(--border)', background: 'var(--surface)' }} onClick={() => ask(t.prompt)}>
            <span className="stat-icon" style={{ background: 'linear-gradient(135deg, var(--primary), var(--purple))', marginBottom: 10 }}>{t.icon}</span>
            <strong style={{ display: 'block' }}>{t.title}</strong>
            <span className="muted" style={{ fontSize: 12.5 }}>{t.sub}</span>
          </button>
        ))}
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
        <input className="input grow" value={input} onChange={(e) => setInput(e.target.value)} placeholder="Ask about your store…" />
        <button type="submit" className="btn btn-primary" disabled={busy || !input.trim()} aria-label="Send"><Send size={16} /></button>
      </form>
    </div>
  );
}
