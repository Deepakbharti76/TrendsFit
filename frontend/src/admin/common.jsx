import { useCallback, useEffect, useState } from 'react';
import { api, apiError } from '../lib/api';

export function useFetch(url, params) {
  const [data, setData] = useState(null);
  const [err, setErr] = useState('');
  const [loading, setLoading] = useState(true);
  const key = url + JSON.stringify(params || {});
  const load = useCallback(() => {
    setLoading(true);
    return api.get(url, { params }).then((r) => { setData(r.data); setErr(''); }).catch((e) => setErr(apiError(e).message)).finally(() => setLoading(false));
  }, [key]); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => { load(); }, [load]);
  return { data, err, loading, reload: load };
}

// The API returns bare names (e.g. ORDER_READ); callers may pass 'PERM_ORDER_READ'. Accept both.
export function can(user, perm) {
  const bare = String(perm).replace(/^PERM_/, '');
  return !!user?.permissions?.some((p) => p === bare || p === `PERM_${bare}`);
}

export const fmtDate = (s) => (s ? new Date(s).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' }) : '');

export function Table({ head, children, empty }) {
  return (
    <div className="table-responsive-tf">
      <table className="table table-hover align-middle mb-0">
        <thead className="table-light"><tr>{head.map((h, i) => <th key={i} className="text-nowrap">{h}</th>)}</tr></thead>
        <tbody>{children}</tbody>
      </table>
      {empty && <div className="p-4 text-center text-muted">{empty}</div>}
    </div>
  );
}
