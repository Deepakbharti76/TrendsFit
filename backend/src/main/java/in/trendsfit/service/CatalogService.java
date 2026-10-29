package in.trendsfit.service;

import in.trendsfit.domain.PricingCalculator;
import in.trendsfit.model.Product;
import in.trendsfit.model.ProductImage;
import in.trendsfit.model.ProductVariant;
import in.trendsfit.repo.ProductRepository;
import in.trendsfit.web.ApiException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Public catalog: server-side search, filter, sort and pagination (JDBC) plus product detail (JPA). */
@Service
public class CatalogService {
    private final JdbcTemplate jdbc;
    private final ProductRepository products;

    public CatalogService(JdbcTemplate jdbc, ProductRepository products) {
        this.jdbc = jdbc;
        this.products = products;
    }

    public record Query(String q, String category, String size, String color, BigDecimal minPrice, BigDecimal maxPrice,
                        String fit, Boolean inStock, Boolean onSale, String sort, int page, int pageSize) {}

    private static final String EFFECTIVE_PRICE = "COALESCE(p.sale_price, p.price)";

    public Map<String, Object> search(Query q) {
        int pageSize = Math.max(1, Math.min(q.pageSize(), 48));
        int page = Math.max(0, q.page());
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE p.status = 'ACTIVE' AND c.active = 1");
        if (q.q() != null && !q.q().isBlank()) {
            String[] words = q.q().trim().toLowerCase().split("\\s+");
            for (int i = 0; i < Math.min(words.length, 5); i++) {
                String like = "%" + words[i].replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                where.append(" AND (LOWER(p.name) LIKE ? OR LOWER(p.sku) LIKE ? OR LOWER(c.name) LIKE ? OR LOWER(p.description) LIKE ? OR LOWER(p.fit) LIKE ?)");
                for (int k = 0; k < 5; k++) args.add(like);
            }
        }
        if (notBlank(q.category())) { where.append(" AND c.slug = ?"); args.add(q.category()); }
        if (notBlank(q.fit())) { where.append(" AND LOWER(p.fit) = ?"); args.add(q.fit().toLowerCase()); }
        if (q.minPrice() != null) { where.append(" AND " + EFFECTIVE_PRICE + " >= ?"); args.add(q.minPrice()); }
        if (q.maxPrice() != null) { where.append(" AND " + EFFECTIVE_PRICE + " <= ?"); args.add(q.maxPrice()); }
        if (Boolean.TRUE.equals(q.onSale())) where.append(" AND p.sale_price IS NOT NULL AND p.sale_price < p.price");
        StringBuilder vcond = new StringBuilder();
        List<Object> vargs = new ArrayList<>();
        if (notBlank(q.size())) { vcond.append(" AND v.size = ?"); vargs.add(q.size()); }
        if (notBlank(q.color())) { vcond.append(" AND LOWER(v.color) = ?"); vargs.add(q.color().toLowerCase()); }
        if (Boolean.TRUE.equals(q.inStock())) vcond.append(" AND v.stock > 0");
        if (vcond.length() > 0) {
            where.append(" AND EXISTS (SELECT 1 FROM product_variants v WHERE v.product_id = p.id AND v.active = 1").append(vcond).append(")");
            args.addAll(vargs);
        }
        String from = " FROM products p JOIN categories c ON c.id = p.category_id";
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + from + where, Long.class, args.toArray());
        long totalRows = total == null ? 0 : total;

