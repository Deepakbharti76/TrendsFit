import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { api, apiError } from '../lib/api';
import { useAuth, useToast } from '../lib/AppContext';
import { ErrorBox, Field, PasswordInput, Pagination, Spinner } from '../components/Bits';
import { Table, can, fmtDate, useFetch } from './common';
import { inr } from '../lib/money';

export function Inventory() {
  const { user } = useAuth();
  const toast = useToast();
  const [q, setQ] = useState('');
  const [sp] = useSearchParams();
  const [low, setLow] = useState(sp.get('low') === '1');
  const [page, setPage] = useState(0);
  const [hist, setHist] = useState(null);
  const { data, err, loading, reload } = useFetch('/admin/inventory', { q: q || undefined, lowOnly: low, page, pageSize: 30 });
  const write = can(user, 'PERM_INVENTORY_WRITE');
  async function adjust(v) {
    const val = window.prompt(`New stock for ${v.product} ${v.size}/${v.color} (currently ${v.stock})`, String(v.stock));
    if (val == null) return;
    const n = Number(val);
    if (!Number.isInteger(n) || n < 0) { toast.push('Enter a whole number, 0 or more.', 'warning'); return; }
    const reason = window.prompt('Reason for this change (required)', 'Stock count');
    if (!reason) return;
    try { await api.post(`/admin/inventory/${v.id}/adjust`, { setTo: n, reason }); toast.push('Stock updated'); reload(); } catch (e) { toast.push(apiError(e).message, 'danger'); }
  }
  async function showHist(v) { try { setHist({ v, rows: (await api.get(`/admin/inventory/${v.id}/history`)).data }); } catch (e) { toast.push(apiError(e).message, 'danger'); } }
  return (
    <div>
      <h1 className="h4 mb-3">Inventory</h1>
      <div className="d-flex flex-wrap gap-3 mb-3 align-items-center">
        <input className="form-control" style={{ maxWidth: 260 }} placeholder="Search product or SKU" value={q} onChange={(e) => { setQ(e.target.value); setPage(0); }} aria-label="Search inventory" />
        <div className="form-check"><input id="low" className="form-check-input" type="checkbox" checked={low} onChange={(e) => { setLow(e.target.checked); setPage(0); }} /><label htmlFor="low" className="form-check-label">Low stock only</label></div>
      </div>
      {err && <ErrorBox message={err} onRetry={reload} />}
      {loading && !data ? <Spinner /> : data && (
        <Table head={['Product', 'SKU', 'Size', 'Colour', 'Stock', '']} empty={data.items.length === 0 ? 'Nothing found.' : ''}>
          {data.items.map((v) => (
            <tr key={v.id}><td>{v.product}</td><td className="small">{v.sku}</td><td>{v.size}</td><td>{v.color}</td><td className={v.stock <= 5 ? 'text-danger fw-bold' : ''}>{v.stock}</td>
              <td className="text-nowrap">{write && <button className="btn btn-sm btn-outline-dark me-1" onClick={() => adjust(v)}>Adjust</button>}<button className="btn btn-sm btn-outline-secondary" onClick={() => showHist(v)}>History</button></td></tr>
          ))}
        </Table>
      )}
      {data && <Pagination page={data.page} totalPages={Math.ceil(data.total / data.pageSize)} onChange={setPage} />}
      {hist && (
        <div className="modal d-block" style={{ background: 'rgba(0,0,0,.5)' }} role="dialog" aria-modal="true"><div className="modal-dialog modal-lg modal-dialog-scrollable"><div className="modal-content">
          <div className="modal-header"><h2 className="modal-title h5">Stock history: {hist.v.product} {hist.v.size}/{hist.v.color}</h2><button className="btn-close" aria-label="Close" onClick={() => setHist(null)} /></div>
          <div className="modal-body p-0"><Table head={['When', 'Change', 'Reason', 'Ref', 'By']} empty={hist.rows.length === 0 ? 'No movements recorded.' : ''}>
            {hist.rows.map((r) => <tr key={r.id}><td className="small">{fmtDate(r.at)}</td><td className={r.delta < 0 ? 'text-danger' : 'text-success'}>{r.delta > 0 ? `+${r.delta}` : r.delta}</td><td>{r.reason}</td><td className="small">{r.refType} {r.refId}</td><td className="small">{r.actor}</td></tr>)}</Table></div>
        </div></div></div>
      )}
    </div>
  );
}

