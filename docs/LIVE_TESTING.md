# Live testing checklist (needs real accounts / browser)
- [ ] `mvn test` passes (incl. Docker integration test)
- [ ] Razorpay TEST keys: pay, fail, close modal, duplicate verify, webhook replay, amount mismatch
- [ ] Order expiry after 30 min releases stock; pay-after-expiry flags audit entry
- [ ] Shiprocket: pincode check, create shipment, AWB, label, webhook status moves; **exchange reverse pickup**
- [ ] SMTP: verify email, reset email, order confirmation
- [ ] Exchange: allowed on day 7, "Exchange period expired" on day 8
- [ ] Real phones (iOS Safari, Android Chrome), slow 3G, Lighthouse
- [ ] Admin RBAC per role; COD flow; invoice download
