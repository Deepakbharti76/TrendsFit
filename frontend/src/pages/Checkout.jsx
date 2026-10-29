import { useEffect, useRef, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { api, apiError } from '../lib/api';
import { useAuth, useCart, useConfig, useToast } from '../lib/AppContext';
import { useSeo } from '../lib/seo';
import { Empty, ErrorBox, Field, Spinner } from '../components/Bits';
import { STATES, digitsOnly, mobileError, mobileInput, pincodeError, usePincodeFill } from '../lib/forms';
import { useQuote } from './Cart';
import { inr } from '../lib/money';
import { idempotencyKey } from '../lib/idem';
import { openCheckout } from '../lib/razorpay';
import { rememberOrder } from '../lib/recentOrders';


export function validateCheckout(f) {
  const e = {};
  if ((f.name || '').trim().length < 2) e.name = 'Enter your full name.';
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test((f.email || '').trim())) e.email = 'Enter a valid email address.';
  const pe = mobileError((f.phone || '').trim());
  if (pe) e.phone = pe;
  if (!(f.line1 || '').trim()) e.line1 = 'Enter your address.';
  if (!(f.city || '').trim()) e.city = 'Enter your city.';
  if (!f.state) e.state = 'Choose your state.';
  if (pincodeError(f.pincode)) e.pincode = 'Enter a valid 6-digit pincode.';
  return e;
}

