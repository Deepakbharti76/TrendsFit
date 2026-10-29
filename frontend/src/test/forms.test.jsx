import { act, render, renderHook, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import '@testing-library/jest-dom/vitest';
import { digitsOnly, lookupPincode, matchState, mobileError, pincodeError, usePincodeFill } from '../lib/forms';
import { Field, PasswordInput } from '../components/Bits';

vi.mock('../lib/api', () => ({ api: { get: vi.fn() }, apiError: (e) => ({ message: e?.message }) }));
import SearchSuggest, { useSuggestions } from '../components/SearchSuggest';

describe('mobile + pincode rules', () => {
  it('digitsOnly strips letters/spaces and caps length', () => {
    expect(digitsOnly('98a76 5-43210999')).toBe('9876543210');
    expect(digitsOnly('560 001x9', 6)).toBe('560001');
  });
  it('mobileError messages', () => {
    expect(mobileError('')).toMatch(/required/);
    expect(mobileError('98765')).toMatch(/exactly 10/);
    expect(mobileError('5876543210')).toMatch(/start with/);
    expect(mobileError('9876 43210')).toMatch(/digits only/);
    expect(mobileError('9876543210')).toBe('');
  });
  it('pincodeError', () => {
    expect(pincodeError('560001')).toBe('');
    for (const b of ['', '56000', '060001', '56000a']) expect(pincodeError(b)).toBeTruthy();
  });
  it('matchState normalises & and spelling', () => {
    expect(matchState('Jammu & Kashmir')).toBe('Jammu and Kashmir');
    expect(matchState('NCT of Delhi')).toBe('Delhi');
    expect(matchState('Karnataka')).toBe('Karnataka');
  });
});

const resp = (body, ok = true) => Promise.resolve({ ok, json: () => Promise.resolve(body) });
describe('lookupPincode', () => {
  it('returns city and state', async () => {
    const f = vi.fn(() => resp([{ Status: 'Success', PostOffice: [{ District: 'Bengaluru', State: 'Karnataka', Name: 'X' }] }]));
    expect(await lookupPincode('560001', f)).toEqual({ city: 'Bengaluru', state: 'Karnataka' });
    expect(f.mock.calls[0][0]).toContain('/pincode/560001');
  });
  it('errors on unknown pincode, bad format and network failure', async () => {
    await expect(lookupPincode('999999', () => resp([{ Status: 'Error', PostOffice: null }]))).rejects.toThrow(/could not find/i);
    await expect(lookupPincode('12', () => resp([]))).rejects.toThrow(/valid 6-digit/);
    await expect(lookupPincode('560002', () => Promise.reject(new Error('net')))).rejects.toThrow(/right now/);
    await expect(lookupPincode('560003', () => resp({}, false))).rejects.toThrow(/right now/);
  });
});

describe('usePincodeFill', () => {
  it('fills once for a valid pincode and reports errors', async () => {
    const onFill = vi.fn();
    const lookup = vi.fn((p) => (p === '560001' ? Promise.resolve({ city: 'Bengaluru', state: 'Karnataka' }) : Promise.reject(new Error('We could not find this pincode. Please check it and try again.'))));
    const { result, rerender } = renderHook(({ p }) => usePincodeFill(p, onFill, lookup), { initialProps: { p: '5600' } });
    expect(result.current.status).toBe('idle');
    rerender({ p: '560001' });
    await waitFor(() => expect(result.current.status).toBe('ok'));
    expect(onFill).toHaveBeenCalledWith({ city: 'Bengaluru', state: 'Karnataka' });
    rerender({ p: '111111' });
    await waitFor(() => expect(result.current.status).toBe('error'));
    expect(result.current.message).toMatch(/could not find/);
    expect(onFill).toHaveBeenCalledTimes(1);
  });
});

describe('PasswordInput', () => {
  it('toggles visibility', async () => {
    render(<PasswordInput id="p" aria-label="pw" />);
    const input = screen.getByLabelText('pw');
    expect(input).toHaveAttribute('type', 'password');
    await userEvent.click(screen.getByRole('button', { name: 'Show password' }));
    expect(input).toHaveAttribute('type', 'text');
    await userEvent.click(screen.getByRole('button', { name: 'Hide password' }));
    expect(input).toHaveAttribute('type', 'password');
  });
});

describe('Field required marker', () => {
  it('shows a red asterisk only when required', () => {
    const { container, rerender } = render(<Field label="Name" id="n" required><input id="n" /></Field>);
    expect(container.querySelector('.req')).toHaveTextContent('*');
    rerender(<Field label="Name (optional)" id="n"><input id="n" /></Field>);
    expect(container.querySelector('.req')).toBeNull();
  });
});

describe('search suggestions', () => {
  it('debounces, ignores short input, and shows products', async () => {
    const fetcher = vi.fn(() => Promise.resolve([{ id: 1, slug: 'baggy-jeans', name: 'Baggy Jeans', category: 'Jeans', finalPrice: 1499 }]));
    const { result, rerender } = renderHook(({ q }) => useSuggestions(q, fetcher), { initialProps: { q: 'b' } });
    expect(fetcher).not.toHaveBeenCalled();
    rerender({ q: 'bag' });
    await waitFor(() => expect(result.current.status).toBe('done'));
    expect(fetcher).toHaveBeenCalledTimes(1);
    expect(result.current.items[0].name).toBe('Baggy Jeans');
  });
  it('renders links to products and an empty state', async () => {
    const { api } = await import('../lib/api');
    api.get.mockResolvedValueOnce({ data: { items: [{ id: 1, slug: 'baggy-jeans', name: 'Baggy Jeans', category: 'Jeans', finalPrice: 1499 }] } });
    const { rerender } = render(<MemoryRouter><SearchSuggest q="baggy" onPick={() => {}} /></MemoryRouter>);
    expect(await screen.findByText('Baggy Jeans')).toBeInTheDocument();
    expect(screen.getByRole('option')).toHaveAttribute('href', '/product/baggy-jeans');
    api.get.mockResolvedValueOnce({ data: { items: [] } });
    rerender(<MemoryRouter><SearchSuggest q="zzzz" onPick={() => {}} /></MemoryRouter>);
    expect(await screen.findByText(/No products found/)).toBeInTheDocument();
  });
});
