import { useState } from 'react';

const PRESETS = [['today', 'Today'], ['7d', '7 days'], ['30d', '30 days'], ['custom', 'Custom']];
const iso = (d) => d.toISOString().slice(0, 10);

/** value = { kind, from, to }. Custom ranges are applied with the Apply button so the server is not called on every keystroke. */
export default function PeriodPicker({ value, onChange }) {
  const today = iso(new Date());
  const [from, setFrom] = useState(value.from || '');
  const [to, setTo] = useState(value.to || '');
  const bad = !from || !to || from > to || to > today;
  return (
    <div className="d-flex flex-wrap gap-2 align-items-end">
      <div className="btn-group" role="group" aria-label="Period">
        {PRESETS.map(([k, label]) => (
          <button key={k} type="button" className={`btn btn-sm ${value.kind === k ? 'btn-dark' : 'btn-outline-dark'}`} aria-pressed={value.kind === k}
            onClick={() => (k === 'custom' ? onChange({ kind: 'custom', from: from || '', to: to || '' }) : onChange({ kind: k }))}>{label}</button>
        ))}
      </div>
      {value.kind === 'custom' && (
        <div className="d-flex flex-wrap gap-2 align-items-end">
          <label className="small">From<input type="date" className="form-control form-control-sm" max={today} value={from} onChange={(e) => setFrom(e.target.value)} /></label>
          <label className="small">To<input type="date" className="form-control form-control-sm" max={today} value={to} onChange={(e) => setTo(e.target.value)} /></label>
          <button type="button" className="btn btn-sm btn-dark" disabled={bad} onClick={() => onChange({ kind: 'custom', from, to })}>Apply</button>
          {from && to && bad && <span className="small text-danger">Choose a valid range (end date not in the future, after the start date).</span>}
        </div>
      )}
    </div>
  );
}
