import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api, apiError } from '../lib/api';
import { useSeo } from '../lib/seo';
import { useAuth, useCart, useToast } from '../lib/AppContext';
import { ErrorBox, Price, Spinner, Stars } from '../components/Bits';
import ProductCard from '../components/ProductCard';
import SizeFinder from '../components/SizeFinder';

function Gallery({ images, name }) {
  const [i, setI] = useState(0);
  const [zoom, setZoom] = useState(false);
  const [pos, setPos] = useState('50% 50%');
  const list = images?.length ? images : [];
  useEffect(() => setI(0), [images]);
  if (!list.length) return <div className="gallery-main d-flex align-items-center justify-content-center text-muted">No image</div>;
  return (
    <div>
      <div className={`gallery-main ${zoom ? 'zoom' : ''}`}
        onClick={() => setZoom((z) => !z)}
        onMouseMove={(e) => { if (!zoom) return; const r = e.currentTarget.getBoundingClientRect(); setPos(`${((e.clientX - r.left) / r.width) * 100}% ${((e.clientY - r.top) / r.height) * 100}%`); }}
        onMouseLeave={() => setZoom(false)} role="button" tabIndex={0} aria-label="Toggle zoom"
        onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); setZoom((z) => !z); } }}>
        <img src={list[i].url} alt={list[i].alt || name} style={{ transformOrigin: pos }} width="800" height="1000" />
      </div>
      {list.length > 1 && (
        <div className="thumbs">
          {list.map((im, k) => <button key={im.url} className={k === i ? 'on' : ''} onClick={() => setI(k)} aria-label={`Image ${k + 1}`}><img src={im.url} alt="" loading="lazy" /></button>)}
        </div>
      )}
    </div>
  );
}

