import axios from 'axios';

const TOKEN_KEY = 'tf_token';
const base = import.meta.env.VITE_API_URL || '/api';

export const api = axios.create({ baseURL: base, timeout: 20000 });

export function getToken() {
  try { return localStorage.getItem(TOKEN_KEY); } catch { return null; }
}
export function setToken(t) {
  try { if (t) localStorage.setItem(TOKEN_KEY, t); else localStorage.removeItem(TOKEN_KEY); } catch { /* storage blocked */ }
}

api.interceptors.request.use((cfg) => {
  const t = getToken();
  if (t) cfg.headers.Authorization = `Bearer ${t}`;
  return cfg;
});

let onUnauthorized = null;
export function setUnauthorizedHandler(fn) { onUnauthorized = fn; }

api.interceptors.response.use(
  (r) => r,
  (err) => {
    if (err.response?.status === 401 && getToken() && onUnauthorized) onUnauthorized();
    return Promise.reject(err);
  },
);

/** Turns any axios error into { message, code, fields } safe to show. */
export function apiError(err) {
  const e = err?.response?.data?.error;
  if (e) return { message: e.message || 'Something went wrong.', code: e.code, fields: e.fields || {} };
  if (err?.code === 'ECONNABORTED') return { message: 'The server took too long to respond. Please try again.', code: 'TIMEOUT', fields: {} };
  if (!err?.response) return { message: 'Cannot reach the server. Check your connection and try again.', code: 'NETWORK', fields: {} };
  return { message: 'Something went wrong. Please try again.', code: 'UNKNOWN', fields: {} };
}

/** Order-owner header for guests (secret order token). */
export function orderHeaders(token) {
  return token ? { 'X-Order-Token': token } : {};
}

export async function downloadInvoice(orderNumber, token) {
  const res = await api.get(`/orders/${orderNumber}/invoice`, { responseType: 'blob', headers: orderHeaders(token) });
  const url = URL.createObjectURL(res.data);
  const a = document.createElement('a');
  a.href = url;
  a.download = `invoice-${orderNumber}.html`;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 2000);
}
