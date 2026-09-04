/** Lightweight dependency-free charts (SVG). */
import { useId } from 'react';

export interface BarDatum { label: string; value: number; color?: string }

export function BarChart({ data, height = 180 }: { data: BarDatum[]; height?: number }) {
  const gid = useId().replace(/[^a-zA-Z0-9]/g, '');
  const max = Math.max(1, ...data.map((d) => d.value));
  const bw = 100 / data.length;
  return (
    <svg viewBox={`0 0 100 ${height}`} style={{ width: '100%', height }} preserveAspectRatio="none" role="img" aria-label="Bar chart">
      <defs>
        <linearGradient id={`g${gid}`} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="var(--primary)" />
          <stop offset="100%" stopColor="var(--primary-2)" />
        </linearGradient>
      </defs>
      {data.map((d, i) => {
        const h = (d.value / max) * (height - 26);
        const x = i * bw + bw * 0.18;
        const w = bw * 0.64;
        const showValue = d.value > 0;
        return (
          <g key={i}>
            <rect x={x} y={height - 20 - h} width={w} height={h} rx={2} fill={d.color ?? `url(#g${gid})`} />
            {showValue && (
              <text x={x + w / 2} y={height - 22 - h} textAnchor="middle" fontSize="9" fontWeight="600" fill="var(--text-2)">{d.value}</text>
            )}
            <text x={x + w / 2} y={height - 7} textAnchor="middle" fontSize="11" fill="var(--text-2)">{d.label}</text>
          </g>
        );
      })}
    </svg>
  );
}

export function Donut({ segments, size = 160, centerLabel }: { segments: Array<{ label: string; value: number; color: string }>; size?: number; centerLabel?: string }) {
  const rawTotal = segments.reduce((s, x) => s + x.value, 0);
  const total = Math.max(1, rawTotal);
  const r = 42;
  const c = 2 * Math.PI * r;
  let offset = 0;
  const hasData = rawTotal > 0;
  return (
    <svg width={size} height={size} viewBox="0 0 100 100" role="img" aria-label="Donut chart">
      <circle cx="50" cy="50" r={r} fill="none" stroke="var(--surface-3)" strokeWidth="14" />
      {hasData && segments.map((s, i) => {
        if (s.value <= 0) return null;
        const len = (s.value / total) * c;
        const el = (
          <circle key={i} cx="50" cy="50" r={r} fill="none" stroke={s.color} strokeWidth="14"
            strokeDasharray={`${len} ${c - len}`} strokeDashoffset={-offset}
            transform="rotate(-90 50 50)" strokeLinecap="butt" />
        );
        offset += len;
        return el;
      })}
      {centerLabel && (
        <text x="50" y="54" textAnchor="middle" fontSize="13" fontWeight="700" fill="var(--text)">{centerLabel}</text>
      )}
    </svg>
  );
}

export function Sparkline({ points, width = 120, height = 36 }: { points: number[]; width?: number; height?: number }) {
  if (points.length < 2) return null;
  const min = Math.min(...points);
  const max = Math.max(...points);
  const span = max - min || 1;
  const step = width / (points.length - 1);
  const d = points.map((p, i) => `${i === 0 ? 'M' : 'L'}${i * step},${height - ((p - min) / span) * (height - 4) - 2}`).join(' ');
  return (
    <svg width={width} height={height} role="img" aria-label="Trend">
      <path d={d} fill="none" stroke="var(--primary)" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

/**
 * Animated area/line chart for time series (revenue, orders, signups).
 * Dependency-free SVG so first paint stays instant.
 */
export function AreaChart({
  points, height = 190, labels, valueFormat, color = 'var(--primary)',
}: {
  points: number[];
  height?: number;
  labels?: string[];
  valueFormat?: (n: number) => string;
  color?: string;
}) {
  const gid = useId().replace(/[^a-zA-Z0-9]/g, '');
  if (!points.length) return null;

  const W = 600;
  const H = height;
  const pad = { top: 12, right: 6, bottom: 20, left: 6 };
  const max = Math.max(...points, 1);
  const min = Math.min(...points, 0);
  const span = max - min || 1;
  const stepX = (W - pad.left - pad.right) / Math.max(points.length - 1, 1);
  const y = (v: number) => pad.top + (1 - (v - min) / span) * (H - pad.top - pad.bottom);
  const x = (i: number) => pad.left + i * stepX;

  const line = points.map((p, i) => `${i === 0 ? 'M' : 'L'}${x(i).toFixed(1)},${y(p).toFixed(1)}`).join(' ');
  const area = `${line} L${x(points.length - 1).toFixed(1)},${H - pad.bottom} L${x(0).toFixed(1)},${H - pad.bottom} Z`;

  return (
    <svg viewBox={`0 0 ${W} ${H}`} style={{ width: '100%', height, overflow: 'visible' }} role="img" aria-label="Trend chart">
      <defs>
        <linearGradient id={`fill${gid}`} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor={color} stopOpacity="0.34" />
          <stop offset="100%" stopColor={color} stopOpacity="0" />
        </linearGradient>
      </defs>

      {[0, 0.5, 1].map((t) => (
        <line key={t} x1={pad.left} x2={W - pad.right}
          y1={pad.top + t * (H - pad.top - pad.bottom)} y2={pad.top + t * (H - pad.top - pad.bottom)}
          stroke="var(--border)" strokeWidth="1" strokeDasharray="3 5" />
      ))}

      <path d={area} fill={`url(#fill${gid})`} style={{ animation: 'fadeIn 620ms ease both 180ms' }} />
      <path
        d={line} fill="none" stroke={color} strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round"
        pathLength={1}
        style={{ strokeDasharray: 1, strokeDashoffset: 1, animation: 'drawLine 900ms var(--ease-out) forwards' }}
      />

      {points.map((p, i) => (
        <circle key={i} cx={x(i)} cy={y(p)} r="3" fill="var(--surface)" stroke={color} strokeWidth="2"
          style={{ animation: `popIn 260ms var(--ease-spring) both`, animationDelay: `${380 + i * 26}ms` }}>
          <title>{`${labels?.[i] ?? ''} ${valueFormat ? valueFormat(p) : p.toLocaleString()}`}</title>
        </circle>
      ))}

      {labels && labels.map((l, i) =>
        i % Math.ceil(labels.length / 7) === 0 ? (
          <text key={i} x={x(i)} y={H - 5} textAnchor="middle" fontSize="11.5" fill="var(--text-3)">{l}</text>
        ) : null
      )}
    </svg>
  );
}
