import { useEffect, useState } from 'react';
import { api, apiError } from '../lib/api';
import { useToast } from '../lib/AppContext';
import { ErrorBox, Spinner } from '../components/Bits';
import { useFetch } from './common';

/** Pure helper (unit-tested): only the values the admin actually changed. */
export function changedValues(original, draft) {
  const out = {};
  for (const k of Object.keys(draft)) if (String(draft[k]) !== String(original[k])) out[k] = String(draft[k]);
  return out;
}

export function SettingsForm({ title, groups: only, intro, children }) {
  const toast = useToast();
  const { data, err, loading, reload } = useFetch('/admin/settings');
  const [draft, setDraft] = useState({});
  const [orig, setOrig] = useState({});
  const [saving, setSaving] = useState(false);
  const [formErr, setFormErr] = useState('');

  useEffect(() => {
    if (!data) return;
    const m = {};
    data.forEach((s) => { m[s.key] = s.value; });
    setDraft(m); setOrig(m);
  }, [data]);

  async function save(e) {
    e.preventDefault();
    const changes = changedValues(orig, draft);
    if (Object.keys(changes).length === 0) { toast.push('No changes to save'); return; }
    setSaving(true); setFormErr('');
    try { await api.put('/admin/settings', changes); toast.push('Settings saved'); reload(); }
    catch (x) { setFormErr(apiError(x).message); }
    finally { setSaving(false); }
  }

  if (loading) return <Spinner />;
  if (err) return <ErrorBox message={err} />;
  const groups = [...new Set((data || []).map((s) => s.group))].filter((g) => !only || only.includes(g));
  const set = (k, v) => setDraft((d) => ({ ...d, [k]: v }));

  return (
    <form onSubmit={save} noValidate>
      <h1 className="h4 mb-1">{title || 'Settings'}</h1>
      <p className="text-muted small">{intro || 'Changes apply immediately and are recorded in the audit log.'} Secret keys (Razorpay, Shiprocket, email, JWT) are never shown or stored here; they live in the server environment.</p>
      {children}
      {formErr && <ErrorBox message={formErr} />}
      {groups.map((g) => (
        <div className="card mb-3" key={g}>
          <div className="card-header fw-semibold">{g}</div>
          <div className="card-body row g-3">
            {data.filter((s) => s.group === g).map((s) => (
              <div className="col-md-6" key={s.key}>
                {s.type === 'BOOL' ? (
                  <div className="form-check form-switch pt-3">
                    <input className="form-check-input" type="checkbox" id={s.key} checked={draft[s.key] === 'true'} onChange={(e) => set(s.key, e.target.checked ? 'true' : 'false')} />
                    <label className="form-check-label" htmlFor={s.key}>{s.label}</label>
                  </div>
                ) : (
                  <>
                    <label className="form-label small" htmlFor={s.key}>{s.label}</label>
                    <input id={s.key} className="form-control" value={draft[s.key] ?? ''} inputMode={['INT', 'MONEY', 'DECIMAL'].includes(s.type) ? 'decimal' : undefined}
                      onChange={(e) => set(s.key, e.target.value)} />
                  </>
                )}
              </div>
            ))}
          </div>
        </div>
      ))}
      <button className="btn btn-dark" disabled={saving}>{saving ? 'Saving…' : 'Save settings'}</button>
    </form>
  );
}

function Status({ ok, yes = 'Configured', no = 'Not configured' }) { return <span className={ok ? 'text-success' : 'text-danger'}>{ok ? yes : no}</span>; }
function Row({ label, children }) { return <div className="d-flex justify-content-between small py-1 gap-3"><span>{label}</span><span className="text-end text-break">{children}</span></div>; }

function IntegrationPanel({ title, render }) {
  const { data, err, loading } = useFetch('/admin/integrations');
  return (
    <div className="card mb-3"><div className="card-header fw-semibold">{title}</div><div className="card-body">
      {err && <ErrorBox message={err} />}{loading && !data ? <Spinner /> : data && render(data)}</div></div>
  );
}

export const StoreSettings = () => <SettingsForm title="Store settings" groups={['Store', 'Inventory', 'Exchange', 'Revenue targets']} intro="Store details shown on invoices and emails, stock alerts, the exchange window and dashboard revenue targets." />;

export const ShippingSettings = () => (
  <SettingsForm title="Shipping settings" groups={['Shipping']} intro="Shipping charges and the parcel defaults sent to Shiprocket.">
    <IntegrationPanel title="Shiprocket status" render={(d) => (
      <>
        <Row label="API login (SHIPROCKET_EMAIL / PASSWORD)"><Status ok={d.shiprocket.configured} /></Row>
        <Row label="Webhook token (SHIPROCKET_WEBHOOK_TOKEN)"><Status ok={d.shiprocket.webhookTokenConfigured} /></Row>
        <Row label="Pickup pincode"><Status ok={d.shiprocket.pickupPincodeSet} yes="Set" no="Not set" /></Row>
        <Row label="Webhook URL to give Shiprocket">{d.shiprocket.webhookUrl}</Row>
        {!d.shiprocket.configured && <div className="small text-muted mt-2">Without credentials the store works, but shipments and live pincode checks are unavailable. Nothing is simulated.</div>}
      </>
    )} />
  </SettingsForm>
);

export const PaymentSettings = () => (
  <SettingsForm title="Payment settings" groups={['Payments']} intro="Cash on Delivery rules and the unpaid-order timeout.">
    <IntegrationPanel title="Razorpay status" render={(d) => (
      <>
        <Row label="API keys (RAZORPAY_KEY_ID / KEY_SECRET)"><Status ok={d.razorpay.keysConfigured} /></Row>
        <Row label="Webhook secret (RAZORPAY_WEBHOOK_SECRET)"><Status ok={d.razorpay.webhookSecretConfigured} /></Row>
        <Row label="Webhook URL to give Razorpay">{d.razorpay.webhookUrl}</Row>
        {!d.razorpay.keysConfigured && <div className="small text-muted mt-2">Online payment is hidden at checkout until the keys are set. Keys are never shown here.</div>}
      </>
    )} />
  </SettingsForm>
);

export const NotificationSettings = () => (
  <SettingsForm title="Email & notifications" groups={['Notifications']} intro="Choose which events appear in the bell at the top of the admin panel.">
    <IntegrationPanel title="Email (SMTP) status" render={(d) => (
      <>
        <Row label="Sending enabled (MAIL_ENABLED)"><Status ok={d.email.enabled} yes="On" no="Off" /></Row>
        <Row label="From address"><Status ok={d.email.fromConfigured} yes="Set" no="Not set" /></Row>
        {!d.email.enabled && <div className="small text-muted mt-2">Emails are switched off, so customers get no email confirmations. Set the MAIL_* variables and MAIL_ENABLED=true.</div>}
      </>
    )} />
  </SettingsForm>
);

export default StoreSettings;
