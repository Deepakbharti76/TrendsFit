import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import '@testing-library/jest-dom/vitest';

const post = vi.fn(() => Promise.resolve({ data: { id: 5 } }));
vi.mock('../lib/api', () => ({
  api: { get: vi.fn((u) => Promise.resolve({ data: u === '/admin/categories' ? [{ id: 1, slug: 't-shirts', name: 'T-Shirts' }] : [] })), post: (...a) => post(...a), put: vi.fn() },
  apiError: (e) => ({ message: e?.message, fields: {} }),
}));
const push = vi.fn();
vi.mock('../lib/AppContext', () => ({ useToast: () => ({ push }) }));

import ProductEdit, { num, variantPriceErrors } from '../admin/ProductEdit';

describe('variant price helpers', () => {
  it('num parses numbers, strings and blanks', () => {
    expect(num('')).toBeNull();
    expect(num(null)).toBeNull();
    expect(num('399')).toBe(399);
    expect(num(399)).toBe(399);
    expect(num('₹1,399.50')).toBe(1399.5);
    expect(Number.isNaN(num('abc'))).toBe(true);
  });
  it('accepts 399 on both variants and blank overrides', () => {
    expect(variantPriceErrors([{ size: 'M', color: 'Black', priceOverride: '399' }, { size: 'S', color: 'Red', priceOverride: 399 }, { size: 'L', color: 'Red', priceOverride: '' }])).toEqual({});
  });
  it('rejects zero, negative and non-numeric overrides on active variants, naming the row', () => {
    const e = variantPriceErrors([{ size: 'M', color: 'Black', priceOverride: '399' }, { size: 'S', color: 'Red', priceOverride: '0' }, { size: 'L', color: 'Red', priceOverride: '-5' }, { size: 'XL', color: 'Red', priceOverride: 'abc' }]);
    expect(Object.keys(e)).toEqual(['1', '2', '3']);
    expect(e[1]).toMatch(/above 0/);
    expect(e[1]).toMatch(/S \/ Red/);
  });
  it('ignores inactive rows', () => {
    expect(variantPriceErrors([{ size: 'S', color: 'Red', priceOverride: '0', active: false }])).toEqual({});
  });
});

describe('ProductEdit form', () => {
  const renderNew = () => render(<MemoryRouter initialEntries={['/admin/products/new']}><Routes><Route path="/admin/products/:id" element={<ProductEdit />} /></Routes></MemoryRouter>);
  it('sends priceOverride as the number 399 for both variants', async () => {
    post.mockClear();
    renderNew();
    await screen.findByLabelText('Name');
    await userEvent.type(screen.getByLabelText('Name'), 'Tee');
    await userEvent.type(screen.getByLabelText('SKU'), 'T1');
    await userEvent.type(screen.getByLabelText(/Regular price/), '599');
    await userEvent.type(screen.getByLabelText(/Sale price/), '399');
    await userEvent.selectOptions(screen.getByLabelText('Category'), 't-shirts');
    for (const [sz, col, st] of [['M', 'Black', '10'], ['S', 'Red', '2']]) {
      await userEvent.click(screen.getByText('+ Add variant'));
      const rows = document.querySelectorAll('tbody tr'); const row = rows[rows.length - 1];
      await userEvent.type(row.querySelector('[aria-label="Size"]'), sz);
      await userEvent.type(row.querySelector('[aria-label="Colour"]'), col);
      await userEvent.clear(row.querySelector('[aria-label="Stock"]')); await userEvent.type(row.querySelector('[aria-label="Stock"]'), st);
      await userEvent.type(row.querySelector('[aria-label="Price override"]'), '399');
    }
    await userEvent.click(screen.getByText('Save product'));
    expect(post).toHaveBeenCalledTimes(1);
    const body = post.mock.calls[0][1];
    expect(body.variants.map((v) => v.priceOverride)).toEqual([399, 399]);
    expect(body.variants.map((v) => v.stock)).toEqual([10, 2]);
  });
  it('blocks a zero override before sending and highlights the row', async () => {
    post.mockClear();
    renderNew();
    await screen.findByLabelText('Name');
    await userEvent.click(screen.getByText('+ Add variant'));
    const row = document.querySelector('tbody tr');
    await userEvent.type(row.querySelector('[aria-label="Size"]'), 'M');
    await userEvent.type(row.querySelector('[aria-label="Colour"]'), 'Black');
    await userEvent.type(row.querySelector('[aria-label="Price override"]'), '0');
    await userEvent.click(screen.getByText('Save product'));
    expect(post).not.toHaveBeenCalled();
    expect(await screen.findByText(/Price override must be a number above 0/)).toBeInTheDocument();
    expect(row.querySelector('[aria-label="Price override"]')).toHaveClass('is-invalid');
  });
});
