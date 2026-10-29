// Admin navigation. Each entry is shown only when the signed-in staff member has the permission (the server enforces it again).
export const ADMIN_NAV = [
  { group: 'Overview', items: [
    { to: '/admin', label: 'Dashboard', perm: 'ANALYTICS_READ', end: true },
    { to: '/admin/reports', label: 'Reports & analytics', perm: 'ANALYTICS_READ' },
  ] },
  { group: 'Catalog', items: [
    { to: '/admin/products', label: 'Products', perm: 'PRODUCT_READ' },
    { to: '/admin/categories', label: 'Categories', perm: 'PRODUCT_READ' },
    { to: '/admin/inventory', label: 'Inventory & stock', perm: 'PRODUCT_READ' },
  ] },
  { group: 'Sales', items: [
    { to: '/admin/orders', label: 'Orders', perm: 'ORDER_READ' },
    { to: '/admin/shipments', label: 'Shipments & tracking', perm: 'ORDER_READ' },
    { to: '/admin/payments', label: 'Payments & refunds', perm: 'PAYMENT_READ' },
    { to: '/admin/exchanges', label: 'Exchanges', perm: 'ORDER_READ' },
    { to: '/admin/customers', label: 'Customers', perm: 'CUSTOMER_READ' },
    { to: '/admin/coupons', label: 'Coupons', perm: 'COUPON_WRITE' },
  ] },
  { group: 'Engage', items: [
    { to: '/admin/support', label: 'Support chat', perm: 'SUPPORT_CHAT', badge: 'unread' },
    { to: '/admin/reviews', label: 'Reviews', perm: 'REVIEW_MODERATE' },
    { to: '/admin/banners', label: 'Website banners', perm: 'CONTENT_WRITE' },
    { to: '/admin/newsletter', label: 'Newsletter', perm: 'NEWSLETTER_READ' },
  ] },
  { group: 'Settings', items: [
    { to: '/admin/settings/store', label: 'Store settings', perm: 'SETTINGS_WRITE' },
    { to: '/admin/settings/shipping', label: 'Shipping settings', perm: 'SETTINGS_WRITE' },
    { to: '/admin/settings/payments', label: 'Payment settings', perm: 'SETTINGS_WRITE' },
    { to: '/admin/settings/notifications', label: 'Email & notifications', perm: 'SETTINGS_WRITE' },
    { to: '/admin/staff', label: 'Admin users & permissions', perm: 'USER_ROLE_WRITE' },
    { to: '/admin/audit', label: 'Audit log', perm: 'AUDIT_READ' },
  ] },
];

const has = (user, perm) => !!user?.permissions?.some((p) => p === perm || p === `PERM_${perm}`);

/** Groups with only the items this user may open; empty groups are dropped. */
export function navFor(user) {
  return ADMIN_NAV.map((g) => ({ ...g, items: g.items.filter((i) => has(user, i.perm)) })).filter((g) => g.items.length > 0);
}

/** First page the user can open (used when someone without analytics access lands on /admin). */
export function firstAllowed(user) {
  const g = navFor(user)[0];
  return g ? g.items[0].to : null;
}

/** Pure helpers for the revenue widgets. */
export function trendText(changePercent) {
  if (changePercent === null || changePercent === undefined) return 'No revenue in the previous period to compare with';
  const n = Number(changePercent);
  if (n === 0) return 'Same as the previous period';
  return `${n > 0 ? '▲' : '▼'} ${Math.abs(n).toLocaleString('en-IN', { maximumFractionDigits: 1 })}% vs previous period`;
}

export function ringOffset(percent, circumference) {
  const p = Math.min(100, Math.max(0, Number(percent) || 0));
  return circumference * (1 - p / 100);
}

export function toCsv(rows, headers) {
  const esc = (v) => {
    let s = String(v ?? '');
    if (/^[=+\-@]/.test(s)) s = `'${s}`; // stop spreadsheet formula injection
    return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
  };
  return [headers.join(','), ...rows.map((r) => headers.map((h) => esc(r[h])).join(','))].join('\n');
}

/**
 * "View store": the first click opens the storefront in a new tab that is given a fixed name; later clicks
 * focus that same tab instead of opening more. The admin tab is never navigated away.
 */
export const STORE_TAB_NAME = 'trendsfit-store';
let storeTab = null;
export function openStoreTab(url = '/', win = window) {
  try {
    if (storeTab && !storeTab.closed) { storeTab.focus(); return storeTab; }
  } catch { storeTab = null; }
  const opened = win.open(url, STORE_TAB_NAME);
  if (opened) {
    try { opened.opener = null; } catch { /* cross-origin: ignore */ }
    storeTab = opened;
  }
  return opened;
}
export const resetStoreTab = () => { storeTab = null; };
