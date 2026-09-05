/**
 * Text-only brand: "SCOTTSTECHX" with a letter-by-letter reveal and a slow
 * metallic sheen, plus the company line and tagline fading in underneath.
 * Pure CSS, respects prefers-reduced-motion. Replaces the logo image on the
 * sign-in screen.
 */
const WORD = 'SCOTTSTECHX';

export default function AnimatedWordmark({ compact = false }: { compact?: boolean }) {
  return (
    <div className={`wordmark ${compact ? 'wordmark--compact' : ''}`} role="img" aria-label="ScottsTechX — Innovate. Integrate. Elevate.">
      <div className="wordmark-main">
        {WORD.split('').map((ch, i) => (
          <span
            key={i}
            className={`wordmark-ch ${i < 6 ? 'wordmark-ch--a' : 'wordmark-ch--b'}`}
            style={{ animationDelay: `${i * 70}ms` }}
          >
            {ch}
          </span>
        ))}
      </div>
      {!compact && <div className="wordmark-sub">Enterprises (U) Ltd</div>}
      <div className="wordmark-tag">
        {['Innovate.', 'Integrate.', 'Elevate.'].map((w, i) => (
          <span key={w} style={{ animationDelay: `${900 + i * 260}ms` }}>{w}</span>
        ))}
      </div>
    </div>
  );
}
