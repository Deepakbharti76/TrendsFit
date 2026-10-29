import { useEffect, useRef, useState } from 'react';
import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom';
import { api, apiError } from '../lib/api';
import { useAuth, useToast } from '../lib/AppContext';
import { useSeo } from '../lib/seo';
import { Empty, ErrorBox, Field, PasswordInput, Spinner, StatusBadge } from '../components/Bits';
import { STATES, digitsOnly, mobileError, mobileInput, pincodeError, usePincodeFill } from '../lib/forms';
import ProductCard from '../components/ProductCard';
import { inr } from '../lib/money';

export function AccountLayout() {
  const { user, logout } = useAuth();
  const nav = useNavigate();
  useSeo({ title: 'My account', noindex: true });
  return (
    <div className="container section">
      <h1 className="display-font h2 mb-1">My account</h1>
      <p className="text-muted">Hello, {user.fullName}</p>
      {!user.emailVerified && <VerifyBanner />}
      <div className="row g-4">
        <div className="col-md-3">
          <div className="list-group rounded-0">
            {[['/account', 'Profile', true], ['/account/orders', 'Orders'], ['/account/addresses', 'Addresses'], ['/account/wishlist', 'Wishlist']].map(([to, l, end]) => (
              <NavLink key={to} end={end} to={to} className={({ isActive }) => `list-group-item list-group-item-action ${isActive ? 'active' : ''}`}>{l}</NavLink>
            ))}
            {user.staff && <Link className="list-group-item list-group-item-action" to="/admin">Admin panel</Link>}
            <button className="list-group-item list-group-item-action text-danger" onClick={() => { logout(); nav('/'); }}>Log out</button>
          </div>
        </div>
        <div className="col-md-9"><Outlet /></div>
      </div>
    </div>
  );
}

function VerifyBanner() {
  const toast = useToast();
  return (
    <div className="alert alert-warning d-flex justify-content-between align-items-center flex-wrap gap-2">
      <span>Please verify your email address.</span>
      <button className="btn btn-sm btn-tf-outline" onClick={() => api.post('/auth/resend-verification').then(() => toast.push('Verification email sent')).catch((e) => toast.push(apiError(e).message, 'danger'))}>Resend email</button>
    </div>
  );
}

export function Profile() {
  const { user, setUser } = useAuth();
  const toast = useToast();
  const [f, setF] = useState({ fullName: user.fullName, phone: mobileInput(user.phone || '') });
  const [errors, setErrors] = useState({});
  const [pw, setPw] = useState({ currentPassword: '', newPassword: '' });
  const [pwErr, setPwErr] = useState('');
  async function save(e) {
    e.preventDefault(); setErrors({});
    if (f.phone && mobileError(f.phone)) { setErrors({ phone: mobileError(f.phone) }); return; }
    try { const r = await api.put('/me', f); setUser(r.data); toast.push('Profile saved'); } catch (x) { const er = apiError(x); setErrors(er.fields); toast.push(er.message, 'danger'); }
  }
  async function changePw(e) {
    e.preventDefault(); setPwErr('');
    try { await api.post('/me/password', pw); setPw({ currentPassword: '', newPassword: '' }); toast.push('Password changed'); } catch (x) { setPwErr(apiError(x).message); }
  }
  return (
    <div className="row g-4">
      <div className="col-lg-6">
        <form onSubmit={save} className="bg-white border p-3">
          <h2 className="h5">Profile</h2>
          <Field label="Full name" id="p-n" error={errors.fullName}><input id="p-n" className="form-control" value={f.fullName} onChange={(e) => setF({ ...f, fullName: e.target.value })} /></Field>
          <Field label="Email" id="p-e"><input id="p-e" className="form-control" value={user.email} disabled /></Field>
          <Field label="Mobile (optional)" id="p-p" error={errors.phone}><input id="p-p" className={`form-control ${errors.phone ? 'is-invalid' : ''}`} inputMode="numeric" value={f.phone} onChange={(e) => setF({ ...f, phone: mobileInput(e.target.value) })} /></Field>
          <button className="btn btn-tf">Save</button>
        </form>
      </div>
      <div className="col-lg-6">
        <form onSubmit={changePw} className="bg-white border p-3">
          <h2 className="h5">Change password</h2>
          {pwErr && <div className="alert alert-danger py-2">{pwErr}</div>}
          <Field label="Current password" id="cp-c"><PasswordInput id="cp-c" autoComplete="current-password" className="form-control" value={pw.currentPassword} onChange={(e) => setPw({ ...pw, currentPassword: e.target.value })} /></Field>
          <Field label="New password" id="cp-n"><PasswordInput id="cp-n" autoComplete="new-password" className="form-control" value={pw.newPassword} onChange={(e) => setPw({ ...pw, newPassword: e.target.value })} /></Field>
          <button className="btn btn-tf">Update password</button>
        </form>
      </div>
    </div>
  );
}