export function Exchanges() {
  const { user } = useAuth();
  const toast = useToast();
  const [status, setStatus] = useState('');
  const { data, err, loading, reload } = useFetch('/admin/exchanges', { status: status || undefined });
  const NEXT = { PENDING: ['APPROVED', 'REJECTED', 'INFO_REQUESTED'], INFO_REQUESTED: ['PENDING', 'APPROVED', 'REJECTED'], APPROVED: ['PICKUP_SCHEDULED', 'RECEIVED'], PICKUP_SCHEDULED: ['RECEIVED'], RECEIVED: ['EXCHANGE_SHIPPED'], EXCHANGE_SHIPPED: ['COMPLETED'] };
  async function go(x, to) {
    const note = window.prompt(`Move exchange #${x.id} to ${to.replaceAll('_', ' ')}. Note for the customer (optional):`, '');
    if (note === null) return;
    try { await api.patch(`/admin/exchanges/${x.id}`, { status: to, note }); toast.push('Exchange updated'); reload(); } catch (e) { toast.push(apiError(e).message, 'danger'); }
  }
  return (
    <div>
      <h1 className="h4 mb-3">Exchange requests</h1>
      <select className="form-select mb-3" style={{ maxWidth: 220 }} value={status} onChange={(e) => setStatus(e.target.value)} aria-label="Status"><option value="">All</option>{Object.keys(NEXT).concat(['REJECTED', 'COMPLETED']).map((s) => <option key={s}>{s}</option>)}</select>
      {err && <ErrorBox message={err} onRetry={reload} />}
      {loading && !data ? <Spinner /> : data && (
        <Table head={['#', 'Order', 'Item', 'Swap', 'Reason', 'Status', 'Actions']} empty={data.length === 0 ? 'No exchange requests.' : ''}>
          {data.map((x) => (
            <tr key={x.id}><td>{x.id}</td><td>{x.orderNumber}<div className="small text-muted">{x.customerName}</div></td><td>{x.itemName}</td><td className="small">{x.oldSize}/{x.oldColor} → {x.newSize}/{x.newColor}</td>
              <td className="small">{x.reason.replaceAll('_', ' ')}{x.description && <div className="text-muted">{x.description}</div>}</td><td><span className="badge text-bg-secondary">{x.status.replaceAll('_', ' ')}</span></td>
              <td>{can(user, 'PERM_EXCHANGE_DECIDE') && (NEXT[x.status] || []).map((t) => <button key={t} className="btn btn-sm btn-outline-dark me-1 mb-1" onClick={() => go(x, t)}>{t.replaceAll('_', ' ')}</button>)}</td></tr>
          ))}
        </Table>
      )}
    </div>
  );
}

