package in.trendsfit.service;

import in.trendsfit.config.AppConfig;
import in.trendsfit.domain.CouponRules;
import in.trendsfit.domain.ExchangeWindow;
import in.trendsfit.domain.Hmac;
import in.trendsfit.domain.Money;
import in.trendsfit.domain.OrderStatus;
import in.trendsfit.domain.PricingCalculator;
import in.trendsfit.domain.Role;
import in.trendsfit.domain.Validators;
import in.trendsfit.model.Coupon;
import in.trendsfit.model.Order;
import in.trendsfit.model.OrderItem;
import in.trendsfit.model.Product;
import in.trendsfit.model.ProductVariant;
import in.trendsfit.repo.CouponRepository;
import in.trendsfit.repo.OrderRepository;
import in.trendsfit.repo.ProductVariantRepository;
import in.trendsfit.security.AuthUser;
import in.trendsfit.security.CurrentUser;
import in.trendsfit.web.ApiException;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Orders: quoting, creation (server-side totals, stock reservation), lifecycle transitions and customer views. */
@Service
public class OrderService {
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RNG = new SecureRandom();

    private final OrderRepository orders;
    private final ProductVariantRepository variants;
    private final CouponRepository coupons;
    private final JdbcTemplate jdbc;
    private final AppConfig cfg;
    private final Clock clock;
    private final EmailService mail;
    private final AuditService audit;

    public OrderService(OrderRepository orders, ProductVariantRepository variants, CouponRepository coupons, JdbcTemplate jdbc,
                        AppConfig cfg, Clock clock, EmailService mail, AuditService audit) {
        this.orders = orders; this.variants = variants; this.coupons = coupons; this.jdbc = jdbc;
        this.cfg = cfg; this.clock = clock; this.mail = mail; this.audit = audit;
    }

    // ---------------------------------------------------------------- requests

    public record ItemReq(Long variantId, Integer qty) {}
    public record Customer(String name, String email, String phone) {}
    public record ShipTo(String line1, String line2, String city, String state, String pincode) {}
    public record CreateReq(Customer customer, ShipTo shipping, List<ItemReq> items, String couponCode, String paymentMethod) {}

    // ---------------------------------------------------------------- quote (cart page)

    /** A priced cart. Nothing is reserved or stored. Totals always come from database prices. */
    @Transactional(readOnly = true)
    public Map<String, Object> quote(List<ItemReq> items, String couponCode, String email) {
        Priced p = price(items, couponCode, email);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("items", p.view);
        m.put("subtotal", p.totals.subtotal());
        m.put("discount", p.totals.discount());
        m.put("shipping", p.totals.shipping());
        m.put("total", p.totals.total());
        m.put("couponCode", p.coupon == null ? null : p.coupon.code);
        m.put("couponMessage", p.couponMessage);
        m.put("codAvailable", cfg.codEnabled && p.totals.total().compareTo(cfg.codMaxOrderValue) <= 0);
        m.put("onlineAvailable", cfg.razorpayConfigured());
        return m;
    }

    private record Priced(List<ProductVariant> variants, List<Integer> qtys, PricingCalculator.Totals totals, Coupon coupon,
                          String couponMessage, List<Map<String, Object>> view) {}

