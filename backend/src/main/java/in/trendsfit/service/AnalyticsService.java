package in.trendsfit.service;

import in.trendsfit.config.AppConfig;
import in.trendsfit.domain.RevenueProgress;
import in.trendsfit.web.ApiException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Revenue, target progress and KPI numbers for the admin dashboard and reports. Everything is computed from real rows.
 *
 * Revenue definition: the order total of confirmed orders, i.e. NOT pending-payment, NOT payment-failed and NOT refunded.
 * Each order is counted once (one row per order), so payments, retries and exchanges can never double-count.
 */
@Service
public class AnalyticsService {
    static final String REVENUE_WHERE = "status NOT IN ('PENDING_PAYMENT','PAYMENT_FAILED') AND payment_status <> 'REFUNDED'";
    private final JdbcTemplate jdbc;
    private final AppConfig cfg;
    private final SettingsService settings;
    private final Clock clock;

    public AnalyticsService(JdbcTemplate jdbc, AppConfig cfg, SettingsService settings, Clock clock) {
        this.jdbc = jdbc; this.cfg = cfg; this.settings = settings; this.clock = clock;
    }

    public Map<String, Object> overview(String periodKind, String from, String to, String granularity, BigDecimal customTarget) {
        RevenueProgress.Period p;
        int size;
        try {
            p = RevenueProgress.resolve(periodKind, from, to, LocalDate.now(clock));
            size = RevenueProgress.bucketSize(granularity == null || granularity.isBlank() ? "day" : granularity);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("BAD_PERIOD", e.getMessage());
        }
        RevenueProgress.Period prev = p.previous();
        Daily cur = daily(p);
        Daily old = daily(prev);
        BigDecimal curTotal = cur.total();
        BigDecimal oldTotal = old.total();

        BigDecimal target;
        if ("custom".equals(periodKind)) target = customTarget == null ? BigDecimal.ZERO : customTarget;
        else target = settings.money("revenue.target." + (periodKind == null ? "30d" : periodKind));
        RevenueProgress.Progress prog = RevenueProgress.compute(curTotal, target);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("period", Map.of("kind", periodKind == null ? "30d" : periodKind, "from", p.from().toString(), "to", p.to().toString(), "days", p.days()));
        m.put("previousPeriod", Map.of("from", prev.from().toString(), "to", prev.to().toString()));
        m.put("granularity", size == 1 ? "day" : size == 7 ? "week" : "month");

        Map<String, Object> rev = new LinkedHashMap<>();
        rev.put("current", curTotal);
        rev.put("previous", oldTotal);
        rev.put("changePercent", RevenueProgress.changePercent(curTotal, oldTotal)); // null when there is no previous revenue to compare with
        rev.put("orders", cur.orders());
        rev.put("previousOrders", old.orders());
        rev.put("averageOrderValue", cur.orders() == 0 ? BigDecimal.ZERO : curTotal.divide(BigDecimal.valueOf(cur.orders()), 2, java.math.RoundingMode.HALF_UP));
        m.put("revenue", rev);

        Map<String, Object> t = new LinkedHashMap<>();
        t.put("set", prog.targetSet());
        t.put("target", prog.target());
        t.put("achieved", prog.achieved());
        t.put("remaining", prog.remaining());
        t.put("percent", prog.percent());
        t.put("ringPercent", prog.ringPercent());
        t.put("reached", prog.reached());
        m.put("target", t);

        List<BigDecimal> curBuckets = RevenueProgress.bucket(cur.revenue, size);
        List<BigDecimal> oldBuckets = RevenueProgress.bucket(old.revenue, size);
        List<BigDecimal> curOrders = RevenueProgress.bucket(toBig(cur.orderCounts), size);
        List<Map<String, Object>> series = new ArrayList<>();
        for (int i = 0; i < curBuckets.size(); i++) {
            Map<String, Object> pt = new LinkedHashMap<>();
            pt.put("label", p.from().plusDays((long) i * size).toString());
            pt.put("revenue", curBuckets.get(i));
            pt.put("orders", curOrders.get(i).longValue());
            pt.put("previousRevenue", i < oldBuckets.size() ? oldBuckets.get(i) : BigDecimal.ZERO);
            series.add(pt);
        }
        m.put("series", series);
        m.put("kpis", kpis());
        return m;
    }

