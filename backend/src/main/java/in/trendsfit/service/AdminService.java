package in.trendsfit.service;

import in.trendsfit.config.AppConfig;
import in.trendsfit.domain.OrderStatus;
import in.trendsfit.domain.Role;
import in.trendsfit.domain.Validators;
import in.trendsfit.model.Coupon;
import in.trendsfit.model.Order;
import in.trendsfit.model.User;
import in.trendsfit.repo.CouponRepository;
import in.trendsfit.repo.OrderRepository;
import in.trendsfit.repo.UserRepository;
import in.trendsfit.security.CurrentUser;
import in.trendsfit.web.ApiException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin console back-end: dashboard analytics (real data only), orders, customers, coupons, review moderation and staff roles. */
@Service
public class AdminService {
    /** Statuses an admin may set by hand. Payment states and exchange states move only through their own flows. */
    private static final Set<OrderStatus> MANUAL = EnumSet.of(OrderStatus.PROCESSING, OrderStatus.PACKED, OrderStatus.SHIPPED,
        OrderStatus.OUT_FOR_DELIVERY, OrderStatus.DELIVERED);

    private final JdbcTemplate jdbc;
    private final OrderService orderService;
    private final OrderRepository orders;
    private final CouponRepository coupons;
    private final UserRepository users;
    private final AccountService account;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final AppConfig cfg;
    private final Clock clock;

    public AdminService(JdbcTemplate jdbc, OrderService orderService, OrderRepository orders, CouponRepository coupons, UserRepository users,
                        AccountService account, PasswordEncoder encoder, AuditService audit, AppConfig cfg, Clock clock) {
        this.jdbc = jdbc; this.orderService = orderService; this.orders = orders; this.coupons = coupons; this.users = users;
        this.account = account; this.encoder = encoder; this.audit = audit; this.cfg = cfg; this.clock = clock;
    }

    // ---------------------------------------------------------------- dashboard

    private static final String COUNTED = "status NOT IN ('PENDING_PAYMENT','PAYMENT_FAILED')";

