import { useEffect, useState } from 'react';
import { Link, useLocation, useParams, useSearchParams } from 'react-router-dom';
import { api, apiError, downloadInvoice, orderHeaders } from '../lib/api';
import { useSeo } from '../lib/seo';
import { useToast } from '../lib/AppContext';
import { ErrorBox, Spinner, StatusBadge } from '../components/Bits';
import { inr } from '../lib/money';
import { openCheckout } from '../lib/razorpay';
import { rememberOrder } from '../lib/recentOrders';

const REASONS = [['SIZE_ISSUE', 'Size issue'], ['COLOR_DIFFERENT', 'Colour different from photos'], ['DEFECT', 'Defect or damage'], ['WRONG_ITEM', 'Wrong item received'], ['OTHER', 'Other']];

function ExchangeForm({ order, token, onDone }) {
  const [itemId, setItemId] = useState(order.items[0]?.id);
  const item = order.items.find((i) => i.id === Number(itemId)) || order.items[0];
  const [variants, setVariants] = useState([]);
  const [newVariant, setNewVariant] = useState('');
  const [reason, setReason] = useState('SIZE_ISSUE');
  const [desc, setDesc] = useState('');
  const [images, setImages] = useState([]);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');

  useEffect(() => {
    if (!item?.slug) return;
    api.get(`/products/${item.slug}`).then((r) => setVariants(r.data.variants.filter((v) => v.stock > 0 && v.id !== item.variantId))).catch(() => setVariants([]));
    setNewVariant('');
  }, [item?.slug, item?.variantId]);

  async function upload(e) {
    const file = e.target.files?.[0];
    if (!file) return;
    const fd = new FormData(); fd.append('file', file);
    try { const r = await api.post('/uploads/exchange', fd); setImages((x) => [...x, r.data.url].slice(0, 5)); } catch (x) { setErr(apiError(x).message); }
    e.target.value = '';
  }

  async function submit(e) {
    e.preventDefault(); setErr(''); setBusy(true);
    try {
      await api.post(`/orders/${order.orderNumber}/exchange`, { orderItemId: item.id, newVariantId: Number(newVariant), reason, description: desc, imageUrls: images }, { headers: orderHeaders(token) });
      onDone();
    } catch (x) { setErr(apiError(x).message); } finally { setBusy(false); }
  }

  return (
    <form onSubmit={submit} className="bg-white border p-3 mt-3">
      <h3 className="h6">Request an exchange</h3>
      {err && <div className="alert alert-danger py-2">{err}</div>}
      <div className="mb-2"><label className="form-label" htmlFor="ex-item">Item</label>
        <select id="ex-item" className="form-select" value={itemId} onChange={(e) => setItemId(e.target.value)}>{order.items.map((i) => <option key={i.id} value={i.id}>{i.name} ({i.size}, {i.color})</option>)}</select></div>
      <div className="mb-2"><label className="form-label" htmlFor="ex-new">Exchange for</label>
        <select id="ex-new" className="form-select" required value={newVariant} onChange={(e) => setNewVariant(e.target.value)}>
          <option value="">Choose size / colour</option>{variants.map((v) => <option key={v.id} value={v.id}>{v.size} · {v.color}</option>)}</select>
        {variants.length === 0 && <div className="small text-muted mt-1">No other size or colour is in stock for this item right now.</div>}</div>
      <div className="mb-2"><label className="form-label" htmlFor="ex-reason">Reason</label>
        <select id="ex-reason" className="form-select" value={reason} onChange={(e) => setReason(e.target.value)}>{REASONS.map(([v, l]) => <option key={v} value={v}>{l}</option>)}</select></div>
      <div className="mb-2"><label className="form-label" htmlFor="ex-desc">Details (optional)</label><textarea id="ex-desc" className="form-control" rows={2} maxLength={480} value={desc} onChange={(e) => setDesc(e.target.value)} /></div>
      <div className="mb-3"><label className="form-label" htmlFor="ex-img">Photos (optional, up to 5)</label><input id="ex-img" className="form-control" type="file" accept="image/jpeg,image/png,image/webp" onChange={upload} />
        {images.length > 0 && <div className="small text-muted mt-1">{images.length} photo(s) attached</div>}</div>
      <button className="btn btn-tf" disabled={busy || !newVariant}>{busy ? 'Sending…' : 'Submit exchange request'}</button>
    </form>
  );
}