export function Customers() {
  const [q, setQ] = useState('');
  const [page, setPage] = useState(0);
  const { data, err, loading, reload } = useFetch('/admin/customers', { q: q || undefined, page, pageSize: 20 });
  return (
    <div>
      <h1 className="h4 mb-3">Customers {data && <small className="text-muted">({data.total})</small>}</h1>
      <input className="form-control mb-3" style={{ maxWidth: 300 }} placeholder="Search name, email, phone" value={q} onChange={(e) => { setQ(e.target.value); setPage(0); }} aria-label="Search customers" />
      {err && <ErrorBox message={err} onRetry={reload} />}
      {loading && !data ? <Spinner /> : data && (
        <Table head={['Name', 'Email', 'Phone', 'Verified', 'Orders', 'Spend', 'Joined']} empty={data.items.length === 0 ? 'No customers yet.' : ''}>
          {data.items.map((c) => <tr key={c.id}><td>{c.fullName}</td><td>{c.email}</td><td>{c.phone}</td><td>{c.emailVerified ? 'Yes' : 'No'}</td><td>{c.orderCount}</td><td>{inr(c.spend)}</td><td className="small">{fmtDate(c.createdAt)}</td></tr>)}
        </Table>
      )}
      {data && <Pagination page={data.page} totalPages={Math.ceil(data.total / data.pageSize)} onChange={setPage} />}
    </div>
  );
}

const BLANK_C = { code: '', type: 'PERCENT', value: '', minOrder: '', maxDiscount: '', expiresAt: '', maxUses: '', perUserLimit: '', active: true };
export function Coupons() {
  const toast = useToast();
  const { data, err, loading, reload } = useFetch('/admin/coupons');
  const [f, setF] = useState(null);
  const [errors, setErrors] = useState({});
  const n = (v) => (v === '' || v == null ? null : Number(v));
  async function save(e) {
    e.preventDefault(); setErrors({});
    const body = { ...f, value: n(f.value), minOrder: n(f.minOrder), maxDiscount: n(f.maxDiscount), maxUses: n(f.maxUses), perUserLimit: n(f.perUserLimit), expiresAt: f.expiresAt ? `${f.expiresAt.slice(0, 16)}:00` : null, startsAt: null };
    try { if (f.id) await api.put(`/admin/coupons/${f.id}`, body); else await api.post('/admin/coupons', body); toast.push('Coupon saved'); setF(null); reload(); } catch (x) { const er = apiError(x); setErrors(er.fields); toast.push(er.message, 'danger'); }
  }
  const set = (k) => (e) => setF({ ...f, [k]: e.target.type === 'checkbox' ? e.target.checked : e.target.value });
  return (
    <div>
      <div className="d-flex justify-content-between mb-3"><h1 className="h4 mb-0">Coupons</h1>{!f && <button className="btn btn-dark btn-sm" onClick={() => setF({ ...BLANK_C })}>+ New coupon</button>}</div>
      {f && (
        <form className="stat mb-3" onSubmit={save}><div className="row">
          <div className="col-md-3"><Field label="Code" id="c-code" error={errors.code}><input id="c-code" className="form-control" value={f.code} onChange={set('code')} /></Field></div>
          <div className="col-md-3"><Field label="Type" id="c-type"><select id="c-type" className="form-select" value={f.type} onChange={set('type')}><option value="PERCENT">Percent</option><option value="FIXED">Fixed ₹</option></select></Field></div>
          <div className="col-md-3"><Field label="Value" id="c-val" error={errors.value}><input id="c-val" type="number" className="form-control" value={f.value ?? ''} onChange={set('value')} /></Field></div>
          <div className="col-md-3"><Field label="Min order ₹" id="c-min"><input id="c-min" type="number" className="form-control" value={f.minOrder ?? ''} onChange={set('minOrder')} /></Field></div>
          <div className="col-md-3"><Field label="Max discount ₹" id="c-max"><input id="c-max" type="number" className="form-control" value={f.maxDiscount ?? ''} onChange={set('maxDiscount')} /></Field></div>
          <div className="col-md-3"><Field label="Expires" id="c-exp" error={errors.expiresAt}><input id="c-exp" type="datetime-local" className="form-control" value={(f.expiresAt || '').slice(0, 16)} onChange={set('expiresAt')} /></Field></div>
          <div className="col-md-3"><Field label="Total uses limit" id="c-mu"><input id="c-mu" type="number" className="form-control" value={f.maxUses ?? ''} onChange={set('maxUses')} /></Field></div>
          <div className="col-md-3"><Field label="Per customer limit" id="c-pu"><input id="c-pu" type="number" className="form-control" value={f.perUserLimit ?? ''} onChange={set('perUserLimit')} /></Field></div>
        </div>
          <div className="form-check mb-3"><input id="c-act" type="checkbox" className="form-check-input" checked={!!f.active} onChange={set('active')} /><label htmlFor="c-act" className="form-check-label">Active</label></div>
          <button className="btn btn-dark btn-sm me-2">Save</button><button type="button" className="btn btn-outline-secondary btn-sm" onClick={() => setF(null)}>Cancel</button></form>
      )}
      {err && <ErrorBox message={err} onRetry={reload} />}
      {loading && !data ? <Spinner /> : data && (
        <Table head={['Code', 'Discount', 'Min order', 'Used', 'Expires', 'Active', '']} empty={data.length === 0 ? 'No coupons yet.' : ''}>
          {data.map((c) => <tr key={c.id}><td><strong>{c.code}</strong></td><td>{c.type === 'PERCENT' ? `${c.value}%${c.maxDiscount ? ` (max ${inr(c.maxDiscount)})` : ''}` : inr(c.value)}</td><td>{inr(c.minOrder)}</td><td>{c.usedCount}{c.maxUses ? ` / ${c.maxUses}` : ''}</td><td className="small">{c.expiresAt ? fmtDate(c.expiresAt) : 'Never'}</td><td>{c.active ? 'Yes' : 'No'}</td>
            <td className="text-nowrap"><button className="btn btn-sm btn-outline-dark me-1" onClick={() => setF({ ...c })}>Edit</button><button className="btn btn-sm btn-outline-danger" onClick={() => window.confirm('Delete (or disable if already used)?') && api.delete(`/admin/coupons/${c.id}`).then(reload).catch((e) => toast.push(apiError(e).message, 'danger'))}>Delete</button></td></tr>)}
        </Table>
      )}
    </div>
  );
}