        String order = switch (q.sort() == null ? "" : q.sort()) {
            case "price_asc" -> EFFECTIVE_PRICE + " ASC, p.id DESC";
            case "price_desc" -> EFFECTIVE_PRICE + " DESC, p.id DESC";
            case "popular" -> "p.sold_count DESC, p.id DESC";
            case "rating" -> "p.rating_avg DESC, p.rating_count DESC, p.id DESC";
            case "discount" -> "(CASE WHEN p.sale_price IS NULL THEN 0 ELSE (p.price - p.sale_price) / p.price END) DESC, p.id DESC";
            default -> "p.id DESC";
        };
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(pageSize);
        pageArgs.add(page * pageSize);
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT p.id, p.slug, p.name, p.price, p.sale_price, p.rating_avg, p.rating_count, p.is_new_arrival, p.is_bestseller, "
            + "p.fit, c.slug AS category_slug, c.name AS category_name" + from + where + " ORDER BY " + order + " LIMIT ? OFFSET ?",
            pageArgs.toArray());
        List<Map<String, Object>> items = cards(rows);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", items);
        out.put("page", page);
        out.put("pageSize", pageSize);
        out.put("total", totalRows);
        out.put("totalPages", (int) Math.ceil(totalRows / (double) pageSize));
        out.put("facets", facets());
        return out;
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }

    /** Filter options for the whole active catalog. */
    public Map<String, Object> facets() {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("sizes", jdbc.queryForList("SELECT DISTINCT v.size FROM product_variants v JOIN products p ON p.id=v.product_id WHERE p.status='ACTIVE' AND v.active=1 ORDER BY v.size", String.class));
        f.put("colors", jdbc.queryForList("SELECT DISTINCT v.color FROM product_variants v JOIN products p ON p.id=v.product_id WHERE p.status='ACTIVE' AND v.active=1 ORDER BY v.color", String.class));
        f.put("fits", jdbc.queryForList("SELECT DISTINCT fit FROM products WHERE status='ACTIVE' AND fit IS NOT NULL AND fit <> '' ORDER BY fit", String.class));
        Map<String, Object> pr = jdbc.queryForMap("SELECT MIN(COALESCE(sale_price, price)) AS mn, MAX(COALESCE(sale_price, price)) AS mx FROM products WHERE status='ACTIVE'");
        f.put("priceMin", pr.get("mn"));
        f.put("priceMax", pr.get("mx"));
        return f;
    }

    /** Turns product rows into storefront cards (adds image, colours, stock, discount). */
    List<Map<String, Object>> cards(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) return List.of();
        List<Long> ids = rows.stream().map(r -> ((Number) r.get("id")).longValue()).toList();
        String in = String.join(",", ids.stream().map(i -> "?").toList());
        Map<Long, List<String>> images = new LinkedHashMap<>();
        for (Map<String, Object> r : jdbc.queryForList("SELECT product_id, url FROM product_images WHERE product_id IN (" + in + ") ORDER BY sort_order, id", ids.toArray()))
            images.computeIfAbsent(((Number) r.get("product_id")).longValue(), k -> new ArrayList<>()).add((String) r.get("url"));
        Map<Long, List<Map<String, Object>>> colors = new LinkedHashMap<>();
        Map<Long, Integer> stock = new LinkedHashMap<>();
        for (Map<String, Object> r : jdbc.queryForList("SELECT product_id, color, MAX(color_hex) AS hex, SUM(stock) AS stock FROM product_variants WHERE active=1 AND product_id IN (" + in + ") GROUP BY product_id, color ORDER BY color", ids.toArray())) {
            long pid = ((Number) r.get("product_id")).longValue();
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("name", r.get("color"));
            c.put("hex", r.get("hex"));
            colors.computeIfAbsent(pid, k -> new ArrayList<>()).add(c);
            stock.merge(pid, ((Number) r.get("stock")).intValue(), Integer::sum);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            long id = ((Number) r.get("id")).longValue();
            BigDecimal price = (BigDecimal) r.get("price");
            BigDecimal sale = (BigDecimal) r.get("sale_price");
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("slug", r.get("slug"));
            m.put("name", r.get("name"));
            m.put("category", r.get("category_name"));
            m.put("categorySlug", r.get("category_slug"));
            m.put("price", price);
            m.put("salePrice", sale);
            m.put("finalPrice", PricingCalculator.unitPrice(price, sale, null));
            m.put("discountPercent", PricingCalculator.discountPercent(price, sale));
            m.put("image", images.getOrDefault(id, List.of()).stream().findFirst().orElse(null));
            m.put("image2", images.getOrDefault(id, List.of()).size() > 1 ? images.get(id).get(1) : null);
            m.put("colors", colors.getOrDefault(id, List.of()));
            m.put("inStock", stock.getOrDefault(id, 0) > 0);
            m.put("ratingAvg", r.get("rating_avg"));
            m.put("ratingCount", r.get("rating_count"));
            m.put("isNew", asBool(r.get("is_new_arrival")));
            m.put("isBestseller", asBool(r.get("is_bestseller")));
            out.add(m);
        }
        return out;
    }

    private static boolean asBool(Object o) {
        if (o instanceof Boolean b) return b;
        return o instanceof Number n && n.intValue() != 0;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(String slug) {
        Product p = products.findBySlugWithCategory(slug).filter(Product::isActive)
            .orElseThrow(() -> ApiException.notFound("Product not found."));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.id);
        m.put("slug", p.slug);
        m.put("name", p.name);
        m.put("sku", p.sku);
        m.put("category", p.category.name);
        m.put("categorySlug", p.category.slug);
        m.put("description", p.description);
        m.put("fit", p.fit);
        m.put("material", p.material);
        m.put("care", p.care);
        m.put("sizeChart", p.sizeChart);
        m.put("videoUrl", p.videoUrl);
        m.put("price", p.price);
        m.put("salePrice", p.salePrice);
        m.put("finalPrice", PricingCalculator.unitPrice(p.price, p.salePrice, null));
        m.put("discountPercent", PricingCalculator.discountPercent(p.price, p.salePrice));
        m.put("seoTitle", p.seoTitle);
        m.put("seoDescription", p.seoDescription);
        m.put("ratingAvg", p.ratingAvg);
        m.put("ratingCount", p.ratingCount);
        m.put("demo", p.demo);
        List<Map<String, Object>> imgs = new ArrayList<>();
        for (ProductImage i : p.images) imgs.add(Map.of("url", i.url, "alt", i.alt == null ? p.name : i.alt));
        m.put("images", imgs);
        List<Map<String, Object>> vs = new ArrayList<>();
        for (ProductVariant v : p.variants) {
            if (!v.active) continue;
            Map<String, Object> vm = new LinkedHashMap<>();
            vm.put("id", v.id);
            vm.put("sku", v.sku);
            vm.put("size", v.size);
            vm.put("color", v.color);
            vm.put("colorHex", v.colorHex);
            vm.put("stock", Math.max(0, Math.min(v.stock, 99)));
            vm.put("price", PricingCalculator.unitPrice(p.price, p.salePrice, v.priceOverride));
            vs.add(vm);
        }
        m.put("variants", vs);
        return m;
    }

    public List<Map<String, Object>> related(String slug, int limit) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT p.id, p.slug, p.name, p.price, p.sale_price, p.rating_avg, p.rating_count, p.is_new_arrival, p.is_bestseller, p.fit, "
            + "c.slug AS category_slug, c.name AS category_name FROM products p JOIN categories c ON c.id=p.category_id "
            + "WHERE p.status='ACTIVE' AND p.slug <> ? AND p.category_id = (SELECT category_id FROM products WHERE slug = ?) "
            + "ORDER BY p.sold_count DESC, p.id DESC LIMIT " + Math.max(1, Math.min(limit, 12)), slug, slug);
        return cards(rows);
    }

    public List<Map<String, Object>> reviews(String slug) {
        return jdbc.queryForList(
            "SELECT r.id, r.rating, r.title, r.body, r.verified_purchase, r.created_at, u.full_name FROM reviews r "
            + "JOIN products p ON p.id=r.product_id JOIN users u ON u.id=r.user_id WHERE p.slug=? AND r.status='APPROVED' ORDER BY r.id DESC LIMIT 50", slug)
            .stream().map(r -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", r.get("id"));
                m.put("rating", r.get("rating"));
                m.put("title", r.get("title"));
                m.put("body", r.get("body"));
                m.put("verifiedPurchase", asBool(r.get("verified_purchase")));
                m.put("createdAt", r.get("created_at"));
                String n = String.valueOf(r.get("full_name")).trim();
                m.put("author", n.isEmpty() ? "Customer" : n.split("\\s+")[0] + (n.contains(" ") ? " " + n.substring(n.lastIndexOf(' ') + 1, n.lastIndexOf(' ') + 2) + "." : ""));
                return m;
            }).toList();
    }

    public Map<String, Object> home() {
        String base = "SELECT p.id, p.slug, p.name, p.price, p.sale_price, p.rating_avg, p.rating_count, p.is_new_arrival, p.is_bestseller, p.fit, "
            + "c.slug AS category_slug, c.name AS category_name FROM products p JOIN categories c ON c.id=p.category_id WHERE p.status='ACTIVE' ";
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("featured", cards(jdbc.queryForList(base + "AND p.is_featured=1 ORDER BY p.id DESC LIMIT 8")));
        m.put("newArrivals", cards(jdbc.queryForList(base + "ORDER BY p.is_new_arrival DESC, p.id DESC LIMIT 8")));
        m.put("bestsellers", cards(jdbc.queryForList(base + "ORDER BY p.is_bestseller DESC, p.sold_count DESC, p.id DESC LIMIT 8")));
        m.put("categories", jdbc.queryForList(
            "SELECT c.slug, c.name, c.description, COUNT(p.id) AS productCount FROM categories c LEFT JOIN products p ON p.category_id=c.id AND p.status='ACTIVE' "
            + "WHERE c.active=1 GROUP BY c.id, c.slug, c.name, c.description, c.sort_order ORDER BY c.sort_order"));
        List<Map<String, Object>> rv = new ArrayList<>();
        for (Map<String, Object> r : jdbc.queryForList(
            "SELECT r.rating, r.title, r.body, r.verified_purchase, u.full_name FROM reviews r "
            + "JOIN users u ON u.id=r.user_id WHERE r.status='APPROVED' ORDER BY r.id DESC LIMIT 6")) {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("rating", r.get("rating"));
            x.put("title", r.get("title"));
            x.put("body", r.get("body"));
            x.put("verifiedPurchase", asBool(r.get("verified_purchase")));
            x.put("author", String.valueOf(r.get("full_name")).trim().split("\\s+")[0]);
            rv.add(x);
        }
        m.put("reviews", rv);
        return m;
    }

    public List<Map<String, Object>> categories() {
        return jdbc.queryForList("SELECT slug, name, description FROM categories WHERE active=1 ORDER BY sort_order");
    }
}
