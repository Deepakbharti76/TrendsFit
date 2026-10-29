// TEST-ONLY mock of the TrendsFit API so the React UI can be previewed and layout-tested without Java/MySQL.
// It is NOT part of the product, ships no real data and is never used in production.  Run: node scripts/mock-api.mjs
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const dir = path.join(path.dirname(fileURLToPath(import.meta.url)), '..', 'frontend', 'public', 'img', 'demo');
const slugs = [...new Set(fs.readdirSync(dir).map((f) => f.replace(/-[12]\.svg$/, '')))];
const catOf = (s) => (/jeans/.test(s) ? ['jeans', 'Jeans'] : /shirt/.test(s) && !/t-shirt|tee|polo|henley/.test(s) ? ['shirts', 'Shirts'] : ['t-shirts', 'T-Shirts']);
const sizesFor = (c) => (c === 'jeans' ? ['28', '30', '32', '34'] : ['S', 'M', 'L', 'XL']);
const products = slugs.map((slug, i) => {
  const [cs, cn] = catOf(slug);
  const price = 599 + ((i * 137) % 1200);
  const sale = i % 3 === 0 ? Math.round(price * 0.8) : null;
  const variants = [];
  let id = (i + 1) * 100;
  for (const size of sizesFor(cs)) for (const [color, hex] of [['Indigo', '#2b3a67'], ['Black', '#1c1c1e']]) variants.push({ id: id++, sku: `${slug}-${size}-${color}`, size, color, colorHex: hex, stock: (i + id) % 7 === 0 ? 0 : 8, price: sale || price });
  return { id: i + 1, slug, name: slug.split('-').map((w) => w[0].toUpperCase() + w.slice(1)).join(' '), sku: `MOCK-${i + 1}`, category: cn, categorySlug: cs, description: 'Mock product for UI testing only.', fit: 'Regular', material: 'Cotton', care: 'Machine wash cold.', sizeChart: 'Size|Chest\nS|36\nM|38', price, salePrice: sale, finalPrice: sale || price, discountPercent: sale ? 20 : 0, images: [{ url: `/img/demo/${slug}-1.svg`, alt: slug }, { url: `/img/demo/${slug}-2.svg`, alt: slug }], variants, ratingAvg: 4.5, ratingCount: 3, demo: true };
});
const card = (p) => ({ id: p.id, slug: p.slug, name: p.name, category: p.category, categorySlug: p.categorySlug, price: p.price, salePrice: p.salePrice, finalPrice: p.finalPrice, discountPercent: p.discountPercent, image: p.images[0].url, image2: p.images[1].url, colors: [{ name: 'Indigo', hex: '#2b3a67' }, { name: 'Black', hex: '#1c1c1e' }], inStock: true, ratingAvg: 4.5, ratingCount: 3, isNew: p.id % 2 === 0, isBestseller: false });
const orders = {};
const send = (res, code, body, type = 'application/json') => { res.writeHead(code, { 'Content-Type': type }); res.end(typeof body === 'string' ? body : JSON.stringify(body)); };
const read = (req) => new Promise((r) => { let b = ''; req.on('data', (c) => (b += c)); req.on('end', () => r(b ? JSON.parse(b) : {})); });

