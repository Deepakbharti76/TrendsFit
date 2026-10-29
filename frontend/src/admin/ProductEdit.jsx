import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { api, apiError } from '../lib/api';
import { useToast } from '../lib/AppContext';
import { ErrorBox, Field, Spinner } from '../components/Bits';

const BLANK = { name: '', slug: '', sku: '', categorySlug: '', description: '', fit: '', material: '', care: '', sizeChart: '', videoUrl: '', price: '', salePrice: '', seoTitle: '', seoDescription: '', status: 'DRAFT', featured: false, newArrival: false, bestseller: false, images: [], variants: [] };
export const num = (v) => {
  if (v === '' || v == null) return null;
  if (typeof v === 'number') return v;
  const t = String(v).replace(/[₹,\s]/g, '');
  return t === '' ? null : Number(t);
};

/**
 * Price-override problems per variant row. Blank override is fine (the variant then uses the product's sale price,
 * or its regular price); a value that is entered must be a number above 0. Inactive rows are not sold, so they are skipped.
 * Returns { [rowIndex]: message }.
 */
export function variantPriceErrors(variants) {
  const out = {};
  variants.forEach((v, i) => {
    if (v.active === false) return;
    const n = num(v.priceOverride);
    if (n === null) return;
    if (!Number.isFinite(n) || n <= 0) out[i] = `Price override must be a number above 0 (or leave it empty to use the product price). Row ${i + 1}: ${v.size || '?'} / ${v.color || '?'}.`;
  });
  return out;
}

