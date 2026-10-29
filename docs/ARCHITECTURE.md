# Architecture
- **domain/** pure Java (money, pricing, coupons, order status, exchange window, JWT, HMAC, invoice) — unit-tested without Spring.
- **service/** transactional business logic; stock reserved atomically (`UPDATE ... WHERE stock>=?`).
- **integration/** `PaymentGateway`/`RazorpayClient`, `ShippingGateway`/`ShiprocketClient` (mockable interfaces).
- **security/** stateless Bearer JWT, permission authorities (`PERM_*`), rate limiting, security headers.
- **web/** REST controllers + uniform JSON errors. Flyway owns the schema (`ddl-auto=none`).
- Orders: guest access via per-order `access_token` (header `X-Order-Token`). `Idempotency-Key` prevents duplicate orders.
- Payments: first of verify/webhook atomically marks PAID; webhook events deduped; amount cross-checked.
- Statuses: Pending Payment, Paid, Processing, Packed, Shipped, Out for Delivery, Delivered, Exchange Requested/Approved/Rejected/Completed. No cancel.
## API (summary)
Public: `/api/config /categories /home /products[/{slug}[/related|/reviews]] /cart/quote /delivery/check /newsletter /size-recommendation`, `/api/orders` (+`/{no}`, `/tracking`, `/invoice`, `/payment`, `/payment/verify`, `/payment/failed`, `/exchange-eligibility`, `/exchange`), `/api/auth/*`, webhooks `/api/payments/webhook`, `/api/shipping/webhook`, `/sitemap.xml`, `/robots.txt`, `/seo/product/{slug}`.
Account: `/api/me/*`. Admin: `/api/admin/*` (permission-checked).
