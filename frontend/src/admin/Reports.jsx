import { useState } from 'react';
import { ErrorBox, Spinner } from '../components/Bits';
import { toCsv, trendText } from '../lib/adminNav';
import { inr } from '../lib/money';
import { RevenueBars } from './charts';
import PeriodPicker from './PeriodPicker';
import { Table, useFetch } from './common';

const GRAN = [['day', 'Daily'], ['week', 'Weekly (7-day blocks)'], ['month', 'Monthly (30-day blocks)']];

export default function Reports() {
  const [period, setPeriod] = useState({ kind: '30d' });
  const [gran, setGran] = useState('day');
  const ok = period.kind !== 'custom' || (period.from && period.to);
  const base = ok ? { period: period.kind, from: period.from || undefined, to: period.to || undefined } : { period: '30d' };
  const ov = useFetch('/admin/analytics/overview', { ...base, granularity: gran });
  const bd = useFetch('/admin/analytics/breakdown', base);

  const exportCsv = () => {
    const rows = ov.data.series.map((s) => ({ period_start: s.label, revenue: s.revenue, orders: s.orders, previous_period_revenue: s.previousRevenue }));
    const csv = toCsv(rows, ['period_start', 'revenue', 'orders', 'previous_period_revenue']);
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }));
    const a = document.createElement('a');
    a.href = url; a.download = `trendsfit-revenue-${ov.data.period.from}_${ov.data.period.to}.csv`;
    document.body.appendChild(a); a.click(); a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 2000);
  };

  const d = ov.data;
  return (
    <div>
      <div className="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
        <h1 className="h4 mb-0">Reports &amp; analytics</h1>
        <button type="button" className="btn btn-sm btn-outline-dark" disabled={!d} onClick={exportCsv}>Download revenue CSV</button>
      </div>
      <div className="mb-3"><PeriodPicker value={period} onChange={setPeriod} /></div>
      {(ov.err || bd.err) && <ErrorBox message={ov.err || bd.err} onRetry={() => { ov.reload(); bd.reload(); }} />}
      {!d ? <Spinner /> : (
        <>
          <div className="row g-3 mb-3">
            <div className="col-6 col-lg-3"><div className="stat"><div className="small text-muted">Revenue</div><div className="v">{inr(d.revenue.current)}</div><div className="small text-muted">{trendText(d.revenue.changePercent)}</div></div></div>
            <div className="col-6 col-lg-3"><div className="stat"><div className="small text-muted">Orders</div><div className="v">{d.revenue.orders}</div><div className="small text-muted">Previous: {d.revenue.previousOrders}</div></div></div>
            <div className="col-6 col-lg-3"><div className="stat"><div className="small text-muted">Average order value</div><div className="v">{inr(d.revenue.averageOrderValue)}</div></div></div>
            <div className="col-6 col-lg-3"><div className="stat"><div className="small text-muted">New customers</div><div className="v">{bd.data ? bd.data.newCustomers : '…'}</div></div></div>
          </div>
          <div className="stat mb-3">
            <div className="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-2"><div className="fw-semibold">Revenue trend ({d.period.from} to {d.period.to})</div>
              <select className="form-select form-select-sm w-auto" value={gran} onChange={(e) => setGran(e.target.value)} aria-label="Chart granularity">{GRAN.map(([v, l]) => <option key={v} value={v}>{l}</option>)}</select></div>
            <RevenueBars series={d.series} />
          </div>
        </>
      )}
      {bd.data && (
        <div className="row g-3">
          <div className="col-lg-6"><div className="stat"><div className="fw-semibold mb-2">By payment method</div>
            {bd.data.byPaymentMethod.length === 0 ? <div className="text-muted">No confirmed orders in this period.</div> : bd.data.byPaymentMethod.map((m) => <div key={m.method} className="d-flex justify-content-between small py-1"><span>{m.method === 'COD' ? 'Cash on Delivery' : 'Online (Razorpay)'} · {m.orders} orders</span><strong>{inr(m.revenue)}</strong></div>)}</div></div>
          <div className="col-lg-6"><div className="stat"><div className="fw-semibold mb-2">Orders by status</div>
            {bd.data.byStatus.length === 0 ? <div className="text-muted">No orders in this period.</div> : bd.data.byStatus.map((s) => <div key={s.status} className="d-flex justify-content-between small py-1"><span>{s.status.replaceAll('_', ' ')}</span><strong>{s.orders}</strong></div>)}</div></div>
          <div className="col-12"><div className="stat"><div className="fw-semibold mb-2">Best sellers</div>
            <Table head={['Product', 'Units', 'Revenue']} empty={bd.data.topProducts.length === 0 ? 'No sales in this period.' : ''}>
              {bd.data.topProducts.map((p) => <tr key={p.name}><td>{p.name}</td><td>{p.units}</td><td>{inr(p.revenue)}</td></tr>)}
            </Table></div></div>
        </div>
      )}
    </div>
  );
}
