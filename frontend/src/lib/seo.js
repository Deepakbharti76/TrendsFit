import { useEffect } from 'react';

function setMeta(attr, key, content) {
  if (content == null) return;
  let el = document.head.querySelector(`meta[${attr}="${key}"]`);
  if (!el) { el = document.createElement('meta'); el.setAttribute(attr, key); document.head.appendChild(el); }
  el.setAttribute('content', content);
}

/** Per-page title, description, canonical, Open Graph and optional JSON-LD. */
export function useSeo({ title, description, image, path, jsonLd, noindex } = {}) {
  useEffect(() => {
    const full = title ? `${title} | TrendsFit` : 'TrendsFit | Premium Jeans, Shirts & T-Shirts';
    document.title = full;
    if (description) setMeta('name', 'description', description);
    setMeta('property', 'og:title', full);
    if (description) setMeta('property', 'og:description', description);
    if (image) setMeta('property', 'og:image', image.startsWith('http') ? image : window.location.origin + image);
    setMeta('name', 'robots', noindex ? 'noindex,nofollow' : 'index,follow');
    const href = window.location.origin + (path ?? window.location.pathname);
    let link = document.head.querySelector('link[rel="canonical"]');
    if (!link) { link = document.createElement('link'); link.rel = 'canonical'; document.head.appendChild(link); }
    link.href = href;
    setMeta('property', 'og:url', href);
    let ld = document.getElementById('tf-jsonld');
    if (jsonLd) {
      if (!ld) { ld = document.createElement('script'); ld.type = 'application/ld+json'; ld.id = 'tf-jsonld'; document.head.appendChild(ld); }
      ld.textContent = JSON.stringify(jsonLd).replace(/</g, '\\u003c');
    } else if (ld) ld.remove();
  }, [title, description, image, path, noindex, JSON.stringify(jsonLd || null)]); // eslint-disable-line react-hooks/exhaustive-deps
}