export default function Product() {
  const { slug } = useParams();
  const nav = useNavigate();
  const toast = useToast();
  const cart = useCart();
  const { user, wishlist, toggleWish } = useAuth();
  const [p, setP] = useState(null);
  const [err, setErr] = useState('');
  const [related, setRelated] = useState([]);
  const [reviews, setReviews] = useState([]);
  const [size, setSize] = useState('');
  const [color, setColor] = useState('');
  const [qty, setQty] = useState(1);
  const [pin, setPin] = useState('');
  const [pinRes, setPinRes] = useState(null);
  const [finder, setFinder] = useState(false);
  const [tab, setTab] = useState('desc');

  const load = () => {
    setErr(''); setP(null);
    api.get(`/products/${slug}`).then((r) => {
      setP(r.data);
      const first = r.data.variants.find((v) => v.stock > 0) || r.data.variants[0];
      setSize(first?.size || ''); setColor(first?.color || '');
    }).catch((e) => setErr(apiError(e).message));
    api.get(`/products/${slug}/related`).then((r) => setRelated(r.data)).catch(() => {});
    api.get(`/products/${slug}/reviews`).then((r) => setReviews(r.data)).catch(() => {});
  };
  useEffect(load, [slug]); // eslint-disable-line

  const sizes = useMemo(() => [...new Set((p?.variants || []).map((v) => v.size))], [p]);
  const colors = useMemo(() => {
    const m = new Map();
    (p?.variants || []).forEach((v) => { if (!m.has(v.color)) m.set(v.color, v.colorHex); });
    return [...m.entries()];
  }, [p]);
  const variant = (p?.variants || []).find((v) => v.size === size && v.color === color);
  const stock = variant?.stock ?? 0;
  const sizeAvailable = (s) => (p?.variants || []).some((v) => v.size === s && v.color === color && v.stock > 0);

  useSeo({
    title: p?.seoTitle || p?.name, description: p?.seoDescription || p?.description?.slice(0, 155), image: p?.images?.[0]?.url,
    jsonLd: p && {
      '@context': 'https://schema.org', '@type': 'Product', name: p.name, image: (p.images || []).map((i) => window.location.origin + i.url), sku: p.sku,
      description: p.description, brand: { '@type': 'Brand', name: 'TrendsFit' },
      offers: { '@type': 'Offer', priceCurrency: 'INR', price: String(p.finalPrice), availability: p.variants.some((v) => v.stock > 0) ? 'https://schema.org/InStock' : 'https://schema.org/OutOfStock', url: window.location.href },
      ...(p.ratingCount > 0 ? { aggregateRating: { '@type': 'AggregateRating', ratingValue: String(p.ratingAvg), reviewCount: p.ratingCount } } : {}),
    },
  });

  if (err) return <div className="container section"><ErrorBox message={err} onRetry={load} /><Link to="/shop">Back to shop</Link></div>;
  if (!p) return <Spinner />;

  const wished = wishlist.includes(p.id);
  const unitPrice = variant?.price ?? p.finalPrice;

  function add(goCart) {
    if (!variant) { toast.push('Please choose a size and colour.', 'warning'); return; }
    if (stock < 1) { toast.push('This option is out of stock.', 'warning'); return; }
    cart.add({ variantId: variant.id, qty, slug: p.slug, name: p.name, size: variant.size, color: variant.color, image: p.images?.[0]?.url });
    toast.push('Added to cart');
    if (goCart) nav('/cart');
  }

  async function checkPin(e) {
    e.preventDefault();
    setPinRes(null);
    try { setPinRes((await api.get('/delivery/check', { params: { pincode: pin } })).data); } catch (x) { setPinRes({ error: apiError(x).message }); }
  }

  async function wish() {
    if (!user) { toast.push('Log in to save items to your wishlist.'); nav('/login', { state: { from: `/product/${slug}` } }); return; }
    try { await toggleWish(p.id); } catch { toast.push('Could not update wishlist.', 'danger'); }
  }

  const kind = p.categorySlug === 'jeans' ? 'BOTTOM' : 'TOP';
  return (
    <div className="container section has-sticky">
      <nav aria-label="Breadcrumb" className="small mb-3"><Link to="/">Home</Link> / <Link to={`/shop?category=${p.categorySlug}`}>{p.category}</Link> / {p.name}</nav>
      {p.demo && <div className="alert alert-warning py-2 small">Demo product for development. Replace with real products from the admin panel.</div>}
      <div className="row g-4 g-lg-5">
        <div className="col-lg-7"><Gallery images={p.images} name={p.name} /></div>
        <div className="col-lg-5">
          <div className="text-uppercase text-muted ls">{p.category}{p.fit ? ` · ${p.fit} fit` : ''}</div>
          <h1 className="display-font h2 mt-1">{p.name}</h1>
          {p.ratingCount > 0 && <div className="mb-2"><Stars value={p.ratingAvg} count={p.ratingCount} /></div>}
          <Price price={p.price} salePrice={p.salePrice} finalPrice={unitPrice} discountPercent={p.discountPercent} size="lg" />
          <div className="small text-muted mb-3">Inclusive of all charges. Free delivery.</div>

          <div className="mb-3">
            <div className="fw-semibold mb-2">Colour: <span className="fw-normal">{color}</span></div>
            {colors.map(([c, hex]) => <button key={c} type="button" className={`color-btn ${c === color ? 'on' : ''}`} style={{ background: hex || '#ccc' }} onClick={() => setColor(c)} aria-label={c} aria-pressed={c === color} />)}
          </div>
          <div className="mb-3">
            <div className="d-flex justify-content-between"><span className="fw-semibold mb-2">Size</span><button className="btn btn-link btn-sm p-0" onClick={() => setFinder(true)}>Find my size</button></div>
            {sizes.map((s) => <button key={s} type="button" className={`size-btn ${s === size ? 'on' : ''}`} disabled={!sizeAvailable(s)} onClick={() => setSize(s)} aria-pressed={s === size}>{s}</button>)}
            {variant && stock > 0 && stock <= 5 && <div className="text-danger small mt-1">Only {stock} left</div>}
            {variant && stock === 0 && <div className="text-danger small mt-1">Out of stock in this colour and size</div>}
          </div>
          <div className="d-flex align-items-center gap-3 mb-3">
            <div className="qty" role="group" aria-label="Quantity">
              <button type="button" aria-label="Decrease" onClick={() => setQty((q) => Math.max(1, q - 1))}>−</button><span aria-live="polite">{qty}</span>
              <button type="button" aria-label="Increase" onClick={() => setQty((q) => Math.min(Math.max(1, Math.min(10, stock)), q + 1))}>+</button>
            </div>
            <button className="btn btn-tf flex-grow-1 d-none d-lg-block" onClick={() => add(false)} disabled={stock < 1}>{stock < 1 ? 'Out of stock' : 'Add to cart'}</button>
            <button className={`btn btn-tf-outline d-none d-lg-block`} onClick={wish} aria-label="Wishlist">{wished ? '♥' : '♡'}</button>
          </div>
          <button className="btn btn-tf-outline w-100 mb-3 d-none d-lg-block" onClick={() => add(true)} disabled={stock < 1}>Buy now</button>

          <form className="mb-3" onSubmit={checkPin}>
            <label htmlFor="pin" className="form-label fw-semibold">Check delivery</label>
            <div className="d-flex gap-2"><input id="pin" className="form-control" inputMode="numeric" maxLength={6} placeholder="Enter pincode" value={pin} onChange={(e) => setPin(e.target.value.replace(/\D/g, ''))} /><button className="btn btn-tf-outline" disabled={pin.length !== 6}>Check</button></div>
            {pinRes && <div className={`small mt-2 ${pinRes.error || pinRes.serviceable === false ? 'text-danger' : ''}`} role="status">{pinRes.error || pinRes.message}</div>}
          </form>
          <ul className="list-unstyled small text-muted">
            <li>✓ Easy exchange within 7 days of delivery</li><li>✓ Secure online payment or Cash on Delivery</li>
          </ul>
        </div>
      </div>

      <div className="mt-5">
        <ul className="nav nav-tabs info-tabs" role="tablist">
          {[['desc', 'Description'], ['size', 'Size chart'], ['care', 'Fabric & care'], ['rev', `Reviews (${reviews.length})`]].map(([k, l]) => (
            <li className="nav-item" key={k}><button className={`nav-link ${tab === k ? 'active' : ''}`} onClick={() => setTab(k)} role="tab" aria-selected={tab === k}>{l}</button></li>
          ))}
        </ul>
        <div className="bg-white border border-top-0 p-3 p-md-4" style={{ whiteSpace: 'pre-line' }}>
          {tab === 'desc' && (p.description || 'No description yet.')}
          {tab === 'size' && (p.sizeChart || 'Size chart coming soon. Use Find my size for an estimate.')}
          {tab === 'care' && <div>{p.material && <p><strong>Material:</strong> {p.material}</p>}{p.care || 'Machine wash cold. Do not bleach.'}</div>}
          {tab === 'rev' && (reviews.length === 0 ? 'No reviews yet.' : reviews.map((r) => (
            <div key={r.id} className="mb-3 pb-3 border-bottom" style={{ whiteSpace: 'normal' }}>
              <Stars value={r.rating} small /> <strong className="ms-2">{r.title}</strong>
              <div className="small text-muted">{r.author}{r.verifiedPurchase ? ' · Verified purchase' : ''}</div>
              <p className="mb-0">{r.body}</p>
            </div>
          )))}
        </div>
      </div>

      {related.length > 0 && (
        <section className="mt-5"><h2 className="display-font h4 mb-3">You may also like</h2>
          <div className="row g-3 row-cols-2 row-cols-md-4">{related.slice(0, 4).map((r) => <div className="col" key={r.id}><ProductCard p={r} /></div>)}</div>
        </section>
      )}

      <div className="sticky-buy">
        <div><div className="small text-muted">{size}{color ? ` · ${color}` : ''}</div><Price price={p.price} salePrice={p.salePrice} finalPrice={unitPrice} discountPercent={0} /></div>
        <button className="btn btn-tf flex-grow-1" onClick={() => add(false)} disabled={stock < 1}>{stock < 1 ? 'Out of stock' : 'Add to cart'}</button>
      </div>
      {finder && <SizeFinder kind={kind} fit={p.fit} onPick={(s) => sizes.includes(s) && setSize(s)} onClose={() => setFinder(false)} />}
    </div>
  );
}
