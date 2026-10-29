import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api, apiError } from '../lib/api';
import { useAuth, useToast } from '../lib/AppContext';
import { ErrorBox, Field, Pagination, Spinner, StatusBadge } from '../components/Bits';
import { inr } from '../lib/money';
import { Table, can, fmtDate, useFetch } from './common';

// ------------------------------------------------------------------ payments, refunds and COD

const PAY_STATUS = ['CREATED', 'PAID', 'FAILED', 'REFUND_SENT', 'REFUNDED'];

export function Payments() {
  const [sp, setSp] = useSearchParams();
  const view = sp.get('view') === 'cod' ? 'cod' : 'online';
  const setView = (v) => setSp(v === 'cod' ? { view: 'cod' } : {});
  return (
    <div>
      <h1 className="h4 mb-3">Payments, refunds &amp; COD</h1>
      <div className="btn-group mb-3" role="group" aria-label="Payment view">
        <button type="button" className={`btn btn-sm ${view === 'online' ? 'btn-dark' : 'btn-outline-dark'}`} aria-pressed={view === 'online'} onClick={() => setView('online')}>Online payments (Razorpay)</button>
        <button type="button" className={`btn btn-sm ${view === 'cod' ? 'btn-dark' : 'btn-outline-dark'}`} aria-pressed={view === 'cod'} onClick={() => setView('cod')}>Cash on Delivery</button>
      </div>
      {view === 'online' ? <OnlinePayments /> : <CodOverview />}
    </div>
  );
}

function OnlinePayments() {
  const [q, setQ] = useState('');
  const [status, setStatus] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [page, setPage] = useState(0);
  const { data, err, loading, reload } = useFetch('/admin/payments', { q: q || undefined, status: status || undefined, from: from || undefined, to: to || undefined, page, pageSize: 20 });
  const integ = useFetch('/admin/dashboard');
  const reset = (fn) => (e) => { fn(e.target.value); setPage(0); };
  return (
    <>
      {integ.data && !integ.data.integrations.razorpay && <div className="alert alert-warning small">Razorpay keys are not configured on the server, so online payments cannot be taken yet. Set RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET (see the README).</div>}
      {data && (
        <div className="row g-3 mb-3">{data.summary.map((s) => <div key={s.status} className="col-6 col-md-3"><div className="stat"><div className="small text-muted">{s.status.replaceAll('_', ' ')}</div><div className="v">{s.count}</div><div className="small text-muted">{inr(s.amount)}</div></div></div>)}
          {data.summary.length === 0 && <div className="col-12 text-muted">No online payment attempts yet.</div>}</div>
      )}
      <div className="d-flex flex-wrap gap-2 mb-3">
        <input className="form-control" style={{ maxWidth: 260 }} placeholder="Order no., name or Razorpay id" value={q} onChange={reset(setQ)} aria-label="Search payments" />
        <select className="form-select" style={{ maxWidth: 180 }} value={status} onChange={reset(setStatus)} aria-label="Payment status"><option value="">All statuses</option>{PAY_STATUS.map((s) => <option key={s} value={s}>{s.replaceAll('_', ' ')}</option>)}</select>
        <input type="date" className="form-control" style={{ maxWidth: 160 }} value={from} onChange={reset(setFrom)} aria-label="From date" />
        <input type="date" className="form-control" style={{ maxWidth: 160 }} value={to} onChange={reset(setTo)} aria-label="To date" />
      </div>
      {err && <ErrorBox message={err} onRetry={reload} />}
      {loading && !data ? <Spinner /> : data && (
        <Table head={['Order', 'Customer', 'Razorpay order / payment', 'Amount', 'Status', 'Date']} empty={data.items.length === 0 ? 'No payments match these filters.' : ''}>
          {data.items.map((p) => (
            <tr key={p.id}><td><Link to={`/admin/orders/${p.orderNumber}`}>{p.orderNumber}</Link></td><td>{p.customerName}</td>
              <td className="small text-break">{p.providerOrderId}<br />{p.providerPaymentId || <span className="text-muted">no payment id yet</span>}{p.failureReason && <div className="text-danger">{p.failureReason}</div>}</td>
              <td>{inr(p.amount)}</td><td><StatusBadge status={p.status === 'PAID' ? 'DELIVERED' : p.status === 'FAILED' ? 'PAYMENT_FAILED' : 'PENDING_PAYMENT'} label={p.status.replaceAll('_', ' ')} /></td><td className="small">{fmtDate(p.createdAt)}</td></tr>
          ))}
        </Table>
      )}
      {data && <Pagination page={data.page} totalPages={data.totalPages} onChange={setPage} />}
      <div className="small text-muted mt-3">To refund an order, open it from the list above and use “Refund full amount”. The refund is confirmed by the Razorpay webhook.</div>
    </>
  );
}

