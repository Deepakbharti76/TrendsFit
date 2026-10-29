# CHANGELOG

## Update 2 — storefront/admin fixes
- **View store** (`frontend/src/admin/AdminLayout.jsx`, `lib/adminNav.js` `openStoreTab`): first click opens a named tab, later clicks focus it; admin tab untouched.
- **Admin brand** (`styles.css`): `.admin-side a` flex rule no longer splits "Trends" / "Fit".
- **Password eye**: `components/Bits.jsx` `PasswordInput`, used in `pages/Auth.jsx` (login incl. admin, register, reset), `pages/Account.jsx` (change password), `admin/Misc.jsx` (new staff).
- **Search suggestions**: new `components/SearchSuggest.jsx` (uses existing `GET /products?q=`), wired in `components/Layout.jsx`.
- **Mobile number / pincode / required fields**: new `lib/forms.js`; `pages/Checkout.jsx`, `pages/Auth.jsx`, `pages/Account.jsx` (addresses); `components/Bits.jsx` `Field required` (red `*`). Backend: `domain/Validators.java` `mobileExact`, used by `service/AuthService.java` (mobile now mandatory), `AccountService.java`, `OrderService.java`.
- **Mobile storefront menu**: "Back to Admin" removed (`components/Layout.jsx`).
- **Product form on mobile**: `styles.css` (`.stat` height:100% made stacked cards overlap).
- **Shipping**: `admin/Orders.jsx` (single notice when Shiprocket missing, busy guard), `lib/AppContext.jsx` (toast de-duplication), `service/ShippingService.java` (clearer message), `service/AdminService.java` (`shiprocketConfigured` in order detail).
- Tests: `frontend/src/test/forms.test.jsx`, `toast.test.jsx` (new); `admin.test.jsx`, `lib.test.js`, `backend/.../ValidationAndInvoiceTest.java` updated.

## Update 1 — admin panel upgrade
Responsive admin shell, dashboard, revenue circle/trend, reports, settings, payments/COD, shipments, categories, banners, support chat (storefront `/support` + `/admin/support`), notification bell; Flyway `V2__app_settings.sql`, `V3__admin_panel.sql`; new services `SettingsService`, `AnalyticsService`, `SupportService`, `AdminOpsService`, controllers `AdminPanelController`, `SupportController`; domain `Settings`, `RevenueProgress`; permissions `SETTINGS_WRITE`, `SUPPORT_CHAT`, `CONTENT_WRITE`, `PAYMENT_READ`; `scripts/verify-sql-admin.sh`.
