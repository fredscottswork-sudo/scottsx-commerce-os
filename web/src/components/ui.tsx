import {
  type ReactNode, type ButtonHTMLAttributes, type InputHTMLAttributes,
  type SelectHTMLAttributes, type TextareaHTMLAttributes, useEffect, useRef, useState,
} from 'react';
import { X, Search, AlertTriangle, Inbox, Loader2 } from 'lucide-react';

/* ═══ Buttons ═══════════════════════════════════════════════════════════════ */
type BtnProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: 'primary' | 'danger' | 'success' | 'outline' | 'ghost' | 'default';
  size?: 'sm' | 'md' | 'lg';
  loading?: boolean;
  icon?: ReactNode;
};

export function Btn({
  variant = 'default', size = 'md', className = '', loading, icon, children, disabled, ...rest
}: BtnProps) {
  const v =
    variant === 'default' ? 'btn'
    : variant === 'outline' ? 'btn btn-outline'
    : `btn btn-${variant}`;
  const s = size === 'md' ? '' : `btn-${size}`;
  return (
    <button className={`${v} ${s} ${className}`.trim()} disabled={disabled || loading} {...rest}>
      {loading ? <Loader2 size={15} className="anim-spin" style={{ animation: 'spin .65s linear infinite' }} /> : icon}
      {children}
    </button>
  );
}

/* ═══ Inputs ════════════════════════════════════════════════════════════════ */
export function Field({
  label, error, children, hint, required,
}: { label: string; error?: string; hint?: string; required?: boolean; children: ReactNode }) {
  return (
    <div className="field">
      <label>
        {label} {required && <span className="t-danger">*</span>}
      </label>
      {children}
      {hint && !error && <span className="field-hint">{hint}</span>}
      {error && <span className="field-error">{error}</span>}
    </div>
  );
}

export function Input({ className = '', invalid, ...rest }: InputHTMLAttributes<HTMLInputElement> & { invalid?: boolean }) {
  return <input className={`input ${invalid ? 'input-invalid' : ''} ${className}`.trim()} {...rest} />;
}
export function Select({ className = '', ...rest }: SelectHTMLAttributes<HTMLSelectElement>) {
  return <select className={`select ${className}`.trim()} {...rest} />;
}
export function TextArea({ className = '', invalid, ...rest }: TextareaHTMLAttributes<HTMLTextAreaElement> & { invalid?: boolean }) {
  return <textarea className={`input ${invalid ? 'input-invalid' : ''} ${className}`.trim()} {...rest} />;
}

export function SearchInput({
  value, onChange, placeholder = 'Search…', autoFocus, onSubmit,
}: {
  value: string; onChange: (v: string) => void; placeholder?: string;
  autoFocus?: boolean; onSubmit?: () => void;
}) {
  return (
    <div className="searchbar grow">
      <Search size={17} className="muted-2" style={{ flexShrink: 0 }} />
      <input
        value={value}
        autoFocus={autoFocus}
        onChange={(e) => onChange(e.target.value)}
        onKeyDown={(e) => { if (e.key === 'Enter') onSubmit?.(); }}
        placeholder={placeholder}
        aria-label={placeholder}
      />
      {value && (
        <button className="btn btn-ghost btn-icon" onClick={() => onChange('')} aria-label="Clear search">
          <X size={15} />
        </button>
      )}
    </div>
  );
}

export function Switch({
  checked, onChange, label, disabled,
}: { checked: boolean; onChange: (v: boolean) => void; label?: string; disabled?: boolean }) {
  return (
    <label className="row" style={{ cursor: disabled ? 'not-allowed' : 'pointer', opacity: disabled ? 0.55 : 1 }}>
      <span className="switch">
        <input
          type="checkbox" checked={checked} disabled={disabled}
          onChange={(e) => onChange(e.target.checked)}
          aria-label={label ?? 'Toggle'}
        />
        <span className="switch-slider" />
      </span>
      {label && <span className="tiny semi">{label}</span>}
    </label>
  );
}

/* ═══ Feedback ══════════════════════════════════════════════════════════════ */
export function Spinner({ large }: { large?: boolean }) {
  return <div className={`spinner ${large ? 'spinner-lg' : ''}`} role="status" aria-label="Loading" />;
}

export function Loading({ label = 'Loading…' }: { label?: string }) {
  return (
    <div className="empty">
      <div className="center mb-12"><Spinner large /></div>
      <span className="muted">{label}</span>
    </div>
  );
}

