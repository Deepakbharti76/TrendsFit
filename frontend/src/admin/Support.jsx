import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api, apiError } from '../lib/api';
import { useToast } from '../lib/AppContext';
import { ErrorBox, Spinner } from '../components/Bits';
import { fmtDate } from './common';

const LIST_MS = 15000;
const THREAD_MS = 8000;

export function Thread({ conv, onSend, onToggle, sending }) {
  const [text, setText] = useState('');
  const end = useRef(null);
  const count = conv.messages.length;
  useEffect(() => { end.current?.scrollIntoView?.({ block: 'end' }); }, [count, conv.id]);
  const submit = async (e) => {
    e.preventDefault();
    const body = text.trim();
    if (!body) return;
    const ok = await onSend(body);
    if (ok) setText('');
  };
  return (
    <div className="chat-thread d-flex flex-column h-100">
      <div className="chat-head d-flex flex-wrap justify-content-between align-items-center gap-2 p-2 border-bottom">
        <div><strong>{conv.customerName}</strong> <span className="small text-muted">{conv.customerEmail}</span>
          {conv.orderNumber && <div className="small">Order <Link to={`/admin/orders/${conv.orderNumber}`}>{conv.orderNumber}</Link>{conv.orderStatus ? ` · ${conv.orderStatus.replaceAll('_', ' ')}` : ''}</div>}</div>
        <button type="button" className="btn btn-sm btn-outline-secondary" onClick={onToggle}>{conv.status === 'OPEN' ? 'Close chat' : 'Reopen chat'}</button>
      </div>
      <div className="chat-messages flex-grow-1 p-3" role="log" aria-live="polite" aria-label="Messages">
        {conv.messages.map((m) => (
          <div key={m.id} className={`chat-msg ${m.sender === 'STAFF' ? 'staff' : 'customer'}`}>
            <div className="chat-bubble">{m.body}</div>
            <div className="chat-meta">{m.sender === 'STAFF' ? (m.staffEmail || 'Staff') : conv.customerName} · {fmtDate(m.createdAt)}{m.sender === 'STAFF' && (m.read ? ' · Seen' : ' · Sent')}</div>
          </div>
        ))}
        <div ref={end} />
      </div>
      <form className="chat-compose d-flex gap-2 p-2 border-top" onSubmit={submit}>
        <textarea className="form-control" rows={2} maxLength={2000} value={text} onChange={(e) => setText(e.target.value)} aria-label="Reply" placeholder="Type your reply…"
          onKeyDown={(e) => { if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) submit(e); }} />
        <button className="btn btn-dark align-self-end" disabled={sending || !text.trim()}>{sending ? 'Sending…' : 'Send'}</button>
      </form>
    </div>
  );
}

