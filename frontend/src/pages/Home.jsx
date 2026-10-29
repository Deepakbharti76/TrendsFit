import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api, apiError } from '../lib/api';
import { useSeo } from '../lib/seo';
import ProductCard from '../components/ProductCard';
import SizeFinder from '../components/SizeFinder';
import { ErrorBox, Stars } from '../components/Bits';
import BannerStrip from '../components/BannerStrip';

function Row({ title, sub, items, to }) {
  if (!items?.length) return null;
  return (
    <section className="section">
      <div className="container">
        <h2 className="section-title">{title}</h2>
        <p className="section-sub">{sub}</p>
        <div className="row g-3 g-md-4 row-cols-2 row-cols-md-3 row-cols-lg-4">
          {items.map((p) => <div className="col" key={p.id}><ProductCard p={p} /></div>)}
        </div>
        {to && <div className="text-center mt-4"><Link to={to} className="btn btn-tf-outline">View all</Link></div>}
      </div>
    </section>
  );
}

function SkeletonRow() {
  return (
    <div className="container section">
      <div className="row g-3 row-cols-2 row-cols-md-4">{[0, 1, 2, 3].map((i) => <div className="col" key={i}><div className="skeleton" style={{ aspectRatio: '4/5' }} /></div>)}</div>
    </div>
  );
}

export default function Home() {
  const [data, setData] = useState(null);
  const [err, setErr] = useState('');
  const [finder, setFinder] = useState(false);
  useSeo({
    title: null,
    description: 'Premium Jeans, Shirts & T-Shirts for everyday style. Free delivery, easy 7-day exchange.',
    jsonLd: { '@context': 'https://schema.org', '@type': 'Organization', name: 'TrendsFit', url: typeof window !== 'undefined' ? window.location.origin : '' },
  });

  const load = () => { setErr(''); api.get('/home').then((r) => setData(r.data)).catch((e) => setErr(apiError(e).message)); };
  useEffect(load, []);

  const heroImgs = data ? [...(data.featured || []), ...(data.newArrivals || [])].map((p) => p.image).filter(Boolean).slice(0, 2) : [];
  const catImage = (slug) => {
    const all = data ? [...(data.featured || []), ...(data.newArrivals || []), ...(data.bestsellers || [])] : [];
    return all.find((p) => p.categorySlug === slug)?.image;
  };

  return (
    <>
      <BannerStrip banners={data?.banners} />
      <section className="hero">
        <div className="container hero-inner">
          <div className="row align-items-center">
            <div className="col-lg-7">
              <div className="ls text-uppercase mb-3" style={{ color: 'var(--tf-sand)' }}>New season · Everyday fits</div>
              <h1>Style That <em>Fits</em> You</h1>
              <p className="lead my-4">Premium Jeans, Shirts &amp; T-Shirts for everyday style.</p>
              <div className="d-flex flex-wrap gap-2">
                <Link to="/shop" className="btn btn-light-tf">Shop Now</Link>
                <button className="btn btn-outline-light rounded-0 text-uppercase fw-semibold" style={{ letterSpacing: '.05em', fontSize: '.8rem' }} onClick={() => setFinder(true)}>Find my size</button>
              </div>
            </div>
            <div className="col-lg-5 hero-art" aria-hidden="true">
              <div className="stack">{heroImgs.map((src, i) => <img key={i} src={src} alt="" />)}</div>
            </div>
          </div>
        </div>
      </section>

      <div className="trust">
        <div className="container"><div className="row row-cols-2 row-cols-md-4">
          {[['🚚', 'Free delivery', 'On every order'], ['↺', '7-day exchange', 'After delivery'], ['🔒', 'Secure payments', 'Razorpay & COD'], ['★', 'Premium fabric', 'Made to last']].map(([i, a, b]) => (
            <div className="col" key={a}><div className="trust-item"><span className="ico" aria-hidden="true">{i}</span><div><strong className="d-block">{a}</strong><span className="text-muted small">{b}</span></div></div></div>
          ))}
        </div></div>
      </div>

      {err && <div className="container mt-4"><ErrorBox message={err} onRetry={load} /></div>}
      {!data && !err && <SkeletonRow />}

      {data && (
        <>
          <section className="section pb-0">
            <div className="container">
              <h2 className="section-title">Shop by category</h2>
              <p className="section-sub">Three wardrobe essentials, done right.</p>
              <div className="row g-3">
                {data.categories.map((c) => (
                  <div className="col-12 col-md-4" key={c.slug}>
                    <Link to={`/shop?category=${c.slug}`} className="cat-tile">
                      {catImage(c.slug) && <img src={catImage(c.slug)} alt="" loading="lazy" />}
                      <div className="label"><small>{c.productCount} styles</small>{c.name}</div>
                    </Link>
                  </div>
                ))}
              </div>
            </div>
          </section>
          <Row title="Featured" sub="Hand-picked favourites." items={data.featured} to="/shop" />
          <Row title="New arrivals" sub="Fresh in this week." items={data.newArrivals} to="/shop?sort=newest" />
          <Row title="Bestsellers" sub="What everyone is wearing." items={data.bestsellers} to="/shop?sort=popular" />

          <section className="section" style={{ background: 'var(--tf-sand)' }}>
            <div className="container text-center" style={{ maxWidth: 640 }}>
              <h2 className="section-title">Not sure about your size?</h2>
              <p className="mb-4">Tell us your waist or chest and we&apos;ll suggest a size. It is an estimate, so always check the size chart too.</p>
              <button className="btn btn-tf" onClick={() => setFinder(true)}>Find my size</button>
            </div>
          </section>

          {data.reviews?.length > 0 && (
            <section className="section">
              <div className="container">
                <h2 className="section-title">What customers say</h2>
                <p className="section-sub">Real reviews from TrendsFit customers.</p>
                <div className="row g-3">
                  {data.reviews.slice(0, 3).map((r, i) => (
                    <div className="col-md-4" key={i}><div className="summary h-100"><Stars value={r.rating} />
                      {r.title && <h3 className="h6 mt-2">{r.title}</h3>}<p className="mb-2">{r.body}</p>
                      <div className="small text-muted">{r.author}{r.verifiedPurchase ? ' · Verified purchase' : ''}</div></div></div>
                  ))}
                </div>
              </div>
            </section>
          )}
        </>
      )}
      {finder && <SizeFinder onClose={() => setFinder(false)} />}
    </>
  );
}