export function Reviews() {
  const toast = useToast();
  const [status, setStatus] = useState('PENDING');
  const { data, err, loading, reload } = useFetch('/admin/reviews', { status });
  const set = (r, s) => api.patch(`/admin/reviews/${r.id}`, { status: s }).then(() => { toast.push(`Review ${s.toLowerCase()}`); reload(); }).catch((e) => toast.push(apiError(e).message, 'danger'));
  return (
    <div>
      <h1 className="h4 mb-3">Review moderation</h1>
      <select className="form-select mb-3" style={{ maxWidth: 200 }} value={status} onChange={(e) => setStatus(e.target.value)} aria-label="Status"><option>PENDING</option><option>APPROVED</option><option>REJECTED</option></select>
      {err && <ErrorBox message={err} onRetry={reload} />}
      {loading && !data ? <Spinner /> : data && (data.length === 0 ? <div className="text-muted">Nothing here.</div> : data.map((r) => (
        <div className="stat mb-2" key={r.id}><div className="d-flex justify-content-between"><strong>{'★'.repeat(r.rating)} {r.title}</strong><span className="small text-muted">{r.product} · {r.author}{r.verifiedPurchase ? ' · verified' : ''}</span></div>
          <p className="mb-2">{r.body}</p><button className="btn btn-sm btn-success me-1" onClick={() => set(r, 'APPROVED')}>Approve</button><button className="btn btn-sm btn-outline-danger" onClick={() => set(r, 'REJECTED')}>Reject</button></div>
      )))}
    </div>
  );
}

export function Newsletter() {
  const { data, err, loading } = useFetch('/admin/newsletter');
  return (
    <div><h1 className="h4 mb-3">Newsletter subscribers {data && <small className="text-muted">({data.length})</small>}</h1>
      {err && <ErrorBox message={err} />}{loading ? <Spinner /> : data && <Table head={['Email', 'Subscribed']} empty={data.length === 0 ? 'No subscribers yet.' : ''}>{data.map((s) => <tr key={s.email}><td>{s.email}</td><td className="small">{fmtDate(s.createdAt)}</td></tr>)}</Table>}</div>
  );
}

