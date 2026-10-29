import { useEffect, useRef, useState } from 'react';

export const STATES = ['Andhra Pradesh', 'Arunachal Pradesh', 'Assam', 'Bihar', 'Chhattisgarh', 'Delhi', 'Goa', 'Gujarat', 'Haryana', 'Himachal Pradesh', 'Jammu and Kashmir', 'Jharkhand', 'Karnataka', 'Kerala', 'Madhya Pradesh', 'Maharashtra', 'Manipur', 'Meghalaya', 'Mizoram', 'Nagaland', 'Odisha', 'Punjab', 'Rajasthan', 'Sikkim', 'Tamil Nadu', 'Telangana', 'Tripura', 'Uttar Pradesh', 'Uttarakhand', 'West Bengal', 'Chandigarh', 'Puducherry', 'Ladakh'];

/** Keeps only digits and caps the length, so letters, spaces and extra digits can never be typed or pasted. */
export const digitsOnly = (v, max = 10) => String(v ?? '').replace(/\D/g, '').slice(0, max);

/**
 * Mobile input sanitiser: digits only, max 10. A pasted "+91 98765 43210" or "098765 43210" is reduced to the
 * 10-digit number instead of being cut off mid-way. (Inputs deliberately have no maxLength attribute, because the
 * browser would truncate the raw pasted text *before* this runs.)
 */
export function mobileInput(v) {
  let d = String(v ?? '').replace(/\D/g, '');
  if (d.length === 12 && d.startsWith('91')) d = d.slice(2);
  else if (d.length === 11 && d.startsWith('0')) d = d.slice(1);
  return d.slice(0, 10);
}

/** Indian mobile: exactly 10 digits starting 6-9. Returns an error message or ''. */
export function mobileError(v) {
  const s = String(v ?? '');
  if (!s) return 'Mobile number is required.';
  if (/\D/.test(s)) return 'Mobile number can contain digits only.';
  if (s.length !== 10) return 'Enter exactly 10 digits.';
  if (!/^[6-9]/.test(s)) return 'Mobile number must start with 6, 7, 8 or 9.';
  return '';
}

export const pincodeError = (v) => (/^[1-9]\d{5}$/.test(String(v ?? '').trim()) ? '' : 'Enter a valid 6-digit pincode.');

/** Maps the postal API's state spelling onto our dropdown values (e.g. "Jammu & Kashmir"). */
export function matchState(name) {
  const norm = (x) => String(x || '').toLowerCase().replace(/&/g, 'and').replace(/\s+/g, ' ').trim();
  const n = norm(name);
  const hit = STATES.find((s) => norm(s) === n);
  if (hit) return hit;
  if (n.includes('delhi')) return 'Delhi';
  if (n.includes('andaman') || n.includes('dadra') || n.includes('daman') || n.includes('lakshadweep')) return String(name);
  return String(name || '');
}

const cache = new Map();

/**
 * Looks up a 6-digit pincode on the public India Post directory (api.postalpincode.in).
 * Resolves to { city, state }; rejects with an Error carrying a user-facing message.
 */
export async function lookupPincode(pin, fetchImpl = (typeof fetch !== 'undefined' ? fetch : null)) {
  if (pincodeError(pin)) throw new Error('Enter a valid 6-digit pincode.');
  if (cache.has(pin)) return cache.get(pin);
  if (!fetchImpl) throw new Error('Could not look up this pincode. Please enter the city and state yourself.');
  const ctl = typeof AbortController !== 'undefined' ? new AbortController() : null;
  const timer = setTimeout(() => ctl?.abort(), 7000);
  let body;
  try {
    const res = await fetchImpl(`https://api.postalpincode.in/pincode/${pin}`, { signal: ctl?.signal });
    if (!res.ok) throw new Error('http');
    body = await res.json();
  } catch {
    throw new Error('Could not look up this pincode right now. Please enter the city and state yourself.');
  } finally {
    clearTimeout(timer);
  }
  const first = Array.isArray(body) ? body[0] : null;
  const po = first?.Status === 'Success' && Array.isArray(first.PostOffice) ? first.PostOffice[0] : null;
  if (!po) throw new Error('We could not find this pincode. Please check it and try again.');
  const out = { city: po.District || po.Block || po.Name || '', state: matchState(po.State) };
  if (!out.city || !out.state) throw new Error('We could not find this pincode. Please check it and try again.');
  cache.set(pin, out);
  return out;
}

/**
 * Fills city and state when the pincode becomes a valid 6-digit value.
 * `onFill({ city, state })` is called only after a successful lookup; the user can still edit both afterwards.
 * Returns { status: 'idle'|'loading'|'ok'|'error', message }.
 */
export function usePincodeFill(pincode, onFill, lookup = lookupPincode) {
  const [state, setState] = useState({ status: 'idle', message: '' });
  const fill = useRef(onFill);
  fill.current = onFill;
  useEffect(() => {
    const pin = String(pincode || '').trim();
    if (pin.length < 6) { setState({ status: 'idle', message: '' }); return undefined; }
    const bad = pincodeError(pin);
    if (bad) { setState({ status: 'error', message: bad }); return undefined; }
    let live = true;
    setState({ status: 'loading', message: 'Checking pincode…' });
    lookup(pin).then((r) => { if (live) { fill.current(r); setState({ status: 'ok', message: `${r.city}, ${r.state}` }); } })
      .catch((e) => { if (live) setState({ status: 'error', message: e.message }); });
    return () => { live = false; };
  }, [pincode]); // eslint-disable-line react-hooks/exhaustive-deps
  return state;
}
