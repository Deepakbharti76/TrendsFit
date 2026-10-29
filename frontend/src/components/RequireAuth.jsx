import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../lib/AppContext';
import { ErrorBox, Spinner } from './Bits';

/** Redirects anonymous visitors to /login; with `staff`, also blocks customers. The server still enforces every permission. */
export default function RequireAuth({ children, staff }) {
  const { user, ready } = useAuth();
  const loc = useLocation();
  if (!ready) return <Spinner />;
  if (!user) return <Navigate to="/login" state={{ from: loc.pathname }} replace />;
  if (staff && !user.staff) return <div className="container section"><ErrorBox message="You do not have access to this area." /></div>;
  return children;
}
