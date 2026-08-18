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
        return (
          <g key={i}>
            <rect x={x} y={height - 20 - h} width={w} height={h} rx={2} fill={d.color ?? `url(#g${gid})`} />
            <text x={x + w / 2} y={height - 7} textAnchor="middle" fontSize="6" fill="var(--text-2)">{d.label}</text>
          </g>
        );
      })}
    </svg>
  );
}

export function Donut({ segments, size = 160, centerLabel }: { segments: Array<{ label: string; value: number; color: string }>; size?: number; centerLabel?: string }) {
  const total = Math.max(1, segments.reduce((s, x) => s + x.value, 0));
  const r = 42;
  const c = 2 * Math.PI * r;
  let offset = 0;
  return (
    <svg width={size} height={size} viewBox="0 0 100 100" role="img" aria-label="Donut chart">
      <circle cx="50" cy="50" r={r} fill="none" stroke="var(--surface-3)" strokeWidth="14" />
      {segments.map((s, i) => {
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