/** Content-shaped placeholders — far better perceived speed than a spinner. */
export function SkeletonGrid({ count = 8 }: { count?: number }) {
  return (
    <div className="pgrid">
      {Array.from({ length: count }).map((_, i) => (
        <div key={i} className="card" style={{ overflow: 'hidden' }}>
          <div className="skeleton" style={{ aspectRatio: '1', borderRadius: 0 }} />
          <div style={{ padding: 12 }}>
            <div className="skeleton skeleton-text" style={{ width: '90%' }} />
            <div className="skeleton skeleton-text" style={{ width: '60%' }} />
            <div className="skeleton skeleton-text" style={{ width: '45%', height: 16 }} />
          </div>
        </div>
      ))}
    </div>
  );
}

export function SkeletonRows({ rows = 5, height = 46 }: { rows?: number; height?: number }) {
  return (
    <div className="col">
      {Array.from({ length: rows }).map((_, i) => (
        <div key={i} className="skeleton" style={{ height, borderRadius: 'var(--radius-sm)' }} />
      ))}
    </div>
  );
}

export function SkeletonStats({ count = 4 }: { count?: number }) {
  return (
    <div className="grid grid-4">
      {Array.from({ length: count }).map((_, i) => (
        <div key={i} className="card stat-card">
          <div className="skeleton skeleton-text" style={{ width: '55%' }} />
          <div className="skeleton" style={{ height: 30, width: '72%', marginTop: 8 }} />
        </div>
      ))}
    </div>
  );
}

export function Empty({
  icon, title, subtitle, action,
}: { icon?: ReactNode; title: string; subtitle?: string; action?: ReactNode }) {
  return (
    <div className="empty">
      <div className="empty-icon">{icon ?? <Inbox size={28} />}</div>
      <strong style={{ display: 'block', fontSize: 'var(--fs-lg)', color: 'var(--text)' }}>{title}</strong>
      {subtitle && <p className="muted mt-4" style={{ maxWidth: 420, margin: '6px auto 0' }}>{subtitle}</p>}
      {action && <div className="center mt-16">{action}</div>}
    </div>
  );
}

export function ErrorBox({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div className="empty">
      <div className="empty-icon" style={{ background: 'rgba(239,68,68,0.12)', color: 'var(--danger)' }}>
        <AlertTriangle size={28} />
      </div>
      <strong style={{ display: 'block', fontSize: 'var(--fs-lg)', color: 'var(--text)' }}>Something went wrong</strong>
      <p className="muted mt-4">{message}</p>
      {onRetry && <div className="center mt-16"><Btn variant="primary" onClick={onRetry}>Try again</Btn></div>}
    </div>
  );
}

export function PageHeader({
  title, sub, actions,
}: { title: string; sub?: string; actions?: ReactNode }) {
  return (
    <div className="page-head">
      <div className="anim-up">
        <h1 className="page-title">{title}</h1>
        {sub && <p className="page-sub">{sub}</p>}
      </div>
      {actions && <div className="row wrap anim-up" style={{ animationDelay: '60ms' }}>{actions}</div>}
    </div>
  );
}

/* ═══ Modal ═════════════════════════════════════════════════════════════════ */
export function Modal({
  open, onClose, title, children, footer, size = 'md',
}: {
  open: boolean; onClose: () => void; title: string;
  children: ReactNode; footer?: ReactNode; size?: 'md' | 'lg';
}) {
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    document.addEventListener('keydown', onKey);
    const prev = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKey);
      document.body.style.overflow = prev;
    };
  }, [open, onClose]);

  if (!open) return null;
  return (
    <div className="modal-backdrop" onClick={onClose} role="dialog" aria-modal="true" aria-label={title}>
      <div className={`modal ${size === 'lg' ? 'modal-lg' : ''}`} onClick={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <h3 style={{ fontSize: 'var(--fs-lg)' }}>{title}</h3>
          <button className="btn btn-ghost btn-icon" onClick={onClose} aria-label="Close"><X size={17} /></button>
        </div>
        <div className="modal-body">{children}</div>
        {footer && <div className="modal-foot">{footer}</div>}
      </div>
    </div>
  );
}

/** Promise-based confirm dialog for destructive actions. */
export function ConfirmModal({
  open, title, message, confirmLabel = 'Confirm', danger, onConfirm, onCancel, loading,
}: {
  open: boolean; title: string; message: string; confirmLabel?: string;
  danger?: boolean; onConfirm: () => void; onCancel: () => void; loading?: boolean;
}) {
  return (
    <Modal
      open={open}
      onClose={onCancel}
      title={title}
      footer={
        <>
          <Btn onClick={onCancel} disabled={loading}>Cancel</Btn>
          <Btn variant={danger ? 'danger' : 'primary'} onClick={onConfirm} loading={loading}>
            {confirmLabel}
          </Btn>
        </>
      }
    >
      <p className="muted" style={{ margin: 0 }}>{message}</p>
    </Modal>
  );
}