function CodOverview() {
  const { data, err, loading, reload } = useFetch('/admin/cod');
  if (err) return <ErrorBox message={err} onRetry={reload} />;
  if (loading || !data) return <Spinner />;
  return (
    <>
      {!data.enabled && <div className="alert alert-warning small">Cash on Delivery is switched off in Payment settings.</div>}
      <div className="row g-3 mb-3">
        <div className="col-6 col-md-3"><div className="stat"><div className="small text-muted">To be collected</div><div className="v">{inr(data.pending.amount)}</div><div className="small text-muted">{data.pending.orders} orders in progress</div></div></div>
        <div className="col-6 col-md-3"><div className="stat"><div className="small text-muted">Collected (delivered)</div><div className="v">{inr(data.collected.amount)}</div><div className="small text-muted">{data.collected.orders} orders</div></div></div>
        <div className="col-6 col-md-3"><div className="stat"><div className="small text-muted">COD limit per order</div><div className="v">{inr(data.maxOrderValue)}</div></div></div>
      </div>
      <Table head={['Order', 'Customer', 'Total', 'Status', 'Date']} empty={data.recent.length === 0 ? 'No COD orders yet.' : ''}>
        {data.recent.map((o) => <tr key={o.orderNumber}><td><Link to={`/admin/orders/${o.orderNumber}`}>{o.orderNumber}</Link></td><td>{o.customerName}</td><td>{inr(o.total)}</td><td><StatusBadge status={o.status} label={o.status.replaceAll('_', ' ')} /></td><td className="small">{fmtDate(o.createdAt)}</td></tr>)}
      </Table>
      <div className="small text-muted mt-2">COD money is counted as collected only once the order is marked Delivered. Reconcile with your courier’s COD remittance report.</div>
    </>
  );
}

// ------------------------------------------------------------------ shipments & tracking

