import { inr } from '../lib/money';
import { ringOffset } from '../lib/adminNav';

const R = 54;
const C = 2 * Math.PI * R;

/** Donut progress ring. `percent` may exceed 100 (the ring stops at 100 but the label shows the real figure). */
export function CircleProgress({ percent, ringPercent, targetSet, label }) {
  const ring = ringPercent ?? Math.min(100, Math.max(0, percent || 0));
  return (
    <figure className="circle-progress mb-0" role="img" aria-label={targetSet ? `${label}: ${percent}% of target` : `${label}: no target set`}>
      <svg viewBox="0 0 140 140" width="100%" height="100%">
        <circle cx="70" cy="70" r={R} fill="none" stroke="#e3e6ee" strokeWidth="14" />
        {targetSet && ring > 0 && (
          <circle cx="70" cy="70" r={R} fill="none" stroke={percent >= 100 ? '#1f8f5f' : '#1a2b4c'} strokeWidth="14" strokeLinecap="round"
            strokeDasharray={C} strokeDashoffset={ringOffset(ring, C)} transform="rotate(-90 70 70)" />
        )}
      </svg>
      <figcaption className="circle-progress-label">
        <span className="cp-value">{targetSet ? `${Number(percent).toLocaleString('en-IN', { maximumFractionDigits: 1 })}%` : '—'}</span>
        <span className="cp-sub">{targetSet ? 'of target' : 'no target'}</span>
      </figcaption>
    </figure>
  );
}

/** Revenue bars for the selected period with the previous period shown as a lighter bar beside each one. */
export function RevenueBars({ series }) {
  const max = Math.max(1, ...series.map((x) => Math.max(Number(x.revenue), Number(x.previousRevenue))));
  if (series.every((x) => Number(x.revenue) === 0 && Number(x.previousRevenue) === 0)) {
    return <div className="text-muted py-4">No revenue in this period or the one before it yet. The chart fills in as real orders arrive.</div>;
  }
  return (
    <div>
      <div className="rev-bars" role="img" aria-label="Revenue per period compared with the previous period">
        {series.map((x) => (
          <div className="rev-col" key={x.label} title={`${x.label}\nRevenue: ${inr(x.revenue)} (${x.orders} orders)\nPrevious: ${inr(x.previousRevenue)}`}>
            <div className="rev-prev" style={{ height: `${(Number(x.previousRevenue) / max) * 100}%` }} />
            <div className="rev-cur" style={{ height: `${(Number(x.revenue) / max) * 100}%` }} />
          </div>
        ))}
      </div>
      <div className="d-flex justify-content-between small text-muted mt-1"><span>{series[0].label}</span><span>Tallest bar: {inr(max)}</span><span>{series[series.length - 1].label}</span></div>
      <div className="small mt-2"><span className="legend-dot cur" /> This period <span className="legend-dot prev ms-3" /> Previous period</div>
    </div>
  );
}
