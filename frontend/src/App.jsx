import { Suspense, lazy } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import Layout from './components/Layout';
import { Spinner } from './components/Bits';
import Home from './pages/Home';
import Shop from './pages/Shop';
import RequireAuth from './components/RequireAuth';

const Product = lazy(() => import('./pages/Product'));
const Cart = lazy(() => import('./pages/Cart'));
const Checkout = lazy(() => import('./pages/Checkout'));
const OrderPage = lazy(() => import('./pages/Order'));
const TrackOrder = lazy(() => import('./pages/TrackOrder'));
const Auth = () => import('./pages/Auth');
const Login = lazy(() => Auth().then((m) => ({ default: m.Login })));
const Register = lazy(() => Auth().then((m) => ({ default: m.Register })));
const Forgot = lazy(() => Auth().then((m) => ({ default: m.Forgot })));
const Reset = lazy(() => Auth().then((m) => ({ default: m.Reset })));
const VerifyEmail = lazy(() => Auth().then((m) => ({ default: m.VerifyEmail })));
const Acc = () => import('./pages/Account');
const AccountLayout = lazy(() => Acc().then((m) => ({ default: m.AccountLayout })));
const Profile = lazy(() => Acc().then((m) => ({ default: m.Profile })));
const MyOrders = lazy(() => Acc().then((m) => ({ default: m.MyOrders })));
const Addresses = lazy(() => Acc().then((m) => ({ default: m.Addresses })));
const Wishlist = lazy(() => Acc().then((m) => ({ default: m.Wishlist })));
const St = () => import('./pages/Static');
const Faq = lazy(() => St().then((m) => ({ default: m.Faq })));
const ShippingPolicy = lazy(() => St().then((m) => ({ default: m.ShippingPolicy })));
const ExchangePolicy = lazy(() => St().then((m) => ({ default: m.ExchangePolicy })));
const Privacy = lazy(() => St().then((m) => ({ default: m.Privacy })));
const Terms = lazy(() => St().then((m) => ({ default: m.Terms })));
const Contact = lazy(() => St().then((m) => ({ default: m.Contact })));
const NotFound = lazy(() => St().then((m) => ({ default: m.NotFound })));

const AdminLayout = lazy(() => import('./admin/AdminLayout'));
const Dashboard = lazy(() => import('./admin/Dashboard'));
const Products = lazy(() => import('./admin/Products'));
const ProductEdit = lazy(() => import('./admin/ProductEdit'));
const Ord = () => import('./admin/Orders');
const AdminOrders = lazy(() => Ord().then((m) => ({ default: m.AdminOrders })));
const AdminOrderDetail = lazy(() => Ord().then((m) => ({ default: m.AdminOrderDetail })));
const settingsPage = (n) => lazy(() => import('./admin/Settings').then((m) => ({ default: m[n] })));
const StoreSettings = settingsPage('StoreSettings'); const ShippingSettings = settingsPage('ShippingSettings');
const PaymentSettings = settingsPage('PaymentSettings'); const NotificationSettings = settingsPage('NotificationSettings');
const Reports = lazy(() => import('./admin/Reports'));
const Support = lazy(() => import('./admin/Support'));
const Ex = () => import('./admin/Extra');
const mkx = (n) => lazy(() => Ex().then((m) => ({ default: m[n] })));
const Payments = mkx('Payments'); const Shipments = mkx('Shipments'); const Categories = mkx('Categories'); const Banners = mkx('Banners');
const SupportChat = lazy(() => import('./pages/SupportChat'));
const Mi = () => import('./admin/Misc');
const mk = (n) => lazy(() => Mi().then((m) => ({ default: m[n] })));
const Inventory = mk('Inventory'); const Exchanges = mk('Exchanges'); const Customers = mk('Customers'); const Coupons = mk('Coupons');
const Reviews = mk('Reviews'); const Newsletter = mk('Newsletter'); const Staff = mk('Staff'); const Audit = mk('Audit');

export default function App() {
  return (
    <Suspense fallback={<Spinner />}>
      <Routes>
        <Route path="/admin" element={<RequireAuth staff><AdminLayout /></RequireAuth>}>
          <Route index element={<Dashboard />} />
          <Route path="products" element={<Products />} />
          <Route path="products/:id" element={<ProductEdit />} />
          <Route path="inventory" element={<Inventory />} />
          <Route path="orders" element={<AdminOrders />} />
          <Route path="orders/:orderNumber" element={<AdminOrderDetail />} />
          <Route path="exchanges" element={<Exchanges />} />
          <Route path="customers" element={<Customers />} />
          <Route path="coupons" element={<Coupons />} />
          <Route path="reviews" element={<Reviews />} />
          <Route path="newsletter" element={<Newsletter />} />
          <Route path="settings" element={<Navigate to="/admin/settings/store" replace />} />
          <Route path="settings/store" element={<StoreSettings />} />
          <Route path="settings/shipping" element={<ShippingSettings />} />
          <Route path="settings/payments" element={<PaymentSettings />} />
          <Route path="settings/notifications" element={<NotificationSettings />} />
          <Route path="reports" element={<Reports />} />
          <Route path="support" element={<Support />} />
          <Route path="payments" element={<Payments />} />
          <Route path="shipments" element={<Shipments />} />
          <Route path="categories" element={<Categories />} />
          <Route path="banners" element={<Banners />} />
          <Route path="staff" element={<Staff />} />
          <Route path="audit" element={<Audit />} />
        </Route>
        <Route element={<Layout />}>
          <Route index element={<Home />} />
          <Route path="shop" element={<Shop />} />
          <Route path="product/:slug" element={<Product />} />
          <Route path="cart" element={<Cart />} />
          <Route path="checkout" element={<Checkout />} />
          <Route path="order/:orderNumber" element={<OrderPage />} />
          <Route path="track-order" element={<TrackOrder />} />
          <Route path="login" element={<Login />} />
          <Route path="register" element={<Register />} />
          <Route path="forgot-password" element={<Forgot />} />
          <Route path="reset-password" element={<Reset />} />
          <Route path="verify-email" element={<VerifyEmail />} />
          <Route path="account" element={<RequireAuth><AccountLayout /></RequireAuth>}>
            <Route index element={<Profile />} />
            <Route path="orders" element={<MyOrders />} />
            <Route path="addresses" element={<Addresses />} />
            <Route path="wishlist" element={<Wishlist />} />
          </Route>
          <Route path="faq" element={<Faq />} />
          <Route path="shipping-policy" element={<ShippingPolicy />} />
          <Route path="exchange-policy" element={<ExchangePolicy />} />
          <Route path="privacy-policy" element={<Privacy />} />
          <Route path="terms" element={<Terms />} />
          <Route path="contact" element={<Contact />} />
          <Route path="support" element={<SupportChat />} />
          <Route path="*" element={<NotFound />} />
        </Route>
      </Routes>
    </Suspense>
  );
}
