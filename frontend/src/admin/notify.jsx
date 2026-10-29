import { useCallback, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../lib/api';
import { fmtDate } from './common';

const POLL_MS = 30000;
const ICON = { NEW_ORDER: '🛍', COD_ORDER: '💵', PAYMENT_FAILED: '⚠️', LOW_STOCK: '📉', OUT_OF_STOCK: '⛔', EXCHANGE_REQUEST: '🔁', REFUND: '↩️', MESSAGE: '💬' };

/**
 * Polls the server every 30 seconds (and when the tab becomes visible again). This is polling, not a live push connection,
 * so numbers can be up to 30 seconds behind. Pass the signed-in user so only allowed endpoints are called.
 */
export function useAdminPulse(user) {
  const [state, setState] = useState({ items: [], count: 0, unread: 0, updatedAt: null, error: false, loading: true });
  const alive = useRef(true);
  const perms = user?.permissions || [];
  const hasPerm = (p) => perms.includes(p) || perms.includes(`PERM_${p}`);
  const wantsNotes = ['ORDER_READ', 'PRODUCT_READ', 'SUPPORT_CHAT', 'PAYMENT_READ'].some(hasPerm);
  const wantsChat = hasPerm('SUPPORT_CHAT');

  const refresh = useCallback(async () => {
    if (!user) return;
    try {
      const [n, u] = await Promise.all([
        wantsNotes ? api.get('/admin/notifications').then((r) => r.data) : Promise.resolve({ items: [], count: 0 }),
        wantsChat ? api.get('/admin/support/unread').then((r) => r.data) : Promise.resolve({ unread: 0 }),
      ]);
      if (alive.current) setState({ items: n.items || [], count: n.count || 0, unread: u.unread || 0, updatedAt: new Date(), error: false, loading: false });
    } catch {
      if (alive.current) setState((s) => ({ ...s, error: true, loading: false }));
    }
  }, [user?.id, wantsNotes, wantsChat]); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    alive.current = true;
    refresh();
    const t = setInterval(() => { if (!document.hidden) refresh(); }, POLL_MS);
    const onVis = () => { if (!document.hidden) refresh(); };
    document.addEventListener('visibilitychange', onVis);
    return () => { alive.current = false; clearInterval(t); document.removeEventListener('visibilitychange', onVis); };
  }, [refresh]);

  return { ...state, refresh };
}

export function NotificationBell({ pulse }) {
  const [open, setOpen] = useState(false);
  const box = useRef(null);
  useEffect(() => {
    if (!open) return undefined;
    const onKey = (e) => { if (e.key === 'Escape') setOpen(false); };
    const onClick = (e) => { if (box.current && !box.current.contains(e.target)) setOpen(false); };
    document.addEventListener('keydown', onKey);
    document.addEventListener('mousedown', onClick);
    return () => { document.removeEventListener('keydown', onKey); document.removeEventListener('mousedown', onClick); };
  }, [open]);
  return (
    <div className="position-relative" ref={box}>
      <button type="button" className="btn btn-sm btn-outline-secondary position-relative" aria-haspopup="true" aria-expanded={open}
        aria-label={`Notifications, ${pulse.count} items`} onClick={() => setOpen((o) => !o)}>
        🔔{pulse.count > 0 && <span className="badge rounded-pill text-bg-danger notif-badge">{pulse.count > 99 ? '99+' : pulse.count}</span>}
      </button>
      {open && (
        <div className="notif-panel shadow" role="dialog" aria-label="Notifications">
          <div className="d-flex justify-content-between align-items-center px-3 py-2 border-bottom">
            <strong className="small">Notifications</strong>
            <button type="button" className="btn btn-sm btn-link p-0" onClick={pulse.refresh}>Refresh</button>
          </div>
          <div className="notif-list">
            {pulse.error && <div className="px-3 py-2 small text-danger">Could not refresh. Showing the last known data.</div>}
            {pulse.items.length === 0 && !pulse.loading && <div className="px-3 py-3 small text-muted">Nothing needs your attention right now.</div>}
            {pulse.items.map((n, i) => (
              <Link key={`${n.kind}-${i}`} to={n.link} className="notif-item" onClick={() => setOpen(false)}>
                <span aria-hidden="true">{ICON[n.kind] || '•'}</span>
                <span><span className="d-block small fw-semibold">{n.title}</span><span className="d-block small text-muted">{n.detail} · {fmtDate(n.at)}</span></span>
              </Link>
            ))}
          </div>
          <div className="px-3 py-2 border-top small text-muted">Checked every 30 seconds{pulse.updatedAt ? ` · last at ${pulse.updatedAt.toLocaleTimeString('en-IN')}` : ''}</div>
        </div>
      )}
    </div>
  );
}