export function Shipments() {
  const { user } = useAuth();
  const toast = useToast();
  const [q, setQ] = useState('');
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);
  const { data, err, loading, reload } = useFetch('/admin/shipments', { q: q || undefined, status: status || undefined, page, pageSize: 20 });
  const label = (id) => api.get(`/admin/shipments/${id}/label`).then((r) => window.open(r.data.labelUrl, '_blank', 'noopener')).catch((e) => toast.push(apiError(e).message, 'danger'));
  return (
    <div>
      <h1 className="h4 mb-3">Shipments &amp; tracking</h1>
      {data && !data.shiprocketConfigured && <div className="alert alert-warning small">Shiprocket is not configured, so shipments cannot be created yet. Set SHIPROCKET_EMAIL and SHIPROCKET_PASSWORD (see the README). Nothing here is simulated.</div>}
      {data && data.readyToShip.length > 0 && (
        <div className="stat mb-3"><div className="fw-semibold mb-2">Ready to ship ({data.readyToShip.length})</div>
          {data.readyToShip.map((o) => <div key={o.orderNumber} className="small py-1 d-flex justify-content-between"><span>{o.orderNumber} · {o.customerName} · {o.city}</span><Link to={`/admin/orders/${o.orderNumber}`}>Create shipment</Link></div>)}</div>
      )}
      <div className="d-flex flex-wrap gap-2 mb-3">
        <input className="form-control" style={{ maxWidth: 260 }} placeholder="Order no., name or AWB" value={q} onChange={(e) => { setQ(e.target.value); setPage(0); }} aria-label="Search shipments" />
        <select className="form-select" style={{ maxWidth: 200 }} value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }} aria-label="Shipment status"><option value="">All statuses</option>{(data?.summary || []).map((s) => <option key={s.status} value={s.status}>{s.status.replaceAll('_', ' ')} ({s.count})</option>)}</select>
      </div>
      {err && <ErrorBox message={err} onRetry={reload} />}
      {loading && !data ? <Spinner /> : data && (
        <Table head={['Order', 'Type', 'AWB / courier', 'Status', 'ETA', 'Last update', '']} empty={data.items.length === 0 ? 'No shipments yet. Create one from an order that is in Processing.' : ''}>
          {data.items.map((s) => (
            <tr key={s.id}><td><Link to={`/admin/orders/${s.orderNumber}`}>{s.orderNumber}</Link><div className="small text-muted">{s.customerName} · {s.city}</div></td>
              <td className="small">{s.kind}</td><td className="small">{s.awb || '—'}<br />{s.courier || ''}</td>
              <td className="small">{(s.statusText || s.status).replaceAll('_', ' ')}<div className="text-muted">Order: {s.orderStatus.replaceAll('_', ' ')}</div></td>
              <td className="small">{s.estimatedDelivery || '—'}</td><td className="small">{fmtDate(s.lastEventAt || s.createdAt)}</td>
              <td className="text-nowrap">{s.trackingUrl && <a className="btn btn-sm btn-outline-dark me-1" href={s.trackingUrl} target="_blank" rel="noopener noreferrer">Track</a>}
                {can(user, 'SHIPMENT_WRITE') && <button type="button" className="btn btn-sm btn-outline-dark" onClick={() => label(s.id)}>Label</button>}</td></tr>
          ))}
        </Table>
      )}
      {data && <Pagination page={data.page} totalPages={data.totalPages} onChange={setPage} />}
      <div className="small text-muted mt-3">Status comes only from Shiprocket webhooks and verified API responses. Creating a shipment never marks an order as delivered.</div>
    </div>
  );
}

// ------------------------------------------------------------------ categories

const EMPTY_CAT = { name: '', slug: '', description: '', sortOrder: 0, active: true };

