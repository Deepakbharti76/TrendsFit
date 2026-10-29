import { Link, useNavigate } from 'react-router-dom';
import { Price, Stars } from './Bits';
import { useAuth, useToast } from '../lib/AppContext';

export default function ProductCard({ p }) {
  const { user, wishlist, toggleWish } = useAuth();
  const toast = useToast();
  const nav = useNavigate();
  const wished = wishlist.includes(p.id);

  async function wish(e) {
    e.preventDefault();
    if (!user) { toast.push('Log in to save items to your wishlist.'); nav('/login', { state: { from: '/shop' } }); return; }
    try { await toggleWish(p.id); } catch { toast.push('Could not update wishlist.', 'danger'); }
  }

  return (
    <article className="product-card h-100">
      <Link to={`/product/${p.slug}`} className="card-media" aria-label={p.name}>
        {p.image ? <img src={p.image} alt={p.name} loading="lazy" width="600" height="750" className="img-a" /> : <div className="noimg">No image</div>}
        {p.image2 && <img src={p.image2} alt="" loading="lazy" width="600" height="750" className="img-b" aria-hidden="true" />}
        <div className="tags">
          {p.discountPercent > 0 && <span className="tag tag-sale">{p.discountPercent}% OFF</span>}
          {p.isNew && <span className="tag">NEW</span>}
          {!p.inStock && <span className="tag tag-out">SOLD OUT</span>}
        </div>
      </Link>
      <button className={`wish ${wished ? 'on' : ''}`} onClick={wish} aria-label={wished ? 'Remove from wishlist' : 'Add to wishlist'} aria-pressed={wished}>{wished ? '♥' : '♡'}</button>
      <div className="card-body-tf">
        <div className="small text-muted text-uppercase ls">{p.category}</div>
        <Link to={`/product/${p.slug}`} className="name">{p.name}</Link>
        <div className="d-flex align-items-center justify-content-between flex-wrap gap-1">
          <Price price={p.price} salePrice={p.salePrice} finalPrice={p.finalPrice} discountPercent={p.discountPercent} />
          {p.ratingCount > 0 && <Stars value={p.ratingAvg} count={p.ratingCount} small />}
        </div>
        {p.colors?.length > 0 && (
          <div className="swatches mt-2" aria-label="Colours">
            {p.colors.slice(0, 5).map((c) => <span key={c.name} className="swatch" title={c.name} style={{ background: c.hex || '#ccc' }} />)}
            {p.colors.length > 5 && <span className="small text-muted">+{p.colors.length - 5}</span>}
          </div>
        )}
      </div>
    </article>
  );
}
