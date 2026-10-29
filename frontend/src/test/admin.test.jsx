import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { firstAllowed, navFor, openStoreTab, resetStoreTab, ringOffset, toCsv, trendText } from '../lib/adminNav';
import { clearChat, loadChat, saveChat, validateStart } from '../lib/supportChat';

const auth = { user: null, logout: vi.fn() };
vi.mock('../lib/AppContext', () => ({
  useAuth: () => auth,
  useToast: () => ({ push: vi.fn() }),
  useConfig: () => ({}),
  useCart: () => ({ lines: [], count: 0 }),
}));
vi.mock('../lib/seo', () => ({ useSeo: () => {} }));
const apiGet = vi.fn();
vi.mock('../lib/api', () => ({
  api: { get: (...a) => apiGet(...a), post: vi.fn(), put: vi.fn(), patch: vi.fn() },
  apiError: (e) => ({ message: e?.message || 'err', code: e?.code }),
}));

import AdminLayout from '../admin/AdminLayout';
import { CircleProgress, RevenueBars } from '../admin/charts';
import { Thread } from '../admin/Support';

const superAdmin = { id: 1, email: 'a@x.test', role: 'SUPER_ADMIN', permissions: ['ANALYTICS_READ', 'PRODUCT_READ', 'PRODUCT_WRITE', 'ORDER_READ', 'PAYMENT_READ', 'SUPPORT_CHAT', 'SETTINGS_WRITE', 'USER_ROLE_WRITE', 'AUDIT_READ', 'CONTENT_WRITE', 'CUSTOMER_READ', 'COUPON_WRITE', 'REVIEW_MODERATE', 'NEWSLETTER_READ'] };

describe('adminNav', () => {
  it('shows only items the permissions allow and drops empty groups', () => {
    const u = { permissions: ['SUPPORT_CHAT'] };
    const g = navFor(u);
    expect(g.map((x) => x.group)).toEqual(['Engage']);
    expect(g[0].items.map((i) => i.to)).toEqual(['/admin/support']);
    expect(navFor(null)).toEqual([]);
  });
  it('accepts permission names with or without the PERM_ prefix', () => {
    expect(navFor({ permissions: ['PERM_ORDER_READ'] }).flatMap((g) => g.items).map((i) => i.to)).toContain('/admin/orders');
  });
  it('includes every required section for a super admin', () => {
    const labels = navFor(superAdmin).flatMap((g) => g.items.map((i) => i.label));
    ['Dashboard', 'Products', 'Categories', 'Inventory & stock', 'Orders', 'Shipments & tracking', 'Customers', 'Payments & refunds', 'Exchanges', 'Reviews', 'Coupons',
      'Website banners', 'Support chat', 'Reports & analytics', 'Store settings', 'Shipping settings', 'Payment settings', 'Email & notifications', 'Admin users & permissions', 'Audit log']
      .forEach((l) => expect(labels).toContain(l));
  });
  it('picks the first allowed page for users without analytics access', () => {
    expect(firstAllowed({ permissions: ['ORDER_READ'] })).toBe('/admin/orders');
    expect(firstAllowed({ permissions: [] })).toBeNull();
  });
  it('describes trends honestly, including no previous data', () => {
    expect(trendText(null)).toMatch(/No revenue in the previous period/);
    expect(trendText(12.5)).toBe('▲ 12.5% vs previous period');
    expect(trendText(-3)).toBe('▼ 3% vs previous period');
    expect(trendText(0)).toBe('Same as the previous period');
  });
  it('clamps the ring offset between empty and full', () => {
    expect(ringOffset(0, 100)).toBe(100);
    expect(ringOffset(50, 100)).toBe(50);
    expect(ringOffset(250, 100)).toBe(0);
    expect(ringOffset(-5, 100)).toBe(100);
  });
  it('escapes CSV and neutralises spreadsheet formulas', () => {
    const csv = toCsv([{ a: '=SUM(1)', b: 'x,"y"' }], ['a', 'b']);
    expect(csv).toBe('a,b\n\'=SUM(1),"x,""y"""');
  });
});

