import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api, apiError } from '../lib/api';
import { useCart, useToast } from '../lib/AppContext';
import { useSeo } from '../lib/seo';
import { Empty, ErrorBox, Spinner } from '../components/Bits';
import { inr } from '../lib/money';

/** Shared by Cart and Checkout: asks the server to price the cart. The browser never computes totals. */
export function useQuote(lines, couponCode, email) {
  const [quote, setQuote] = useState(null);
  const [err, setErr] = useState('');
  const [loading, setLoading] = useState(false);
  const key = JSON.stringify([lines.map((l) => [l.variantId, l.qty]), couponCode, email]);
  useEffect(() => {
    if (lines.length === 0) { setQuote(null); setErr(''); return undefined; }
    let live = true;
    setLoading(true);
    api.post('/cart/quote', { items: lines.map((l) => ({ variantId: l.variantId, qty: l.qty })), couponCode: couponCode || null, email: email || null })
      .then((r) => { if (live) { setQuote(r.data); setErr(''); } })
      .catch((e) => { if (live) { const x = apiError(e); setErr(x.message); setQuote(null); } })
      .finally(() => live && setLoading(false));
    return () => { live = false; };
  }, [key]); // eslint-disable-line react-hooks/exhaustive-deps
  return { quote, err, loading };
}

export default function Cart() {
  const cart = useCart();
  const toast = useToast();
  const [code, setCode] = useState('');
  const [applied, setApplied] = useState('');
  const { quote, err, loading } = useQuote(cart.lines, applied, '');
  useSeo({ title: 'Your cart', noindex: true });

  // If the server says a coupon is invalid, drop it so the cart can still be priced.
  useEffect(() => { if (err && applied && /coupon/i.test(err)) { toast.push(err, 'warning'); setApplied(''); } }, [err]); // eslint-disable-line

  // Items that vanished from the catalogue make the whole quote fail; let the customer remove them.
  const stale = err && !/coupon/i.test(err);

  if (cart.lines.length === 0) return <div className="container section"><Empty title="Your cart is empty" text="Add something you love." to="/shop" cta="Continue shopping" /></div>;

  return (
    <div className="container section">
      <h1 className="display-font h2 mb-4">Your cart</h1>
      {stale && <ErrorBox message={err} />}
      <div className="row g-4">
        <div className="col-lg-8">
          {(quote?.items || cart.lines.map((l) => ({ ...l, unitPrice: null, lineTotal: null, inStock: true }))).map((l) => {
            const local = cart.lines.find((x) => x.variantId === l.variantId) || {};
            return (
              <div className="cart-line" key={l.variantId}>
                {(l.image || local.image) ? <img src={l.image || local.image} alt="" /> : <div style={{ width: 84 }} />}
                <div className="flex-grow-1">
                  <Link to={`/product/${l.slug || local.slug}`} className="fw-semibold">{l.name || local.name}</Link>
                  <div className="small text-muted">Size {l.size || local.size} · {l.color || local.color}</div>
                  {l.unitPrice != null && <div className="mt-1">{inr(l.unitPrice)}</div>}
                  {l.inStock === false && <div className="text-danger small">Only {l.stock} left. Reduce the quantity.</div>}
                  <div className="d-flex align-items-center gap-3 mt-2">
                    <div className="qty" role="group" aria-label="Quantity">
                      <button aria-label="Decrease quantity" onClick={() => cart.setQty(l.variantId, local.qty - 1)}>−</button><span>{local.qty}</span>
                      <button aria-label="Increase quantity" onClick={() => cart.setQty(l.variantId, local.qty + 1)}>+</button>
                    </div>
                    <button className="btn btn-link btn-sm text-danger p-0" onClick={() => cart.remove(l.variantId)}>Remove</button>
                  </div>
                </div>
                <div className="fw-semibold">{l.lineTotal != null ? inr(l.lineTotal) : ''}</div>
              </div>
            );
          })}
        </div>
        <div className="col-lg-4">
          <div className="summary">
            <h2 className="h5 mb-3">Order summary</h2>
            {!quote && loading && <Spinner />}
            {quote && (
              <>
                <div className="d-flex justify-content-between mb-1"><span>Subtotal</span><span>{inr(quote.subtotal)}</span></div>
                {Number(quote.discount) > 0 && <div className="d-flex justify-content-between mb-1 text-success"><span>Discount ({quote.couponCode})</span><span>−{inr(quote.discount)}</span></div>}
                <div className="d-flex justify-content-between mb-1"><span>Shipping</span><span>{Number(quote.shipping) === 0 ? 'Free' : inr(quote.shipping)}</span></div>
                <hr />
                <div className="d-flex justify-content-between fw-bold fs-5 mb-3"><span>Total</span><span>{inr(quote.total)}</span></div>
              </>
            )}
            <form className="d-flex gap-2 mb-3" onSubmit={(e) => { e.preventDefault(); setApplied(code.trim().toUpperCase()); }}>
              <input className="form-control" placeholder="Coupon code" value={code} onChange={(e) => setCode(e.target.value)} aria-label="Coupon code" />
              <button className="btn btn-tf-outline" type="submit">Apply</button>
            </form>
            {applied && quote?.couponCode && <div className="small text-success mb-2">{quote.couponMessage} <button className="btn btn-link btn-sm p-0" onClick={() => { setApplied(''); setCode(''); }}>Remove</button></div>}
            <Link className={`btn btn-tf w-100 ${!quote || quote.items.some((i) => !i.inStock) ? 'disabled' : ''}`} to="/checkout" state={{ coupon: applied }} aria-disabled={!quote}>Checkout</Link>
            <div className="small text-muted mt-2">Prices and totals are confirmed by our server at checkout.</div>
          </div>
        </div>
      </div>
    </div>
  );
}
