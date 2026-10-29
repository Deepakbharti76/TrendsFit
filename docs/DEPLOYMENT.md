# Deployment (low cost)
1. Build: `cd backend && mvn -DskipTests package`; `cd frontend && npm ci && npm run build`.
2. Run the jar behind nginx with HTTPS; serve `frontend/dist` statically with SPA fallback to `index.html`.
3. Set `FORWARD_HEADERS_STRATEGY=framework` behind a proxy so rate limiting sees real client IPs.
4. Use the default (prod) profile — no demo data. Set all env vars from `.env.example`; never commit `.env`.
5. Social/crawler previews: in nginx, rewrite known bot user-agents on `/product/<slug>` to `/seo/product/<slug>` on the backend.
6. Razorpay webhook URL: `https://<domain>/api/payments/webhook`. Shiprocket webhook: `https://<domain>/api/shipping/webhook` with header `x-api-key: $SHIPROCKET_WEBHOOK_TOKEN`.
7. Back up MySQL daily; mount a persistent `UPLOAD_DIR`.
