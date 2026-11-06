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
// The cart belongs to whoever is using it: a visitor's cart lives under GUEST_KEY, a signed-in customer's under their own key.
// Signing in or out never carries one person's items over to the next person on the same browser.
const GUEST_KEY = 'tf_cart';
const userKey = (id) => `tf_cart_u${id}`;
function readCart(key) { try { return sanitize(JSON.parse(localStorage.getItem(key) || '[]')); } catch { return []; } }
function dropCart(key) { try { localStorage.removeItem(key); } catch { /* ignore */ } }

function reducer(state, a) {
  switch (a.type) {
    case 'add': return addLine(state, a.line);
    case 'qty': return setQty(state, a.variantId, a.qty);
    case 'remove': return removeLine(state, a.variantId);
    case 'clear': return [];
    case 'set': return a.lines;
    default: return state;
  }
}

export function CartProvider({ children }) {
  const { user, ready } = useAuth();
  const scope = user ? userKey(user.id) : GUEST_KEY;
  const [lines, dispatch] = useReducer(reducer, [], () => (getToken() ? [] : readCart(GUEST_KEY)));
  const loaded = useRef(getToken() ? null : GUEST_KEY); // which key the current `lines` were loaded from

  // Save only into the key the lines were loaded from (never the previous person's items into the new key).
  // This effect must stay ABOVE the switch effect: during a switch it still sees the old key and skips.
  useEffect(() => {
    if (loaded.current !== scope) return;
    try { localStorage.setItem(scope, JSON.stringify(lines)); } catch { /* ignore */ }
  }, [lines, scope]);
  // Switch carts when the person changes (login, logout, another account).
  useEffect(() => {
    if (!ready || loaded.current === scope) return;
    if (user) dropCart(GUEST_KEY); // signing in: the visitor's cart is not copied into the account
    else if (loaded.current) dropCart(GUEST_KEY); // signing out: leave an empty cart for the next visitor
    loaded.current = scope;
    dispatch({ type: 'set', lines: user ? readCart(scope) : [] });
  }, [scope, ready, user]);

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
