import { useEffect, useState } from 'react';
import { Link, Navigate } from 'react-router-dom';
import { api, apiError } from '../lib/api';
import { useAuth, useToast } from '../lib/AppContext';
import { ErrorBox } from '../components/Bits';
import { firstAllowed, trendText } from '../lib/adminNav';
import { inr } from '../lib/money';
import { CircleProgress, RevenueBars } from './charts';
import PeriodPicker from './PeriodPicker';
import { can, useFetch } from './common';

const GRAN = [['day', 'Daily'], ['week', 'Weekly (7-day blocks)'], ['month', 'Monthly (30-day blocks)']];
const REFRESH_MS = 60000;

function Skeleton({ h = 120 }) { return <div className="skeleton" style={{ height: h }} aria-hidden="true" />; }

function Kpi({ label, value, to, hint }) {
  const body = (<><div className="small text-muted">{label}</div><div className="v">{value}</div>{hint && <div className="small text-muted">{hint}</div>}</>);
  return <div className="col-6 col-md-4 col-xl-3"><div className="stat kpi">{to ? <Link to={to} className="kpi-link">{body}</Link> : body}</div></div>;
}

function RevenueCard({ ov, kind, canEditTarget, onSaveTarget, customTarget, setCustomTarget }) {
  const t = ov.target;
  const r = ov.revenue;
  const [draft, setDraft] = useState('');
  useEffect(() => { setDraft(kind === 'custom' ? (customTarget || '') : (t.set ? String(t.target) : '')); }, [kind, t.target, t.set, customTarget]);
  const save = (e) => {
    e.preventDefault();
    if (kind === 'custom') setCustomTarget(draft);
    else onSaveTarget(draft === '' ? '0' : draft);
  };
  return (
    <div className="stat h-100">
      <div className="fw-semibold mb-2">Revenue vs target</div>
      <div className="d-flex flex-wrap align-items-center gap-3">
        <div className="circle-wrap"><CircleProgress percent={t.percent} ringPercent={t.ringPercent} targetSet={t.set} label="Revenue" /></div>
        <div className="flex-grow-1" style={{ minWidth: 190 }}>
          <div className="small text-muted">Revenue achieved</div>
          <div className="h4 mb-1">{inr(r.current)}</div>
          <div className="small">Target: <strong>{t.set ? inr(t.target) : 'not set'}</strong></div>
          {t.set && <div className="small">{t.reached ? <span className="text-success">Target reached{Number(t.percent) > 100 ? ` (${t.percent}%)` : ''}</span> : <>Remaining: <strong>{inr(t.remaining)}</strong></>}</div>}
          <div className={`small mt-1 ${r.changePercent === null ? 'text-muted' : Number(r.changePercent) >= 0 ? 'text-success' : 'text-danger'}`}>{trendText(r.changePercent)}</div>
          <div className="small text-muted">Previous period: {inr(r.previous)}</div>
        </div>
      </div>
      {(canEditTarget || kind === 'custom') && (
        <form className="d-flex gap-2 mt-3 align-items-end" onSubmit={save}>
          <label className="small flex-grow-1">{kind === 'custom' ? 'Target for this custom range (₹, not saved)' : `Target for ${kind === 'today' ? 'Today' : kind === '7d' ? '7 days' : '30 days'} (₹, saved in settings)`}
            <input className="form-control form-control-sm" inputMode="decimal" value={draft} onChange={(e) => setDraft(e.target.value)} placeholder="e.g. 50000" /></label>
          <button className="btn btn-sm btn-dark">{kind === 'custom' ? 'Apply' : 'Save target'}</button>
        </form>
      )}
      <div className="small text-muted mt-2">Revenue counts confirmed orders (paid online or COD placed). Unpaid, failed and refunded orders are excluded.</div>
    </div>
  );
}