    /** Counts that do not depend on the selected period. */
    public Map<String, Object> kpis() {
        LocalDate today = LocalDate.now(clock);
        LocalDateTime start30 = today.minusDays(29).atStartOfDay();
        Map<String, Object> k = new LinkedHashMap<>();
        k.put("revenueToday", sumRevenue(today.atStartOfDay(), today.plusDays(1).atStartOfDay()));
        k.put("revenue30d", sumRevenue(start30, today.plusDays(1).atStartOfDay()));
        k.put("revenue7d", sumRevenue(today.minusDays(6).atStartOfDay(), today.plusDays(1).atStartOfDay()));
        k.put("totalOrders", count("SELECT COUNT(*) FROM orders WHERE " + REVENUE_WHERE));
        k.put("ordersToFulfil", count("SELECT COUNT(*) FROM orders WHERE status IN ('PAID','PROCESSING','PACKED')"));
        k.put("deliveredOrders", count("SELECT COUNT(*) FROM orders WHERE status = 'DELIVERED'"));
        k.put("paymentFailed30d", count("SELECT COUNT(*) FROM orders WHERE status = 'PAYMENT_FAILED' AND created_at >= ?", start30));
        k.put("totalCustomers", count("SELECT COUNT(*) FROM users WHERE role = 'CUSTOMER'"));
        k.put("newCustomers30d", count("SELECT COUNT(*) FROM users WHERE role = 'CUSTOMER' AND created_at >= ?", start30));
        k.put("pendingExchanges", count("SELECT COUNT(*) FROM exchanges WHERE status IN ('PENDING','INFO_REQUESTED')"));
        k.put("pendingReviews", count("SELECT COUNT(*) FROM reviews WHERE status = 'PENDING'"));
        k.put("lowStockVariants", count("SELECT COUNT(*) FROM product_variants v JOIN products p ON p.id=v.product_id WHERE v.active=1 AND p.status='ACTIVE' AND v.stock > 0 AND v.stock <= ?", cfg.lowStockThreshold));
        k.put("outOfStockVariants", count("SELECT COUNT(*) FROM product_variants v JOIN products p ON p.id=v.product_id WHERE v.active=1 AND p.status='ACTIVE' AND v.stock = 0"));
        String cod = "FROM orders WHERE payment_method = 'COD' AND status IN ('PROCESSING','PACKED','SHIPPED','OUT_FOR_DELIVERY')";
        k.put("pendingCodOrders", count("SELECT COUNT(*) " + cod));
        BigDecimal codAmt = jdbc.queryForObject("SELECT COALESCE(SUM(total),0) " + cod, BigDecimal.class);
        k.put("pendingCodAmount", codAmt == null ? BigDecimal.ZERO : codAmt);
        k.put("unreadMessages", count("SELECT COUNT(*) FROM support_messages WHERE sender = 'CUSTOMER' AND read_at IS NULL"));
        return k;
    }

    /** Payment-method split, status split and best sellers for a period (the Reports page). */
    public Map<String, Object> breakdown(String periodKind, String from, String to) {
        RevenueProgress.Period p;
        try {
            p = RevenueProgress.resolve(periodKind, from, to, LocalDate.now(clock));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("BAD_PERIOD", e.getMessage());
        }
        LocalDateTime a = p.from().atStartOfDay();
        LocalDateTime b = p.to().plusDays(1).atStartOfDay();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("period", Map.of("from", p.from().toString(), "to", p.to().toString(), "days", p.days()));
        m.put("byPaymentMethod", jdbc.queryForList("SELECT payment_method AS method, COUNT(*) AS orders, COALESCE(SUM(total),0) AS revenue FROM orders WHERE " + REVENUE_WHERE
            + " AND created_at >= ? AND created_at < ? GROUP BY payment_method ORDER BY revenue DESC", a, b));
        m.put("byStatus", jdbc.queryForList("SELECT status, COUNT(*) AS orders FROM orders WHERE created_at >= ? AND created_at < ? GROUP BY status ORDER BY orders DESC", a, b));
        m.put("topProducts", jdbc.queryForList("SELECT i.name, SUM(i.qty) AS units, SUM(i.line_total) AS revenue FROM order_items i JOIN orders o ON o.id=i.order_id WHERE o.status NOT IN ('PENDING_PAYMENT','PAYMENT_FAILED') AND o.payment_status <> 'REFUNDED'"
            + " AND o.created_at >= ? AND o.created_at < ? GROUP BY i.product_id, i.name ORDER BY units DESC, revenue DESC LIMIT 10", a, b));
        m.put("newCustomers", count("SELECT COUNT(*) FROM users WHERE role = 'CUSTOMER' AND created_at >= ? AND created_at < ?", a, b));
        return m;
    }

    // ------------------------------------------------------------------ helpers

    private record Daily(List<BigDecimal> revenue, List<Long> orderCounts) {
        BigDecimal total() { return revenue.stream().reduce(BigDecimal.ZERO, BigDecimal::add); }
        long orders() { return orderCounts.stream().mapToLong(Long::longValue).sum(); }
    }

    private Daily daily(RevenueProgress.Period p) {
        int days = p.days();
        List<BigDecimal> rev = new ArrayList<>();
        List<Long> cnt = new ArrayList<>();
        for (int i = 0; i < days; i++) { rev.add(BigDecimal.ZERO); cnt.add(0L); }
        for (Map<String, Object> r : jdbc.queryForList("SELECT DATE(created_at) AS d, SUM(total) AS revenue, COUNT(*) AS n FROM orders WHERE " + REVENUE_WHERE
                + " AND created_at >= ? AND created_at < ? GROUP BY DATE(created_at)", p.from().atStartOfDay(), p.to().plusDays(1).atStartOfDay())) {
            Object dv = r.get("d");
            LocalDate d = dv instanceof java.sql.Date sd ? sd.toLocalDate() : LocalDate.parse(String.valueOf(dv).substring(0, 10));
            int idx = (int) java.time.temporal.ChronoUnit.DAYS.between(p.from(), d);
            if (idx >= 0 && idx < days) {
                rev.set(idx, new BigDecimal(String.valueOf(r.get("revenue"))));
                cnt.set(idx, ((Number) r.get("n")).longValue());
            }
        }
        return new Daily(rev, cnt);
    }

    private static List<BigDecimal> toBig(List<Long> l) {
        List<BigDecimal> o = new ArrayList<>();
        for (Long x : l) o.add(BigDecimal.valueOf(x));
        return o;
    }

    private BigDecimal sumRevenue(LocalDateTime from, LocalDateTime toExclusive) {
        BigDecimal v = jdbc.queryForObject("SELECT COALESCE(SUM(total),0) FROM orders WHERE " + REVENUE_WHERE + " AND created_at >= ? AND created_at < ?", BigDecimal.class, from, toExclusive);
        return v == null ? BigDecimal.ZERO : v;
    }

    private long count(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }
}
