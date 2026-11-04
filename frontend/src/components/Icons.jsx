import { useEffect, useState } from 'react';

/** Modern line icons (stroke = currentColor, so they follow the TrendsFit navy and the hover colours). */
export function UserIcon(props) {
  return (
    <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false" {...props}>
      <circle cx="12" cy="8.2" r="3.9" />
      <path d="M4.2 20.2c.6-3.7 3.7-6 7.8-6s7.2 2.3 7.8 6" />
    </svg>
  );
}

export function SearchIcon(props) {
  return (
    <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false" {...props}>
      <circle cx="10.8" cy="10.8" r="6.6" />
      <path d="M15.8 15.8 21 21" />
    </svg>
  );
}

export function HeartIcon(props) {
  return (
    <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false" {...props}>
      <path d="M12 20.4 4.6 13.2a4.9 4.9 0 0 1 6.9-6.9l.5.5.5-.5a4.9 4.9 0 0 1 6.9 6.9z" />
    </svg>
  );
}

export function CartIcon(props) {
  return (
    <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false" {...props}>
      <path d="M2.8 3.6h2.5l2.1 11.1a1.6 1.6 0 0 0 1.6 1.3h8.2a1.6 1.6 0 0 0 1.6-1.2l1.5-6.6H6.1" />
      <circle cx="9.6" cy="20" r="1.4" />
      <circle cx="17.4" cy="20" r="1.4" />
    </svg>
  );
}

/** Round profile picture: the user's uploaded photo, or the default user icon when there is none (or it fails to load). */
export function Avatar({ url, size = 30, className = '', alt = '' }) {
  const [bad, setBad] = useState(false);
  useEffect(() => setBad(false), [url]);
  const show = url && !bad;
  return (
    <span className={`avatar ${className}`} style={{ width: size, height: size }}>
      {show
        ? <img src={url} alt={alt} onError={() => setBad(true)} />
        : <UserIcon width={Math.round(size * 0.66)} height={Math.round(size * 0.66)} />}
    </span>
  );
}