export function Categories() {
  const { user } = useAuth();
  const toast = useToast();
  const write = can(user, 'PRODUCT_WRITE');
  const { data, err, loading, reload } = useFetch('/admin/categories/manage');
  const [f, setF] = useState(EMPTY_CAT);
  const [editing, setEditing] = useState(null);
  const [saving, setSaving] = useState(false);
  const [fe, setFe] = useState('');
  const set = (k, v) => setF((x) => ({ ...x, [k]: v }));
  const edit = (c) => { setEditing(c.id); setF({ name: c.name, slug: c.slug, description: c.description || '', sortOrder: c.sortOrder, active: !!c.active }); setFe(''); };
  const cancel = () => { setEditing(null); setF(EMPTY_CAT); setFe(''); };
  const save = async (e) => {
    e.preventDefault();
    setSaving(true); setFe('');
    try {
      const body = { ...f, sortOrder: Number(f.sortOrder) || 0 };
      if (editing) await api.put(`/admin/categories/${editing}`, body); else await api.post('/admin/categories', body);
      toast.push(editing ? 'Category updated' : 'Category added'); cancel(); reload();
    } catch (x) { setFe(apiError(x).message); } finally { setSaving(false); }
  };
  const del = async (c) => {
    if (!window.confirm(`Delete the category “${c.name}”? This cannot be undone.`)) return;
    try { await api.delete(`/admin/categories/${c.id}`); toast.push('Category deleted'); reload(); } catch (x) { toast.push(apiError(x).message, 'danger'); }
  };
  return (
    <div>
      <h1 className="h4 mb-1">Categories</h1>
      <p className="small text-muted">Categories group products on the storefront. Hide a category instead of deleting it if it still has products. Collections are not part of this version.</p>
      {err && <ErrorBox message={err} onRetry={reload} />}
      <div className="row g-3">
        <div className="col-lg-7">
          {loading && !data ? <Spinner /> : data && (
            <Table head={['Name', 'URL name', 'Products', 'Order', 'Shown', '']} empty={data.length === 0 ? 'No categories yet.' : ''}>
              {data.map((c) => <tr key={c.id}><td>{c.name}<div className="small text-muted">{c.description}</div></td><td className="small">{c.slug}</td><td>{c.productCount}</td><td>{c.sortOrder}</td><td>{c.active ? 'Yes' : <span className="text-muted">Hidden</span>}</td>
                <td className="text-nowrap">{write && <><button type="button" className="btn btn-sm btn-outline-dark me-1" onClick={() => edit(c)}>Edit</button><button type="button" className="btn btn-sm btn-outline-danger" onClick={() => del(c)}>Delete</button></>}</td></tr>)}
            </Table>
          )}
        </div>
        {write && (
          <div className="col-lg-5">
            <form className="stat" onSubmit={save} noValidate>
              <div className="fw-semibold mb-2">{editing ? 'Edit category' : 'Add category'}</div>
              {fe && <ErrorBox message={fe} />}
              <Field label="Name" id="cat-name"><input id="cat-name" className="form-control" maxLength={80} value={f.name} onChange={(e) => set('name', e.target.value)} /></Field>
              <Field label="URL name (optional)" id="cat-slug"><input id="cat-slug" className="form-control" maxLength={80} value={f.slug} onChange={(e) => set('slug', e.target.value)} placeholder="made from the name if empty" /></Field>
              <Field label="Description" id="cat-desc"><input id="cat-desc" className="form-control" maxLength={300} value={f.description} onChange={(e) => set('description', e.target.value)} /></Field>
              <Field label="Display order" id="cat-sort"><input id="cat-sort" type="number" min="0" max="10000" className="form-control" value={f.sortOrder} onChange={(e) => set('sortOrder', e.target.value)} /></Field>
              <div className="form-check mb-3"><input id="cat-active" type="checkbox" className="form-check-input" checked={f.active} onChange={(e) => set('active', e.target.checked)} /><label htmlFor="cat-active" className="form-check-label">Show on the storefront</label></div>
              <button className="btn btn-dark me-2" disabled={saving}>{saving ? 'Saving…' : 'Save'}</button>
              {editing && <button type="button" className="btn btn-outline-secondary" onClick={cancel}>Cancel</button>}
            </form>
          </div>
        )}
      </div>
    </div>
  );
}

// ------------------------------------------------------------------ website banners

const EMPTY_BANNER = { title: '', subtitle: '', imageUrl: '', linkUrl: '', sortOrder: 0, active: true };

