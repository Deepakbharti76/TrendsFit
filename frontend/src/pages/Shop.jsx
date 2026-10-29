import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { api, apiError } from '../lib/api';
import { useSeo } from '../lib/seo';
import ProductCard from '../components/ProductCard';
import { Empty, ErrorBox, Pagination } from '../components/Bits';

const SORTS = [['newest', 'Newest'], ['popular', 'Best selling'], ['price_asc', 'Price: low to high'], ['price_desc', 'Price: high to low'], ['rating', 'Top rated'], ['discount', 'Biggest discount']];
const CATS = [['', 'All'], ['jeans', 'Jeans'], ['t-shirts', 'T-Shirts'], ['shirts', 'Shirts']];

function Filters({ params, set, facets }) {
  const get = (k) => params.get(k) || '';
  const chip = (k, v) => (
    <button key={v} type="button" className={`chip ${get(k) === v ? 'on' : ''}`} aria-pressed={get(k) === v} onClick={() => set({ [k]: get(k) === v ? '' : v })}>{v}</button>
  );
  const [min, setMin] = useState(get('minPrice'));
  const [max, setMax] = useState(get('maxPrice'));
  useEffect(() => { setMin(get('minPrice')); setMax(get('maxPrice')); }, [params.toString()]); // eslint-disable-line
  return (
    <div className="filter-panel">
      <h3>Category</h3>
      <div>{CATS.map(([v, l]) => <button key={v} type="button" className={`chip ${get('category') === v ? 'on' : ''}`} onClick={() => set({ category: v })}>{l}</button>)}</div>
      {facets?.sizes?.length > 0 && <><h3>Size</h3><div>{facets.sizes.map((s) => chip('size', s))}</div></>}
      {facets?.colors?.length > 0 && <><h3>Colour</h3><div>{facets.colors.map((s) => chip('color', s))}</div></>}
      {facets?.fits?.length > 0 && <><h3>Fit</h3><div>{facets.fits.map((s) => chip('fit', s))}</div></>}
      <h3>Price (₹)</h3>
      <div className="d-flex gap-2 align-items-center">
        <input className="form-control" inputMode="numeric" placeholder={facets?.priceMin ? String(Math.floor(facets.priceMin)) : 'Min'} value={min} onChange={(e) => setMin(e.target.value.replace(/\D/g, ''))} aria-label="Minimum price" />
        <span>–</span>
        <input className="form-control" inputMode="numeric" placeholder={facets?.priceMax ? String(Math.ceil(facets.priceMax)) : 'Max'} value={max} onChange={(e) => setMax(e.target.value.replace(/\D/g, ''))} aria-label="Maximum price" />
      </div>
      <button className="btn btn-sm btn-tf-outline mt-2" onClick={() => set({ minPrice: min, maxPrice: max })}>Apply price</button>
      <h3>Availability</h3>
      <div className="form-check"><input className="form-check-input" type="checkbox" id="f-stock" checked={get('inStock') === 'true'} onChange={(e) => set({ inStock: e.target.checked ? 'true' : '' })} /><label className="form-check-label" htmlFor="f-stock">In stock only</label></div>
      <div className="form-check"><input className="form-check-input" type="checkbox" id="f-sale" checked={get('onSale') === 'true'} onChange={(e) => set({ onSale: e.target.checked ? 'true' : '' })} /><label className="form-check-label" htmlFor="f-sale">On sale</label></div>
    </div>
  );
}

