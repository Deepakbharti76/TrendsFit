import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import ProductCard from '../components/ProductCard';
import RequireAuth from '../components/RequireAuth';
import { Pagination, Price, StatusBadge } from '../components/Bits';

const authState = { user: null, ready: true, wishlist: [], toggleWish: vi.fn() };
vi.mock('../lib/AppContext', () => ({
  useAuth: () => authState,
  useToast: () => ({ push: vi.fn() }),
  useCart: () => ({ lines: [], count: 0 }),
  useConfig: () => ({ exchangeWindowDays: 7 }),
}));

const product = { id: 1, slug: 'slim-jeans', name: 'Slim Jeans', category: 'Jeans', price: 1999, salePrice: 1499, finalPrice: 1499, discountPercent: 25, image: '/a.jpg', image2: '/b.jpg', inStock: true, isNew: true, colors: [{ name: 'Blue', hex: '#235' }], ratingAvg: 4.5, ratingCount: 12 };

describe('ProductCard', () => {
  it('shows name, sale price, discount badge and links to the product', () => {
    render(<MemoryRouter><ProductCard p={product} /></MemoryRouter>);
    expect(screen.getByText('Slim Jeans')).toBeInTheDocument();
    expect(screen.getByText('₹1,499')).toBeInTheDocument();
    expect(screen.getByText('₹1,999')).toBeInTheDocument();
    expect(screen.getByText('25% OFF')).toBeInTheDocument();
    expect(screen.getAllByRole('link', { name: /slim jeans/i })[0]).toHaveAttribute('href', '/product/slim-jeans');
  });
  it('marks sold-out products', () => {
    render(<MemoryRouter><ProductCard p={{ ...product, inStock: false }} /></MemoryRouter>);
    expect(screen.getByText('SOLD OUT')).toBeInTheDocument();
  });
  it('sends anonymous users to log in when they tap the wishlist heart', async () => {
    render(<MemoryRouter initialEntries={['/']}><Routes><Route path="/" element={<ProductCard p={product} />} /><Route path="/login" element={<div>LOGIN PAGE</div>} /></Routes></MemoryRouter>);
    await userEvent.click(screen.getByRole('button', { name: /add to wishlist/i }));
    expect(screen.getByText('LOGIN PAGE')).toBeInTheDocument();
  });
});

describe('RequireAuth', () => {
  const tree = (staff) => (
    <MemoryRouter initialEntries={['/secret']}>
      <Routes><Route path="/secret" element={<RequireAuth staff={staff}><div>SECRET</div></RequireAuth>} /><Route path="/login" element={<div>LOGIN</div>} /></Routes>
    </MemoryRouter>
  );
  it('redirects anonymous visitors', () => { authState.user = null; render(tree(false)); expect(screen.getByText('LOGIN')).toBeInTheDocument(); });
  it('lets customers in to customer pages but not admin pages', () => {
    authState.user = { staff: false };
    const { unmount } = render(tree(false)); expect(screen.getByText('SECRET')).toBeInTheDocument(); unmount();
    render(tree(true)); expect(screen.queryByText('SECRET')).not.toBeInTheDocument(); expect(screen.getByText(/do not have access/i)).toBeInTheDocument();
  });
  it('lets staff into admin pages', () => { authState.user = { staff: true }; render(tree(true)); expect(screen.getByText('SECRET')).toBeInTheDocument(); });
});

describe('small components', () => {
  it('hides the strikethrough price when there is no sale', () => {
    render(<Price price={999} salePrice={null} finalPrice={999} discountPercent={0} />);
    expect(screen.getByText('₹999')).toBeInTheDocument();
    expect(document.querySelector('.price-was')).toBeNull();
  });
  it('paginates', async () => {
    const onChange = vi.fn();
    render(<Pagination page={0} totalPages={3} onChange={onChange} />);
    await userEvent.click(screen.getByRole('button', { name: 'Next' }));
    expect(onChange).toHaveBeenCalledWith(1);
    expect(screen.getByRole('button', { name: 'Previous' }).closest('li')).toHaveClass('disabled');
  });
  it('renders nothing for a single page', () => { const { container } = render(<Pagination page={0} totalPages={1} onChange={() => {}} />); expect(container).toBeEmptyDOMElement(); });
  it('labels statuses', () => { render(<StatusBadge status="DELIVERED" label="Delivered" />); expect(screen.getByText('Delivered')).toHaveClass('text-bg-success'); });
});
