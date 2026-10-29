import { useState } from 'react';
import { Link } from 'react-router-dom';
import { inr } from '../lib/money';

export function Price({ price, salePrice, finalPrice, discountPercent, size = '' }) {
  const fin = finalPrice ?? salePrice ?? price;
  const onSale = salePrice != null && Number(salePrice) < Number(price);
  return (
    <span className={`price ${size}`}>
      <span className="price-now">{inr(fin)}</span>
      {onSale && <><span className="price-was">{inr(price)}</span>{discountPercent > 0 && <span className="price-off">{discountPercent}% off</span>}</>}
    </span>
  );
}

export function Stars({ value = 0, count, small }) {
  const v = Math.round(Number(value) * 2) / 2;
  return (
    <span className={`stars ${small ? 'small' : ''}`} aria-label={`${value} out of 5`}>
      {[1, 2, 3, 4, 5].map((i) => <span key={i} className={i <= v ? 'on' : i - 0.5 === v ? 'half' : ''} aria-hidden="true">★</span>)}
      {count != null && <span className="stars-count">({count})</span>}
    </span>
  );
}

export function Spinner({ label = 'Loading' }) {
  return <div className="text-center py-5" role="status"><div className="spinner-border" style={{ color: 'var(--tf-ink)' }} /><span className="visually-hidden">{label}</span></div>;
}

export function ErrorBox({ message, onRetry }) {
  return (
    <div className="alert alert-danger d-flex justify-content-between align-items-center" role="alert">
      <span>{message}</span>
      {onRetry && <button className="btn btn-sm btn-outline-danger" onClick={onRetry}>Try again</button>}
    </div>
  );
}

export function Empty({ title, text, to, cta }) {
  return (
    <div className="text-center py-5">
      <h2 className="h4 display-font">{title}</h2>
      {text && <p className="text-muted">{text}</p>}
      {to && <Link to={to} className="btn btn-tf">{cta}</Link>}
    </div>
  );
}

export function Pagination({ page, totalPages, onChange }) {
  if (totalPages <= 1) return null;
  const pages = [];
  const start = Math.max(0, Math.min(page - 2, totalPages - 5));
  for (let i = start; i < Math.min(totalPages, start + 5); i++) pages.push(i);
  return (
    <nav aria-label="Pages" className="mt-4">
      <ul className="pagination justify-content-center flex-wrap">
        <li className={`page-item ${page === 0 ? 'disabled' : ''}`}><button className="page-link" onClick={() => onChange(page - 1)}>Previous</button></li>
        {pages.map((p) => <li key={p} className={`page-item ${p === page ? 'active' : ''}`}><button className="page-link" onClick={() => onChange(p)}>{p + 1}</button></li>)}
        <li className={`page-item ${page >= totalPages - 1 ? 'disabled' : ''}`}><button className="page-link" onClick={() => onChange(page + 1)}>Next</button></li>
      </ul>
    </nav>
  );
}

export function Field({ label, error, children, id, required, hint }) {
  return (
    <div className="mb-3">
      <label htmlFor={id} className="form-label">{label}{required && <span className="req" aria-hidden="true"> *</span>}{required && <span className="visually-hidden"> (required)</span>}</label>
      {children}
      {hint && !error && <div className="form-text">{hint}</div>}
      {error && <div className="invalid-feedback d-block" role="alert">{error}</div>}
    </div>
  );
}

/** Password input with a show/hide eye button. Accepts the usual <input> props. */
export function PasswordInput({ className = 'form-control', ...props }) {
  const [show, setShow] = useState(false);
  return (
    <div className="pw-wrap">
      <input {...props} type={show ? 'text' : 'password'} className={`${className} pw-input`} />
      <button type="button" className="pw-eye" onClick={() => setShow((s) => !s)} aria-label={show ? 'Hide password' : 'Show password'} aria-pressed={show}>
        {show ? (
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/><line x1="1" y1="1" x2="23" y2="23"/></svg>
        ) : (
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
        )}
      </button>
    </div>
  );
}

export function StatusBadge({ status, label }) {
  const tone = {
    PAID: 'success', PROCESSING: 'primary', PACKED: 'info', SHIPPED: 'info', OUT_FOR_DELIVERY: 'info', DELIVERED: 'success',
    PENDING_PAYMENT: 'warning', PAYMENT_FAILED: 'danger', EXCHANGE_REQUESTED: 'warning', EXCHANGE_APPROVED: 'primary',
    EXCHANGE_REJECTED: 'danger', EXCHANGE_COMPLETED: 'success',
  }[status] || 'secondary';
  return <span className={`badge text-bg-${tone}`}>{label || status}</span>;
}