export default function Checkout() {
  const cart = useCart();
  const cfg = useConfig();
  const { user } = useAuth();
  const toast = useToast();
  const nav = useNavigate();
  const loc = useLocation();
  const [f, setF] = useState({ name: '', email: '', phone: '', line1: '', line2: '', city: '', state: '', pincode: '' });
  const [method, setMethod] = useState('');
  const [errors, setErrors] = useState({});
  const [busy, setBusy] = useState(false);
  const [formErr, setFormErr] = useState('');
  const [saved, setSaved] = useState([]);
  const [pending, setPending] = useState(null); // order created, payment not finished
  const coupon = loc.state?.coupon || '';
  const { quote, err, loading } = useQuote(cart.lines, coupon, f.email);
  useSeo({ title: 'Checkout', noindex: true });

  useEffect(() => { if (user) setF((x) => ({ ...x, name: x.name || user.fullName || '', email: x.email || user.email || '', phone: x.phone || mobileInput(user.phone || '') })); }, [user]);
  useEffect(() => { if (user) api.get('/me/addresses').then((r) => { setSaved(r.data); const d = r.data.find((a) => a.isDefault) || r.data[0]; if (d) applyAddr(d); }).catch(() => {}); }, [user]); // eslint-disable-line
  useEffect(() => { if (!method && quote) setMethod(quote.onlineAvailable ? 'ONLINE' : quote.codAvailable ? 'COD' : ''); }, [quote]); // eslint-disable-line

  const savedPin = useRef('');
  function applyAddr(a) { savedPin.current = a.pincode; setF((x) => ({ ...x, name: a.fullName, phone: mobileInput(a.phone), line1: a.line1, line2: a.line2 || '', city: a.city, state: a.state, pincode: a.pincode })); }
  const set = (k) => (e) => setF((x) => ({ ...x, [k]: e.target.value }));
  const lookup = usePincodeFill(f.pincode, ({ city, state }) => { if (savedPin.current === f.pincode) return; setF((x) => ({ ...x, city, state })); });

  if (cart.lines.length === 0 && !pending) return <div className="container section"><Empty title="Your cart is empty" to="/shop" cta="Continue shopping" /></div>;

  function finish(orderNumber, token) {
    rememberOrder(orderNumber, token);
    cart.clear();
    nav(`/order/${orderNumber}?token=${token}`, { replace: true, state: { justPlaced: true } });
  }

  async function verify(order, resp) {
    try {
      await api.post(`/orders/${order.orderNumber}/payment/verify`, resp, { headers: { 'X-Order-Token': order.accessToken } });
      finish(order.orderNumber, order.accessToken);
    } catch (e) {
      setFormErr(apiError(e).message);
      setPending(order);
      setBusy(false);
    }
  }

  async function pay(order, payment) {
    try {
      await openCheckout(payment, {
        onPaid: (r) => verify(order, r),
        onFailed: async (reason) => {
          setFormErr('Payment failed. You can try again, or choose Cash on Delivery from a new order.');
          setBusy(false);
          setPending(order);
          api.post(`/orders/${order.orderNumber}/payment/failed`, { reason }, { headers: { 'X-Order-Token': order.accessToken } }).catch(() => {});
        },
        onDismiss: () => { setBusy(false); setPending(order); setFormErr('Payment was not completed. Your order is saved; you can pay again.'); },
      });
    } catch (e) {
      setBusy(false);
      setPending(order);
      setFormErr(e.message || 'Could not open the payment window.');
    }
  }

  async function retryPayment() {
    setBusy(true); setFormErr('');
    try {
      const r = await api.post(`/orders/${pending.orderNumber}/payment`, null, { headers: { 'X-Order-Token': pending.accessToken } });
      await pay(pending, r.data);
    } catch (e) { setFormErr(apiError(e).message); setBusy(false); }
  }

  async function submit(e) {
    e.preventDefault();
    const v = validateCheckout(f);
    setErrors(v); setFormErr('');
    if (Object.keys(v).length) { toast.push('Please correct the highlighted fields.', 'warning'); return; }
    if (!method) { setFormErr('Choose a payment method.'); return; }
    setBusy(true);
    const body = {
      customer: { name: f.name.trim(), email: f.email.trim(), phone: f.phone },
      shipping: { line1: f.line1.trim(), line2: f.line2.trim() || null, city: f.city.trim(), state: f.state, pincode: f.pincode.trim() },
      items: cart.lines.map((l) => ({ variantId: l.variantId, qty: l.qty })),
      couponCode: coupon || null,
      paymentMethod: method,
    };
    try {
      const r = await api.post('/orders', body, { headers: { 'Idempotency-Key': idempotencyKey(body) } });
      const order = r.data;
      if (order.paymentMethod === 'COD') { finish(order.orderNumber, order.accessToken); return; }
      if (order.payment) await pay(order, order.payment);
      else { setPending(order); setBusy(false); }
    } catch (x) {
      const er = apiError(x);
      setErrors(Object.fromEntries(Object.entries(er.fields || {}).map(([k, m]) => [k.split('.').pop(), m])));
      setFormErr(er.message);
      setBusy(false);
    }
  }

  const input = (k, label, extra = {}, required = true) => {
    const { sanitize, ...rest } = extra;
    return (
    <Field label={label} error={errors[k]} id={`co-${k}`} required={required}>
      <input id={`co-${k}`} className={`form-control ${errors[k] ? 'is-invalid' : ''}`} value={f[k]} onChange={sanitize ? (e) => setF((x) => ({ ...x, [k]: sanitize(e.target.value) })) : set(k)} {...rest} aria-required={required || undefined} />
    </Field>
  );
  };

  return (
    <div className="container section">
      <h1 className="display-font h2 mb-4">Checkout</h1>
      {err && <ErrorBox message={err} />}
      {pending && (
        <div className="alert alert-warning d-flex flex-wrap justify-content-between align-items-center gap-2" role="alert">
          <span>Order <strong>{pending.orderNumber}</strong> is waiting for payment.</span>
          <span className="d-flex gap-2">
            <button className="btn btn-tf btn-sm" disabled={busy} onClick={retryPayment}>Pay now</button>
            <Link className="btn btn-tf-outline btn-sm" to={`/order/${pending.orderNumber}?token=${pending.accessToken}`}>View order</Link>
          </span>
        </div>
      )}
      {formErr && <div className="alert alert-danger" role="alert">{formErr}</div>}
      <form className="row g-4" onSubmit={submit} noValidate>
        <div className="col-lg-7">
          {saved.length > 0 && (
            <div className="mb-3"><label className="form-label">Saved addresses</label>
              <select className="form-select" onChange={(e) => { const a = saved.find((x) => String(x.id) === e.target.value); if (a) applyAddr(a); }} defaultValue="">
                <option value="" disabled>Choose a saved address</option>
                {saved.map((a) => <option key={a.id} value={a.id}>{a.label} · {a.line1}, {a.city} {a.pincode}</option>)}
              </select>
            </div>
          )}
          <h2 className="h5 mb-3">Contact</h2>
          <div className="row"><div className="col-md-6">{input('name', 'Full name', { autoComplete: 'name' })}</div><div className="col-md-6">{input('phone', 'Mobile number', { autoComplete: 'tel-national', inputMode: 'numeric', sanitize: mobileInput, placeholder: '10-digit mobile number' })}</div></div>
          {input('email', 'Email', { type: 'email', autoComplete: 'email' })}
          {!user && <div className="small text-muted mb-3">Checking out as a guest. <Link to="/login" state={{ from: '/checkout' }}>Log in</Link> to use saved addresses and see order history.</div>}
          <h2 className="h5 mb-3 mt-2">Delivery address</h2>
          {input('line1', 'Address (house, street)', { autoComplete: 'address-line1' })}
          {input('line2', 'Apartment, landmark (optional)', { autoComplete: 'address-line2' }, false)}
          <div className="row">
            <div className="col-md-4">{input('pincode', 'Pincode', { inputMode: 'numeric', sanitize: (v) => digitsOnly(v, 6), autoComplete: 'postal-code' })}{lookup.status === 'loading' && <div className="small text-muted mt-n2 mb-2" role="status">{lookup.message}</div>}{lookup.status === 'ok' && !errors.pincode && <div className="small text-success mt-n2 mb-2" role="status">✓ {lookup.message}</div>}{lookup.status === 'error' && !errors.pincode && <div className="small text-danger mt-n2 mb-2" role="alert">{lookup.message}</div>}</div>
            <div className="col-md-4">{input('city', 'City', { autoComplete: 'address-level2' })}</div>
            <div className="col-md-4"><Field label="State" error={errors.state} id="co-state" required><select id="co-state" className={`form-select ${errors.state ? 'is-invalid' : ''}`} value={f.state} onChange={set('state')}><option value="">Select</option>{STATES.map((s) => <option key={s}>{s}</option>)}</select></Field></div>
          </div>
        </div>
        <div className="col-lg-5">
          <div className="summary">
            <h2 className="h5 mb-3">Payment</h2>
            {loading && !quote && <Spinner />}
            {quote && (
              <>
                <label className={`pay-opt ${method === 'ONLINE' ? 'on' : ''} ${!quote.onlineAvailable ? 'off' : ''}`}>
                  <input type="radio" name="pm" disabled={!quote.onlineAvailable} checked={method === 'ONLINE'} onChange={() => setMethod('ONLINE')} />
                  <span><strong>Pay online</strong><br /><span className="small text-muted">{quote.onlineAvailable ? 'UPI, cards, netbanking, wallets via Razorpay' : 'Not available right now'}</span></span>
                </label>
                <label className={`pay-opt ${method === 'COD' ? 'on' : ''} ${!quote.codAvailable ? 'off' : ''}`}>
                  <input type="radio" name="pm" disabled={!quote.codAvailable} checked={method === 'COD'} onChange={() => setMethod('COD')} />
                  <span><strong>Cash on Delivery</strong><br /><span className="small text-muted">{quote.codAvailable ? 'Pay when your order arrives' : `Available up to ${inr(cfg.codMaxOrderValue)}`}</span></span>
                </label>
                <hr />
                {quote.items.map((i) => <div key={i.variantId} className="d-flex justify-content-between small mb-1"><span>{i.name} ({i.size}) × {i.qty}</span><span>{inr(i.lineTotal)}</span></div>)}
                <hr />
                <div className="d-flex justify-content-between"><span>Subtotal</span><span>{inr(quote.subtotal)}</span></div>
                {Number(quote.discount) > 0 && <div className="d-flex justify-content-between text-success"><span>Discount</span><span>−{inr(quote.discount)}</span></div>}
                <div className="d-flex justify-content-between"><span>Shipping</span><span>{Number(quote.shipping) === 0 ? 'Free' : inr(quote.shipping)}</span></div>
                <div className="d-flex justify-content-between fw-bold fs-5 mt-2 mb-3"><span>Total</span><span>{inr(quote.total)}</span></div>
              </>
            )}
            <button className="btn btn-tf w-100" disabled={busy || !quote || !!pending}>{busy ? 'Please wait…' : method === 'COD' ? 'Place order' : 'Pay & place order'}</button>
            <div className="small text-muted mt-2">By placing the order you agree to our <Link to="/terms">Terms</Link> and <Link to="/exchange-policy">Exchange Policy</Link>. Orders cannot be cancelled after placement; eligible items can be exchanged within {cfg.exchangeWindowDays} days of delivery.</div>
          </div>
        </div>
      </form>
    </div>
  );
}