/* ═══ Display ═══════════════════════════════════════════════════════════════ */
export function Badge({
  children, tone = 'default', live,
}: {
  children: ReactNode;
  tone?: 'default' | 'primary' | 'green' | 'red' | 'amber' | 'violet' | 'cyan';
  live?: boolean;
}) {
  return (
    <span className={`badge ${tone === 'default' ? '' : `badge-${tone}`} ${live ? 'badge-live' : ''}`}
      style={live ? { paddingLeft: 17 } : undefined}>
      {children}
    </span>
  );
}

const STATUS_TONE: Record<string, 'green' | 'amber' | 'red' | 'violet' | 'default' | 'cyan' | 'primary'> = {
  approved: 'green', live: 'green', paid: 'green', delivered: 'green', answered: 'green', active: 'green',
  pending: 'amber', open: 'amber', shipped: 'cyan', processing: 'cyan',
  rejected: 'red', suspended: 'red', cancelled: 'red', failed: 'red',
  draft: 'default', closed: 'default', refunded: 'violet',
};

export function StatusBadge({ status }: { status: string }) {
  const key = (status || '').toLowerCase();
  return <Badge tone={STATUS_TONE[key] ?? 'default'}>{status}</Badge>;
}

export function Avatar({
  name, src, size = 'md',
}: { name?: string; src?: string | null; size?: 'sm' | 'md' | 'lg' }) {
  const cls = size === 'md' ? 'avatar' : `avatar avatar-${size}`;
  return (
    <span className={cls} aria-hidden>
      {src ? <img src={src} alt="" loading="lazy" /> : (name || 'U').charAt(0).toUpperCase()}
    </span>
  );
}

export function Tabs<T extends string>({
  tabs, active, onChange,
}: { tabs: { id: T; label: string; count?: number }[]; active: T; onChange: (id: T) => void }) {
  return (
    <div className="tabs" role="tablist">
      {tabs.map((t) => (
        <button
          key={t.id}
          role="tab"
          aria-selected={active === t.id}
          className={`tab ${active === t.id ? 'active' : ''}`}
          onClick={() => onChange(t.id)}
        >
          {t.label}
          {t.count !== undefined && t.count > 0 && (
            <span className="badge badge-primary" style={{ marginLeft: 7 }}>{t.count}</span>
          )}
        </button>
      ))}
    </div>
  );
}

export function StatCard({
  label, value, icon, color = 'var(--primary)', delta, hint, index = 0,
}: {
  label: string; value: ReactNode; icon?: ReactNode; color?: string;
  delta?: { value: string; positive: boolean }; hint?: string; index?: number;
}) {
  return (
    <div className="card stat-card card-hover" style={{ '--i': index } as React.CSSProperties}>
      <div className="row-between" style={{ alignItems: 'flex-start' }}>
        <div className="grow">
          <div className="stat-label">{label}</div>
          <div className="stat-value">{value}</div>
          {delta && (
            <span className={`stat-delta ${delta.positive ? 't-success' : 't-danger'}`}>
              {delta.positive ? '▲' : '▼'} {delta.value}
            </span>
          )}
          {hint && !delta && <span className="tiny muted-2">{hint}</span>}
        </div>
        {icon && <div className="stat-icon" style={{ background: color }}>{icon}</div>}
      </div>
    </div>
  );
}