export default function ProductEdit() {
  const { id } = useParams();
  const nav = useNavigate();
  const toast = useToast();
  const isNew = id === 'new';
  const [f, setF] = useState(isNew ? BLANK : null);
  const [cats, setCats] = useState([]);
  const [errors, setErrors] = useState({});
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api.get('/admin/categories').then((r) => setCats(r.data)).catch(() => {});
    if (!isNew) api.get(`/admin/products/${id}`).then((r) => setF({ ...BLANK, ...r.data, price: r.data.price ?? '', salePrice: r.data.salePrice ?? '' })).catch((e) => setErr(apiError(e).message));
  }, [id]); // eslint-disable-line

  if (err) return <ErrorBox message={err} />;
  if (!f) return <Spinner />;
  const set = (k) => (e) => setF({ ...f, [k]: e.target.type === 'checkbox' ? e.target.checked : e.target.value });
  const setVar = (i, k, v) => setF({ ...f, variants: f.variants.map((x, j) => (j === i ? { ...x, [k]: v } : x)) });

  async function upload(e) {
    const file = e.target.files?.[0];
    if (!file) return;
    const fd = new FormData(); fd.append('file', file);
    try { const r = await api.post('/admin/uploads', fd); setF((x) => ({ ...x, images: [...x.images, { url: r.data.url, alt: x.name }] })); } catch (x) { toast.push(apiError(x).message, 'danger'); }
    e.target.value = '';
  }
  const move = (i, d) => { const a = f.images.slice(); const j = i + d; if (j < 0 || j >= a.length) return; [a[i], a[j]] = [a[j], a[i]]; setF({ ...f, images: a }); };

  async function save(e) {
    e.preventDefault(); setErrors({});
    const ve = variantPriceErrors(f.variants);
    if (Object.keys(ve).length) {
      setErrors(Object.fromEntries(Object.entries(ve).map(([i, m]) => [`variant.${i}.priceOverride`, m])));
      toast.push('Please fix the highlighted variant price.', 'warning');
      return;
    }
    setBusy(true);
    const body = { ...f, price: num(f.price), salePrice: num(f.salePrice), variants: f.variants.map((v) => ({ ...v, stock: v.stock === '' ? 0 : Number(v.stock), priceOverride: num(v.priceOverride) })) };
    try {
      const r = isNew ? await api.post('/admin/products', body) : await api.put(`/admin/products/${id}`, body);
      toast.push('Product saved');
      if (isNew) nav(`/admin/products/${r.data.id}`, { replace: true });
    } catch (x) { const er = apiError(x); setErrors(er.fields); toast.push(er.message, 'danger'); } finally { setBusy(false); }
  }

  const inp = (k, label, extra = {}) => <Field label={label} id={`pe-${k}`} error={errors[k]}><input id={`pe-${k}`} className={`form-control ${errors[k] ? 'is-invalid' : ''}`} value={f[k] ?? ''} onChange={set(k)} {...extra} /></Field>;
  const area = (k, label, rows = 3) => <Field label={label} id={`pe-${k}`}><textarea id={`pe-${k}`} rows={rows} className="form-control" value={f[k] ?? ''} onChange={set(k)} /></Field>;

  return (
    <form onSubmit={save} className="product-form">
      <div className="d-flex justify-content-between align-items-center mb-3"><h1 className="h4 mb-0">{isNew ? 'New product' : `Edit: ${f.name}`}</h1><button className="btn btn-dark" disabled={busy}>{busy ? 'Saving…' : 'Save product'}</button></div>
      <div className="row g-3">
        <div className="col-lg-8">
          <div className="stat mb-3">
            {inp('name', 'Name')}
            <div className="row"><div className="col-md-6">{inp('sku', 'SKU')}</div><div className="col-md-6">{inp('slug', 'URL slug (auto if empty)')}</div></div>
            {area('description', 'Description', 5)}
            <div className="row"><div className="col-md-6">{inp('fit', 'Fit (e.g. Slim, Regular)')}</div><div className="col-md-6">{inp('material', 'Material')}</div></div>
            {area('care', 'Care instructions', 2)}{area('sizeChart', 'Size chart (text)', 4)}{inp('videoUrl', 'Video URL (optional)')}
          </div>
          <div className="stat mb-3">
            <div className="d-flex justify-content-between mb-2"><strong>Images</strong><input type="file" accept="image/jpeg,image/png,image/webp" onChange={upload} aria-label="Upload image" /></div>
            <div className="d-flex flex-wrap gap-2">{f.images.map((im, i) => (
              <div key={im.url} className="border p-1 text-center" style={{ width: 110 }}>
                <img src={im.url} alt="" style={{ width: '100%', aspectRatio: '4/5', objectFit: 'cover' }} />
                <div className="btn-group btn-group-sm mt-1"><button type="button" className="btn btn-outline-secondary" onClick={() => move(i, -1)} aria-label="Move left">‹</button><button type="button" className="btn btn-outline-danger" onClick={() => setF({ ...f, images: f.images.filter((_, j) => j !== i) })} aria-label="Remove image">✕</button><button type="button" className="btn btn-outline-secondary" onClick={() => move(i, 1)} aria-label="Move right">›</button></div>
              </div>))}
            </div>
            <div className="input-group input-group-sm mt-2"><input id="img-url" className="form-control" placeholder="…or paste an image URL / path" />
              <button type="button" className="btn btn-outline-dark" onClick={() => { const el = document.getElementById('img-url'); if (el.value.trim()) { setF({ ...f, images: [...f.images, { url: el.value.trim(), alt: f.name }] }); el.value = ''; } }}>Add</button></div>
          </div>
          <div className="stat">
            <div className="d-flex justify-content-between mb-2"><strong>Variants (size × colour)</strong>
              <button type="button" className="btn btn-sm btn-outline-dark" onClick={() => setF({ ...f, variants: [...f.variants, { size: '', color: '', colorHex: '#999999', stock: 0, priceOverride: '', active: true }] })}>+ Add variant</button></div>
            <div className="table-responsive"><table className="table table-sm align-middle"><thead><tr><th>Size</th><th>Colour</th><th>Hex</th><th>Stock</th><th>Price override</th><th>Active</th><th /></tr></thead><tbody>
              {f.variants.map((v, i) => (
                <tr key={v.id || `n${i}`}>
                  <td><input className="form-control form-control-sm" style={{ width: 70 }} value={v.size} onChange={(e) => setVar(i, 'size', e.target.value)} aria-label="Size" /></td>
                  <td><input className="form-control form-control-sm" value={v.color} onChange={(e) => setVar(i, 'color', e.target.value)} aria-label="Colour" /></td>
                  <td><input type="color" className="form-control form-control-color" value={v.colorHex || '#999999'} onChange={(e) => setVar(i, 'colorHex', e.target.value)} aria-label="Colour hex" /></td>
                  <td><input type="number" min="0" className="form-control form-control-sm" style={{ width: 80 }} value={v.stock} onChange={(e) => setVar(i, 'stock', e.target.value)} aria-label="Stock" /></td>
                  <td><input type="number" min="0" step="0.01" className={`form-control form-control-sm ${errors[`variant.${i}.priceOverride`] ? 'is-invalid' : ''}`} style={{ width: 100 }} placeholder="Product price" value={v.priceOverride ?? ''} onChange={(e) => setVar(i, 'priceOverride', e.target.value)} aria-label="Price override" /></td>
                  <td><input type="checkbox" checked={v.active !== false} onChange={(e) => setVar(i, 'active', e.target.checked)} aria-label="Active" /></td>
                  <td>{!v.id && <button type="button" className="btn btn-sm btn-outline-danger" onClick={() => setF({ ...f, variants: f.variants.filter((_, j) => j !== i) })}>✕</button>}</td>
                </tr>))}
            </tbody></table></div>
            {Object.entries(errors).filter(([k]) => k.startsWith('variant.')).map(([k, m]) => <div key={k} className="invalid-feedback d-block mb-1" role="alert">{m}</div>)}
            <div className="small text-muted mb-1">Price override is optional: leave it empty and the variant sells at the product's sale price (or regular price if there is no sale). If you enter one it must be above 0.</div>
            <div className="small text-muted">Variants removed from this list are deactivated, never deleted, so order history stays intact. Stock changes are logged in stock history.</div>
          </div>
        </div>
        <div className="col-lg-4">
          <div className="stat mb-3">
            <Field label="Category" id="pe-cat" error={errors.categorySlug}><select id="pe-cat" className={`form-select ${errors.categorySlug ? 'is-invalid' : ''}`} value={f.categorySlug} onChange={set('categorySlug')}><option value="">Choose…</option>{cats.map((c) => <option key={c.id} value={c.slug}>{c.name}</option>)}</select></Field>
            <Field label="Status" id="pe-st" error={errors.status}><select id="pe-st" className="form-select" value={f.status} onChange={set('status')}><option>DRAFT</option><option>ACTIVE</option><option>ARCHIVED</option></select></Field>
            {inp('price', 'Regular price (₹)', { type: 'number', min: 0, step: '0.01' })}
            {inp('salePrice', 'Sale price (₹, optional)', { type: 'number', min: 0, step: '0.01' })}
            {[['featured', 'Featured on homepage'], ['newArrival', 'New arrival'], ['bestseller', 'Bestseller']].map(([k, l]) => <div className="form-check" key={k}><input id={`pe-${k}`} className="form-check-input" type="checkbox" checked={!!f[k]} onChange={set(k)} /><label htmlFor={`pe-${k}`} className="form-check-label">{l}</label></div>)}
          </div>
          <div className="stat">{inp('seoTitle', 'SEO title')}{area('seoDescription', 'SEO description', 3)}</div>
        </div>
      </div>
    </form>
  );
}
