import { type ReactNode, type ButtonHTMLAttributes, type InputHTMLAttributes, type SelectHTMLAttributes, type TextareaHTMLAttributes } from 'react';
import { X, Search } from 'lucide-react';

/* ── Buttons ── */
type BtnProps = ButtonHTMLAttributes<HTMLButtonElement> & { variant?: 'primary' | 'danger' | 'success' | 'outline' | 'ghost'; size?: 'sm' | 'md' | 'lg' };
export function Btn({ variant = 'outline', size = 'md', className = '', ...rest }: BtnProps) {
  const v = variant === 'outline' ? 'btn' : `btn btn-${variant}`;
  const s = size === 'md' ? '' : size === 'sm' ? 'btn-sm' : 'btn-lg';
  return <button className={`${v} ${s} ${className}`} {...rest} />;
}

/* ── Inputs ── */
export function Field({ label, error, children, hint }: { label: string; error?: string; hint?: string; children: ReactNode }) {
  return (
    <div className="field">
      <label>{label}</label>
      {children}
      {hint && !error && <span className="muted" style={{ fontSize: 12.5 }}>{hint}</span>}
      {error && <span className="field-error">{error}</span>}
    </div>
  );
}
export function Input(props: InputHTMLAttributes<HTMLInputElement>) {
  const { className = '', ...rest } = props;
  return <input className={`input ${className}`} {...rest} />;
}
export function Select(props: SelectHTMLAttributes<HTMLSelectElement>) {
  const { className = '', ...rest } = props;
  return <select className={`select ${className}`} {...rest} />;
}
export function TextArea(props: TextareaHTMLAttributes<HTMLTextAreaElement>) {
  const { className = '', ...rest } = props;
  return <textarea className={`input ${className}`} {...rest} />;
}
export function SearchInput({ value, onChange, placeholder = 'Search…' }: { value: string; onChange: (v: string) => void; placeholder?: string }) {
  return (
    <div className="search grow">
      <Search size={16} />
      <input className="input" value={value} onChange={(e) => onChange(e.target.value)} placeholder={placeholder} />
    </div>
  );
}

/* ── Feedback ── */
export function Spinner() { return <div className="spinner" role="status" aria-label="Loading" />; }
export function Loading({ label = 'Loading…' }: { label?: string }) {
  return (
    <div className="center-box">
      <Spinner />
      <span>{label}</span>
    </div>
  );
}
export function Empty({ emoji = '📭', title, subtitle }: { emoji?: string; title: string; subtitle?: string }) {
  return (
    <div className="center-box">
      <div className="big">{emoji}</div>
      <strong>{title}</strong>
      {subtitle && <span>{subtitle}</span>}
    </div>
  );
}
export function ErrorBox({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div className="center-box">
      <div className="big">⚠️</div>
      <strong>Something went wrong</strong>
      <span>{message}</span>
      {onRetry && <Btn variant="primary" onClick={onRetry}>Try again</Btn>}
    </div>
  );
}
export function PageHeader({ title, sub, actions }: { title: string; sub?: string; actions?: ReactNode }) {
  return (
    <div className="row-between wrap mb-16">
      <div>
        <h1 className="page-title">{title}</h1>
        {sub && <p className="page-sub">{sub}</p>}
      </div>
      {actions && <div className="row">{actions}</div>}
    </div>
  );
}

/* ── Badges / status ── */
const statusColor: Record<string, string> = {
  paid: 'badge-green', delivered: 'badge-green', successful: 'badge-green', approved: 'badge-green', answered: 'badge-green',
  shipped: 'badge-blue', pending: 'badge-amber', processing: 'badge-amber', open: 'badge-blue',
  failed: 'badge-red', cancelled: 'badge-red', rejected: 'badge-red', closed: 'badge-gray',
};
export function StatusBadge({ status }: { status: string }) {
  return <span className={`badge ${statusColor[status.toLowerCase()] ?? 'badge-gray'}`}>{status}</span>;
}

/* ── Card ── */
export function Card({ children, className = '', pad = true, style }: { children: ReactNode; className?: string; pad?: boolean; style?: React.CSSProperties }) {
  return <div className={`card ${pad ? 'card-pad' : ''} ${className}`} style={style}>{children}</div>;
}

/* ── Modal ── */
export function Modal({ open, onClose, title, children, footer }: { open: boolean; onClose: () => void; title: string; children: ReactNode; footer?: ReactNode }) {
  if (!open) return null;
  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()} role="dialog" aria-modal="true" aria-label={title}>
        <div className="modal-head">
          <strong>{title}</strong>
          <button className="btn btn-sm" onClick={onClose} aria-label="Close"><X size={16} /></button>
        </div>
        <div className="modal-body">{children}</div>
        {footer && <div className="modal-foot">{footer}</div>}
      </div>
    </div>
  );
}

export function Confirm({ open, onClose, onConfirm, title, message, danger }: { open: boolean; onClose: () => void; onConfirm: () => void; title: string; message: string; danger?: boolean }) {
  return (
    <Modal open={open} onClose={onClose} title={title}
      footer={
        <>
          <Btn onClick={onClose}>Cancel</Btn>
          <Btn variant={danger ? 'danger' : 'primary'} onClick={() => { onConfirm(); onClose(); }}>Confirm</Btn>
        </>
      }
    >
      <p style={{ margin: 0 }}>{message}</p>
    </Modal>
  );
}

/* ── Pagination ── */
export function Pagination({ page, pageSize, total, onPage }: { page: number; pageSize: number; total: number; onPage: (p: number) => void }) {
  const pages = Math.max(1, Math.ceil(total / pageSize));
  if (pages <= 1) return null;
  return (
    <div className="row mt-16" style={{ justifyContent: 'center' }}>
      <Btn size="sm" disabled={page <= 1} onClick={() => onPage(page - 1)}>Prev</Btn>
      <span className="muted">Page {page} of {pages}</span>
      <Btn size="sm" disabled={page >= pages} onClick={() => onPage(page + 1)}>Next</Btn>
    </div>
  );
}

/* ── Data table wrapper ── */
export function Table({ head, children }: { head: string[]; children: ReactNode }) {
  return (
    <div className="table-wrap">
      <table className="table">
        <thead>
          <tr>{head.map((h) => <th key={h}>{h}</th>)}</tr>
        </thead>
        <tbody>{children}</tbody>
      </table>
    </div>
  );
}

/* ── Toggle ── */
export function Toggle({ checked, onChange, label }: { checked: boolean; onChange: (v: boolean) => void; label: string }) {
  return (
    <label className="row" style={{ cursor: 'pointer', gap: 10 }}>
      <input type="checkbox" checked={checked} onChange={(e) => onChange(e.target.checked)} style={{ width: 18, height: 18, accentColor: 'var(--primary)' }} />
      <span>{label}</span>
    </label>
  );
}
