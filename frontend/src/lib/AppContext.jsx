import { createContext, useCallback, useContext, useEffect, useMemo, useReducer, useRef, useState } from 'react';
import { api, getToken, setToken, setUnauthorizedHandler } from './api';
import { addLine, removeLine, sanitize, setQty, countItems } from './cart';

// ------------------------------------------------------------------ toast
const ToastCtx = createContext({ push: () => {} });
export const useToast = () => useContext(ToastCtx);

export function ToastProvider({ children }) {
  const [items, setItems] = useState([]);
  // The same message + kind is shown once at a time: a repeat while it is still on screen just restarts its timer.
  const timers = useRef(new Map());
  const push = useCallback((message, kind = 'dark') => {
    const key = `${kind}|${message}`;
    const id = key;
    setItems((x) => (x.some((i) => i.id === id) ? x : [...x, { id, message, kind }]));
    clearTimeout(timers.current.get(key));
    timers.current.set(key, setTimeout(() => {
      timers.current.delete(key);
      setItems((x) => x.filter((i) => i.id !== id));
    }, 3800));
  }, []);
  return (
    <ToastCtx.Provider value={{ push }}>
      {children}
      <div className="toast-container position-fixed top-0 end-0 p-3" style={{ zIndex: 2000 }} role="status" aria-live="polite">
        {items.map((t) => (
          <div key={t.id} className={`toast show align-items-center text-bg-${t.kind} border-0 mb-2`}>
            <div className="d-flex"><div className="toast-body">{t.message}</div></div>
          </div>
        ))}
      </div>
    </ToastCtx.Provider>
  );
}

// ------------------------------------------------------------------ site config
const ConfigCtx = createContext({ businessName: 'TrendsFit', codEnabled: true, onlinePaymentEnabled: false, exchangeWindowDays: 7 });
export const useConfig = () => useContext(ConfigCtx);

export function ConfigProvider({ children }) {
  const [cfg, setCfg] = useState({ businessName: 'TrendsFit', codEnabled: true, onlinePaymentEnabled: false, exchangeWindowDays: 7 });
  useEffect(() => { api.get('/config').then((r) => setCfg((c) => ({ ...c, ...r.data }))).catch(() => {}); }, []);
  return <ConfigCtx.Provider value={cfg}>{children}</ConfigCtx.Provider>;
}

// ------------------------------------------------------------------ auth
const AuthCtx = createContext(null);
export const useAuth = () => useContext(AuthCtx);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [ready, setReady] = useState(!getToken());
  const [wishlist, setWishlist] = useState([]);

  const logout = useCallback(() => { setToken(null); setUser(null); setWishlist([]); }, []);

  useEffect(() => {
    setUnauthorizedHandler(logout);
    if (!getToken()) return;
    api.get('/me').then((r) => setUser(r.data)).catch(() => setToken(null)).finally(() => setReady(true));
  }, [logout]);

  useEffect(() => {
    if (!user) return;
    api.get('/me/wishlist/ids').then((r) => setWishlist(r.data)).catch(() => {});
  }, [user]);

  const login = useCallback((session) => { setToken(session.token); setUser(session.user); }, []);

  const toggleWish = useCallback(async (productId) => {
    if (!user) return false;
    if (wishlist.includes(productId)) {
      await api.delete(`/me/wishlist/${productId}`);
      setWishlist((w) => w.filter((x) => x !== productId));
    } else {
      await api.post(`/me/wishlist/${productId}`);
      setWishlist((w) => [...w, productId]);
    }
    return true;
  }, [user, wishlist]);

  const value = useMemo(() => ({ user, ready, login, logout, setUser, wishlist, toggleWish }), [user, ready, login, logout, wishlist, toggleWish]);
  return <AuthCtx.Provider value={value}>{children}</AuthCtx.Provider>;
}

// ------------------------------------------------------------------ cart
const CartCtx = createContext(null);
export const useCart = () => useContext(CartCtx);
const CART_KEY = 'tf_cart';

function reducer(state, a) {
  switch (a.type) {
    case 'add': return addLine(state, a.line);
    case 'qty': return setQty(state, a.variantId, a.qty);
    case 'remove': return removeLine(state, a.variantId);
    case 'clear': return [];
    default: return state;
  }
}

export function CartProvider({ children }) {
  const [lines, dispatch] = useReducer(reducer, [], () => {
    try { return sanitize(JSON.parse(localStorage.getItem(CART_KEY) || '[]')); } catch { return []; }
  });
  useEffect(() => { try { localStorage.setItem(CART_KEY, JSON.stringify(lines)); } catch { /* ignore */ } }, [lines]);
  const value = useMemo(() => ({
    lines,
    count: countItems(lines),
    add: (line) => dispatch({ type: 'add', line }),
    setQty: (variantId, qty) => dispatch({ type: 'qty', variantId, qty }),
    remove: (variantId) => dispatch({ type: 'remove', variantId }),
    clear: () => dispatch({ type: 'clear' }),
  }), [lines]);
  return <CartCtx.Provider value={value}>{children}</CartCtx.Provider>;
}