    private Priced price(List<ItemReq> items, String couponCode, String email) {
        if (items == null || items.isEmpty()) throw ApiException.badRequest("EMPTY_CART", "Your cart is empty.");
        Map<Long, Integer> merged = new TreeMap<>();
        for (ItemReq i : items) {
            if (i == null || i.variantId() == null || i.qty() == null || i.qty() < 1)
                throw ApiException.badRequest("BAD_ITEM", "Invalid cart item.");
            merged.merge(i.variantId(), i.qty(), Integer::sum);
        }
        if (merged.size() > 30) throw ApiException.badRequest("CART_TOO_BIG", "Too many different items in one order.");
        List<ProductVariant> vs = variants.findAllWithProduct(merged.keySet());
        if (vs.size() != merged.size()) throw ApiException.badRequest("ITEM_UNAVAILABLE", "One or more items are no longer available.");
        vs.sort((a, b) -> Long.compare(a.id, b.id));
        List<PricingCalculator.Line> lines = new ArrayList<>();
        List<Integer> qtys = new ArrayList<>();
        List<Map<String, Object>> view = new ArrayList<>();
        for (ProductVariant v : vs) {
            Product p = v.product;
            int qty = merged.get(v.id);
            if (qty > 10) throw ApiException.badRequest("QTY_LIMIT", "You can order at most 10 units of one item.");
            if (!v.active || !p.isActive())
                throw ApiException.badRequest("ITEM_UNAVAILABLE", p.name + " is no longer available.");
            BigDecimal unit = PricingCalculator.unitPrice(p.price, p.salePrice, v.priceOverride);
            lines.add(new PricingCalculator.Line(v.id, unit, qty));
            qtys.add(qty);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("variantId", v.id);
            row.put("productId", p.id);
            row.put("slug", p.slug);
            row.put("name", p.name);
            row.put("size", v.size);
            row.put("color", v.color);
            row.put("unitPrice", unit);
            row.put("qty", qty);
            row.put("lineTotal", Money.round(unit.multiply(BigDecimal.valueOf(qty))));
            row.put("inStock", v.stock >= qty);
            row.put("stock", Math.max(0, Math.min(v.stock, 99)));
            row.put("image", firstImage(p));
            view.add(row);
        }
        BigDecimal subtotal = PricingCalculator.compute(lines, Money.ZERO, cfg.shippingPolicy()).subtotal();
        Coupon coupon = null;
        String couponMsg = null;
        BigDecimal discount = Money.ZERO;
        if (couponCode != null && !couponCode.isBlank()) {
            Optional<Coupon> c = coupons.findByCodeIgnoreCase(couponCode.trim());
            if (c.isEmpty()) {
                throw ApiException.badRequest("COUPON_INVALID", "This coupon code is not valid.");
            }
            Coupon cp = c.get();
            int uses = (email == null || email.isBlank()) ? 0
                : jdbc.queryForObject("SELECT COUNT(*) FROM coupon_redemptions WHERE coupon_id = ? AND email = ?", Integer.class, cp.id, email.trim().toLowerCase());
            CouponRules.Outcome o = CouponRules.evaluate(toRule(cp), subtotal, LocalDateTime.now(clock), uses);
            if (!o.valid()) throw ApiException.badRequest("COUPON_" + o.code(), o.message());
            discount = o.discount();
            coupon = cp;
            couponMsg = o.message();
        }
        PricingCalculator.Totals totals = PricingCalculator.compute(lines, discount, cfg.shippingPolicy());
        return new Priced(vs, qtys, totals, coupon, couponMsg, view);
    }

    static CouponRules.Coupon toRule(Coupon c) {
        return new CouponRules.Coupon(c.code, CouponRules.Type.valueOf(c.type), c.value, c.minOrder, c.maxDiscount,
            c.startsAt, c.expiresAt, c.maxUses, c.perUserLimit, c.usedCount, c.active);
    }

    private static String firstImage(Product p) {
        return p.images.isEmpty() ? null : p.images.get(0).url;
    }

    // ---------------------------------------------------------------- create

