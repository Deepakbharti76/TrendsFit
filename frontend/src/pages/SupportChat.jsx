import { useCallback, useEffect, useRef, useState } from 'react';
import { api, apiError } from '../lib/api';
import { useAuth } from '../lib/AppContext';
import { useSeo } from '../lib/seo';
import { clearChat, loadChat, saveChat, validateStart } from '../lib/supportChat';
import { ErrorBox, Field, Spinner } from '../components/Bits';

const POLL_MS = 8000;
const hdr = (token) => ({ 'X-Support-Token': token });

function StartForm({ onStarted }) {
  const { user } = useAuth();
  const [f, setF] = useState({ name: user?.fullName || '', email: user?.email || '', orderNumber: '', message: '' });
  const [errs, setErrs] = useState({});
  const [msg, setMsg] = useState('');
  const [busy, setBusy] = useState(false);
  const set = (k, v) => setF((x) => ({ ...x, [k]: v }));
  const submit = async (e) => {
    e.preventDefault();
    const v = validateStart(f);
    setErrs(v);
    if (Object.keys(v).length) return;
    setBusy(true); setMsg('');
    try {
      const r = await api.post('/support/conversations', f);
      onStarted({ id: r.data.id, token: r.data.token });
    } catch (x) { setMsg(apiError(x).message); } finally { setBusy(false); }
  };
  return (
    <form onSubmit={submit} noValidate>
      {msg && <ErrorBox message={msg} />}
      <Field label="Your name" id="sc-name" error={errs.name}><input id="sc-name" className="form-control" maxLength={80} value={f.name} onChange={(e) => set('name', e.target.value)} /></Field>
      <Field label="Email" id="sc-email" error={errs.email}><input id="sc-email" type="email" className="form-control" value={f.email} onChange={(e) => set('email', e.target.value)} /></Field>
      <Field label="Order number (optional)" id="sc-order"><input id="sc-order" className="form-control" value={f.orderNumber} onChange={(e) => set('orderNumber', e.target.value)} placeholder="Use the email you ordered with" /></Field>
      <Field label="How can we help?" id="sc-msg" error={errs.message}><textarea id="sc-msg" className="form-control" rows={4} maxLength={2000} value={f.message} onChange={(e) => set('message', e.target.value)} /></Field>
      <button className="btn btn-tf" disabled={busy}>{busy ? 'Starting…' : 'Start chat'}</button>
    </form>
  );
}

function Conversation({ chat, onReset }) {
  const [conv, setConv] = useState(null);
  const [err, setErr] = useState('');
  const [text, setText] = useState('');
  const [sending, setSending] = useState(false);
  const end = useRef(null);
  const load = useCallback(() => api.get(`/support/conversations/${chat.id}`, { headers: hdr(chat.token) })
    .then((r) => { setConv(r.data); setErr(''); })
    .catch((e) => { const x = apiError(e); if (x.code === 'NOT_FOUND') onReset(); else setErr(x.message); }), [chat.id, chat.token]); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => { load(); const t = setInterval(() => { if (!document.hidden) load(); }, POLL_MS); return () => clearInterval(t); }, [load]);
  useEffect(() => { end.current?.scrollIntoView?.({ block: 'end' }); }, [conv?.messages?.length]);
  const send = async (e) => {
    e.preventDefault();
    const body = text.trim();
    if (!body) return;
    setSending(true);
    try { const r = await api.post(`/support/conversations/${chat.id}/messages`, { body }, { headers: hdr(chat.token) }); setConv(r.data); setText(''); setErr(''); }
    catch (x) { setErr(apiError(x).message); } finally { setSending(false); }
  };
  if (!conv) return err ? <ErrorBox message={err} onRetry={load} /> : <Spinner />;
  return (
    <div className="chat-thread customer-chat">
      <div className="chat-messages p-3" role="log" aria-live="polite" aria-label="Chat messages">
        {conv.messages.map((m) => (
          <div key={m.id} className={`chat-msg ${m.sender === 'CUSTOMER' ? 'staff' : 'customer'}`}>
            <div className="chat-bubble">{m.body}</div>
            <div className="chat-meta">{m.sender === 'CUSTOMER' ? 'You' : 'TrendsFit support'} · {new Date(m.createdAt).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' })}</div>
          </div>
        ))}
        <div ref={end} />
      </div>
      {err && <div className="px-3"><ErrorBox message={err} /></div>}
      <form className="chat-compose d-flex gap-2 p-2 border-top" onSubmit={send}>
        <textarea className="form-control" rows={2} maxLength={2000} value={text} onChange={(e) => setText(e.target.value)} aria-label="Your message" placeholder="Type your message…" />
        <button className="btn btn-tf align-self-end" disabled={sending || !text.trim()}>{sending ? 'Sending…' : 'Send'}</button>
      </form>
      <div className="small text-muted p-2">We usually reply during business hours. This chat keeps checking for replies while this page is open. {conv.status === 'CLOSED' ? 'This chat was closed; sending a message reopens it.' : ''} <button type="button" className="btn btn-link btn-sm p-0" onClick={onReset}>Start a new chat</button></div>
    </div>
  );
}

export default function SupportChat() {
  useSeo({ title: 'Chat with support', description: 'Chat with the TrendsFit support team.', noindex: true });
  const [chat, setChat] = useState(() => loadChat());
  return (
    <div className="container section" style={{ maxWidth: 720 }}>
      <h1 className="display-font h2 mb-3">Chat with support</h1>
      {chat
        ? <Conversation chat={chat} onReset={() => { clearChat(); setChat(null); }} />
        : <StartForm onStarted={(c) => { saveChat(c); setChat(c); }} />}
    </div>
  );
}
