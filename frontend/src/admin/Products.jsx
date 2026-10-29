import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api, apiError } from '../lib/api';
import { useAuth, useToast } from '../lib/AppContext';
import { Pagination, ErrorBox, Spinner } from '../components/Bits';
import { Table, can, useFetch } from './common';
import { inr } from '../lib/money';

const CSV_HELP = 'name,sku,category,price,sale_price,fit,material,description,status,sizes,colors,stock,image_urls\nSlim Fit Blue Jeans,TF-J-001,jeans,1799,1499,Slim,Cotton denim,Everyday jeans,ACTIVE,28;30;32,Blue;Black,10,/uploads/products/example.jpg';

export default function Products() {
  const { user } = useAuth();
  const toast = useToast();
  const [q, setQ] = useState('');
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);
  const [sel, setSel] = useState([]);
  const [bulk, setBulk] = useState('');
  const [pct, setPct] = useState('');
  const [csv, setCsv] = useState('');
  const [showImport, setShowImport] = useState(false);
  const { data, err, loading, reload } = useFetch('/admin/products', { q: q || undefined, status: status || undefined, page, pageSize: 20 });
  const write = can(user, 'PERM_PRODUCT_WRITE');

  async function runBulk() {
    if (!bulk || !sel.length) return;
    try { const r = await api.post('/admin/products/bulk', { ids: sel, action: bulk, value: pct ? Number(pct) : null }); toast.push(`${r.data.updated} products updated`); setSel([]); reload(); } catch (e) { toast.push(apiError(e).message, 'danger'); }
  }
  async function runImport() {
    try { const r = await api.post('/admin/products/import', { csv }); toast.push(`${r.data.created} products imported`); setCsv(''); setShowImport(false); reload(); } catch (e) { toast.push(apiError(e).message, 'danger'); }
  }
  async function remove(p) {
    if (!window.confirm(`Delete "${p.name}"? Products that were ordered are archived instead.`)) return;
    try { const r = await api.delete(`/admin/products/${p.id}`); toast.push(`Product ${r.data.result.toLowerCase()}`); reload(); } catch (e) { toast.push(apiError(e).message, 'danger'); }
  }
  const toggle = (id) => setSel((s) => (s.includes(id) ? s.filter((x) => x !== id) : [...s, id]));

  return (
    <div>
      <div className="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
        <h1 className="h4 mb-0">Products {data && <small className="text-muted">({data.total})</small>}</h1>
        {write && <div className="d-flex gap-2"><button className="btn btn-outline-dark btn-sm" onClick={() => setShowImport((s) => !s)}>Import CSV</button><Link className="btn btn-dark btn-sm" to="/admin/products/new">+ Add product</Link></div>}
      </div>
      {showImport && (
        <div className="stat mb-3">
          <div className="small text-muted mb-2">Header row required. sizes / colors / image_urls are separated by ";". The import is all-or-nothing and reports the first bad row.</div>
          <textarea className="form-control mb-2" rows={5} placeholder={CSV_HELP} value={csv} onChange={(e) => setCsv(e.target.value)} />
          <input type="file" accept=".csv,text/csv" className="form-control mb-2" onChange={(e) => { const f = e.target.files?.[0]; if (f) f.text().then(setCsv); }} />
          <button className="btn btn-dark btn-sm" disabled={!csv.trim()} onClick={runImport}>Import</button>
        </div>
      )}
      <div className="d-flex flex-wrap gap-2 mb-3">
        <input className="form-control" style={{ maxWidth: 260 }} placeholder="Search name or SKU" value={q} onChange={(e) => { setQ(e.target.value); setPage(0); }} aria-label="Search products" />
        <select className="form-select" style={{ maxWidth: 160 }} value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }} aria-label="Status filter"><option value="">All statuses</option><option>ACTIVE</option><option>DRAFT</option><option>ARCHIVED</option></select>
        {write && sel.length > 0 && (
          <div className="d-flex gap-2 align-items-center">
            <select className="form-select" value={bulk} onChange={(e) => setBulk(e.target.value)} aria-label="Bulk action"><option value="">Bulk action ({sel.length})</option><option value="ACTIVATE">Publish</option><option value="DRAFT">Move to draft</option><option value="ARCHIVE">Archive</option><option value="FEATURE">Feature</option><option value="UNFEATURE">Unfeature</option><option value="DISCOUNT_PERCENT">Discount %</option><option value="CLEAR_SALE">Clear sale price</option></select>
            {bulk === 'DISCOUNT_PERCENT' && <input className="form-control" style={{ width: 90 }} placeholder="%" value={pct} onChange={(e) => setPct(e.target.value.replace(/\D/g, ''))} />}
            <button className="btn btn-dark" onClick={runBulk}>Apply</button>
          </div>
        )}
      </div>
      {err && <ErrorBox message={err} onRetry={reload} />}
      {loading && !data ? <Spinner /> : data && (
        <Table head={['', 'Product', 'SKU', 'Category', 'Price', 'Stock', 'Status', '']} empty={data.items.length === 0 ? 'No products yet. Add one or import a CSV.' : ''}>
          {data.items.map((p) => (
            <tr key={p.id}>
              <td>{write && <input type="checkbox" checked={sel.includes(p.id)} onChange={() => toggle(p.id)} aria-label={`Select ${p.name}`} />}</td>
              <td><div className="d-flex align-items-center gap-2">{p.image && <img src={p.image} alt="" width="38" height="48" style={{ objectFit: 'cover' }} />}<div>{p.name}{(p.demo === 1 || p.demo === true) && <span className="badge text-bg-warning ms-2">DEMO</span>}</div></div></td>
              <td className="small">{p.sku}</td><td>{p.category}</td>
              <td>{p.salePrice ? <><strong>{inr(p.salePrice)}</strong> <s className="text-muted small">{inr(p.price)}</s></> : inr(p.price)}</td>
              <td className={Number(p.stock) <= 5 ? 'text-danger fw-semibold' : ''}>{p.stock}</td>
              <td><span className={`badge text-bg-${p.status === 'ACTIVE' ? 'success' : p.status === 'DRAFT' ? 'secondary' : 'dark'}`}>{p.status}</span></td>
              <td className="text-nowrap">{write && <><Link to={`/admin/products/${p.id}`} className="btn btn-sm btn-outline-dark me-1">Edit</Link><button className="btn btn-sm btn-outline-danger" onClick={() => remove(p)}>Delete</button></>}</td>
            </tr>
          ))}
        </Table>
      )}
      {data && <Pagination page={data.page} totalPages={data.totalPages} onChange={setPage} />}
    </div>
  );
}