    @Transactional
    public Order create(CreateReq req, String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            if (idempotencyKey.length() > 64) throw ApiException.badRequest("BAD_KEY", "Invalid idempotency key.");
            Optional<Order> existing = orders.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) return existing.get();
        }
        validate(req);
        String method = req.paymentMethod().trim().toUpperCase();
        if (method.equals("ONLINE") && !cfg.razorpayConfigured())
            throw ApiException.unavailable("Online payment is not available right now. Please choose Cash on Delivery or try again later.");
        if (method.equals("COD") && !cfg.codEnabled)
            throw ApiException.badRequest("COD_DISABLED", "Cash on Delivery is not available.");

        String email = req.customer().email().trim().toLowerCase();
        Priced pr = price(req.items(), req.couponCode(), email);
        if (method.equals("COD") && pr.totals.total().compareTo(cfg.codMaxOrderValue) > 0)
            throw ApiException.badRequest("COD_LIMIT", "Cash on Delivery is available for orders up to ₹" + cfg.codMaxOrderValue.toPlainString() + ".");

        Order o = new Order();
        AuthUser u = CurrentUser.get();
        o.userId = u == null ? null : u.id();
        o.customerName = req.customer().name().trim();
        o.email = email;
        o.phone = Validators.mobileExact(req.customer().phone());
        ShipTo s = req.shipping();
        o.shipLine1 = s.line1().trim();
        o.shipLine2 = s.line2() == null || s.line2().isBlank() ? null : s.line2().trim();
        o.shipCity = s.city().trim();
        o.shipState = s.state().trim();
        o.shipPincode = s.pincode().trim();
        o.paymentMethod = method;
        o.subtotal = pr.totals.subtotal();
        o.discount = pr.totals.discount();
        o.shipping = pr.totals.shipping();
        o.total = pr.totals.total();
        o.couponCode = pr.coupon == null ? null : pr.coupon.code;
        o.idempotencyKey = idempotencyKey == null || idempotencyKey.isBlank() ? null : idempotencyKey;
        o.accessToken = Tokens.random(24).substring(0, 32).replace('-', 'a').replace('_', 'b');
        o.orderNumber = newOrderNumber();
        o.stockHeld = true;
        for (int i = 0; i < pr.variants.size(); i++) {
            ProductVariant v = pr.variants.get(i);
            int qty = pr.qtys.get(i);
            OrderItem it = new OrderItem();
            it.order = o;
            it.productId = v.product.id;
            it.variantId = v.id;
            it.name = v.product.name;
            it.sku = v.sku;
            it.size = v.size;
            it.color = v.color;
            it.imageUrl = firstImage(v.product);
            it.unitPrice = PricingCalculator.unitPrice(v.product.price, v.product.salePrice, v.priceOverride);
            it.qty = qty;
            it.lineTotal = Money.round(it.unitPrice.multiply(BigDecimal.valueOf(qty)));
            o.items.add(it);
        }
        if (method.equals("COD")) {
            o.status = OrderStatus.PROCESSING.name();
            o.paymentStatus = "COD";
        } else {
            o.status = OrderStatus.PENDING_PAYMENT.name();
            o.paymentStatus = "PENDING";
        }
        try {
            o = orders.saveAndFlush(o);
        } catch (DataIntegrityViolationException e) {
            if (o.idempotencyKey != null) {
                Optional<Order> again = orders.findByIdempotencyKey(o.idempotencyKey);
                if (again.isPresent()) return again.get();
            }
            throw e;
        }
        // Stock is reserved atomically; a shortage throws and rolls the whole order back.
        for (OrderItem it : o.items) reserve(it.variantId, it.qty, "ORDER_RESERVED", o.id, it.name + " (" + it.size + ")");
        if (pr.coupon != null) redeem(pr.coupon, o);
        history(o.id, o.status, method.equals("COD") ? "Order placed (Cash on Delivery)" : "Order placed, waiting for payment");
        if (method.equals("COD")) mail.sendOrderConfirmation(o.email, o.orderNumber, "₹" + o.total.toPlainString(), o.accessToken);
        return o;
    }

    private void validate(CreateReq r) {
        Map<String, String> e = new LinkedHashMap<>();
        if (r == null || r.customer() == null || r.shipping() == null) throw ApiException.badRequest("BAD_REQUEST", "Missing order details.");
        Customer c = r.customer();
        ShipTo s = r.shipping();
        if (c.name() == null || c.name().trim().length() < 2) e.put("customer.name", "Enter your full name.");
        if (!Validators.email(c.email())) e.put("customer.email", "Enter a valid email address.");
        if (c.phone() == null || c.phone().isBlank()) e.put("customer.phone", "Mobile number is required.");
        else if (Validators.mobileExact(c.phone()) == null) e.put("customer.phone", "Enter a valid 10-digit mobile number (digits only, no spaces or +91).");
        if (blank(s.line1()) || s.line1().length() > 200) e.put("shipping.line1", "Enter your address.");
        if (blank(s.city())) e.put("shipping.city", "Enter your city.");
        if (blank(s.state())) e.put("shipping.state", "Enter your state.");
        if (!Validators.pincode(s.pincode())) e.put("shipping.pincode", "Enter a valid 6-digit pincode.");
        if (r.paymentMethod() == null || !(r.paymentMethod().equalsIgnoreCase("ONLINE") || r.paymentMethod().equalsIgnoreCase("COD")))
            e.put("paymentMethod", "Choose a payment method.");
        if (!e.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", "Please correct the highlighted fields.", e);
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }

    private String newOrderNumber() {
        StringBuilder sb = new StringBuilder("TF").append(LocalDateTime.now(clock).format(DateTimeFormatter.ofPattern("yyMMdd"))).append('-');
        for (int i = 0; i < 5; i++) sb.append(ALPHABET.charAt(RNG.nextInt(ALPHABET.length())));
        return sb.toString();
    }

    private void redeem(Coupon c, Order o) {
        int n = jdbc.update("UPDATE coupons SET used_count = used_count + 1 WHERE id = ? AND active = 1 AND (max_uses IS NULL OR used_count < max_uses)", c.id);
        if (n == 0) throw ApiException.conflict("COUPON_EXHAUSTED", "This coupon has just reached its usage limit.");
        jdbc.update("INSERT INTO coupon_redemptions (coupon_id, user_id, email, order_id, discount) VALUES (?,?,?,?,?)", c.id, o.userId, o.email, o.id, o.discount);
    }

    // ---------------------------------------------------------------- stock

    /** Atomic decrement. Never lets stock go below zero, even with concurrent orders. */
    public void reserve(long variantId, int qty, String reason, long orderId, String label) {
        int n = jdbc.update("UPDATE product_variants SET stock = stock - ? WHERE id = ? AND active = 1 AND stock >= ?", qty, variantId, qty);
        if (n == 0) throw ApiException.conflict("OUT_OF_STOCK", label + " is out of stock or has fewer units than requested.");
        jdbc.update("INSERT INTO stock_movements (variant_id, delta, reason, ref_type, ref_id, actor_email) VALUES (?,?,?,?,?,?)",
            variantId, -qty, reason, "ORDER", orderId, null);
    }

    public void release(long variantId, int qty, String reason, long orderId) {
        jdbc.update("UPDATE product_variants SET stock = stock + ? WHERE id = ?", qty, variantId);
        jdbc.update("INSERT INTO stock_movements (variant_id, delta, reason, ref_type, ref_id, actor_email) VALUES (?,?,?,?,?,?)",
            variantId, qty, reason, "ORDER", orderId, null);
    }

    /**
     * Re-reserve stock for an order whose hold was released after a payment timeout (used when the customer pays again).
     * Throws OUT_OF_STOCK (and rolls everything back) when any item can no longer be reserved.
     */
    @Transactional
    public void reacquireStock(Order detached) {
        Order o = orders.findById(detached.id).orElseThrow(() -> ApiException.notFound("Order not found."));
        if (o.stockHeld) return;
        for (OrderItem it : o.items) reserve(it.variantId, it.qty, "ORDER_RESERVED", o.id, it.name + " (" + it.size + ")");
        o.stockHeld = true;
        orders.save(o);
    }

    /** Called when the customer pays again after a failure or expiry: re-holds stock and returns the order to PENDING_PAYMENT. */
    @Transactional
    public Order prepareRetry(Order detached) {
        Order o = orders.findById(detached.id).orElseThrow(() -> ApiException.notFound("Order not found."));
        if (o.statusEnum() == OrderStatus.PAYMENT_FAILED) {
            if (!o.stockHeld) {
                for (OrderItem it : o.items) reserve(it.variantId, it.qty, "ORDER_RESERVED", o.id, it.name + " (" + it.size + ")");
                o.stockHeld = true;
            }
            o.paymentStatus = "PENDING";
            changeStatus(o, OrderStatus.PENDING_PAYMENT, "Customer retrying payment");
        }
        return o;
    }

    /** For money already received after the stock hold expired: reserve what we can and report shortages for manual follow-up. */
    public List<String> reserveBestEffort(Order o) {
        List<String> shortages = new ArrayList<>();
        if (o.stockHeld) return shortages;
        for (OrderItem it : o.items) {
            try {
                reserve(it.variantId, it.qty, "ORDER_RESERVED", o.id, it.name);
            } catch (ApiException e) {
                shortages.add(it.name + " " + it.size + " x" + it.qty);
            }
        }
        o.stockHeld = true;
        return shortages;
    }

    // ---------------------------------------------------------------- lifecycle

    /** Validates against the lifecycle graph, stores history, sets delivered_at, e-mails the customer on key steps. */
    @Transactional
    public Order changeStatus(Order o, OrderStatus target, String note) {
        OrderStatus cur = o.statusEnum();
        if (cur == target) return o;
        if (!cur.canMoveTo(target))
            throw ApiException.conflict("BAD_TRANSITION", "An order cannot move from " + cur.label() + " to " + target.label() + ".");
        o.status = target.name();
        if (target == OrderStatus.DELIVERED && o.deliveredAt == null) o.deliveredAt = LocalDateTime.now(clock);
        orders.save(o);
        history(o.id, target.name(), note);
        if (target == OrderStatus.PAID || target == OrderStatus.SHIPPED || target == OrderStatus.OUT_FOR_DELIVERY || target == OrderStatus.DELIVERED)
            mail.sendStatus(o.email, o.orderNumber, target.label(), o.accessToken);
        return o;
    }

    public void history(long orderId, String status, String note) {
        jdbc.update("INSERT INTO order_status_history (order_id, status, note) VALUES (?,?,?)", orderId, status,
            note == null ? null : (note.length() > 300 ? note.substring(0, 300) : note));
    }

    /**
     * System expiry (not a customer cancel): unpaid online orders past the payment window are marked PAYMENT_FAILED
     * and their stock is released. The customer can still pay later; stock is then re-reserved if available.
     */
    @Transactional
    public int expireUnpaid() {
        LocalDateTime cutoff = LocalDateTime.now(clock).minusMinutes(cfg.paymentExpiryMinutes);
        List<Long> ids = jdbc.queryForList(
            "SELECT id FROM orders WHERE status IN ('PENDING_PAYMENT','PAYMENT_FAILED') AND payment_method = 'ONLINE' AND stock_held = 1 AND created_at < ?", Long.class, cutoff);
        int n = 0;
        for (Long id : ids) {
            Order o = orders.findById(id).orElse(null);
            if (o == null || !o.stockHeld || !("PENDING_PAYMENT".equals(o.status) || "PAYMENT_FAILED".equals(o.status))) continue;
            for (OrderItem it : o.items) release(it.variantId, it.qty, "PAYMENT_EXPIRED", o.id);
            o.stockHeld = false;
            o.status = OrderStatus.PAYMENT_FAILED.name();
            o.paymentStatus = "EXPIRED";
            jdbc.update("UPDATE payments SET status = 'EXPIRED' WHERE order_id = ? AND status = 'CREATED'", o.id);
            orders.save(o);
            history(o.id, o.status, "Payment window expired. Stock released.");
            n++;
        }
        return n;
    }

    // ---------------------------------------------------------------- reading

    public Order requireByNumber(String orderNumber) {
        return orders.findByOrderNumber(orderNumber).orElseThrow(() -> ApiException.notFound("Order not found."));
    }

    /** Owner (JWT), staff with ORDER_READ, or holder of the secret access token. Anything else looks like "not found". */
    @Transactional(readOnly = true)
    public Order authorize(String orderNumber, String token) {
        Order o = orders.findByOrderNumber(orderNumber == null ? "" : orderNumber).orElseThrow(() -> ApiException.notFound("Order not found."));
        AuthUser u = CurrentUser.get();
        if (u != null) {
            if (o.userId != null && o.userId == u.id()) return o;
            if (u.role().can(Role.Permission.ORDER_READ)) return o;
        }
        if (token != null && !token.isBlank() && Hmac.safeEquals(token, o.accessToken)) return o;
        throw ApiException.notFound("Order not found.");
    }

    @Transactional(readOnly = true)
    public Map<String, Object> view(Order detached) {
        Order o = orders.findById(detached.id).orElseThrow(() -> ApiException.notFound("Order not found."));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("orderNumber", o.orderNumber);
        m.put("status", o.status);
        m.put("statusLabel", o.statusEnum().label());
        m.put("paymentMethod", o.paymentMethod);
        m.put("paymentStatus", o.paymentStatus);
        m.put("customerName", o.customerName);
        m.put("email", o.email);
        m.put("phone", o.phone);
        Map<String, Object> ship = new LinkedHashMap<>();
        ship.put("line1", o.shipLine1);
        ship.put("line2", o.shipLine2);
        ship.put("city", o.shipCity);
        ship.put("state", o.shipState);
        ship.put("pincode", o.shipPincode);
        m.put("shipping", ship);
        m.put("subtotal", o.subtotal);
        m.put("discount", o.discount);
        m.put("shippingCharge", o.shipping);
        m.put("total", o.total);
        m.put("couponCode", o.couponCode);
        m.put("createdAt", o.createdAt);
        m.put("deliveredAt", o.deliveredAt);
        List<Map<String, Object>> items = new ArrayList<>();
        Map<Long, String> slugs = new java.util.HashMap<>();
        if (!o.items.isEmpty()) {
            String in = String.join(",", o.items.stream().map(i -> "?").toList());
            for (Map<String, Object> row : jdbc.queryForList("SELECT id, slug FROM products WHERE id IN (" + in + ")", o.items.stream().map(i -> (Object) i.productId).toArray()))
                slugs.put(((Number) row.get("id")).longValue(), (String) row.get("slug"));
        }
        for (OrderItem it : o.items) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("slug", slugs.get(it.productId));
            r.put("id", it.id);
            r.put("productId", it.productId);
            r.put("variantId", it.variantId);
            r.put("name", it.name);
            r.put("size", it.size);
            r.put("color", it.color);
            r.put("image", it.imageUrl);
            r.put("qty", it.qty);
            r.put("unitPrice", it.unitPrice);
            r.put("lineTotal", it.lineTotal);
            items.add(r);
        }
        m.put("items", items);
        m.put("timeline", jdbc.queryForList("SELECT status, note, created_at AS at FROM order_status_history WHERE order_id = ? ORDER BY id", o.id));
        m.put("shipments", jdbc.queryForList(
            "SELECT kind, awb, courier, status_text AS statusText, tracking_url AS trackingUrl, estimated_delivery AS estimatedDelivery FROM shipments WHERE order_id = ? ORDER BY id", o.id));
        ExchangeWindow.Result ex = ExchangeWindow.check(o.deliveredAt, LocalDateTime.now(clock), cfg.exchangeWindowDays);
        Map<String, Object> exm = new LinkedHashMap<>();
        exm.put("eligible", ex.eligible());
        exm.put("message", ex.eligible() ? "You can request an exchange until " + ex.closesAt().toLocalDate() + "." : ex.message());
        exm.put("closesAt", ex.closesAt());
        exm.put("windowDays", cfg.exchangeWindowDays);
        m.put("exchangeEligibility", exm);
        m.put("exchanges", jdbc.queryForList("SELECT id, order_item_id AS orderItemId, new_variant_id AS newVariantId, reason, status, admin_note AS adminNote, created_at AS createdAt FROM exchanges WHERE order_id = ? ORDER BY id DESC", o.id));
        m.put("canPay", (o.status.equals("PENDING_PAYMENT") || o.status.equals("PAYMENT_FAILED")) && o.paymentMethod.equals("ONLINE"));
        m.put("invoiceAvailable", !o.status.equals("PENDING_PAYMENT") && !o.status.equals("PAYMENT_FAILED"));
        return m;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listForUser(long userId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Order o : orders.findByUserIdOrderByIdDesc(userId)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("orderNumber", o.orderNumber);
            m.put("status", o.status);
            m.put("statusLabel", o.statusEnum().label());
            m.put("total", o.total);
            m.put("createdAt", o.createdAt);
            m.put("itemCount", o.items.stream().mapToInt(i -> i.qty).sum());
            m.put("thumb", o.items.isEmpty() ? null : o.items.get(0).imageUrl);
            m.put("accessToken", o.accessToken);
            out.add(m);
        }
        return out;
    }
}
