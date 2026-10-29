import { useEffect, useState } from "react";
import {
  Link,
  NavLink,
  Outlet,
  useLocation,
  useNavigate,
} from "react-router-dom";
import { useAuth, useCart, useConfig } from "../lib/AppContext";
import { api } from "../lib/api";
import SearchSuggest from "./SearchSuggest";

const NAV = [
  ["/shop", "Shop All"],
  ["/shop?category=jeans", "Jeans"],
  ["/shop?category=t-shirts", "T-Shirts"],
  ["/shop?category=shirts", "Shirts"],
];

export function Header() {
  const { count } = useCart();
  const { user, logout } = useAuth();
  const [menu, setMenu] = useState(false);
  const [search, setSearch] = useState(false);
  const [q, setQ] = useState("");
  const nav = useNavigate();
  const loc = useLocation();
  useEffect(() => {
    setMenu(false);
    setSearch(false);
  }, [loc.pathname, loc.search]);

  function submit(e) {
    e.preventDefault();
    if (q.trim()) nav(`/shop?q=${encodeURIComponent(q.trim())}`);
  }

  return (
    <>
      <div className="announce">
        Free delivery on every order · Easy 7-day exchange
      </div>
      <header className="site-header">
        <div className="container d-flex align-items-center justify-content-between py-2">
          <button
            className="icon-btn d-lg-none"
            aria-label="Open menu"
            aria-expanded={menu}
            onClick={() => setMenu((m) => !m)}
          >
            {menu ? "✕" : "☰"}
          </button>
          <Link to="/" className="brand" aria-label="TrendsFit home">
            Trends<span>Fit</span>
          </Link>
          <nav className="nav-main d-none d-lg-block" aria-label="Main">
            {NAV.map(([to, label]) => (
              <Link key={to} to={to}>
                {label}
              </Link>
            ))}
          </nav>
          <div className="d-flex align-items-center">
            <button
              className="icon-btn"
              aria-label="Search"
              onClick={() => setSearch((s) => !s)}
            >
              ⌕
            </button>
            <Link
              className="icon-btn d-none d-sm-inline-block"
              to={user ? "/account/wishlist" : "/login"}
              aria-label="Wishlist"
            >
              ♡
            </Link>
            <Link
              className="icon-btn"
              to={user ? "/account" : "/login"}
              aria-label={user ? "My account" : "Log in"}
            >
              ☺
            </Link>
            <Link
              className="icon-btn"
              to="/cart"
              aria-label={`Cart, ${count} items`}
            >
              🛍{count > 0 && <span className="count">{count}</span>}
            </Link>
          </div>
        </div>
        {search && (
          <form className="search-bar py-2" onSubmit={submit} role="search">
            <div className="container position-relative">
              <div className="d-flex gap-2">
                <input
                  autoFocus
                  className="form-control"
                  type="search"
                  placeholder="Search jeans, shirts, t-shirts..."
                  value={q}
                  onChange={(e) => setQ(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === "Escape") setSearch(false);
                  }}
                  aria-label="Search products"
                  autoComplete="off"
                />
                <button className="btn btn-tf">Search</button>
              </div>
              <SearchSuggest q={q} onPick={() => setSearch(false)} />
            </div>
          </form>
        )}
        {menu && (
          <div className="mobile-menu d-lg-none">
            <div className="container pb-3">
              {NAV.map(([to, label]) => (
                <Link key={to} to={to}>
                  {label}
                </Link>
              ))}
              <Link to="/track-order">Track Order</Link>
              {user ? (
                <a
                  href="#logout"
                  onClick={(e) => {
                    e.preventDefault();
                    logout();
                    nav("/");
                  }}
                >
                  Log out
                </a>
              ) : (
                <Link to="/login">Log in / Register</Link>
              )}
            </div>
          </div>
        )}
      </header>
    </>
  );
}

export function Footer() {
  const cfg = useConfig();
  const [email, setEmail] = useState("");
  const [msg, setMsg] = useState("");

  async function subscribe(e) {
    e.preventDefault();
    try {
      const r = await api.post("/newsletter", { email });
      setMsg(r.data.message);
      setEmail("");
    } catch (err) {
      setMsg(
        err?.response?.data?.error?.message || "Could not subscribe right now.",
      );
    }
  }

  return (
    <>
      <section className="newsletter section">
        <div className="container text-center" style={{ maxWidth: 560 }}>
          <h2 className="section-title">Join the TrendsFit list</h2>
          <p className="section-sub mb-3">
            New drops and offers. No spam, unsubscribe anytime.
          </p>
          <form className="d-flex gap-2" onSubmit={subscribe}>
            <input
              type="email"
              required
              className="form-control"
              placeholder="Your email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              aria-label="Email address"
            />
            <button className="btn btn-tf">Subscribe</button>
          </form>
          {msg && (
            <p className="mt-2 small" role="status">
              {msg}
            </p>
          )}
        </div>
      </section>
      <footer className="site-footer">
        <div className="container">
          <div className="row g-4">
            <div className="col-12 col-md-4">
              <div className="brand text-white mb-2">
                Trends<span>Fit</span>
              </div>
              <p>Premium Jeans, Shirts & T-Shirts for everyday style.</p>
              {cfg.supportEmail && <p className="mb-0">{cfg.supportEmail}</p>}
            </div>
            <div className="col-6 col-md-2">
              <h4>Shop</h4>
              <Link to="/shop?category=jeans">Jeans</Link>
              <Link to="/shop?category=t-shirts">T-Shirts</Link>
              <Link to="/shop?category=shirts">Shirts</Link>
              <Link to="/shop">New Arrivals</Link>
            </div>
            <div className="col-6 col-md-3">
              <h4>Help</h4>
              <Link to="/track-order">Track Order</Link>
              <Link to="/shipping-policy">Shipping</Link>
              <Link to="/exchange-policy">Exchange Policy</Link>
              <Link to="/faq">FAQ</Link>
              <Link to="/contact">Contact</Link>
              <Link to="/support">Chat with us</Link>
            </div>
            <div className="col-12 col-md-3">
              <h4>Company</h4>
              <Link to="/privacy-policy">Privacy Policy</Link>
              <Link to="/terms">Terms</Link>
              <Link to="/login">My Account</Link>
            </div>
          </div>
          <hr className="border-secondary mt-4" />
          <div className="small d-flex flex-wrap justify-content-between gap-2">
            <span>
              © {new Date().getFullYear()} TrendsFit. All rights reserved.
            </span>
            <span>
              Secure payments via Razorpay · Cash on Delivery available
            </span>
          </div>
        </div>
      </footer>
      {cfg.whatsapp && (
        <a
          className="wa-float"
          href="https://wa.me/917644832949"
          target="_blank"
          rel="noopener noreferrer"
          aria-label="Chat on WhatsApp"
        >
          ✆
        </a>
      )}
    </>
  );
}

export default function Layout() {
  const loc = useLocation();
  useEffect(() => {
    window.scrollTo(0, 0);
  }, [loc.pathname]);
  return (
    <>
      <Header />
      <main id="main">
        <Outlet />
      </main>
      <Footer />
    </>
  );
}