export function Banners() {
  const toast = useToast();
  const { data, err, loading, reload } = useFetch('/admin/banners');
  const [f, setF] = useState(EMPTY_BANNER);
  const [editing, setEditing] = useState(null);
  const [saving, setSaving] = useState(false);
  const [fe, setFe] = useState('');
  const set = (k, v) => setF((x) => ({ ...x, [k]: v }));
  const cancel = () => { setEditing(null); setF(EMPTY_BANNER); setFe(''); };
  const edit = (b) => { setEditing(b.id); setF({ title: b.title, subtitle: b.subtitle || '', imageUrl: b.imageUrl || '', linkUrl: b.linkUrl || '', sortOrder: b.sortOrder, active: !!b.active }); setFe(''); };
  const upload = async (file) => {
    if (!file) return;
    const fd = new FormData();
    fd.append('file', file);
    try { const r = await api.post('/admin/banners/upload', fd); set('imageUrl', r.data.url); } catch (x) { setFe(apiError(x).message); }
  };
  const save = async (e) => {
    e.preventDefault();
    setSaving(true); setFe('');
    try {
      const body = { ...f, sortOrder: Number(f.sortOrder) || 0 };
      if (editing) await api.put(`/admin/banners/${editing}`, body); else await api.post('/admin/banners', body);
      toast.push('Banner saved'); cancel(); reload();
    } catch (x) { setFe(apiError(x).message); } finally { setSaving(false); }
  };
  const del = async (b) => {
    if (!window.confirm(`Delete the banner “${b.title}”?`)) return;
    try { await api.delete(`/admin/banners/${b.id}`); toast.push('Banner deleted'); reload(); } catch (x) { toast.push(apiError(x).message, 'danger'); }
  };
  return (
    <div>
      <h1 className="h4 mb-1">Website banners</h1>
      <p className="small text-muted">Active banners appear in a strip at the top of the storefront home page, in the order below. Upload a JPG, PNG or WebP image (up to 3 MB).</p>
      {err && <ErrorBox message={err} onRetry={reload} />}
      <div className="row g-3">
        <div className="col-lg-7">
          {loading && !data ? <Spinner /> : data && (
            <Table head={['Banner', 'Link', 'Order', 'Shown', '']} empty={data.length === 0 ? 'No banners yet.' : ''}>
              {data.map((b) => <tr key={b.id}><td><div className="d-flex gap-2 align-items-center">{b.imageUrl && <img src={b.imageUrl} alt="" width="56" height="36" style={{ objectFit: 'cover' }} />}<div><strong>{b.title}</strong><div className="small text-muted">{b.subtitle}</div></div></div></td>
                <td className="small text-break">{b.linkUrl || '—'}</td><td>{b.sortOrder}</td><td>{b.active ? 'Yes' : <span className="text-muted">Hidden</span>}</td>
                <td className="text-nowrap"><button type="button" className="btn btn-sm btn-outline-dark me-1" onClick={() => edit(b)}>Edit</button><button type="button" className="btn btn-sm btn-outline-danger" onClick={() => del(b)}>Delete</button></td></tr>)}
            </Table>
          )}
        </div>
        <div className="col-lg-5">
          <form className="stat" onSubmit={save} noValidate>
            <div className="fw-semibold mb-2">{editing ? 'Edit banner' : 'Add banner'}</div>
            {fe && <ErrorBox message={fe} />}
            <Field label="Title" id="b-title"><input id="b-title" className="form-control" maxLength={120} value={f.title} onChange={(e) => set('title', e.target.value)} /></Field>
            <Field label="Subtitle" id="b-sub"><input id="b-sub" className="form-control" maxLength={200} value={f.subtitle} onChange={(e) => set('subtitle', e.target.value)} /></Field>
            <Field label="Image" id="b-img"><input id="b-img" type="file" accept="image/jpeg,image/png,image/webp" className="form-control" onChange={(e) => upload(e.target.files[0])} />
              {f.imageUrl && <img src={f.imageUrl} alt="Banner preview" className="mt-2 img-fluid" style={{ maxHeight: 90 }} />}</Field>
            <Field label="Link (e.g. /shop?category=jeans)" id="b-link"><input id="b-link" className="form-control" maxLength={300} value={f.linkUrl} onChange={(e) => set('linkUrl', e.target.value)} /></Field>
            <Field label="Display order" id="b-sort"><input id="b-sort" type="number" min="0" max="10000" className="form-control" value={f.sortOrder} onChange={(e) => set('sortOrder', e.target.value)} /></Field>
            <div className="form-check mb-3"><input id="b-active" type="checkbox" className="form-check-input" checked={f.active} onChange={(e) => set('active', e.target.checked)} /><label htmlFor="b-active" className="form-check-label">Show on the storefront</label></div>
            <button className="btn btn-dark me-2" disabled={saving}>{saving ? 'Saving…' : 'Save'}</button>
            {editing && <button type="button" className="btn btn-outline-secondary" onClick={cancel}>Cancel</button>}
          </form>
        </div>
      </div>
    </div>
  );
}
