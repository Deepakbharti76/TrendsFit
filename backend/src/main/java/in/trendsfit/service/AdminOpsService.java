package in.trendsfit.service;

import in.trendsfit.config.AppConfig;
import in.trendsfit.domain.Role;
import in.trendsfit.domain.Validators;
import in.trendsfit.web.ApiException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin panel extras: notifications, payment and shipment lists, COD overview, categories, banners and integration status. */
@Service
public class AdminOpsService {
    private final JdbcTemplate jdbc;
    private final AppConfig cfg;
    private final SettingsService settings;
    private final AuditService audit;
    private final Clock clock;

    public AdminOpsService(JdbcTemplate jdbc, AppConfig cfg, SettingsService settings, AuditService audit, Clock clock) {
        this.jdbc = jdbc; this.cfg = cfg; this.settings = settings; this.audit = audit; this.clock = clock;
    }

    // ------------------------------------------------------------------ notifications (computed from real rows; the browser polls)

    public Map<String, Object> notifications(Role role) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime d1 = now.minusDays(1);
        LocalDateTime d7 = now.minusDays(7);
        List<Map<String, Object>> items = new ArrayList<>();
        boolean orders = role.can(Role.Permission.ORDER_READ);
        if (orders && (settings.flag("notify.newOrder") || settings.flag("notify.cod"))) {
            for (Map<String, Object> r : jdbc.queryForList("SELECT order_number, total, payment_method, created_at FROM orders WHERE created_at >= ? "
                    + "AND status NOT IN ('PENDING_PAYMENT','PAYMENT_FAILED') ORDER BY id DESC LIMIT 10", d1)) {
                boolean cod = "COD".equals(r.get("payment_method"));
                if (cod ? !settings.flag("notify.cod") : !settings.flag("notify.newOrder")) continue;
                items.add(item(cod ? "COD_ORDER" : "NEW_ORDER", (cod ? "New COD order " : "New order ") + r.get("order_number"), "₹" + r.get("total"),
                    "/admin/orders/" + r.get("order_number"), r.get("created_at")));
            }
        }
        if (orders && settings.flag("notify.paymentFailed")) {
            for (Map<String, Object> r : jdbc.queryForList("SELECT order_number, total, created_at FROM orders WHERE status = 'PAYMENT_FAILED' AND created_at >= ? ORDER BY id DESC LIMIT 10", d7))
                items.add(item("PAYMENT_FAILED", "Payment failed for " + r.get("order_number"), "₹" + r.get("total"), "/admin/orders/" + r.get("order_number"), r.get("created_at")));
        }
        if (role.can(Role.Permission.PRODUCT_READ) && settings.flag("notify.stock")) {
            Long low = jdbc.queryForObject("SELECT COUNT(*) FROM product_variants v JOIN products p ON p.id=v.product_id WHERE v.active=1 AND p.status='ACTIVE' AND v.stock > 0 AND v.stock <= ?", Long.class, cfg.lowStockThreshold);
            Long out = jdbc.queryForObject("SELECT COUNT(*) FROM product_variants v JOIN products p ON p.id=v.product_id WHERE v.active=1 AND p.status='ACTIVE' AND v.stock = 0", Long.class);
            if (out != null && out > 0) items.add(item("OUT_OF_STOCK", out + " variant(s) out of stock", "Restock to keep selling", "/admin/inventory?low=1", now));
            if (low != null && low > 0) items.add(item("LOW_STOCK", low + " variant(s) low on stock", "At or below " + cfg.lowStockThreshold, "/admin/inventory?low=1", now));
        }
        if (orders && settings.flag("notify.exchange")) {
            for (Map<String, Object> r : jdbc.queryForList("SELECT e.id, o.order_number, e.created_at FROM exchanges e JOIN orders o ON o.id=e.order_id WHERE e.status IN ('PENDING','INFO_REQUESTED') ORDER BY e.id DESC LIMIT 5"))
                items.add(item("EXCHANGE_REQUEST", "Exchange request for " + r.get("order_number"), "Waiting for a decision", "/admin/exchanges", r.get("created_at")));
        }
        if (role.can(Role.Permission.PAYMENT_READ) && settings.flag("notify.refund")) {
            for (Map<String, Object> r : jdbc.queryForList("SELECT o.order_number, p.status, p.amount, p.updated_at FROM payments p JOIN orders o ON o.id=p.order_id "
                    + "WHERE p.status IN ('REFUND_SENT','REFUNDED') AND p.updated_at >= ? ORDER BY p.id DESC LIMIT 5", d7))
                items.add(item("REFUND", "Refund " + ("REFUNDED".equals(r.get("status")) ? "completed" : "requested") + " for " + r.get("order_number"), "₹" + r.get("amount"), "/admin/payments", r.get("updated_at")));
        }
        if (role.can(Role.Permission.SUPPORT_CHAT) && settings.flag("notify.message")) {
            for (Map<String, Object> r : jdbc.queryForList("SELECT c.id, c.customer_name, MAX(m.created_at) AS at, COUNT(*) AS n FROM support_conversations c JOIN support_messages m ON m.conversation_id=c.id "
                    + "WHERE m.sender='CUSTOMER' AND m.read_at IS NULL GROUP BY c.id, c.customer_name ORDER BY at DESC LIMIT 10"))
                items.add(item("MESSAGE", "New message from " + r.get("customer_name"), r.get("n") + " unread", "/admin/support?c=" + r.get("id"), r.get("at")));
        }
        items.sort(Comparator.comparing((Map<String, Object> m) -> (LocalDateTime) m.get("at")).reversed());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("count", items.size());
        m.put("items", items.size() > 30 ? items.subList(0, 30) : items);
        return m;
    }

    private static Map<String, Object> item(String kind, String title, String detail, String link, Object at) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", kind); m.put("title", title); m.put("detail", detail); m.put("link", link);
        m.put("at", at instanceof java.sql.Timestamp ts ? ts.toLocalDateTime() : at instanceof LocalDateTime ldt ? ldt : LocalDateTime.now());
        return m;
    }

    // ------------------------------------------------------------------ payments / COD / shipments

    public Map<String, Object> payments(String q, String status, String from, String to, int page, int pageSize) {
        int size = Math.max(1, Math.min(pageSize, 100));
        List<Object> args = new ArrayList<>();
        StringBuilder w = new StringBuilder(" WHERE 1=1");
        if (status != null && !status.isBlank()) { w.append(" AND p.status = ?"); args.add(status); }
        if (q != null && !q.isBlank()) {
            String like = "%" + q.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            w.append(" AND (LOWER(o.order_number) LIKE ? OR LOWER(o.customer_name) LIKE ? OR LOWER(p.provider_payment_id) LIKE ? OR LOWER(p.provider_order_id) LIKE ?)");
            args.add(like); args.add(like); args.add(like); args.add(like);
        }
        addDates(w, args, "p.created_at", from, to);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM payments p JOIN orders o ON o.id=p.order_id" + w, Long.class, args.toArray());
        List<Object> pa = new ArrayList<>(args);
        pa.add(size);
        pa.add(Math.max(0, page) * size);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("items", jdbc.queryForList("SELECT p.id, o.order_number AS orderNumber, o.customer_name AS customerName, p.provider, p.provider_order_id AS providerOrderId, "
            + "p.provider_payment_id AS providerPaymentId, p.amount, p.status, p.failure_reason AS failureReason, p.created_at AS createdAt "
            + "FROM payments p JOIN orders o ON o.id=p.order_id" + w + " ORDER BY p.id DESC LIMIT ? OFFSET ?", pa.toArray()));
        m.put("total", total == null ? 0 : total);
        m.put("page", Math.max(0, page));
        m.put("pageSize", size);
        m.put("totalPages", (int) Math.ceil((total == null ? 0 : total) / (double) size));
        m.put("summary", jdbc.queryForList("SELECT status, COUNT(*) AS count, COALESCE(SUM(amount),0) AS amount FROM payments GROUP BY status ORDER BY status"));
        return m;
    }

    /** Cash on Delivery overview: money still to be collected by the courier versus already collected (delivered). */
    public Map<String, Object> cod() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", cfg.codEnabled);
        m.put("maxOrderValue", cfg.codMaxOrderValue);
        m.put("pending", jdbc.queryForMap("SELECT COUNT(*) AS orders, COALESCE(SUM(total),0) AS amount FROM orders WHERE payment_method = 'COD' AND status IN ('PROCESSING','PACKED','SHIPPED','OUT_FOR_DELIVERY')"));
        m.put("collected", jdbc.queryForMap("SELECT COUNT(*) AS orders, COALESCE(SUM(total),0) AS amount FROM orders WHERE payment_method = 'COD' AND status = 'DELIVERED'"));
        m.put("recent", jdbc.queryForList("SELECT order_number AS orderNumber, customer_name AS customerName, total, status, created_at AS createdAt FROM orders WHERE payment_method = 'COD' "
            + "AND status NOT IN ('PENDING_PAYMENT','PAYMENT_FAILED') ORDER BY id DESC LIMIT 25"));
        return m;
    }

    public Map<String, Object> shipments(String q, String status, int page, int pageSize) {
        int size = Math.max(1, Math.min(pageSize, 100));
        List<Object> args = new ArrayList<>();
        StringBuilder w = new StringBuilder(" WHERE 1=1");
        if (status != null && !status.isBlank()) { w.append(" AND s.status = ?"); args.add(status); }
        if (q != null && !q.isBlank()) {
            String like = "%" + q.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            w.append(" AND (LOWER(o.order_number) LIKE ? OR LOWER(o.customer_name) LIKE ? OR LOWER(s.awb) LIKE ?)");
            args.add(like); args.add(like); args.add(like);
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM shipments s JOIN orders o ON o.id=s.order_id" + w, Long.class, args.toArray());
        List<Object> pa = new ArrayList<>(args);
        pa.add(size);
        pa.add(Math.max(0, page) * size);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("items", jdbc.queryForList("SELECT s.id, o.order_number AS orderNumber, o.customer_name AS customerName, o.ship_city AS city, s.kind, s.awb, s.courier, s.status, "
            + "s.status_text AS statusText, s.tracking_url AS trackingUrl, s.estimated_delivery AS estimatedDelivery, s.last_event_at AS lastEventAt, s.created_at AS createdAt, o.status AS orderStatus "
            + "FROM shipments s JOIN orders o ON o.id=s.order_id" + w + " ORDER BY s.id DESC LIMIT ? OFFSET ?", pa.toArray()));
        m.put("total", total == null ? 0 : total);
        m.put("page", Math.max(0, page));
        m.put("pageSize", size);
        m.put("totalPages", (int) Math.ceil((total == null ? 0 : total) / (double) size));
        m.put("summary", jdbc.queryForList("SELECT status, COUNT(*) AS count FROM shipments GROUP BY status ORDER BY count DESC"));
        m.put("readyToShip", jdbc.queryForList("SELECT o.order_number AS orderNumber, o.customer_name AS customerName, o.ship_city AS city FROM orders o WHERE o.status = 'PROCESSING' "
            + "AND NOT EXISTS (SELECT 1 FROM shipments s WHERE s.order_id = o.id AND s.kind = 'FORWARD') ORDER BY o.id LIMIT 20"));
        m.put("shiprocketConfigured", cfg.shiprocketConfigured());
        return m;
    }

    private static void addDates(StringBuilder w, List<Object> args, String col, String from, String to) {
        try {
            if (from != null && !from.isBlank()) { w.append(" AND ").append(col).append(" >= ?"); args.add(LocalDate.parse(from).atStartOfDay()); }
            if (to != null && !to.isBlank()) { w.append(" AND ").append(col).append(" < ?"); args.add(LocalDate.parse(to).plusDays(1).atStartOfDay()); }
        } catch (java.time.format.DateTimeParseException e) {
            throw ApiException.badRequest("BAD_DATE", "Dates must look like 2026-01-31.");
        }
    }

    // ------------------------------------------------------------------ categories

    public List<Map<String, Object>> categoryList() {
        return jdbc.queryForList("SELECT c.id, c.slug, c.name, c.description, c.sort_order AS sortOrder, c.active, "
            + "(SELECT COUNT(*) FROM products p WHERE p.category_id = c.id) AS productCount FROM categories c ORDER BY c.sort_order, c.id");
    }

    public record CategoryReq(String name, String slug, String description, Integer sortOrder, Boolean active) {}

    @Transactional
    public Map<String, Object> saveCategory(Long id, CategoryReq r) {
        String name = r.name() == null ? "" : r.name().trim();
        if (name.isEmpty() || name.length() > 80) throw ApiException.badRequest("BAD_NAME", "Category name is required (up to 80 characters).");
        String slug = Validators.slugify(r.slug() != null && !r.slug().isBlank() ? r.slug() : name);
        if (slug.length() > 80) slug = slug.substring(0, 80);
        String desc = r.description() == null ? null : r.description().trim();
        if (desc != null && desc.length() > 300) throw ApiException.badRequest("BAD_DESCRIPTION", "Description can be up to 300 characters.");
        int sort = r.sortOrder() == null ? 0 : r.sortOrder();
        if (sort < 0 || sort > 10000) throw ApiException.badRequest("BAD_SORT", "Sort order must be between 0 and 10000.");
        boolean active = r.active() == null || r.active();
        try {
            if (id == null) {
                jdbc.update("INSERT INTO categories (slug, name, description, sort_order, active) VALUES (?,?,?,?,?)", slug, name, desc, sort, active ? 1 : 0);
                Long nid = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
                audit.log("CATEGORY_CREATED", "category", nid, name);
                return Map.of("id", nid, "slug", slug);
            }
            int n = jdbc.update("UPDATE categories SET slug = ?, name = ?, description = ?, sort_order = ?, active = ? WHERE id = ?", slug, name, desc, sort, active ? 1 : 0, id);
            if (n == 0) throw ApiException.notFound("Category not found.");
            audit.log("CATEGORY_UPDATED", "category", id, name);
            return Map.of("id", id, "slug", slug);
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("DUPLICATE_SLUG", "Another category already uses this URL name.");
        }
    }

    @Transactional
    public void deleteCategory(long id) {
        Long used = jdbc.queryForObject("SELECT COUNT(*) FROM products WHERE category_id = ?", Long.class, id);
        if (used != null && used > 0) throw ApiException.conflict("CATEGORY_IN_USE", "This category still has products. Move or archive them first, or just hide the category.");
        int n = jdbc.update("DELETE FROM categories WHERE id = ?", id);
        if (n == 0) throw ApiException.notFound("Category not found.");
        audit.log("CATEGORY_DELETED", "category", id, null);
    }

    // ------------------------------------------------------------------ banners

    public List<Map<String, Object>> bannerList() {
        return jdbc.queryForList("SELECT id, title, subtitle, image_url AS imageUrl, link_url AS linkUrl, active, sort_order AS sortOrder FROM banners ORDER BY sort_order, id");
    }

    /** Active banners for the public home page. */
    public List<Map<String, Object>> activeBanners() {
        return jdbc.queryForList("SELECT id, title, subtitle, image_url AS imageUrl, link_url AS linkUrl FROM banners WHERE active = 1 ORDER BY sort_order, id LIMIT 6");
    }

    public record BannerReq(String title, String subtitle, String imageUrl, String linkUrl, Boolean active, Integer sortOrder) {}

    @Transactional
    public Map<String, Object> saveBanner(Long id, BannerReq r) {
        String title = r.title() == null ? "" : r.title().trim();
        if (title.isEmpty() || title.length() > 120) throw ApiException.badRequest("BAD_TITLE", "Banner title is required (up to 120 characters).");
        String sub = blankToNull(r.subtitle(), 200, "Subtitle");
        String img = blankToNull(r.imageUrl(), 300, "Image");
        String link = blankToNull(r.linkUrl(), 300, "Link");
        if (img != null && !(img.startsWith("/uploads/") || img.startsWith("https://"))) throw ApiException.badRequest("BAD_IMAGE", "Image must be an uploaded image or an https:// address.");
        if (link != null && !((link.startsWith("/") && !link.startsWith("//")) || link.startsWith("https://"))) throw ApiException.badRequest("BAD_LINK", "Link must start with / (a page on your store) or https://.");
        int sort = r.sortOrder() == null ? 0 : r.sortOrder();
        if (sort < 0 || sort > 10000) throw ApiException.badRequest("BAD_SORT", "Sort order must be between 0 and 10000.");
        boolean active = r.active() == null || r.active();
        if (id == null) {
            jdbc.update("INSERT INTO banners (title, subtitle, image_url, link_url, active, sort_order) VALUES (?,?,?,?,?,?)", title, sub, img, link, active ? 1 : 0, sort);
            Long nid = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            audit.log("BANNER_CREATED", "banner", nid, title);
            return Map.of("id", nid);
        }
        int n = jdbc.update("UPDATE banners SET title = ?, subtitle = ?, image_url = ?, link_url = ?, active = ?, sort_order = ? WHERE id = ?", title, sub, img, link, active ? 1 : 0, sort, id);
        if (n == 0) throw ApiException.notFound("Banner not found.");
        audit.log("BANNER_UPDATED", "banner", id, title);
        return Map.of("id", id);
    }

    @Transactional
    public void deleteBanner(long id) {
        if (jdbc.update("DELETE FROM banners WHERE id = ?", id) == 0) throw ApiException.notFound("Banner not found.");
        audit.log("BANNER_DELETED", "banner", id, null);
    }

    private static String blankToNull(String s, int max, String label) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim();
        if (t.length() > max) throw ApiException.badRequest("TOO_LONG", label + " is too long (max " + max + ").");
        return t;
    }

    // ------------------------------------------------------------------ integration status (never returns a secret)

    public Map<String, Object> integrations() {
        Map<String, Object> rz = new LinkedHashMap<>();
        rz.put("keysConfigured", cfg.razorpayConfigured());
        rz.put("webhookSecretConfigured", !cfg.razorpayWebhookSecret.isBlank());
        rz.put("webhookUrl", cfg.publicUrl + "/api/payments/webhook");
        Map<String, Object> sr = new LinkedHashMap<>();
        sr.put("configured", cfg.shiprocketConfigured());
        sr.put("webhookTokenConfigured", !cfg.shiprocketWebhookToken.isBlank());
        sr.put("pickupPincodeSet", !cfg.pickupPostcode.isBlank());
        sr.put("webhookUrl", cfg.publicUrl + "/api/shipping/webhook");
        Map<String, Object> em = new LinkedHashMap<>();
        em.put("enabled", cfg.mailEnabled);
        em.put("fromConfigured", !cfg.mailFrom.isBlank());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("razorpay", rz); m.put("shiprocket", sr); m.put("email", em);
        return m;
    }
}
