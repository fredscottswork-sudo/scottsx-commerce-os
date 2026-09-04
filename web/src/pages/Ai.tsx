import { useEffect, useState, type ReactNode } from 'react';
import {
  Sparkles, Mic, Cpu, Database, Zap, Search as SearchIcon, Wand2, Image as ImageIcon,
} from 'lucide-react';
import { AiConsole } from '../components/AiConsole';
import { VisualSearch } from '../components/VisualSearch';
import { aiService } from '../api/services';
import { Badge } from '../components/ui';

interface AiStatus {
  configured: boolean;
  provider: string;
  model: string;
  grounded: boolean;
  capabilities: Record<string, boolean>;
}

const CAPABILITIES: { key: string; label: string; icon: ReactNode }[] = [
  { key: 'chat', label: 'Conversational shopping', icon: <Sparkles size={13} /> },
  { key: 'imageSearch', label: 'Photo search', icon: <ImageIcon size={13} /> },
  { key: 'voiceSearch', label: 'Voice search', icon: <Mic size={13} /> },
  { key: 'search', label: 'Whole-catalogue search', icon: <SearchIcon size={13} /> },
  { key: 'agents', label: '6 specialist agents', icon: <Wand2 size={13} /> },
  { key: 'grounded', label: 'Live stock & prices', icon: <Database size={13} /> },
];

/** Public AI page — anonymous shoppers get the same grounded engine. */
export default function Ai() {
  const [status, setStatus] = useState<AiStatus | null>(null);

  useEffect(() => {
    aiService.status().then(setStatus).catch(() => undefined);
  }, []);

  const caps = CAPABILITIES.filter((c) =>
    c.key === 'grounded' ? (status?.grounded ?? true) : (status?.capabilities?.[c.key] ?? true)
  );

  return (
    <>
      {/* ── STX AI hero ─────────────────────────────────────────────────── */}
      <section className="card ai-hero">
        <div className="ai-hero-badge"><Sparkles size={15} /> STX AI</div>
        <h1 className="ai-hero-title">
          The ScottsTechX intelligence engine
        </h1>
        <p className="ai-hero-sub">
          One assistant that knows the whole marketplace — every listing, price, stock count,
          store and deal. Ask in plain language, show it a photo, or just say what you need.
        </p>
        <div className="ai-cap-row">
          {caps.map((c) => (
            <span key={c.key} className="ai-cap">{c.icon} {c.label}</span>
          ))}
        </div>
        {status && (
          <p className="tiny muted-2 mt-12">
            Engine: <Badge tone={status.grounded ? 'green' : 'amber'}>
              {status.grounded ? 'Store-aware' : 'Limited'}
            </Badge>{' '}
            <span className="mono"> {status.provider} · {status.model}</span>
          </p>
        )}
      </section>

      {/* ── How to unlock the full model ─────────────────────────────────── */}
      {status && !status.configured && (
        <div className="card ai-powerup anim-up">
          <Cpu size={17} className="ai-power-ico" />
          <div className="grow" style={{ minWidth: 0 }}>
            <strong className="semi">Running in local catalogue mode</strong>
            <p className="tiny muted mt-4">
              Search, image matching and answers are fully functional with zero configuration,
              but the replies come from a deterministic on-device planner. To upgrade to a real
              LLM (deeper answers, vision, natural-language reasoning) put an OpenRouter key in
              the backend: <code className="mono">OPENROUTER_API_KEY</code> in{' '}
              <code className="mono">12_Backend/.env</code> — or{' '}
              <code className="mono">APIFREELLM_API_KEY</code> with{' '}
              <code className="mono">AI_PROVIDER=apifreellm</code>. Model picks:
              <code className="mono"> AI_MODEL</code>. No code changes needed — the same
              endpoints light up automatically.
            </p>
          </div>
        </div>
      )}

      <div className="ai-workspace">
        {/* ── Photo search card ─────────────────────────────────────────── */}
        <aside className="card visual-side">
          <VisualSearch />
        </aside>

        {/* ── The assistant itself ──────────────────────────────────────── */}
        <div className="ai-main">
          <AiConsole
            audience="buyer"
            screen="web-public-ai"
            title="Shop by conversation"
            subtitle="Tell the assistant what you need and your budget. It searches the whole ScottsTechX catalogue and explains its picks."
          />
        </div>
      </div>

      <p className="tiny muted-2 mt-16 center" style={{ maxWidth: 640, marginInline: 'auto' }}>
        <Zap size={12} style={{ verticalAlign: -2 }} /> Every answer is grounded in live catalogue
        data — real prices, real stock, real stores in Kampala and beyond.
      </p>
    </>
  );
}
