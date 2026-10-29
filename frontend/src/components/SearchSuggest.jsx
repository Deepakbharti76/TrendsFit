import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../lib/api';
import { inr } from '../lib/money';

/**
 * Product suggestions for the header search. Uses the existing GET /products?q= endpoint
 * (debounced, latest request wins), so there is no separate index to keep in sync.
 */
export function useSuggestions(q, fetcher = (term) => api.get('/products', { params: { q: term, page: 0, pageSize: 6 } }).then((r) => r.data.items || [])) {
  const [items, setItems] = useState([]);
  const [status, setStatus] = useState('idle'); // idle | loading | done | error
  const fetchRef = useRef(fetcher);
  fetchRef.current = fetcher;
  useEffect(() => {
    const term = q.trim();
    if (term.length < 2) { setItems([]); setStatus('idle'); return undefined; }
    let live = true;
    setStatus('loading');
    const t = setTimeout(() => {
      fetchRef.current(term).then((r) => { if (live) { setItems(r.slice(0, 6)); setStatus('done'); } })
        .catch(() => { if (live) { setItems([]); setStatus('error'); } });
    }, 250);
    return () => { live = false; clearTimeout(t); };
  }, [q]);
  return { items, status };
}

export default function SearchSuggest({ q, onPick }) {
  const { items, status } = useSuggestions(q);
  if (status === 'idle' || status === 'error') return null;
  return (
    <div className="search-suggest" role="listbox" aria-label="Product suggestions">
      {status === 'loading' && items.length === 0 && <div className="px-3 py-2 small text-muted" role="status">Searching…</div>}
      {status === 'done' && items.length === 0 && <div className="px-3 py-2 small text-muted" role="status">No products found for “{q.trim()}”.</div>}
      {items.map((p) => (
        <Link key={p.id ?? p.slug} to={`/product/${p.slug}`} role="option" aria-selected="false" className="search-suggest-item" onClick={onPick}>
          {p.image ? <img src={p.image} alt="" width="40" height="50" loading="lazy" /> : <span className="ss-noimg" aria-hidden="true" />}
          <span className="ss-text"><span className="ss-name">{p.name}</span><span className="ss-meta">{p.category}{p.category ? ' · ' : ''}{inr(p.finalPrice ?? p.salePrice ?? p.price)}</span></span>
        </Link>
      ))}
    </div>
  );
}
