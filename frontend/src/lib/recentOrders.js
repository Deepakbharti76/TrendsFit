const KEY = 'tf_recent_orders';

/** Remembers guest order numbers + secret tokens in this browser only, so the buyer can reopen them. */
export function rememberOrder(orderNumber, token) {
  try {
    const list = JSON.parse(localStorage.getItem(KEY) || '[]').filter((o) => o.orderNumber !== orderNumber);
    list.unshift({ orderNumber, token, at: Date.now() });
    localStorage.setItem(KEY, JSON.stringify(list.slice(0, 10)));
  } catch { /* storage blocked */ }
}
export function recentOrders() {
  try { return JSON.parse(localStorage.getItem(KEY) || '[]'); } catch { return []; }
}