http.createServer(async (req, res) => {
  const u = new URL(req.url, 'http://x'); const p = u.pathname; const q = u.searchParams;
  if (p === '/api/config') return send(res, 200, { businessName: 'TrendsFit', supportEmail: 'support@example.test', codEnabled: true, codMaxOrderValue: 10000, onlinePaymentEnabled: false, exchangeWindowDays: 7, freeShipping: true });
  if (p === '/api/categories') return send(res, 200, [{ slug: 'jeans', name: 'Jeans' }, { slug: 't-shirts', name: 'T-Shirts' }, { slug: 'shirts', name: 'Shirts' }]);
  if (p === '/api/home') return send(res, 200, { featured: products.slice(0, 4).map(card), newArrivals: products.slice(4, 8).map(card), bestsellers: products.slice(8, 12).map(card), categories: [{ slug: 'jeans', name: 'Jeans', description: 'Slim, straight and relaxed fits.', productCount: 7 }, { slug: 't-shirts', name: 'T-Shirts', description: 'Everyday tees.', productCount: 7 }, { slug: 'shirts', name: 'Shirts', description: 'Casual shirts.', productCount: 6 }], reviews: [{ rating: 5, title: 'Perfect fit', body: 'Mock review text.', author: 'Asha', verifiedPurchase: true }] });
  if (p === '/api/products') {
    let l = products.filter((x) => (!q.get('category') || x.categorySlug === q.get('category')) && (!q.get('q') || x.name.toLowerCase().includes(q.get('q').toLowerCase())));
    const ps = Number(q.get('pageSize') || 12); const pg = Number(q.get('page') || 0);
    return send(res, 200, { items: l.slice(pg * ps, pg * ps + ps).map(card), page: pg, pageSize: ps, total: l.length, totalPages: Math.ceil(l.length / ps), facets: { sizes: ['28', '30', '32', 'S', 'M', 'L'], colors: ['Black', 'Indigo'], fits: ['Regular'], priceMin: 599, priceMax: 1800 } });
  }
  let m;
  if ((m = p.match(/^\/api\/products\/([^/]+)$/))) { const x = products.find((y) => y.slug === m[1]); return x ? send(res, 200, x) : send(res, 404, { error: { code: 'NOT_FOUND', message: 'Product not found.' } }); }
  if (/^\/api\/products\/[^/]+\/related$/.test(p)) return send(res, 200, products.slice(0, 4).map(card));
  if (/^\/api\/products\/[^/]+\/reviews$/.test(p)) return send(res, 200, [{ id: 1, rating: 5, title: 'Great', body: 'Mock review.', author: 'Asha R.', verifiedPurchase: true }]);
  if (p === '/api/delivery/check') return send(res, 200, { checked: false, serviceable: null, message: 'Delivery availability and dates will be confirmed after you place your order.' });
  if (p === '/api/cart/quote') {
    const b = await read(req); const items = []; let sub = 0;
    for (const it of b.items) { const pr = products.find((x) => x.variants.some((v) => v.id === it.variantId)); const v = pr.variants.find((x) => x.id === it.variantId); const lt = v.price * it.qty; sub += lt; items.push({ variantId: v.id, slug: pr.slug, name: pr.name, size: v.size, color: v.color, unitPrice: v.price, qty: it.qty, lineTotal: lt, inStock: true, stock: 8, image: pr.images[0].url }); }
    return send(res, 200, { items, subtotal: sub, discount: 0, shipping: 0, total: sub, couponCode: null, couponMessage: null, codAvailable: true, onlineAvailable: false });
  }
  if (p === '/api/orders' && req.method === 'POST') { const b = await read(req); const n = 'TF000000-MOCK1'; orders[n] = { b }; return send(res, 200, { orderNumber: n, accessToken: 'mocktoken', status: 'PROCESSING', paymentMethod: b.paymentMethod, total: 0 }); }
  if ((m = p.match(/^\/api\/orders\/([^/]+)$/))) return send(res, 200, { orderNumber: m[1], status: 'PROCESSING', statusLabel: 'Processing', paymentMethod: 'COD', paymentStatus: 'COD', customerName: 'Mock Buyer', email: 'b@example.test', phone: '9876543210', shipping: { line1: '1 Test Rd', city: 'Pune', state: 'Maharashtra', pincode: '411001' }, subtotal: 1199, discount: 0, shippingCharge: 0, total: 1199, createdAt: new Date().toISOString(), items: [{ id: 1, name: 'Mock Jeans', size: '32', color: 'Indigo', qty: 1, lineTotal: 1199, image: '/img/demo/' + slugs[0] + '-1.svg', slug: slugs[0], variantId: 100 }], timeline: [{ status: 'PROCESSING', note: 'Order placed (Cash on Delivery)', at: new Date().toISOString() }], shipments: [], exchangeEligibility: { eligible: false, message: 'Exchange is available after your order is delivered.' }, exchanges: [], canPay: false, invoiceAvailable: true });
  if (p === '/api/auth/login') return send(res, 200, { token: 'mock', user: { id: 1, email: 'admin@example.test', fullName: 'Mock Admin', role: 'SUPER_ADMIN', staff: true, emailVerified: true, permissions: ['PERM_ANALYTICS_READ', 'PERM_PRODUCT_READ', 'PERM_PRODUCT_WRITE', 'PERM_ORDER_READ'] } });
  if (p === '/api/me') return send(res, 200, { id: 1, email: 'admin@example.test', fullName: 'Mock Admin', role: 'SUPER_ADMIN', staff: true, emailVerified: true, permissions: ['PERM_ANALYTICS_READ', 'PERM_PRODUCT_READ', 'PERM_PRODUCT_WRITE', 'PERM_ORDER_READ'] });
  if (p === '/api/me/wishlist/ids') return send(res, 200, []);
  if (p === '/api/admin/dashboard') return send(res, 200, { revenueToday: 0, revenue30d: 0, orders30d: 0, ordersToday: 0, customers: 0, newCustomers30d: 0, pendingExchanges: 0, pendingReviews: 0, lowStockCount: 0, lowStockThreshold: 5, ordersByStatus: [], dailySeries: [], topProducts: [], lowStock: [], integrations: { razorpay: false, razorpayWebhook: false, shiprocket: false, email: false } });
  if (p === '/api/admin/products') return send(res, 200, { items: products.slice(0, 10).map((x) => ({ id: x.id, name: x.name, slug: x.slug, sku: x.sku, status: 'ACTIVE', price: x.price, salePrice: x.salePrice, category: x.category, stock: 40, demo: true, image: x.images[0].url })), total: products.length, page: 0, pageSize: 20, totalPages: 1 });
  send(res, 404, { error: { code: 'NOT_FOUND', message: 'Not mocked.' } });
}).listen(8080, () => console.log('TEST-ONLY mock API on :8080'));