    /** Every number is computed from real rows. An empty shop returns zeros and empty lists, never sample data. */
    public Map<String, Object> dashboard() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime startToday = now.toLocalDate().atStartOfDay();
        LocalDateTime start30 = startToday.minusDays(29);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("revenueToday", sum("SELECT COALESCE(SUM(total),0) FROM orders WHERE " + COUNTED + " AND created_at >= ?", startToday));
        m.put("revenue30d", sum("SELECT COALESCE(SUM(total),0) FROM orders WHERE " + COUNTED + " AND created_at >= ?", start30));
        m.put("orders30d", jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE " + COUNTED + " AND created_at >= ?", Long.class, start30));
        m.put("ordersToday", jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE " + COUNTED + " AND created_at >= ?", Long.class, startToday));
        m.put("customers", jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE role = 'CUSTOMER'", Long.class));
        m.put("newCustomers30d", jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE role = 'CUSTOMER' AND created_at >= ?", Long.class, start30));
        m.put("pendingExchanges", jdbc.queryForObject("SELECT COUNT(*) FROM exchanges WHERE status IN ('PENDING','INFO_REQUESTED')", Long.class));
        m.put("pendingReviews", jdbc.queryForObject("SELECT COUNT(*) FROM reviews WHERE status = 'PENDING'", Long.class));
        m.put("lowStockCount", jdbc.queryForObject("SELECT COUNT(*) FROM product_variants v JOIN products p ON p.id=v.product_id WHERE v.active=1 AND p.status='ACTIVE' AND v.stock <= ?", Long.class, cfg.lowStockThreshold));
        m.put("lowStockThreshold", cfg.lowStockThreshold);
        m.put("ordersByStatus", jdbc.queryForList("SELECT status, COUNT(*) AS count FROM orders GROUP BY status ORDER BY count DESC"));
        // daily series for the last 30 days, zero-filled so the chart is honest
        Map<LocalDate, BigDecimal> rev = new LinkedHashMap<>();
        Map<LocalDate, Long> cnt = new LinkedHashMap<>();
        for (int i = 0; i < 30; i++) { LocalDate d = start30.toLocalDate().plusDays(i); rev.put(d, BigDecimal.ZERO); cnt.put(d, 0L); }
        for (Map<String, Object> r : jdbc.queryForList("SELECT DATE(created_at) AS d, SUM(total) AS revenue, COUNT(*) AS n FROM orders WHERE " + COUNTED + " AND created_at >= ? GROUP BY DATE(created_at)", start30)) {
            Object dv = r.get("d");
            LocalDate d = dv instanceof java.sql.Date sd ? sd.toLocalDate() : LocalDate.parse(String.valueOf(dv).substring(0, 10));
            if (rev.containsKey(d)) { rev.put(d, new BigDecimal(String.valueOf(r.get("revenue")))); cnt.put(d, ((Number) r.get("n")).longValue()); }
        }
        List<Map<String, Object>> series = new ArrayList<>();
        for (LocalDate d : rev.keySet()) series.add(Map.of("date", d.toString(), "revenue", rev.get(d), "orders", cnt.get(d)));
        m.put("dailySeries", series);
        m.put("topProducts", jdbc.queryForList(
            "SELECT i.name, SUM(i.qty) AS units, SUM(i.line_total) AS revenue FROM order_items i JOIN orders o ON o.id=i.order_id WHERE o." + COUNTED
            + " AND o.created_at >= ? GROUP BY i.product_id, i.name ORDER BY units DESC LIMIT 5", start30));
        m.put("lowStock", jdbc.queryForList(
            "SELECT v.id, p.name AS product, v.size, v.color, v.stock FROM product_variants v JOIN products p ON p.id=v.product_id WHERE v.active=1 AND p.status='ACTIVE' AND v.stock <= ? ORDER BY v.stock, p.name LIMIT 10", cfg.lowStockThreshold));
        m.put("integrations", Map.of("razorpay", cfg.razorpayConfigured(), "razorpayWebhook", !cfg.razorpayWebhookSecret.isBlank(),
            "shiprocket", cfg.shiprocketConfigured(), "email", cfg.mailEnabled));
        return m;
    }

    private BigDecimal sum(String sql, Object arg) {
        BigDecimal v = jdbc.queryForObject(sql, BigDecimal.class, arg);
        return v == null ? BigDecimal.ZERO : v;
    }

    // ---------------------------------------------------------------- orders

    public Map<String, Object> orderList(String status, String q, String from, String to, String paymentMethod, String paymentStatus, int page, int pageSize) {
        int size = Math.max(1, Math.min(pageSize, 100));
        List<Object> args = new ArrayList<>();
        StringBuilder w = new StringBuilder(" WHERE 1=1");
        if (status != null && !status.isBlank()) { w.append(" AND o.status = ?"); args.add(status); }
        if (paymentMethod != null && !paymentMethod.isBlank()) { w.append(" AND o.payment_method = ?"); args.add(paymentMethod); }
        if (paymentStatus != null && !paymentStatus.isBlank()) { w.append(" AND o.payment_status = ?"); args.add(paymentStatus); }
        if (q != null && !q.isBlank()) {
            String like = "%" + q.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            w.append(" AND (LOWER(o.order_number) LIKE ? OR LOWER(o.email) LIKE ? OR LOWER(o.customer_name) LIKE ? OR o.phone LIKE ?)");
            args.add(like); args.add(like); args.add(like); args.add(like);
        }
        try {
            if (from != null && !from.isBlank()) { w.append(" AND o.created_at >= ?"); args.add(LocalDate.parse(from).atStartOfDay()); }
            if (to != null && !to.isBlank()) { w.append(" AND o.created_at < ?"); args.add(LocalDate.parse(to).plusDays(1).atStartOfDay()); }
        } catch (java.time.format.DateTimeParseException e) {
            throw ApiException.badRequest("BAD_DATE", "Dates must look like 2026-01-31.");
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM orders o" + w, Long.class, args.toArray());
        List<Object> pa = new ArrayList<>(args);
        pa.add(size);
        pa.add(Math.max(0, page) * size);
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT o.order_number AS orderNumber, o.status, o.payment_method AS paymentMethod, o.payment_status AS paymentStatus, o.total, o.customer_name AS customerName, "
            + "o.email, o.created_at AS createdAt FROM orders o" + w + " ORDER BY o.id DESC LIMIT ? OFFSET ?", pa.toArray());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("items", rows);
        m.put("total", total == null ? 0 : total);
        m.put("page", Math.max(0, page));
        m.put("pageSize", size);
        m.put("totalPages", (int) Math.ceil((total == null ? 0 : total) / (double) size));
        return m;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> orderDetail(String orderNumber) {
        Order o = orderService.requireByNumber(orderNumber);
        Map<String, Object> m = orderService.view(o);
        m.put("payments", jdbc.queryForList("SELECT id, provider_order_id AS providerOrderId, provider_payment_id AS providerPaymentId, amount, status, failure_reason AS failureReason, created_at AS createdAt FROM payments WHERE order_id = ? ORDER BY id DESC", o.id));
        m.put("shipmentRows", jdbc.queryForList("SELECT id, kind, awb, courier, status, status_text AS statusText, tracking_url AS trackingUrl, label_url AS labelUrl FROM shipments WHERE order_id = ? ORDER BY id DESC", o.id));
        m.put("shiprocketConfigured", cfg.shiprocketConfigured());
        m.put("allowedNext", o.statusEnum().next().stream().filter(MANUAL::contains).map(Enum::name).sorted().toList());
        return m;
    }

    @Transactional
    public void setOrderStatus(String orderNumber, String target, String note) {
        OrderStatus t;
        try {
            t = OrderStatus.valueOf(target);
        } catch (RuntimeException e) {
            throw ApiException.badRequest("BAD_STATUS", "Unknown status.");
        }
        if (!MANUAL.contains(t)) throw ApiException.badRequest("BAD_STATUS", "This status cannot be set manually.");
        Order o = orderService.requireByNumber(orderNumber);
        if (o.paymentMethod.equals("ONLINE") && !o.paymentStatus.equals("PAID"))
            throw ApiException.conflict("NOT_PAID", "An online order can only be processed after the payment is confirmed.");
        orderService.changeStatus(o, t, note == null || note.isBlank() ? "Updated by staff" : note);
        if (t == OrderStatus.DELIVERED && o.paymentMethod.equals("COD")) {
            o.paymentStatus = "PAID";
            orders.save(o);
        }
        audit.log("ORDER_STATUS", "order", orderNumber, t.name());
    }

    // ---------------------------------------------------------------- customers

    public Map<String, Object> customers(String q, int page, int pageSize) {
        int size = Math.max(1, Math.min(pageSize, 100));
        List<Object> args = new ArrayList<>();
        StringBuilder w = new StringBuilder(" WHERE u.role = 'CUSTOMER'");
        if (q != null && !q.isBlank()) {
            String like = "%" + q.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            w.append(" AND (LOWER(u.email) LIKE ? OR LOWER(u.full_name) LIKE ? OR u.phone LIKE ?)");
            args.add(like); args.add(like); args.add(like);
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM users u" + w, Long.class, args.toArray());
        List<Object> pa = new ArrayList<>(args);
        pa.add(size);
        pa.add(Math.max(0, page) * size);
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT u.id, u.full_name AS fullName, u.email, u.phone, u.email_verified AS emailVerified, u.enabled, u.created_at AS createdAt, "
            + "(SELECT COUNT(*) FROM orders o WHERE o.user_id = u.id AND o.status NOT IN ('PENDING_PAYMENT','PAYMENT_FAILED')) AS orderCount, "
            + "(SELECT COALESCE(SUM(o.total),0) FROM orders o WHERE o.user_id = u.id AND o.status NOT IN ('PENDING_PAYMENT','PAYMENT_FAILED')) AS spend "
            + "FROM users u" + w + " ORDER BY u.id DESC LIMIT ? OFFSET ?", pa.toArray());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("items", rows);
        m.put("total", total == null ? 0 : total);
        m.put("page", Math.max(0, page));
        m.put("pageSize", size);
        return m;
    }

    // ---------------------------------------------------------------- coupons

    public record CouponReq(String code, String type, BigDecimal value, BigDecimal minOrder, BigDecimal maxDiscount, LocalDateTime startsAt,
                            LocalDateTime expiresAt, Integer maxUses, Integer perUserLimit, Boolean active) {}

    public List<Coupon> couponList() { return coupons.findAllByOrderByIdDesc(); }

    @Transactional
    public Coupon saveCoupon(Long id, CouponReq r) {
        Map<String, String> e = new LinkedHashMap<>();
        if (r.code() == null || !r.code().trim().matches("[A-Za-z0-9_-]{3,30}")) e.put("code", "Code must be 3-30 letters, digits, - or _.");
        if (r.type() == null || !(r.type().equals("PERCENT") || r.type().equals("FIXED"))) e.put("type", "Type must be PERCENT or FIXED.");
        if (r.value() == null || r.value().signum() <= 0) e.put("value", "Enter a value above 0.");
        else if ("PERCENT".equals(r.type()) && r.value().compareTo(BigDecimal.valueOf(100)) > 0) e.put("value", "Percent cannot exceed 100.");
        if (r.expiresAt() != null && r.startsAt() != null && !r.expiresAt().isAfter(r.startsAt())) e.put("expiresAt", "Expiry must be after the start.");
        if (!e.isEmpty()) throw new in.trendsfit.web.ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "VALIDATION", "Please correct the highlighted fields.", e);
        Coupon c = id == null ? new Coupon() : coupons.findById(id).orElseThrow(() -> ApiException.notFound("Coupon not found."));
        String code = r.code().trim().toUpperCase();
        coupons.findByCodeIgnoreCase(code).ifPresent(x -> {
            if (id == null || !x.id.equals(id)) throw ApiException.conflict("CODE_EXISTS", "This coupon code already exists.");
        });
        c.code = code;
        c.type = r.type();
        c.value = r.value();
        c.minOrder = r.minOrder() == null ? BigDecimal.ZERO : r.minOrder();
        c.maxDiscount = r.maxDiscount();
        c.startsAt = r.startsAt();
        c.expiresAt = r.expiresAt();
        c.maxUses = r.maxUses();
        c.perUserLimit = r.perUserLimit();
        if (r.active() != null) c.active = r.active();
        c = coupons.save(c);
        audit.log(id == null ? "COUPON_CREATED" : "COUPON_UPDATED", "coupon", c.id, c.code);
        return c;
    }

    @Transactional
    public void deleteCoupon(long id) {
        Coupon c = coupons.findById(id).orElseThrow(() -> ApiException.notFound("Coupon not found."));
        Long used = jdbc.queryForObject("SELECT COUNT(*) FROM coupon_redemptions WHERE coupon_id = ?", Long.class, id);
        if (used != null && used > 0) { c.active = false; coupons.save(c); audit.log("COUPON_DISABLED", "coupon", id, c.code); }
        else { coupons.delete(c); audit.log("COUPON_DELETED", "coupon", id, c.code); }
    }

    // ---------------------------------------------------------------- reviews

    public List<Map<String, Object>> reviews(String status) {
        return jdbc.queryForList("SELECT r.id, r.rating, r.title, r.body, r.status, r.verified_purchase AS verifiedPurchase, r.created_at AS createdAt, "
            + "p.name AS product, u.full_name AS author FROM reviews r JOIN products p ON p.id=r.product_id JOIN users u ON u.id=r.user_id "
            + "WHERE r.status = ? ORDER BY r.id DESC LIMIT 200", status == null || status.isBlank() ? "PENDING" : status);
    }

    @Transactional
    public void moderate(long id, String status) {
        if (status == null || !(status.equals("APPROVED") || status.equals("REJECTED"))) throw ApiException.badRequest("BAD_STATUS", "Status must be APPROVED or REJECTED.");
        Long pid;
        try {
            pid = jdbc.queryForObject("SELECT product_id FROM reviews WHERE id = ?", Long.class, id);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            throw ApiException.notFound("Review not found.");
        }
        jdbc.update("UPDATE reviews SET status = ? WHERE id = ?", status, id);
        account.refreshRating(pid);
        audit.log("REVIEW_" + status, "review", id, null);
    }

    // ---------------------------------------------------------------- staff / roles

    public List<Map<String, Object>> staff() {
        return jdbc.queryForList("SELECT id, full_name AS fullName, email, role, enabled, created_at AS createdAt FROM users WHERE role <> 'CUSTOMER' ORDER BY id");
    }

    public record StaffReq(String fullName, String email, String password, String role) {}

    @Transactional
    public Map<String, Object> createStaff(StaffReq r) {
        Role role = parseStaffRole(r.role());
        if (!Validators.email(r.email())) throw ApiException.badRequest("VALIDATION", "Enter a valid email.");
        if (r.fullName() == null || r.fullName().trim().length() < 2) throw ApiException.badRequest("VALIDATION", "Enter the name.");
        List<String> pw = Validators.passwordProblems(r.password(), r.email());
        if (!pw.isEmpty()) throw ApiException.badRequest("VALIDATION", String.join(" ", pw));
        String em = r.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(em)) throw ApiException.conflict("EMAIL_EXISTS", "This email already has an account.");
        User u = new User();
        u.email = em;
        u.fullName = r.fullName().trim();
        u.passwordHash = encoder.encode(r.password());
        u.role = role.name();
        u.emailVerified = true;
        u = users.save(u);
        audit.log("STAFF_CREATED", "user", u.id, em + " as " + role);
        return Map.of("id", u.id);
    }

    @Transactional
    public void updateStaff(long id, String roleName, Boolean enabled) {
        User u = users.findById(id).orElseThrow(() -> ApiException.notFound("User not found."));
        var me = CurrentUser.require();
        if (u.id == me.id()) throw ApiException.badRequest("SELF_EDIT", "You cannot change your own role or access.");
        if (roleName != null) u.role = parseStaffRole(roleName).name();
        if (enabled != null) u.enabled = enabled;
        users.save(u);
        audit.log("STAFF_UPDATED", "user", id, u.role + (u.enabled ? "" : " (disabled)"));
    }

    private static Role parseStaffRole(String name) {
        try {
            return Role.valueOf(name);
        } catch (RuntimeException e) {
            throw ApiException.badRequest("BAD_ROLE", "Unknown role.");
        }
    }

    public List<Map<String, Object>> newsletter() {
        return jdbc.queryForList("SELECT email, created_at AS createdAt FROM newsletter_subscribers ORDER BY id DESC LIMIT 1000");
    }
}
