import { useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { api, apiError, downloadInvoice } from '../lib/api';
import { useAuth, useToast } from '../lib/AppContext';
import { ErrorBox, Pagination, Spinner, StatusBadge } from '../components/Bits';
import { Table, can, fmtDate, useFetch } from './common';
import { inr } from '../lib/money';

const STATUSES = ['PENDING_PAYMENT', 'PAYMENT_FAILED', 'PAID', 'PROCESSING', 'PACKED', 'SHIPPED', 'OUT_FOR_DELIVERY', 'DELIVERED', 'EXCHANGE_REQUESTED', 'EXCHANGE_APPROVED', 'EXCHANGE_REJECTED', 'EXCHANGE_COMPLETED'];

export function AdminOrders() {
  const [sp] = useSearchParams();
  const [status, setStatus] = useState(STATUSES.includes(sp.get('status')) ? sp.get('status') : '');
  const [method, setMethod] = useState(['ONLINE', 'COD'].includes(sp.get('paymentMethod')) ? sp.get('paymentMethod') : '');
  const [payStatus, setPayStatus] = useState(sp.get('paymentStatus') || '');
  const [q, setQ] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [page, setPage] = useState(0);
  const { data, err, loading, reload } = useFetch('/admin/orders', { status: status || undefined, paymentMethod: method || undefined, paymentStatus: payStatus || undefined, q: q || undefined, from: from || undefined, to: to || undefined, page, pageSize: 20 });
  return (
    <div>
      <h1 className="h4 mb-3">Orders {data && <small className="text-muted">({data.total})</small>}</h1>
      <div className="d-flex flex-wrap gap-2 mb-3">
        <input className="form-control" style={{ maxWidth: 240 }} placeholder="Order no., name, email, phone" value={q} onChange={(e) => { setQ(e.target.value); setPage(0); }} aria-label="Search orders" />
        <select className="form-select" style={{ maxWidth: 200 }} value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }} aria-label="Status"><option value="">All statuses</option>{STATUSES.map((s) => <option key={s} value={s}>{s.replaceAll('_', ' ')}</option>)}</select>
        <select className="form-select" style={{ maxWidth: 160 }} value={method} onChange={(e) => { setMethod(e.target.value); setPage(0); }} aria-label="Payment method"><option value="">Any method</option><option value="ONLINE">Online</option><option value="COD">COD</option></select>
        <select className="form-select" style={{ maxWidth: 170 }} value={payStatus} onChange={(e) => { setPayStatus(e.target.value); setPage(0); }} aria-label="Payment status"><option value="">Any payment status</option>{['PENDING', 'PAID', 'COD', 'FAILED', 'EXPIRED', 'REFUNDED'].map((x) => <option key={x} value={x}>{x}</option>)}</select>
        <input type="date" className="form-control" style={{ maxWidth: 160 }} value={from} onChange={(e) => { setFrom(e.target.value); setPage(0); }} aria-label="From date" />
        <input type="date" className="form-control" style={{ maxWidth: 160 }} value={to} onChange={(e) => { setTo(e.target.value); setPage(0); }} aria-label="To date" />
      </div>
      {err && <ErrorBox message={err} onRetry={reload} />}
      {loading && !data ? <Spinner /> : data && (
        <Table head={['Order', 'Date', 'Customer', 'Payment', 'Total', 'Status']} empty={data.items.length === 0 ? 'No orders found.' : ''}>
          {data.items.map((o) => (
            <tr key={o.orderNumber}>
              <td><Link to={`/admin/orders/${o.orderNumber}`}>{o.orderNumber}</Link></td><td className="small">{fmtDate(o.createdAt)}</td>
              <td>{o.customerName}<div className="small text-muted">{o.email}</div></td><td className="small">{o.paymentMethod} · {o.paymentStatus}</td><td>{inr(o.total)}</td>
              <td><StatusBadge status={o.status} label={o.status.replaceAll('_', ' ')} /></td>
            </tr>
          ))}
        </Table>
      )}
      {data && <Pagination page={data.page} totalPages={data.totalPages} onChange={setPage} />}
    </div>
  );
}