describe('CircleProgress', () => {
  it('shows the percentage of the target', () => {
    render(<CircleProgress percent={50} ringPercent={50} targetSet label="Revenue" />);
    expect(screen.getByRole('img', { name: /Revenue: 50% of target/ })).toBeInTheDocument();
    expect(screen.getByText('50%')).toBeInTheDocument();
  });
  it('shows the real figure above 100% and a dash without a target', () => {
    const { unmount } = render(<CircleProgress percent={150} ringPercent={100} targetSet label="Revenue" />);
    expect(screen.getByText('150%')).toBeInTheDocument();
    unmount();
    render(<CircleProgress percent={0} ringPercent={0} targetSet={false} label="Revenue" />);
    expect(screen.getByText('no target')).toBeInTheDocument();
  });
});

describe('RevenueBars', () => {
  it('shows an honest empty state with no revenue', () => {
    render(<RevenueBars series={[{ label: '2026-10-01', revenue: 0, orders: 0, previousRevenue: 0 }]} />);
    expect(screen.getByText(/No revenue in this period/)).toBeInTheDocument();
  });
  it('draws a bar per bucket', () => {
    render(<RevenueBars series={[{ label: 'a', revenue: 100, orders: 1, previousRevenue: 50 }, { label: 'b', revenue: 0, orders: 0, previousRevenue: 0 }]} />);
    expect(screen.getByRole('img', { name: /Revenue per period/ }).querySelectorAll('.rev-col')).toHaveLength(2);
  });
});

describe('AdminLayout', () => {
  beforeEach(() => {
    apiGet.mockReset();
    apiGet.mockImplementation((url) => Promise.resolve({ data: url.includes('unread') ? { unread: 3 } : { items: [], count: 0 } }));
    auth.user = superAdmin;
  });
  const renderLayout = (path = '/admin/orders') => render(
    <MemoryRouter initialEntries={[path]}><Routes><Route path="/admin" element={<AdminLayout />}><Route index element={<div>DASH</div>} /><Route path="orders" element={<div>ORDERS PAGE</div>} /><Route path="support" element={<div>SUPPORT PAGE</div>} /></Route></Routes></MemoryRouter>,
  );

  it('opens the storefront in a new tab without leaving the admin', async () => {
    renderLayout();
    const links = screen.getAllByRole('link', { name: /View store/ });
    expect(links.length).toBeGreaterThanOrEqual(2); // top bar + sidebar
    links.forEach((a) => { expect(a).toHaveAttribute('target', 'trendsfit-store'); expect(a).toHaveAttribute('href', '/'); });
    expect(screen.getByText('ORDERS PAGE')).toBeInTheDocument();
  });
  it('first click opens one named tab; repeat clicks reuse it and the admin stays put', async () => {
    resetStoreTab();
    const tab = { closed: false, focus: vi.fn(), opener: {} };
    const open = vi.spyOn(window, 'open').mockReturnValue(tab);
    renderLayout();
    const link = screen.getAllByRole('link', { name: /View store/ })[0];
    await userEvent.click(link);
    await userEvent.click(link);
    await userEvent.click(screen.getAllByRole('link', { name: /View store/ })[1]);
    expect(open).toHaveBeenCalledTimes(1);
    expect(open).toHaveBeenCalledWith('/', 'trendsfit-store');
    expect(tab.focus).toHaveBeenCalledTimes(2);
    expect(tab.opener).toBeNull();
    expect(screen.getByText('ORDERS PAGE')).toBeInTheDocument();
    tab.closed = true; // user closed the store tab -> next click opens it again
    openStoreTab('/', window);
    expect(open).toHaveBeenCalledTimes(2);
    open.mockRestore();
  });
  it('shows TrendsFit as one word in the admin brand', () => {
    renderLayout();
    screen.getAllByRole('link', { name: 'TrendsFit admin home' }).forEach((a) => expect(a.textContent).toBe('TrendsFit'));
  });
  it('keeps the layout while moving between pages and offers a Dashboard link', async () => {
    renderLayout('/admin');
    expect(screen.getByText('DASH')).toBeInTheDocument();
    await userEvent.click(screen.getAllByRole('link', { name: 'Orders', hidden: true })[0]);
    expect(await screen.findByText('ORDERS PAGE')).toBeInTheDocument();
    expect(screen.getAllByRole('link', { name: 'Dashboard' }).length).toBeGreaterThan(0);
  });
  it('opens and closes the mobile drawer', async () => {
    renderLayout();
    const drawer = document.getElementById('admin-drawer');
    expect(drawer).not.toHaveClass('open');
    await userEvent.click(screen.getByRole('button', { name: 'Open admin menu' }));
    expect(drawer).toHaveClass('open');
    expect(document.body).toHaveClass('admin-lock');
    await userEvent.keyboard('{Escape}');
    expect(drawer).not.toHaveClass('open');
    expect(document.body).not.toHaveClass('admin-lock');
    await userEvent.click(screen.getByRole('button', { name: 'Open admin menu' }));
    await userEvent.click(screen.getByRole('button', { name: 'Close menu' }));
    expect(drawer).not.toHaveClass('open');
  });
  it('only lists pages the role may open', () => {
    auth.user = { ...superAdmin, role: 'SUPPORT_MANAGER', permissions: ['SUPPORT_CHAT', 'ORDER_READ'] };
    renderLayout('/admin/support');
    expect(screen.queryAllByRole('link', { name: 'Audit log', hidden: true })).toHaveLength(0);
    expect(screen.queryAllByRole('link', { name: 'Store settings', hidden: true })).toHaveLength(0);
    expect(screen.getAllByRole('link', { name: /Support chat/, hidden: true }).length).toBeGreaterThan(0);
  });
  it('shows the unread chat count from the server', async () => {
    renderLayout('/admin/support');
    expect((await screen.findAllByText('3')).length).toBeGreaterThan(0);
  });
});

