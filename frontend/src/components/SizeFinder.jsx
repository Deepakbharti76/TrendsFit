import { useState } from 'react';
import { api, apiError } from '../lib/api';

/** Find-My-Size: calls the server estimate and always shows its disclaimer. */
export default function SizeFinder({ kind = 'TOP', fit, onPick, onClose }) {
  const [k, setK] = useState(kind);
  const [v, setV] = useState({ waist: '', chest: '', height: '', weight: '' });
  const [res, setRes] = useState(null);
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);

  async function go(e) {
    e.preventDefault();
    setErr(''); setRes(null); setBusy(true);
    try {
      const body = k === 'BOTTOM'
        ? { kind: 'BOTTOM', waistInches: Number(v.waist), fit }
        : { kind: 'TOP', chestInches: v.chest ? Number(v.chest) : null, heightCm: v.height ? Number(v.height) : null, weightKg: v.weight ? Number(v.weight) : null, fit };
      const r = await api.post('/size-recommendation', body);
      setRes(r.data);
    } catch (x) { setErr(apiError(x).message); } finally { setBusy(false); }
  }

  const set = (n) => (e) => setV((s) => ({ ...s, [n]: e.target.value }));
  return (
    <div className="modal d-block" style={{ background: 'rgba(0,0,0,.5)' }} role="dialog" aria-modal="true" aria-label="Find my size">
      <div className="modal-dialog modal-dialog-centered">
        <div className="modal-content rounded-0">
          <div className="modal-header"><h2 className="modal-title h5 display-font">Find my size</h2><button className="btn-close" aria-label="Close" onClick={onClose} /></div>
          <form onSubmit={go}>
            <div className="modal-body">
              <div className="btn-group w-100 mb-3" role="group">
                <button type="button" className={`btn ${k === 'TOP' ? 'btn-tf' : 'btn-tf-outline'}`} onClick={() => setK('TOP')}>Shirts / T-Shirts</button>
                <button type="button" className={`btn ${k === 'BOTTOM' ? 'btn-tf' : 'btn-tf-outline'}`} onClick={() => setK('BOTTOM')}>Jeans</button>
              </div>
              {k === 'BOTTOM' ? (
                <div><label className="form-label" htmlFor="sf-waist">Waist (inches)</label><input id="sf-waist" className="form-control" type="number" step="0.5" required value={v.waist} onChange={set('waist')} /></div>
              ) : (
                <>
                  <label className="form-label" htmlFor="sf-chest">Chest (inches)</label>
                  <input id="sf-chest" className="form-control mb-2" type="number" step="0.5" value={v.chest} onChange={set('chest')} />
                  <div className="text-muted small mb-2">or enter height and weight</div>
                  <div className="row g-2"><div className="col"><input aria-label="Height in cm" className="form-control" type="number" placeholder="Height (cm)" value={v.height} onChange={set('height')} /></div>
                    <div className="col"><input aria-label="Weight in kg" className="form-control" type="number" placeholder="Weight (kg)" value={v.weight} onChange={set('weight')} /></div></div>
                </>
              )}
              {err && <div className="alert alert-danger mt-3 mb-0">{err}</div>}
              {res && (
                <div className="alert alert-success mt-3 mb-0">
                  <div>We suggest size <strong>{res.size}</strong>.</div>
                  <div className="small mt-1">{res.disclaimer}</div>
                  {onPick && <button type="button" className="btn btn-sm btn-tf mt-2" onClick={() => { onPick(res.size); onClose(); }}>Select {res.size}</button>}
                </div>
              )}
            </div>
            <div className="modal-footer"><button className="btn btn-tf" disabled={busy}>{busy ? 'Checking…' : 'Recommend a size'}</button></div>
          </form>
        </div>
      </div>
    </div>
  );
}
