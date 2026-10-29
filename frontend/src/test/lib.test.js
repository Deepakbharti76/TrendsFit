import { describe, expect, it } from 'vitest';
import { addLine, countItems, MAX_QTY, removeLine, sanitize, setQty } from '../lib/cart';
import { inr } from '../lib/money';
import { idempotencyKey } from '../lib/idem';
import { apiError, orderHeaders } from '../lib/api';
import { validateCheckout } from '../pages/Checkout';
import { passwordHint } from '../pages/Auth';

describe('cart helpers', () => {
  it('adds new lines and merges the same variant', () => {
    let l = addLine([], { variantId: 1, qty: 2, name: 'A' });
    l = addLine(l, { variantId: 1, qty: 3 });
    l = addLine(l, { variantId: 2, qty: 1 });
    expect(l).toHaveLength(2);
    expect(l[0].qty).toBe(5);
    expect(countItems(l)).toBe(6);
  });
  it('caps quantity at the maximum', () => {
    const l = addLine(addLine([], { variantId: 1, qty: 9 }), { variantId: 1, qty: 9 });
    expect(l[0].qty).toBe(MAX_QTY);
    expect(setQty(l, 1, 99)[0].qty).toBe(MAX_QTY);
  });
  it('removes a line when quantity drops below 1', () => {
    expect(setQty([{ variantId: 1, qty: 1 }], 1, 0)).toEqual([]);
    expect(removeLine([{ variantId: 1, qty: 1 }, { variantId: 2, qty: 1 }], 1)).toEqual([{ variantId: 2, qty: 1 }]);
  });
  it('sanitises tampered localStorage data', () => {
    expect(sanitize('x')).toEqual([]);
    const s = sanitize([{ variantId: 1, qty: 2, name: 'ok', evil: '<script>' }, { variantId: 'a', qty: 1 }, { variantId: 3, qty: -1 }, null, { variantId: 4, qty: 500 }]);
    expect(s).toHaveLength(2);
    expect(s[0]).not.toHaveProperty('evil');
    expect(s[1].qty).toBe(MAX_QTY);
  });
});

describe('money', () => {
  it('formats rupees', () => {
    expect(inr(1299)).toBe('₹1,299');
    expect(inr('999.50')).toBe('₹999.50');
    expect(inr(100000)).toBe('₹1,00,000');
    expect(inr(undefined)).toBe('₹0');
  });
});

describe('idempotency key', () => {
  it('is stable for the same payload and differs when it changes', () => {
    const a = idempotencyKey({ x: 1 });
    expect(idempotencyKey({ x: 1 })).toBe(a);
    expect(idempotencyKey({ x: 2 })).not.toBe(a);
    expect(a.length).toBeLessThanOrEqual(64);
  });
});

describe('api helpers', () => {
  it('maps server errors and network failures', () => {
    expect(apiError({ response: { data: { error: { code: 'X', message: 'Nope', fields: { a: 'b' } } } } })).toEqual({ code: 'X', message: 'Nope', fields: { a: 'b' } });
    expect(apiError({ response: undefined }).code).toBe('NETWORK');
    expect(apiError({ code: 'ECONNABORTED' }).code).toBe('TIMEOUT');
    expect(apiError({ response: { status: 500, data: {} } }).code).toBe('UNKNOWN');
  });
  it('builds the guest order header only with a token', () => {
    expect(orderHeaders('abc')).toEqual({ 'X-Order-Token': 'abc' });
    expect(orderHeaders('')).toEqual({});
  });
});

describe('checkout validation', () => {
  const ok = { name: 'Asha Rao', email: 'a@b.co', phone: '9876543210', line1: '12 MG Road', city: 'Pune', state: 'Maharashtra', pincode: '411001' };
  it('accepts a complete address', () => expect(validateCheckout(ok)).toEqual({}));
  it('requires exactly 10 digits (no +91, spaces, letters)', () => {
    expect(validateCheckout({ ...ok, phone: '' }).phone).toBe('Mobile number is required.');
    for (const bad of ['+91 98765 43210', '09876543210', '98765 43210', '98765abcde', '98765432101', '987654321', '5876543210'])
      expect(validateCheckout({ ...ok, phone: bad }).phone).toBeTruthy();
  });
  it('flags every bad field', () => {
    const e = validateCheckout({ name: '', email: 'x', phone: '12345', line1: '', city: '', state: '', pincode: '000000' });
    expect(Object.keys(e).sort()).toEqual(['city', 'email', 'line1', 'name', 'phone', 'pincode', 'state']);
  });
});

describe('password hint', () => {
  it('lists what is missing', () => {
    expect(passwordHint('abc')).toEqual(['at least 10 characters', 'an uppercase letter', 'a number']);
    expect(passwordHint('Abcdefgh12')).toEqual([]);
  });
});

import { can } from '../admin/common';
describe('admin can()', () => {
  it('matches bare permission names returned by the API', () => {
    const u = { permissions: ['ORDER_READ'] };
    expect(can(u, 'PERM_ORDER_READ')).toBe(true);
    expect(can(u, 'ORDER_READ')).toBe(true);
    expect(can(u, 'PERM_ORDER_WRITE')).toBe(false);
    expect(can(null, 'PERM_ORDER_READ')).toBe(false);
  });
});

import { changedValues } from '../admin/Settings';
describe('settings changedValues()', () => {
  it('returns only changed keys as strings', () => {
    expect(changedValues({ a: '1', b: 'x', c: 'true' }, { a: '1', b: 'y', c: 'false' })).toEqual({ b: 'y', c: 'false' });
    expect(changedValues({ a: '1' }, { a: '1' })).toEqual({});
  });
});
