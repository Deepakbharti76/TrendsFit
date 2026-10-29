import { useEffect, useState } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../lib/AppContext';
import { navFor, openStoreTab, STORE_TAB_NAME } from '../lib/adminNav';
import { useSeo } from '../lib/seo';
import { NotificationBell, useAdminPulse } from './notify';

export default function AdminLayout() {
  const { user, logout } = useAuth();
  const nav = useNavigate();
  const loc = useLocation();
  const [open, setOpen] = useState(false);
  const pulse = useAdminPulse(user);
  const viewStore = (e) => { if (e.metaKey || e.ctrlKey || e.shiftKey || e.button === 1) return; e.preventDefault(); openStoreTab('/'); };
  useSeo({ title: 'Admin', noindex: true });

  // The drawer closes on navigation and on Escape, and the page behind it does not scroll while it is open.
  useEffect(() => { setOpen(false); }, [loc.pathname, loc.search]);
  useEffect(() => {
    if (!open) return undefined;
    const onKey = (e) => { if (e.key === 'Escape') setOpen(false); };
    document.addEventListener('keydown', onKey);
    document.body.classList.add('admin-lock');
    return () => { document.removeEventListener('keydown', onKey); document.body.classList.remove('admin-lock'); };
  }, [open]);

  const groups = navFor(user);
  const side = (
    <nav className="admin-nav" aria-label="Admin">
      {groups.map((g) => (
        <div key={g.group} className="mb-2">
          <div className="admin-nav-group">{g.group}</div>
          {g.items.map((i) => (
            <NavLink key={i.to} to={i.to} end={i.end} className={({ isActive }) => (isActive ? 'active' : '')}>
              {i.label}
              {i.badge === 'unread' && pulse.unread > 0 && <span className="badge rounded-pill text-bg-danger ms-2">{pulse.unread}</span>}
            </NavLink>
          ))}
        </div>
      ))}
      <div className="admin-nav-group">Store</div>
      <a href="/" target={STORE_TAB_NAME} onClick={viewStore}>View store ↗<span className="visually-hidden"> (opens in a new tab)</span></a>
    </nav>
  );
  const brand = (
    <div className="admin-brand">
      <Link to="/admin" className="brand text-white" aria-label="TrendsFit admin home">Trends<span>Fit</span></Link>
      <small className="d-block">{user.role.replaceAll('_', ' ')}</small>
    </div>
  );

  return (
    <div className="admin-shell">
      <a className="skip-link" href="#admin-main">Skip to content</a>
      <aside className="admin-side admin-side-desktop">{brand}{side}</aside>
      {open && <div className="admin-backdrop" onClick={() => setOpen(false)} aria-hidden="true" />}
      <aside id="admin-drawer" className={`admin-side admin-drawer${open ? ' open' : ''}`} aria-label="Admin menu" aria-hidden={!open}>
        <div className="d-flex justify-content-between align-items-start">{brand}
          <button type="button" className="btn btn-sm text-white m-2" aria-label="Close menu" onClick={() => setOpen(false)}>✕</button></div>
        {side}
      </aside>
      <div className="admin-main">
        <header className="admin-top">
          <button type="button" className="btn btn-sm btn-outline-dark admin-burger" aria-label="Open admin menu" aria-expanded={open} aria-controls="admin-drawer" onClick={() => setOpen(true)}>☰</button>
          <Link to="/admin" className="btn btn-sm btn-outline-secondary d-none d-sm-inline-block">Dashboard</Link>
          <span className="flex-grow-1" />
          <a className="btn btn-sm btn-outline-dark" href="/" target={STORE_TAB_NAME} onClick={viewStore}>View store ↗<span className="visually-hidden"> (opens in a new tab)</span></a>
          <NotificationBell pulse={pulse} />
          <span className="small text-muted d-none d-md-inline text-truncate" style={{ maxWidth: 180 }}>{user.email}</span>
          <button type="button" className="btn btn-sm btn-outline-secondary" onClick={() => { logout(); nav('/login'); }}>Log out</button>
        </header>
        <main id="admin-main" className="admin-content" tabIndex={-1}><Outlet /></main>
      </div>
    </div>
  );
}