export default function Shop() {
  const [params, setParams] = useSearchParams();
  const [data, setData] = useState(null);
  const [err, setErr] = useState('');
  const [loading, setLoading] = useState(true);
  const [drawer, setDrawer] = useState(false);
  const qs = params.toString();
  const category = params.get('category');
  const title = params.get('q') ? `Search: ${params.get('q')}` : (CATS.find((c) => c[0] === category)?.[1] && category ? CATS.find((c) => c[0] === category)[1] : 'Shop All');
  useSeo({ title, description: `Shop ${title === 'Shop All' ? 'jeans, shirts and t-shirts' : title.toLowerCase()} at TrendsFit.` });

  const set = (patch) => {
    const next = new URLSearchParams(params);
    Object.entries(patch).forEach(([k, v]) => { if (v === '' || v == null) next.delete(k); else next.set(k, v); });
    if (!('page' in patch)) next.delete('page');
    setParams(next);
  };

  const load = useMemo(() => () => {
    setLoading(true); setErr('');
    const p = Object.fromEntries(new URLSearchParams(qs));
    api.get('/products', { params: { ...p, page: p.page ? Number(p.page) : 0, pageSize: 12 } })
      .then((r) => setData(r.data)).catch((e) => setErr(apiError(e).message)).finally(() => setLoading(false));
  }, [qs]);
  useEffect(load, [load]);
  useEffect(() => { if (data) window.scrollTo({ top: 0, behavior: 'smooth' }); }, [data?.page]); // eslint-disable-line

  const active = ['q', 'category', 'size', 'color', 'fit', 'minPrice', 'maxPrice', 'inStock', 'onSale'].filter((k) => params.get(k));

  return (
    <div className="container section">
      <div className="d-flex justify-content-between align-items-end flex-wrap gap-2 mb-3">
        <div>
          <h1 className="display-font h2 mb-0">{title}</h1>
          {data && <div className="text-muted small">{data.total} {data.total === 1 ? 'product' : 'products'}</div>}
        </div>
        <div className="d-flex gap-2">
          <button className="btn btn-tf-outline d-lg-none" onClick={() => setDrawer(true)}>Filters{active.length ? ` (${active.length})` : ''}</button>
          <select className="form-select" style={{ width: 'auto' }} value={params.get('sort') || 'newest'} onChange={(e) => set({ sort: e.target.value })} aria-label="Sort by">
            {SORTS.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
          </select>
        </div>
      </div>
      {active.length > 0 && (
        <div className="mb-3">
          {active.map((k) => <button key={k} className="chip on" onClick={() => set({ [k]: '' })} aria-label={`Remove filter ${k}`}>{k}: {params.get(k)} ✕</button>)}
          <button className="btn btn-link btn-sm" onClick={() => setParams(new URLSearchParams())}>Clear all</button>
        </div>
      )}
      <div className="row">
        <aside className="col-lg-3 d-none d-lg-block"><Filters params={params} set={set} facets={data?.facets} /></aside>
        <div className="col-lg-9">
          {err && <ErrorBox message={err} onRetry={load} />}
          {loading && !data && <div className="row g-3 row-cols-2 row-cols-md-3">{[0, 1, 2, 3, 4, 5].map((i) => <div className="col" key={i}><div className="skeleton" style={{ aspectRatio: '4/5' }} /></div>)}</div>}
          {data && data.items.length === 0 && <Empty title="No products found" text="Try removing a filter or searching for something else." to="/shop" cta="Clear filters" />}
          {data && data.items.length > 0 && (
            <div className="row g-3 g-md-4 row-cols-2 row-cols-md-3" style={{ opacity: loading ? 0.5 : 1 }}>
              {data.items.map((p) => <div className="col" key={p.id}><ProductCard p={p} /></div>)}
            </div>
          )}
          {data && <Pagination page={data.page} totalPages={data.totalPages} onChange={(p) => set({ page: p })} />}
        </div>
      </div>
      {drawer && (
        <div className="modal d-block" style={{ background: 'rgba(0,0,0,.45)' }} onClick={() => setDrawer(false)}>
          <div className="bg-white h-100 p-3 overflow-auto offcanvas-filters" onClick={(e) => e.stopPropagation()} role="dialog" aria-modal="true" aria-label="Filters">
            <div className="d-flex justify-content-between align-items-center"><h2 className="h5 mb-0">Filters</h2><button className="btn-close" aria-label="Close filters" onClick={() => setDrawer(false)} /></div>
            <Filters params={params} set={set} facets={data?.facets} />
            <button className="btn btn-tf w-100 mt-4" onClick={() => setDrawer(false)}>Show {data?.total ?? ''} results</button>
          </div>
        </div>
      )}
    </div>
  );
}