/** Number that animates up to its value — makes dashboards feel alive. */
export function CountUp({ value, format }: { value: number; format?: (n: number) => string }) {
  const [shown, setShown] = useState(0);
  const ref = useRef(0);

  useEffect(() => {
    const from = ref.current;
    const delta = value - from;
    if (delta === 0) { setShown(value); return; }
    const duration = 620;
    const start = performance.now();
    let raf = 0;
    const tick = (now: number) => {
      const t = Math.min((now - start) / duration, 1);
      const eased = 1 - Math.pow(1 - t, 3);
      setShown(Math.round(from + delta * eased));
      if (t < 1) raf = requestAnimationFrame(tick);
      else ref.current = value;
    };
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, [value]);

  return <>{format ? format(shown) : shown.toLocaleString()}</>;
}

/** Lightweight sparkline/bar chart with a grow-in animation. */
export function MiniBars({
  data, height = 60, color = 'var(--primary)',
}: { data: { label?: string; value: number }[]; height?: number; color?: string }) {
  const max = Math.max(...data.map((d) => d.value), 1);
  return (
    <div className="row" style={{ gap: 4, height, alignItems: 'flex-end' }}>
      {data.map((d, i) => (
        <div
          key={i}
          title={`${d.label ?? ''} ${d.value.toLocaleString()}`}
          style={{
            flex: 1,
            height: `${Math.max((d.value / max) * 100, 3)}%`,
            background: color,
            borderRadius: '4px 4px 2px 2px',
            opacity: 0.55 + (d.value / max) * 0.45,
            transformOrigin: 'bottom',
            animation: `scaleIn 420ms var(--ease-out) both`,
            animationDelay: `${i * 26}ms`,
            minWidth: 3,
          }}
        />
      ))}
    </div>
  );
}

export function Pagination({
  page, pageSize, total, onPage,
}: { page: number; pageSize: number; total: number; onPage: (p: number) => void }) {
  const pages = Math.max(1, Math.ceil(total / pageSize));
  if (pages <= 1) return null;
  return (
    <div className="row-between mt-16">
      <span className="tiny muted">
        Showing {Math.min((page - 1) * pageSize + 1, total)}–{Math.min(page * pageSize, total)} of {total}
      </span>
      <div className="row" style={{ gap: 6 }}>
        <Btn size="sm" disabled={page <= 1} onClick={() => onPage(page - 1)}>Previous</Btn>
        <span className="tiny semi" style={{ padding: '0 8px' }}>{page} / {pages}</span>
        <Btn size="sm" disabled={page >= pages} onClick={() => onPage(page + 1)}>Next</Btn>
      </div>
    </div>
  );
}

/** Renders the tiny markdown subset the AI emits (**bold**, bullets, headings). */
export function RichText({ text }: { text: string }) {
  const lines = (text || '').split('\n');
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 5 }}>
      {lines.map((line, i) => {
        if (!line.trim()) return <div key={i} style={{ height: 5 }} />;
        const html = line
          .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
          .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
          .replace(/\*(.+?)\*/g, '<em>$1</em>')
          .replace(/`(.+?)`/g, '<code>$1</code>');
        const bullet = /^\s*[•\-*]\s+/.test(line);
        return (
          <div
            key={i}
            style={bullet ? { paddingLeft: 12 } : undefined}
            dangerouslySetInnerHTML={{ __html: bullet ? html.replace(/^\s*[•\-*]\s+/, '• ') : html }}
          />
        );
      })}
    </div>
  );
}

/* ═══ Card & Table ══════════════════════════════════════════════════════════ */
export function Card({
  children, className = '', title, actions, flush, hover, style,
}: {
  children: ReactNode; className?: string; title?: ReactNode;
  actions?: ReactNode; flush?: boolean; hover?: boolean; style?: React.CSSProperties;
}) {
  return (
    <div className={`card ${flush ? 'card-flush' : ''} ${hover ? 'card-hover' : ''} ${className}`.trim()} style={style}>
      {(title || actions) && (
        <div className="card-head">
          {typeof title === 'string' ? <h2 className="card-title">{title}</h2> : title}
          {actions}
        </div>
      )}
      {children}
    </div>
  );
}

/** Responsive table that turns into stacked rows on small screens. */
export function Table<T>({
  columns, rows, keyOf, empty, onRowClick,
}: {
  columns: { key: string; header: ReactNode; render: (row: T, i: number) => ReactNode; width?: string; hideSm?: boolean }[];
  rows: T[];
  keyOf: (row: T, i: number) => string;
  empty?: ReactNode;
  onRowClick?: (row: T) => void;
}) {
  if (rows.length === 0 && empty) return <>{empty}</>;
  return (
    <div className="table-wrap">
      <table className="table">
        <thead>
          <tr>
            {columns.map((c) => (
              <th key={c.key} style={c.width ? { width: c.width } : undefined} className={c.hideSm ? 'hide-sm' : ''}>
                {c.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((r, i) => (
            <tr
              key={keyOf(r, i)}
              onClick={onRowClick ? () => onRowClick(r) : undefined}
              className={onRowClick ? 'row-click' : ''}
              style={{ animation: 'fadeInUp 300ms var(--ease-out) both', animationDelay: `${Math.min(i, 12) * 22}ms` }}
            >
              {columns.map((c) => (
                <td key={c.key} className={c.hideSm ? 'hide-sm' : ''}>{c.render(r, i)}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/** Alias kept so older call sites (`<Toggle …/>`) keep working. */
export const Toggle = Switch;