export function MyOrders() {
  const [list, setList] = useState(null);
  const [err, setErr] = useState('');
  useEffect(() => { api.get('/me/orders').then((r) => setList(r.data)).catch((e) => setErr(apiError(e).message)); }, []);
  if (err) return <ErrorBox message={err} />;
  if (!list) return <Spinner />;
  if (!list.length) return <Empty title="No orders yet" to="/shop" cta="Start shopping" />;
  return (
    <div>{list.map((o) => (
      <Link key={o.orderNumber} to={`/order/${o.orderNumber}?token=${o.accessToken}`} className="d-flex gap-3 align-items-center bg-white border p-3 mb-2 text-reset">
        {o.thumb ? <img src={o.thumb} alt="" width="56" style={{ aspectRatio: '4/5', objectFit: 'cover' }} /> : <div style={{ width: 56 }} />}
        <div className="flex-grow-1"><div className="fw-semibold">{o.orderNumber}</div><div className="small text-muted">{new Date(o.createdAt).toLocaleDateString('en-IN')} · {o.itemCount} item(s)</div></div>
        <div className="text-end"><div>{inr(o.total)}</div><StatusBadge status={o.status} label={o.statusLabel} /></div>
      </Link>
    ))}</div>
  );
}

const EMPTY_ADDR = { label: 'HOME', fullName: '', phone: '', line1: '', line2: '', city: '', state: '', pincode: '', isDefault: false };