export default function OrderPage() {
  const { orderNumber } = useParams();
  const [sp] = useSearchParams();
  const loc = useLocation();
  const token = sp.get('token') || '';
  const toast = useToast();
  const [o, setO] = useState(null);
  const [err, setErr] = useState('');
  const [showEx, setShowEx] = useState(false);
  const [busy, setBusy] = useState(false);
  useSeo({ title: `Order ${orderNumber}`, noindex: true });

  const load = () => api.get(`/orders/${orderNumber}`, { headers: orderHeaders(token) })
    .then((r) => { setO(r.data); if (token) rememberOrder(orderNumber, token); }).catch((e) => setErr(apiError(e).message));
  useEffect(() => { load(); }, [orderNumber, token]); // eslint-disable-line

  async function payNow() {
    setBusy(true);
    try {
      const r = await api.post(`/orders/${orderNumber}/payment`, null, { headers: orderHeaders(token) });
      await openCheckout(r.data, {
        onPaid: async (resp) => { try { await api.post(`/orders/${orderNumber}/payment/verify`, resp, { headers: orderHeaders(token) }); toast.push('Payment confirmed'); load(); } catch (e) { toast.push(apiError(e).message, 'danger'); } },
        onFailed: () => { toast.push('Payment failed. Please try again.', 'danger'); api.post(`/orders/${orderNumber}/payment/failed`, {}, { headers: orderHeaders(token) }).catch(() => {}).finally(load); },
        onDismiss: () => setBusy(false),
      });
    } catch (e) { toast.push(e.message && !e.response ? e.message : apiError(e).message, 'danger'); }
    setBusy(false);
  }

  async function invoice() {
    try { await downloadInvoice(orderNumber, token); } catch (e) { toast.push(apiError(e).message, 'danger'); }
  }

  if (err) return <div className="container section"><ErrorBox message={err} /><p>If you placed this order as a guest, open the link from your confirmation email, or use <Link to="/track-order">Track order</Link>.</p></div>;
  if (!o) return <Spinner />;
  const ex = o.exchangeEligibility;
  const hasOpenExchange = o.exchanges?.some((x) => !['REJECTED', 'COMPLETED'].includes(x.status));

  return (
    <div className="container section" style={{ maxWidth: 980 }}>
      {loc.state?.justPlaced && (
        <div className="alert alert-success" role="status">
          <h1 className="h4 display-font">Thank you! Your order is placed.</h1>
          <div>Order number <strong>{o.orderNumber}</strong>. A confirmation email is on its way (if email is enabled). Bookmark this page, it is your tracking link.</div>
        </div>
      )}
      <div className="d-flex flex-wrap justify-content-between align-items-start gap-2 mb-3">
        <div><h1 className="h3 display-font mb-1">Order {o.orderNumber}</h1><div className="text-muted small">Placed {new Date(o.createdAt).toLocaleString('en-IN')}</div></div>
        <StatusBadge status={o.status} label={o.statusLabel} />
      </div>
      <div className="row g-4">
        <div className="col-lg-7">
          <div className="bg-white border p-3 mb-3">
            {o.items.map((i) => (
              <div className="cart-line" key={i.id}>
                {i.image && <img src={i.image} alt="" />}
                <div className="flex-grow-1"><div className="fw-semibold">{i.name}</div><div className="small text-muted">Size {i.size} · {i.color} · Qty {i.qty}</div></div>
                <div>{inr(i.lineTotal)}</div>
              </div>
            ))}
            <div className="pt-3">
              <div className="d-flex justify-content-between"><span>Subtotal</span><span>{inr(o.subtotal)}</span></div>
              {Number(o.discount) > 0 && <div className="d-flex justify-content-between text-success"><span>Discount {o.couponCode && `(${o.couponCode})`}</span><span>−{inr(o.discount)}</span></div>}
              <div className="d-flex justify-content-between"><span>Shipping</span><span>{Number(o.shippingCharge) === 0 ? 'Free' : inr(o.shippingCharge)}</span></div>
              <div className="d-flex justify-content-between fw-bold fs-5"><span>Total</span><span>{inr(o.total)}</span></div>
              <div className="small text-muted">{o.paymentMethod === 'COD' ? 'Cash on Delivery' : 'Online payment'} · {o.paymentStatus}</div>
            </div>
          </div>
          <div className="d-flex flex-wrap gap-2">
            {o.canPay && <button className="btn btn-tf" onClick={payNow} disabled={busy}>Pay now</button>}
            {o.invoiceAvailable && <button className="btn btn-tf-outline" onClick={invoice}>Download invoice</button>}
          </div>
          <div className="mt-4">
            <h2 className="h5">Exchange</h2>
            <p className="mb-2">{ex.message}</p>
            {o.exchanges?.map((x) => <div key={x.id} className="small border p-2 bg-white mb-2">Exchange #{x.id}: <strong>{x.status.replaceAll('_', ' ')}</strong>{x.adminNote ? ` · ${x.adminNote}` : ''}</div>)}
            {ex.eligible && !hasOpenExchange && !showEx && <button className="btn btn-tf-outline" onClick={() => setShowEx(true)}>Request exchange</button>}
            {showEx && ex.eligible && <ExchangeForm order={o} token={token} onDone={() => { setShowEx(false); toast.push('Exchange request sent'); load(); }} />}
          </div>
        </div>
        <div className="col-lg-5">
          <div className="bg-white border p-3 mb-3">
            <h2 className="h6 text-uppercase ls">Delivery address</h2>
            <div>{o.customerName}<br />{o.shipping.line1}{o.shipping.line2 ? `, ${o.shipping.line2}` : ''}<br />{o.shipping.city}, {o.shipping.state} {o.shipping.pincode}<br />{o.phone}</div>
          </div>
          {o.shipments?.length > 0 && (
            <div className="bg-white border p-3 mb-3">
              <h2 className="h6 text-uppercase ls">Shipment</h2>
              {o.shipments.map((s, k) => (
                <div key={k} className="small mb-2">{s.courier || 'Courier'} · AWB {s.awb}<br />{s.statusText}{s.estimatedDelivery ? ` · Expected ${s.estimatedDelivery}` : ''}
                  {s.trackingUrl && <><br /><a href={s.trackingUrl} target="_blank" rel="noopener noreferrer">Track shipment</a></>}</div>
              ))}
            </div>
          )}
          <div className="bg-white border p-3">
            <h2 className="h6 text-uppercase ls mb-3">Order timeline</h2>
            <ul className="timeline">
              {[...o.timeline].reverse().map((t, k) => <li key={k}><strong>{t.status.replaceAll('_', ' ')}</strong>{t.note && <div className="small text-muted">{t.note}</div>}<div className="small text-muted">{new Date(t.at).toLocaleString('en-IN')}</div></li>)}
            </ul>
          </div>
        </div>
      </div>
    </div>
  );
}
