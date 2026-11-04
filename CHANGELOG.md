# CHANGELOG

## Update 4 — Colour-specific product images

- **Product page:** the colour buttons show only the colours that product actually has (taken from its active variants). Clicking a colour switches the gallery to the images tagged with that colour. If a colour has no tagged images, the common (untagged) images are shown; products with no tagged images behave exactly as before.
- **Cart / order:** the cart line uses the selected colour's image. The colour on the order still comes from the chosen variant on the server.
- **Admin → Edit product:** each image has an optional "Colour" dropdown (All colours / the product's own variant colours).
- **Database:** new migration `V4__product_image_color.sql` adds a nullable `color` column to `product_images`. Existing images stay `NULL` (= all colours). There is no global colour limit.
- **To make a shirt show only Black and Pink:** in Admin → Products → edit the shirt, delete or untick Active on the variants of every other colour, tag the two photos Black / Pink, and Save.
- Files: `backend/.../db/migration/V4__product_image_color.sql`, `model/ProductImage.java`, `service/CatalogService.java`, `service/AdminProductService.java`, `frontend/src/pages/Product.jsx`, `frontend/src/admin/ProductEdit.jsx`, `CHANGELOG.md`

## Update 3 — Mobile menu full-screen overlay

- **Fixed:** on phones/tablets the open menu was an in-flow block inside the sticky header, so it only pushed the page down (the hero showed below it) and the page still scrolled.
- The open menu is now a full-screen overlay (`position: fixed; inset: 0; z-index: 2000`) on screens under 992px. Logo, X, search, account and cart icons stay in the same place.
- Page scroll is locked while it is open (`html.menu-locked`) and restored on close; the scroll position is kept.
- Closes with the X button, the Esc key, a link tap, or when the window becomes desktop-wide.
- Desktop navbar is unchanged.
- Files: `frontend/src/components/Layout.jsx`, `frontend/src/styles.css`, `CHANGELOG.md`

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