export function Addresses() {
  const toast = useToast();
  const [list, setList] = useState(null);
  const [edit, setEdit] = useState(null);
  const [errors, setErrors] = useState({});
  const load = () => api.get('/me/addresses').then((r) => setList(r.data)).catch(() => setList([]));
  useEffect(() => { load(); }, []);
  const loadedPin = useRef('');
  const lookup = usePincodeFill(edit?.pincode, ({ city, state }) => { if (loadedPin.current === edit?.pincode) return; setEdit((x) => (x ? { ...x, city, state } : x)); });
  function startEdit(a) { loadedPin.current = a.pincode || ''; setEdit(a); }
  async function save(e) {
    e.preventDefault(); setErrors({});
    const v = {};
    if ((edit.fullName || '').trim().length < 2) v.fullName = 'Enter the receiver\'s name.';
    if (mobileError(edit.phone || '')) v.phone = mobileError(edit.phone || '');
    if (!(edit.line1 || '').trim()) v.line1 = 'Enter the address.';
    if (!(edit.city || '').trim()) v.city = 'Enter the city.';
    if (!edit.state) v.state = 'Choose the state.';
    if (pincodeError(edit.pincode)) v.pincode = 'Enter a valid 6-digit pincode.';
    if (Object.keys(v).length) { setErrors(v); toast.push('Please correct the highlighted fields.', 'warning'); return; }
    try {
      if (edit.id) await api.put(`/me/addresses/${edit.id}`, edit); else await api.post('/me/addresses', edit);
      setEdit(null); toast.push('Address saved'); load();
    } catch (x) { const er = apiError(x); setErrors(er.fields); toast.push(er.message, 'danger'); }
  }
  const set = (k) => (e) => setEdit({ ...edit, [k]: e.target.value });
  if (!list) return <Spinner />;
  return (
    <div>
      {!edit && <button className="btn btn-tf mb-3" onClick={() => startEdit({ ...EMPTY_ADDR })}>Add address</button>}
      {edit && (
        <form onSubmit={save} className="bg-white border p-3 mb-3">
          <div className="row">
            <div className="col-md-6"><Field label="Full name" id="a-fullName" error={errors.fullName} required><input id="a-fullName" className={`form-control ${errors.fullName ? 'is-invalid' : ''}`} autoComplete="name" value={edit.fullName || ''} onChange={set('fullName')} /></Field></div>
            <div className="col-md-6"><Field label="Mobile number" id="a-phone" error={errors.phone} required><input id="a-phone" className={`form-control ${errors.phone ? 'is-invalid' : ''}`} inputMode="numeric" placeholder="10-digit mobile number" value={edit.phone || ''} onChange={(e) => setEdit({ ...edit, phone: mobileInput(e.target.value) })} /></Field></div>
            <div className="col-md-6"><Field label="Address" id="a-line1" error={errors.line1} required><input id="a-line1" className={`form-control ${errors.line1 ? 'is-invalid' : ''}`} autoComplete="address-line1" value={edit.line1 || ''} onChange={set('line1')} /></Field></div>
            <div className="col-md-6"><Field label="Apartment / landmark (optional)" id="a-line2"><input id="a-line2" className="form-control" autoComplete="address-line2" value={edit.line2 || ''} onChange={set('line2')} /></Field></div>
            <div className="col-md-6"><Field label="Pincode" id="a-pincode" error={errors.pincode} required><input id="a-pincode" className={`form-control ${errors.pincode ? 'is-invalid' : ''}`} inputMode="numeric" autoComplete="postal-code" value={edit.pincode || ''} onChange={(e) => setEdit({ ...edit, pincode: digitsOnly(e.target.value, 6) })} />
              {lookup.status === 'loading' && <div className="small text-muted mt-1" role="status">{lookup.message}</div>}
              {lookup.status === 'ok' && !errors.pincode && <div className="small text-success mt-1" role="status">✓ {lookup.message}</div>}
              {lookup.status === 'error' && !errors.pincode && <div className="small text-danger mt-1" role="alert">{lookup.message}</div>}</Field></div>
            <div className="col-md-6"><Field label="City" id="a-city" error={errors.city} required><input id="a-city" className={`form-control ${errors.city ? 'is-invalid' : ''}`} autoComplete="address-level2" value={edit.city || ''} onChange={set('city')} /></Field></div>
            <div className="col-md-6"><Field label="State" id="a-state" error={errors.state} required><select id="a-state" className={`form-select ${errors.state ? 'is-invalid' : ''}`} value={edit.state || ''} onChange={set('state')}><option value="">Select</option>{STATES.map((x) => <option key={x}>{x}</option>)}{edit.state && !STATES.includes(edit.state) && <option>{edit.state}</option>}</select></Field></div>
            <div className="col-md-6"><Field label="Label" id="a-l"><select id="a-l" className="form-select" value={edit.label} onChange={set('label')}><option>HOME</option><option>WORK</option><option>OTHER</option></select></Field></div>
          </div>
          <div className="form-check mb-3"><input id="a-d" type="checkbox" className="form-check-input" checked={!!edit.isDefault} onChange={(e) => setEdit({ ...edit, isDefault: e.target.checked })} /><label htmlFor="a-d" className="form-check-label">Make default</label></div>
          <button className="btn btn-tf me-2">Save</button><button type="button" className="btn btn-tf-outline" onClick={() => setEdit(null)}>Cancel</button>
        </form>
      )}
      {list.length === 0 && !edit && <p className="text-muted">No saved addresses.</p>}
      <div className="row g-3">
        {list.map((a) => (
          <div className="col-md-6" key={a.id}><div className="bg-white border p-3 h-100">
            <div className="d-flex justify-content-between"><strong>{a.label}</strong>{a.isDefault && <span className="badge text-bg-dark">Default</span>}</div>
            <div className="small mt-1">{a.fullName}<br />{a.line1}{a.line2 ? `, ${a.line2}` : ''}<br />{a.city}, {a.state} {a.pincode}<br />{a.phone}</div>
            <div className="mt-2"><button className="btn btn-link btn-sm p-0 me-3" onClick={() => startEdit({ ...a, phone: mobileInput(a.phone || '') })}>Edit</button>
              <button className="btn btn-link btn-sm text-danger p-0" onClick={() => api.delete(`/me/addresses/${a.id}`).then(load)}>Delete</button></div>
          </div></div>
        ))}
      </div>
    </div>
  );
}

export function Wishlist() {
  const { wishlist } = useAuth();
  const [list, setList] = useState(null);
  useEffect(() => { api.get('/me/wishlist').then((r) => setList(r.data)).catch(() => setList([])); }, [wishlist.length]);
  if (!list) return <Spinner />;
  if (!list.length) return <Empty title="Your wishlist is empty" text="Tap the heart on any product to save it." to="/shop" cta="Browse products" />;
  return <div className="row g-3 row-cols-2 row-cols-md-3">{list.map((p) => <div className="col" key={p.id}><ProductCard p={p} /></div>)}</div>;
}