export default function Support() {
  const toast = useToast();
  const [sp, setSp] = useSearchParams();
  const selected = sp.get('c') ? Number(sp.get('c')) : null;
  const [q, setQ] = useState('');
  const [status, setStatus] = useState('');
  const [unread, setUnread] = useState(sp.get('unread') === '1');
  const [list, setList] = useState(null);
  const [err, setErr] = useState('');
  const [conv, setConv] = useState(null);
  const [sending, setSending] = useState(false);

  const loadList = useCallback(() => api.get('/admin/support/conversations', { params: { q: q || undefined, status: status || undefined, unreadOnly: unread, pageSize: 50 } })
    .then((r) => { setList(r.data); setErr(''); }).catch((e) => setErr(apiError(e).message)), [q, status, unread]);
  const loadConv = useCallback((id) => api.get(`/admin/support/conversations/${id}`).then((r) => setConv(r.data)).catch((e) => toast.push(apiError(e).message, 'danger')), []); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => { loadList(); const t = setInterval(() => { if (!document.hidden) loadList(); }, LIST_MS); return () => clearInterval(t); }, [loadList]);
  useEffect(() => {
    if (!selected) { setConv(null); return undefined; }
    loadConv(selected);
    const t = setInterval(() => { if (!document.hidden) loadConv(selected).then(loadList); }, THREAD_MS);
    return () => clearInterval(t);
  }, [selected]); // eslint-disable-line react-hooks/exhaustive-deps

  const open = (id) => { const n = new URLSearchParams(sp); n.set('c', String(id)); setSp(n); };
  const back = () => { const n = new URLSearchParams(sp); n.delete('c'); setSp(n); };
  const send = async (body) => {
    setSending(true);
    try { const r = await api.post(`/admin/support/conversations/${selected}/messages`, { body }); setConv(r.data); loadList(); return true; }
    catch (e) { toast.push(apiError(e).message, 'danger'); return false; }
    finally { setSending(false); }
  };
  const toggle = async () => {
    try { await api.patch(`/admin/support/conversations/${selected}`, { status: conv.status === 'OPEN' ? 'CLOSED' : 'OPEN' }); toast.push('Chat updated'); loadConv(selected); loadList(); }
    catch (e) { toast.push(apiError(e).message, 'danger'); }
  };

  return (
    <div>
      <h1 className="h4 mb-3">Customer support chat {list && list.unreadTotal > 0 && <span className="badge text-bg-danger ms-1">{list.unreadTotal} unread</span>}</h1>
      <div className="chat-shell">
        <div className={`chat-list ${selected ? 'd-none d-md-flex' : 'd-flex'} flex-column`}>
          <div className="p-2 border-bottom">
            <input className="form-control form-control-sm mb-2" placeholder="Search name, email or order" value={q} onChange={(e) => setQ(e.target.value)} aria-label="Search conversations" />
            <div className="d-flex gap-2 align-items-center">
              <select className="form-select form-select-sm" value={status} onChange={(e) => setStatus(e.target.value)} aria-label="Status filter"><option value="">All</option><option value="OPEN">Open</option><option value="CLOSED">Closed</option></select>
              <div className="form-check mb-0"><input id="unreadOnly" type="checkbox" className="form-check-input" checked={unread} onChange={(e) => setUnread(e.target.checked)} /><label htmlFor="unreadOnly" className="form-check-label small text-nowrap">Unread</label></div>
            </div>
          </div>
          <div className="chat-items flex-grow-1">
            {err && <ErrorBox message={err} onRetry={loadList} />}
            {!list && !err && <Spinner />}
            {list && list.items.length === 0 && <div className="p-3 small text-muted">No conversations yet. Customers can start one from the Contact page on the storefront.</div>}
            {list && list.items.map((c) => (
              <button type="button" key={c.id} className={`chat-item ${selected === c.id ? 'active' : ''}`} onClick={() => open(c.id)}>
                <span className="d-flex justify-content-between"><strong className="text-truncate">{c.customerName}</strong>{c.unread > 0 && <span className="badge rounded-pill text-bg-danger">{c.unread}</span>}</span>
                <span className="d-block small text-muted text-truncate">{c.orderNumber ? `Order ${c.orderNumber} · ` : ''}{c.lastMessage}</span>
                <span className="d-block small text-muted">{fmtDate(c.lastMessageAt)}{c.status === 'CLOSED' ? ' · Closed' : ''}</span>
              </button>
            ))}
          </div>
        </div>
        <div className={`chat-pane ${selected ? 'd-flex' : 'd-none d-md-flex'} flex-column`}>
          {selected && <button type="button" className="btn btn-sm btn-link d-md-none align-self-start" onClick={back}>← All conversations</button>}
          {!selected && <div className="m-auto text-muted p-4">Select a conversation to read and reply.</div>}
          {selected && !conv && <Spinner />}
          {selected && conv && <Thread conv={conv} onSend={send} onToggle={toggle} sending={sending} />}
        </div>
      </div>
      <div className="small text-muted mt-2">New messages are checked every few seconds (polling, not an instant push).</div>
    </div>
  );
}