export function AdminOrderDetail() {
  const { orderNumber } = useParams();
  const { user } = useAuth();
  const toast = useToast();
  const { data: o, err, reload } = useFetch(`/admin/orders/${orderNumber}`);
  const [next, setNext] = useState('');
  const [note, setNote] = useState('');
  const [busy, setBusy] = useState(false);
  if (err) return <ErrorBox message={err} onRetry={reload} />;
  if (!o) return <Spinner />;
  // One request at a time: repeated clicks while a call is running are ignored, so the same error can't stack up.
  const act = (fn, ok) => {
    if (busy) return Promise.resolve();
    setBusy(true);
    return fn().then(() => { toast.push(ok); reload(); }).catch((e) => toast.push(apiError(e).message, 'danger')).finally(() => setBusy(false));
  };

  return (
    <div>
      <div className="d-flex justify-content-between align-items-start flex-wrap gap-2 mb-3">
        <h1 className="h4 mb-0">Order {o.orderNumber} <StatusBadge status={o.status} label={o.statusLabel} /></h1>
        <div className="d-flex gap-2 align-items-center">
          <button type="button" className="btn btn-sm btn-outline-dark" onClick={() => downloadInvoice(o.orderNumber).catch((e) => toast.push(apiError(e).message || 'Invoice is not available yet', 'danger'))}>Download invoice</button>
          <Link to="/admin/orders">← Orders</Link>
        </div>
      </div>
      <div className="row g-3">
        <div className="col-lg-7">
          <div className="stat mb-3">
            {o.items.map((i) => <div key={i.id} className="d-flex justify-content-between small py-1"><span>{i.name} · {i.size} · {i.color} × {i.qty}</span><span>{inr(i.lineTotal)}</span></div>)}
            <hr className="my-2" />
            <div className="d-flex justify-content-between small"><span>Subtotal</span><span>{inr(o.subtotal)}</span></div>
            {Number(o.discount) > 0 && <div className="d-flex justify-content-between small"><span>Discount {o.couponCode}</span><span>−{inr(o.discount)}</span></div>}
            <div className="d-flex justify-content-between small"><span>Shipping</span><span>{inr(o.shippingCharge)}</span></div>
            <div className="d-flex justify-content-between fw-bold"><span>Total</span><span>{inr(o.total)}</span></div>
          </div>
          <div className="stat mb-3"><div className="fw-semibold mb-2">Payments</div>
            {o.payments.length === 0 ? <div className="small text-muted">{o.paymentMethod === 'COD' ? 'Cash on Delivery: collected by the courier.' : 'No payment attempts yet.'}</div> : o.payments.map((p) => <div key={p.id} className="small py-1">{p.providerOrderId} · {p.providerPaymentId || 'no payment id'} · <strong>{p.status}</strong> · {inr(p.amount)}</div>)}
            {can(user, 'PERM_ORDER_WRITE') && o.payments.some((p) => p.status === 'PAID') && <button className="btn btn-sm btn-outline-danger mt-2" onClick={() => window.confirm('Request a full refund from Razorpay?') && act(() => api.post(`/admin/orders/${o.orderNumber}/refund`), 'Refund requested')}>Refund full amount</button>}
          </div>
          <div className="stat"><div className="fw-semibold mb-2">Timeline</div>
            <ul className="timeline">{[...o.timeline].reverse().map((t, k) => <li key={k}><strong>{t.status.replaceAll('_', ' ')}</strong> <span className="small text-muted">{fmtDate(t.at)}</span>{t.note && <div className="small text-muted">{t.note}</div>}</li>)}</ul></div>
        </div>
        <div className="col-lg-5">
          <div className="stat mb-3 small"><div className="fw-semibold mb-1">Customer</div>{o.customerName}<br />{o.email}<br />{o.phone}<hr className="my-2" />{o.shipping.line1}{o.shipping.line2 ? `, ${o.shipping.line2}` : ''}<br />{o.shipping.city}, {o.shipping.state} {o.shipping.pincode}</div>
          {can(user, 'PERM_ORDER_WRITE') && (
            <div className="stat mb-3"><div className="fw-semibold mb-2">Update status</div>
              {o.allowedNext.length === 0 ? <div className="small text-muted">No manual status change available.</div> : (
                <>
                  <select className="form-select mb-2" value={next} onChange={(e) => setNext(e.target.value)} aria-label="New status"><option value="">Choose status</option>{o.allowedNext.map((s) => <option key={s} value={s}>{s.replaceAll('_', ' ')}</option>)}</select>
                  <input className="form-control mb-2" placeholder="Note (optional)" value={note} onChange={(e) => setNote(e.target.value)} />
                  <button className="btn btn-dark btn-sm" disabled={!next} onClick={() => act(() => api.patch(`/admin/orders/${o.orderNumber}/status`, { status: next, note }), 'Status updated').then(() => { setNext(''); setNote(''); })}>Update</button>
                </>)}
              {o.status === 'PAID' && <div className="small text-muted mt-2">Move a paid order to Processing to start fulfilment.</div>}
            </div>
          )}
          {can(user, 'PERM_SHIPMENT_WRITE') && (
            <div className="stat"><div className="fw-semibold mb-2">Shipment (Shiprocket)</div>
              {o.shipmentRows.map((s) => (
                <div key={s.id} className="small mb-2">{s.courier || 'Courier'} · AWB {s.awb}<br />{s.statusText || s.status}
                  <div className="mt-1 d-flex gap-2"><button className="btn btn-sm btn-outline-dark" onClick={() => api.get(`/admin/shipments/${s.id}/label`).then((r) => window.open(r.data.labelUrl, '_blank', 'noopener')).catch((e) => toast.push(apiError(e).message, 'danger'))}>Label</button>
                    {s.trackingUrl && <a className="btn btn-sm btn-outline-dark" href={s.trackingUrl} target="_blank" rel="noopener noreferrer">Track</a>}</div></div>
              ))}
              {o.shipmentRows.length === 0 && (o.status === 'PROCESSING'
                ? (o.shiprocketConfigured === false
                  ? <div className="alert alert-warning small mb-0" role="status">Shiprocket is not set up yet, so shipments can't be created. Add <code>SHIPROCKET_EMAIL</code> and <code>SHIPROCKET_PASSWORD</code> to the backend environment and restart the server. See Settings → Shipping.</div>
                  : <button className="btn btn-dark btn-sm" disabled={busy} onClick={() => act(() => api.post(`/admin/orders/${o.orderNumber}/shipment`), 'Shipment created')}>{busy ? 'Creating…' : 'Create shipment & AWB'}</button>)
                : <div className="small text-muted">Move the order to Processing, then create the shipment.</div>)}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
