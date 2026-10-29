import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useSeo } from '../lib/seo';
import { recentOrders } from '../lib/recentOrders';

export default function TrackOrder() {
  const nav = useNavigate();
  const [n, setN] = useState('');
  const [t, setT] = useState('');
  const recent = recentOrders();
  useSeo({ title: 'Track your order', description: 'Track your TrendsFit order.' });
  return (
    <div className="container section" style={{ maxWidth: 560 }}>
      <h1 className="display-font h2 mb-3">Track your order</h1>
      <p className="text-muted">Enter your order number and the tracking code from your confirmation link (the part after <code>?token=</code>). Logged-in customers can see all orders in <Link to="/account/orders">My orders</Link>.</p>
      <form onSubmit={(e) => { e.preventDefault(); nav(`/order/${n.trim().toUpperCase()}?token=${encodeURIComponent(t.trim())}`); }}>
        <div className="mb-3"><label className="form-label" htmlFor="tn">Order number</label><input id="tn" className="form-control" placeholder="TF251015-AB12C" required value={n} onChange={(e) => setN(e.target.value)} /></div>
        <div className="mb-3"><label className="form-label" htmlFor="tt">Tracking code</label><input id="tt" className="form-control" required value={t} onChange={(e) => setT(e.target.value)} /></div>
        <button className="btn btn-tf">Track</button>
      </form>
      {recent.length > 0 && (
        <div className="mt-4"><h2 className="h6 text-uppercase ls">Recent orders on this device</h2>
          {recent.map((o) => <div key={o.orderNumber}><Link to={`/order/${o.orderNumber}?token=${o.token}`}>{o.orderNumber}</Link></div>)}</div>
      )}
    </div>
  );
}