describe('Support Thread', () => {
  const conv = { id: 5, customerName: 'Asha', customerEmail: 'a@b.test', status: 'OPEN', orderNumber: 'TF1', orderStatus: 'SHIPPED',
    messages: [{ id: 1, sender: 'CUSTOMER', body: 'Where is my order?', createdAt: '2026-10-17T10:00:00', read: true }, { id: 2, sender: 'STAFF', staffEmail: 'x@y.test', body: 'On its way', createdAt: '2026-10-17T10:05:00', read: true }] };
  it('shows messages in order with delivery state and sends replies', async () => {
    const onSend = vi.fn().mockResolvedValue(true);
    render(<MemoryRouter><Thread conv={conv} onSend={onSend} onToggle={vi.fn()} sending={false} /></MemoryRouter>);
    const bubbles = screen.getAllByText(/Where is my order\?|On its way/).map((n) => n.textContent);
    expect(bubbles).toEqual(['Where is my order?', 'On its way']);
    expect(screen.getByText(/Seen/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'TF1' })).toHaveAttribute('href', '/admin/orders/TF1');
    await userEvent.type(screen.getByLabelText('Reply'), 'Thanks!');
    await userEvent.click(screen.getByRole('button', { name: 'Send' }));
    expect(onSend).toHaveBeenCalledWith('Thanks!');
  });
  it('does not send an empty reply', () => {
    render(<MemoryRouter><Thread conv={conv} onSend={vi.fn()} onToggle={vi.fn()} sending={false} /></MemoryRouter>);
    expect(screen.getByRole('button', { name: 'Send' })).toBeDisabled();
  });
});

describe('customer chat storage and validation', () => {
  beforeEach(() => localStorage.clear());
  it('remembers and clears the chat', () => {
    expect(loadChat()).toBeNull();
    saveChat({ id: 7, token: 'a'.repeat(32) });
    expect(loadChat()).toEqual({ id: 7, token: 'a'.repeat(32) });
    clearChat();
    expect(loadChat()).toBeNull();
  });
  it('ignores corrupted storage', () => {
    localStorage.setItem('tf_support_chat', '{bad json');
    expect(loadChat()).toBeNull();
    localStorage.setItem('tf_support_chat', JSON.stringify({ id: 'x', token: 1 }));
    expect(loadChat()).toBeNull();
  });
  it('validates the start form', () => {
    expect(Object.keys(validateStart({ name: '', email: 'bad', message: '' })).sort()).toEqual(['email', 'message', 'name']);
    expect(validateStart({ name: 'A', email: 'a@b.co', message: 'hi' })).toEqual({});
    expect(validateStart({ name: 'A', email: 'a@b.co', message: 'x'.repeat(2001) }).message).toMatch(/2000/);
  });
});