export default function Dashboard() {
  const { user } = useAuth();
  const toast = useToast();
  const [period, setPeriod] = useState({ kind: '30d' });
  const [gran, setGran] = useState('day');
  const [customTarget, setCustomTarget] = useState('');
  const allowed = can(user, 'ANALYTICS_READ');
  const params = { period: period.kind, from: period.from || undefined, to: period.to || undefined, granularity: gran, target: period.kind === 'custom' && customTarget ? customTarget : undefined };
  const ready = allowed && (period.kind !== 'custom' || (period.from && period.to));
  const ov = useFetch('/admin/analytics/overview', ready ? params : { period: '30d' });
  const dash = useFetch('/admin/dashboard');
  const [updated, setUpdated] = useState(null);

  useEffect(() => { if (ov.data) setUpdated(new Date()); }, [ov.data]);
  useEffect(() => {
    const t = setInterval(() => { if (!document.hidden) { ov.reload(); dash.reload(); } }, REFRESH_MS);
    return () => clearInterval(t);
  }, [ov.reload, dash.reload]); // eslint-disable-line react-hooks/exhaustive-deps

  if (!allowed) {
    const to = firstAllowed(user);
    return to && to !== '/admin' ? <Navigate to={to} replace /> : <div className="alert alert-info">Your role has no admin pages yet. Ask a Super Admin for access.</div>;
  }

  const saveTarget = async (value) => {
    try {
      await api.put('/admin/settings', { [`revenue.target.${period.kind}`]: value });
      toast.push('Revenue target saved');
      ov.reload();
    } catch (e) { toast.push(apiError(e).message, 'danger'); }
  };

  const d = ov.data;
  const k = d?.kpis;
  const refresh = () => { ov.reload(); dash.reload(); };
  return (
    <div>
      <div className="d-flex flex-wrap justify-content-between align-items-start gap-2 mb-3">
        <h1 className="h4 mb-0">Dashboard</h1>
        <div className="small text-muted">{updated ? `Updated ${updated.toLocaleTimeString('en-IN')} · refreshes every minute` : ''} <button type="button" className="btn btn-sm btn-outline-secondary ms-1" onClick={refresh}>Refresh</button></div>
      </div>
      <div className="mb-3"><PeriodPicker value={period} onChange={setPeriod} /></div>
      {ov.err && <ErrorBox message={ov.err} onRetry={ov.reload} />}

      <div className="row g-3 mb-3">
        <div className="col-lg-5">{d ? <RevenueCard ov={d} kind={period.kind} canEditTarget={can(user, 'SETTINGS_WRITE')} onSaveTarget={saveTarget} customTarget={customTarget} setCustomTarget={setCustomTarget} /> : <Skeleton h={260} />}</div>
        <div className="col-lg-7">
          <div className="stat h-100">
            <div className="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-2">
              <div className="fw-semibold">Revenue trend{d ? <span className="small text-muted fw-normal"> · {d.period.from} to {d.period.to}</span> : ''}</div>
              <select className="form-select form-select-sm w-auto" value={gran} onChange={(e) => setGran(e.target.value)} aria-label="Chart granularity">{GRAN.map(([v, l]) => <option key={v} value={v}>{l}</option>)}</select>
            </div>
            {d ? <RevenueBars series={d.series} /> : <Skeleton h={200} />}
          </div>
        </div>
      </div>

      <h2 className="h6 text-muted text-uppercase mb-2">At a glance</h2>
      {k ? (
        <div className="row g-3 mb-4">
          <Kpi label="Today's revenue" value={inr(k.revenueToday)} />
          <Kpi label="Revenue, 7 days" value={inr(k.revenue7d)} />
          <Kpi label="Revenue, 30 days" value={inr(k.revenue30d)} />
          <Kpi label="Total orders" value={k.totalOrders} to="/admin/orders" />
          <Kpi label="Orders to fulfil" value={k.ordersToFulfil} to="/admin/orders?status=PROCESSING" hint="Paid, processing or packed" />
          <Kpi label="Delivered orders" value={k.deliveredOrders} to="/admin/orders?status=DELIVERED" />
          <Kpi label="Payment failed (30 days)" value={k.paymentFailed30d} to="/admin/orders?status=PAYMENT_FAILED" />
          <Kpi label="Pending COD collection" value={k.pendingCodOrders} to="/admin/payments?view=cod" hint={`${inr(k.pendingCodAmount)} to be collected`} />
          <Kpi label="Customers" value={k.totalCustomers} to="/admin/customers" />
          <Kpi label="New customers (30 days)" value={k.newCustomers30d} to="/admin/customers" />
          <Kpi label="Pending exchanges" value={k.pendingExchanges} to="/admin/exchanges" />
          <Kpi label="Reviews to moderate" value={k.pendingReviews} to="/admin/reviews" />
          <Kpi label="Low-stock variants" value={k.lowStockVariants} to="/admin/inventory?low=1" />
          <Kpi label="Out-of-stock variants" value={k.outOfStockVariants} to="/admin/inventory?low=1" />
          <Kpi label="Unread customer messages" value={k.unreadMessages} to="/admin/support?unread=1" />
        </div>
      ) : <div className="row g-3 mb-4">{Array.from({ length: 8 }).map((_, i) => <div key={i} className="col-6 col-md-4 col-xl-3"><Skeleton h={84} /></div>)}</div>}

      {dash.err && <ErrorBox message={dash.err} onRetry={dash.reload} />}
      {dash.data && (
        <div className="row g-3">
          <div className="col-lg-6"><div className="stat"><div className="fw-semibold mb-2">Orders by status</div>
            {dash.data.ordersByStatus.length === 0 ? <div className="text-muted">No orders yet.</div> : dash.data.ordersByStatus.map((s) => <div key={s.status} className="d-flex justify-content-between small py-1"><Link to={`/admin/orders?status=${s.status}`}>{s.status.replaceAll('_', ' ')}</Link><strong>{s.count}</strong></div>)}</div></div>
          <div className="col-lg-6"><div className="stat"><div className="fw-semibold mb-2">Top products (30 days)</div>
            {dash.data.topProducts.length === 0 ? <div className="text-muted">No sales yet.</div> : dash.data.topProducts.map((p) => <div key={p.name} className="d-flex justify-content-between small py-1"><span>{p.name}</span><span>{p.units} sold · {inr(p.revenue)}</span></div>)}</div></div>
          <div className="col-lg-6"><div className="stat"><div className="fw-semibold mb-2">Low stock</div>
            {dash.data.lowStock.length === 0 ? <div className="text-muted">Nothing is running low.</div> : dash.data.lowStock.map((v) => <div key={v.id} className="d-flex justify-content-between small py-1"><span>{v.product} · {v.size} · {v.color}</span><strong className={v.stock === 0 ? 'text-danger' : ''}>{v.stock}</strong></div>)}</div></div>
          <div className="col-lg-6"><div className="stat"><div className="fw-semibold mb-2">Integrations</div>
            {[['Razorpay keys', dash.data.integrations.razorpay], ['Razorpay webhook secret', dash.data.integrations.razorpayWebhook], ['Shiprocket', dash.data.integrations.shiprocket], ['Email (SMTP)', dash.data.integrations.email]].map(([l, ok]) => (
              <div key={l} className="d-flex justify-content-between small py-1"><span>{l}</span><span className={ok ? 'text-success' : 'text-danger'}>{ok ? 'Configured' : 'Not configured'}</span></div>
            ))}</div></div>
        </div>
      )}
    </div>
  );
}