const ROLES = ['SUPER_ADMIN', 'ADMIN', 'PRODUCT_MANAGER', 'ORDER_MANAGER', 'SUPPORT_MANAGER', 'CUSTOMER'];
export function Staff() {
  const { user } = useAuth();
  const toast = useToast();
  const { data, err, loading, reload } = useFetch('/admin/staff');
  const [f, setF] = useState({ fullName: '', email: '', password: '', role: 'SUPPORT_MANAGER' });
  async function add(e) { e.preventDefault(); try { await api.post('/admin/staff', f); toast.push('Staff user created'); setF({ fullName: '', email: '', password: '', role: 'SUPPORT_MANAGER' }); reload(); } catch (x) { toast.push(apiError(x).message, 'danger'); } }
  const upd = (u, body) => api.patch(`/admin/staff/${u.id}`, body).then(() => { toast.push('Updated'); reload(); }).catch((e) => toast.push(apiError(e).message, 'danger'));
  return (
    <div>
      <h1 className="h4 mb-3">Staff & roles</h1>
      <form className="stat mb-3" onSubmit={add}><div className="row g-2">
        <div className="col-md-3"><input className="form-control" placeholder="Full name" required value={f.fullName} onChange={(e) => setF({ ...f, fullName: e.target.value })} aria-label="Full name" /></div>
        <div className="col-md-3"><input className="form-control" type="email" placeholder="Email" required value={f.email} onChange={(e) => setF({ ...f, email: e.target.value })} aria-label="Email" /></div>
        <div className="col-md-3"><PasswordInput className="form-control" placeholder="Temporary password" required autoComplete="new-password" value={f.password} onChange={(e) => setF({ ...f, password: e.target.value })} aria-label="Password" /></div>
        <div className="col-md-2"><select className="form-select" value={f.role} onChange={(e) => setF({ ...f, role: e.target.value })} aria-label="Role">{ROLES.filter((r) => r !== 'CUSTOMER').map((r) => <option key={r}>{r}</option>)}</select></div>
        <div className="col-md-1"><button className="btn btn-dark w-100">Add</button></div></div></form>
      {err && <ErrorBox message={err} onRetry={reload} />}
      {loading && !data ? <Spinner /> : data && (
        <Table head={['Name', 'Email', 'Role', 'Active']}>
          {data.map((u) => <tr key={u.id}><td>{u.fullName}</td><td>{u.email}</td>
            <td><select className="form-select form-select-sm" disabled={u.email === user.email} value={u.role} onChange={(e) => upd(u, { role: e.target.value })} aria-label={`Role of ${u.email}`}>{ROLES.map((r) => <option key={r}>{r}</option>)}</select></td>
            <td><input type="checkbox" disabled={u.email === user.email} checked={!!u.enabled} onChange={(e) => upd(u, { enabled: e.target.checked })} aria-label={`Enabled ${u.email}`} /></td></tr>)}
        </Table>
      )}
    </div>
  );
}

export function Audit() {
  const { data, err, loading } = useFetch('/admin/audit', { limit: 200 });
  return (
    <div><h1 className="h4 mb-3">Audit log</h1>{err && <ErrorBox message={err} />}{loading ? <Spinner /> : data && (
      <Table head={['When', 'Who', 'Action', 'Entity', 'Detail']} empty={data.length === 0 ? 'No activity yet.' : ''}>
        {data.map((a) => <tr key={a.id}><td className="small text-nowrap">{fmtDate(a.created_at)}</td><td className="small">{a.actor_email}</td><td>{a.action}</td><td className="small">{a.entity} {a.entity_id}</td><td className="small">{a.detail}</td></tr>)}
      </Table>)}</div>
  );
}
