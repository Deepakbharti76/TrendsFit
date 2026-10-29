package in.trendsfit.service;

import in.trendsfit.domain.Money;
import in.trendsfit.domain.Validators;
import in.trendsfit.domain.VariantPricing;
import in.trendsfit.model.Category;
import in.trendsfit.model.Product;
import in.trendsfit.model.ProductImage;
import in.trendsfit.model.ProductVariant;
import in.trendsfit.repo.CategoryRepository;
import in.trendsfit.repo.ProductRepository;
import in.trendsfit.security.CurrentUser;
import in.trendsfit.web.ApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin catalog management: products with variants and images, bulk actions, inventory and stock history. */
@Service
public class AdminProductService {
    private static final Set<String> STATUSES = Set.of("DRAFT", "ACTIVE", "ARCHIVED");

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public AdminProductService(ProductRepository products, CategoryRepository categories, JdbcTemplate jdbc, AuditService audit) {
        this.products = products; this.categories = categories; this.jdbc = jdbc; this.audit = audit;
    }

    public record ImageReq(String url, String alt) {}
    public record VariantReq(Long id, String sku, String size, String color, String colorHex, Integer stock, BigDecimal priceOverride, Boolean active) {}
    public record ProductReq(String name, String slug, String sku, String categorySlug, String description, String fit, String material,
                             String care, String sizeChart, String videoUrl, BigDecimal price, BigDecimal salePrice, String seoTitle,
                             String seoDescription, String status, Boolean featured, Boolean newArrival, Boolean bestseller,
                             List<ImageReq> images, List<VariantReq> variants) {}

    // ---------------------------------------------------------------- list / read

    public Map<String, Object> list(String q, String status, String category, int page, int pageSize) {
        int size = Math.max(1, Math.min(pageSize, 100));
        List<Object> args = new ArrayList<>();
        StringBuilder w = new StringBuilder(" WHERE 1=1");
        if (q != null && !q.isBlank()) {
            w.append(" AND (LOWER(p.name) LIKE ? OR LOWER(p.sku) LIKE ?)");
            String like = "%" + q.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            args.add(like); args.add(like);
        }
        if (status != null && !status.isBlank()) { w.append(" AND p.status = ?"); args.add(status); }
        if (category != null && !category.isBlank()) { w.append(" AND c.slug = ?"); args.add(category); }
        String from = " FROM products p JOIN categories c ON c.id=p.category_id" + w;
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + from, Long.class, args.toArray());
        List<Object> pa = new ArrayList<>(args);
        pa.add(size);
        pa.add(Math.max(0, page) * size);
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT p.id, p.name, p.slug, p.sku, p.status, p.price, p.sale_price AS salePrice, p.is_featured AS featured, p.is_demo AS demo, c.name AS category, "
            + "(SELECT COALESCE(SUM(stock),0) FROM product_variants v WHERE v.product_id=p.id AND v.active=1) AS stock, "
            + "(SELECT url FROM product_images i WHERE i.product_id=p.id ORDER BY i.sort_order, i.id LIMIT 1) AS image"
            + from + " ORDER BY p.id DESC LIMIT ? OFFSET ?", pa.toArray());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("items", rows);
        m.put("total", total == null ? 0 : total);
        m.put("page", Math.max(0, page));
        m.put("pageSize", size);
        m.put("totalPages", (int) Math.ceil((total == null ? 0 : total) / (double) size));
        return m;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(long id) {
        Product p = products.findById(id).orElseThrow(() -> ApiException.notFound("Product not found."));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.id);
        m.put("name", p.name);
        m.put("slug", p.slug);
        m.put("sku", p.sku);
        m.put("categorySlug", ((Category) org.hibernate.Hibernate.unproxy(p.category)).slug); // lazy proxy: public fields are not proxied
        m.put("description", p.description);
        m.put("fit", p.fit);
        m.put("material", p.material);
        m.put("care", p.care);
        m.put("sizeChart", p.sizeChart);
        m.put("videoUrl", p.videoUrl);
        m.put("price", p.price);
        m.put("salePrice", p.salePrice);
        m.put("seoTitle", p.seoTitle);
        m.put("seoDescription", p.seoDescription);
        m.put("status", p.status);
        m.put("featured", p.featured);
        m.put("newArrival", p.newArrival);
        m.put("bestseller", p.bestseller);
        m.put("demo", p.demo);
        List<Map<String, Object>> imgs = new ArrayList<>();
        for (ProductImage i : p.images) imgs.add(Map.of("url", i.url, "alt", i.alt == null ? "" : i.alt));
        m.put("images", imgs);
        List<Map<String, Object>> vs = new ArrayList<>();
        for (ProductVariant v : p.variants) {
            Map<String, Object> vm = new LinkedHashMap<>();
            vm.put("id", v.id);
            vm.put("sku", v.sku);
            vm.put("size", v.size);
            vm.put("color", v.color);
            vm.put("colorHex", v.colorHex);
            vm.put("stock", v.stock);
            vm.put("priceOverride", v.priceOverride);
            vm.put("active", v.active);
            vs.add(vm);
        }
        m.put("variants", vs);
        return m;
    }

    public List<Map<String, Object>> categories() {
        return jdbc.queryForList("SELECT id, slug, name, active FROM categories ORDER BY sort_order");
    }

    // ---------------------------------------------------------------- create / update

    @Transactional
    public Map<String, Object> create(ProductReq r) {
        Product p = new Product();
        apply(p, r, true);
        p = products.saveAndFlush(p);
        applyVariants(p, r.variants());
        products.saveAndFlush(p);
        audit.log("PRODUCT_CREATED", "product", p.id, p.name);
        return Map.of("id", p.id, "slug", p.slug);
    }

    @Transactional
    public Map<String, Object> update(long id, ProductReq r) {
        Product p = products.findById(id).orElseThrow(() -> ApiException.notFound("Product not found."));
        apply(p, r, false);
        applyVariants(p, r.variants());
        products.saveAndFlush(p);
        audit.log("PRODUCT_UPDATED", "product", p.id, p.name);
        return Map.of("id", p.id, "slug", p.slug);
    }

    private void apply(Product p, ProductReq r, boolean creating) {
        Map<String, String> e = new LinkedHashMap<>();
        if (r.name() == null || r.name().trim().length() < 2) e.put("name", "Enter a product name.");
        if (r.sku() == null || r.sku().isBlank()) e.put("sku", "Enter a SKU.");
        if (r.price() == null || r.price().signum() <= 0) e.put("price", "Enter a price above 0.");
        if (r.salePrice() != null && r.price() != null && (r.salePrice().signum() <= 0 || r.salePrice().compareTo(r.price()) >= 0))
            e.put("salePrice", "Sale price must be above 0 and below the regular price.");
        if (r.status() != null && !STATUSES.contains(r.status())) e.put("status", "Status must be DRAFT, ACTIVE or ARCHIVED.");
        Category c = r.categorySlug() == null ? null : categories.findBySlug(r.categorySlug()).orElse(null);
        if (c == null) e.put("categorySlug", "Choose a category.");
        String slug = r.slug() != null && !r.slug().isBlank() ? Validators.slugify(r.slug()) : Validators.slugify(r.name() == null ? "" : r.name());
        if (slug.isEmpty()) e.put("slug", "Could not build a URL slug.");
        if (e.isEmpty()) {
            String sku = r.sku().trim();
            Product bySlug = products.findBySlug(slug).orElse(null);
            if (bySlug != null && (creating || !bySlug.id.equals(p.id))) e.put("slug", "This URL slug is already used.");
            Long skuClash = jdbc.queryForObject("SELECT COUNT(*) FROM products WHERE sku = ? AND id <> ?", Long.class, sku, creating ? -1L : p.id);
            if (skuClash != null && skuClash > 0) e.put("sku", "This SKU is already used.");
        }
        if (!e.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", "Please correct the highlighted fields.", e);
        p.name = r.name().trim();
        p.slug = slug;
        p.sku = r.sku().trim();
        p.category = c;
        p.description = r.description();
        p.fit = blankToNull(r.fit());
        p.material = blankToNull(r.material());
        p.care = blankToNull(r.care());
        p.sizeChart = blankToNull(r.sizeChart());
        p.videoUrl = blankToNull(r.videoUrl());
        p.price = Money.round(r.price());
        p.salePrice = r.salePrice() == null ? null : Money.round(r.salePrice());
        p.seoTitle = blankToNull(r.seoTitle());
        p.seoDescription = blankToNull(r.seoDescription());
        if (r.status() != null) p.status = r.status();
        if (r.featured() != null) p.featured = r.featured();
        if (r.newArrival() != null) p.newArrival = r.newArrival();
        if (r.bestseller() != null) p.bestseller = r.bestseller();
        if (r.images() != null) {
            p.images.clear();
            int order = 0;
            for (ImageReq i : r.images()) {
                if (i.url() == null || i.url().isBlank()) continue;
                ProductImage pi = new ProductImage();
                pi.product = p;
                pi.url = i.url().trim();
                pi.alt = blankToNull(i.alt());
                pi.sortOrder = order++;
                p.images.add(pi);
            }
        }
    }

    private void applyVariants(Product p, List<VariantReq> reqs) {
        if (reqs == null) return;
        Map<Long, ProductVariant> existing = new HashMap<>();
        for (ProductVariant v : p.variants) existing.put(v.id, v);
        List<Object[]> pendingMoves = new ArrayList<>();
        java.util.HashSet<Long> kept = new java.util.HashSet<>();
        java.util.HashSet<String> combos = new java.util.HashSet<>();
        java.util.HashSet<String> usedSkus = new java.util.HashSet<>();
        int row = -1;
        for (VariantReq vr : reqs) {
            row++;
            if (vr.size() == null || vr.size().isBlank() || vr.color() == null || vr.color().isBlank())
                throw ApiException.badRequest("VALIDATION", "Every variant needs a size and a colour.");
            if (vr.stock() != null && vr.stock() < 0) throw ApiException.badRequest("VALIDATION", "Stock cannot be negative.");
            if (!combos.add(vr.size().trim().toUpperCase() + "|" + vr.color().trim().toLowerCase()))
                throw ApiException.badRequest("VALIDATION", "Duplicate size/colour combination: " + vr.size() + " / " + vr.color());
            boolean variantActive = vr.active() == null || vr.active();
            // A blank override is valid (the variant sells at the product's sale price, or regular price). A supplied value
            // must be above 0. See VariantPricing for the rules; the message names the row so it can be found and fixed.
            VariantPricing.Result pr = VariantPricing.check(vr.priceOverride(), variantActive);
            if (pr != VariantPricing.Result.OK) {
                String who = "Variant " + vr.size().trim() + " / " + vr.color().trim() + " (row " + (row + 1) + "): ";
                String problem = pr == VariantPricing.Result.NON_POSITIVE
                    ? who + "price override must be above 0 (received " + vr.priceOverride().stripTrailingZeros().toPlainString() + "). Leave it empty to use the product's sale or regular price."
                    : who + "price override is too large.";
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", problem, Map.of("variant." + row + ".priceOverride", problem));
            }
            ProductVariant v = vr.id() == null ? null : existing.get(vr.id());
            if (vr.id() != null && v == null) throw ApiException.badRequest("VALIDATION", "Variant " + vr.id() + " does not belong to this product.");
            int newStock = vr.stock() == null ? (v == null ? 0 : v.stock) : vr.stock();
            boolean isNew = v == null;
            if (isNew) { v = new ProductVariant(); v.product = p; v.stock = 0; p.variants.add(v); }
            v.size = vr.size().trim();
            v.color = vr.color().trim();
            v.colorHex = blankToNull(vr.colorHex());
            v.priceOverride = VariantPricing.toStore(vr.priceOverride());
            v.active = variantActive;
            boolean autoSku = vr.sku() == null || vr.sku().isBlank();
            String sku = !autoSku ? vr.sku().trim()
                : (p.sku + "-" + v.size + "-" + v.color.replaceAll("[^A-Za-z0-9]", "").toUpperCase());
            // Variant SKUs are unique across the whole shop (uq_var_sku). An automatic SKU that is already taken by another
            // variant (for example one left over from an earlier product with the same SKU) gets a -2, -3... suffix;
            // a SKU typed by the admin is reported instead of failing with a generic database error.
            long selfId = v.id == null ? -1L : v.id;
            String baseSku = sku;
            int suffix = 2;
            while (usedSkus.contains(sku.toLowerCase()) || skuTaken(sku, selfId)) {
                if (!autoSku) throw ApiException.badRequest("VALIDATION", "Variant SKU " + sku + " is already used by another variant.");
                sku = baseSku + "-" + suffix++;
            }
            usedSkus.add(sku.toLowerCase());
            v.sku = sku;
            int delta = newStock - v.stock;
            v.stock = newStock;
            if (!isNew) kept.add(v.id);
            // Movements are written after the flush. They are keyed by size/colour, NOT by the `v` object: saveAndFlush() on a
            // product that already has an id is a JPA merge, which stores a COPY of every new variant and leaves `v` itself
            // without an id (that produced a NULL variant_id and the generic "already exists or is still in use" error).
            pendingMoves.add(new Object[]{comboKey(v.size, v.color), delta, isNew});
        }
        for (ProductVariant v : p.variants) if (v.id != null && !kept.contains(v.id) && existing.containsKey(v.id)) v.active = false;
        Product saved = products.saveAndFlush(p);
        Map<String, ProductVariant> byCombo = new HashMap<>();
        for (ProductVariant sv : saved.variants) byCombo.put(comboKey(sv.size, sv.color), sv);
        for (Object[] mv : pendingMoves) {
            ProductVariant sv = byCombo.get((String) mv[0]);
            if (sv == null || sv.id == null) throw new IllegalStateException("Variant was not saved: " + mv[0]);
            int d = (Integer) mv[1];
            if (d != 0) movement(sv.id, d, (Boolean) mv[2] ? "INITIAL_STOCK" : "ADMIN_EDIT", "PRODUCT", saved.id);
        }
    }

    private static String comboKey(String size, String color) {
        return size.trim().toUpperCase() + "|" + color.trim().toLowerCase();
    }

    private boolean skuTaken(String sku, long selfId) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM product_variants WHERE sku = ? AND id <> ?", Long.class, sku, selfId);
        return n != null && n > 0;
    }

    private void movement(Long variantId, int delta, String reason, String refType, Long refId) {
        jdbc.update("INSERT INTO stock_movements (variant_id, delta, reason, ref_type, ref_id, actor_email) VALUES (?,?,?,?,?,?)",
            variantId, delta, reason, refType, refId, actor());
    }

    private static String actor() {
        var u = CurrentUser.get();
        return u == null ? null : u.email();
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    // ---------------------------------------------------------------- delete / bulk

    /** Products that were ever ordered are archived (history must stay intact); never-ordered ones are removed. */
    @Transactional
    public Map<String, Object> delete(long id) {
        Product p = products.findById(id).orElseThrow(() -> ApiException.notFound("Product not found."));
        Long ordered = jdbc.queryForObject("SELECT COUNT(*) FROM order_items WHERE product_id = ?", Long.class, id);
        if (ordered != null && ordered > 0) {
            p.status = "ARCHIVED";
            products.save(p);
            audit.log("PRODUCT_ARCHIVED", "product", id, p.name);
            return Map.of("result", "ARCHIVED");
        }
        products.delete(p);
        audit.log("PRODUCT_DELETED", "product", id, p.name);
        return Map.of("result", "DELETED");
    }

    public record BulkReq(List<Long> ids, String action, BigDecimal value) {}

    @Transactional
    public Map<String, Object> bulk(BulkReq r) {
        if (r == null || r.ids() == null || r.ids().isEmpty() || r.action() == null) throw ApiException.badRequest("BAD_REQUEST", "Select products and an action.");
        if (r.ids().size() > 500) throw ApiException.badRequest("BAD_REQUEST", "At most 500 products per bulk action.");
        String in = String.join(",", r.ids().stream().map(i -> "?").toList());
        Object[] ids = r.ids().toArray();
        int n;
        switch (r.action()) {
            case "ACTIVATE" -> n = jdbc.update("UPDATE products SET status='ACTIVE' WHERE id IN (" + in + ")", ids);
            case "DRAFT" -> n = jdbc.update("UPDATE products SET status='DRAFT' WHERE id IN (" + in + ")", ids);
            case "ARCHIVE" -> n = jdbc.update("UPDATE products SET status='ARCHIVED' WHERE id IN (" + in + ")", ids);
            case "FEATURE" -> n = jdbc.update("UPDATE products SET is_featured=1 WHERE id IN (" + in + ")", ids);
            case "UNFEATURE" -> n = jdbc.update("UPDATE products SET is_featured=0 WHERE id IN (" + in + ")", ids);
            case "CLEAR_SALE" -> n = jdbc.update("UPDATE products SET sale_price=NULL WHERE id IN (" + in + ")", ids);
            case "DISCOUNT_PERCENT" -> {
                if (r.value() == null || r.value().signum() <= 0 || r.value().compareTo(new BigDecimal("90")) > 0)
                    throw ApiException.badRequest("BAD_VALUE", "Enter a discount between 1 and 90 percent.");
                BigDecimal factor = BigDecimal.valueOf(100).subtract(r.value()).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
                Object[] args = new Object[ids.length + 1];
                args[0] = factor;
                System.arraycopy(ids, 0, args, 1, ids.length);
                n = jdbc.update("UPDATE products SET sale_price = ROUND(price * ?, 2) WHERE id IN (" + in + ")", args);
            }
            default -> throw ApiException.badRequest("BAD_ACTION", "Unknown bulk action.");
        }
        audit.log("PRODUCT_BULK_" + r.action(), "product", r.ids().size() + " items", null);
        return Map.of("updated", n);
    }


    // ---------------------------------------------------------------- CSV import

    /**
     * All-or-nothing CSV import (max 500 rows). Header: name,sku,category,price,sale_price,fit,material,description,status,sizes,colors,stock,image_urls
     * sizes / colors / image_urls are ';' separated; one variant is created for every size x colour with the given stock each.
     */
    @Transactional
    public Map<String, Object> importCsv(String csv) {
        if (csv == null || csv.isBlank()) throw ApiException.badRequest("NO_DATA", "Paste or upload CSV content.");
        List<List<String>> rows = parseCsv(csv);
        if (rows.size() < 2) throw ApiException.badRequest("NO_ROWS", "The CSV needs a header row and at least one product row.");
        if (rows.size() - 1 > 500) throw ApiException.badRequest("TOO_MANY_ROWS", "Import at most 500 products at a time.");
        List<String> head = rows.get(0).stream().map(h -> h.trim().toLowerCase()).toList();
        for (String req : List.of("name", "sku", "category", "price"))
            if (!head.contains(req)) throw ApiException.badRequest("BAD_HEADER", "Missing column: " + req);
        int created = 0;
        for (int i = 1; i < rows.size(); i++) {
            List<String> r = rows.get(i);
            if (r.stream().allMatch(String::isBlank)) continue;
            try {
                java.util.function.Function<String, String> col = n -> {
                    int idx = head.indexOf(n);
                    return idx >= 0 && idx < r.size() ? r.get(idx).trim() : "";
                };
                List<String> sizes = splitList(col.apply("sizes"));
                List<String> colors = splitList(col.apply("colors"));
                if (sizes.isEmpty()) sizes = List.of("M");
                if (colors.isEmpty()) colors = List.of("Default");
                int stock = col.apply("stock").isEmpty() ? 0 : Integer.parseInt(col.apply("stock"));
                List<VariantReq> vs = new ArrayList<>();
                for (String sz : sizes) for (String cl : colors) vs.add(new VariantReq(null, null, sz, cl, null, stock, null, true));
                List<ImageReq> imgs = new ArrayList<>();
                for (String u : splitList(col.apply("image_urls"))) imgs.add(new ImageReq(u, null));
                String sale = col.apply("sale_price");
                String status = col.apply("status").isEmpty() ? "DRAFT" : col.apply("status").toUpperCase();
                create(new ProductReq(col.apply("name"), null, col.apply("sku"), col.apply("category"), col.apply("description"), col.apply("fit"),
                    col.apply("material"), null, null, null, new BigDecimal(col.apply("price")), sale.isEmpty() ? null : new BigDecimal(sale),
                    null, null, status, false, false, false, imgs, vs));
                created++;
            } catch (ApiException e) {
                throw ApiException.badRequest("ROW_" + (i + 1), "Row " + (i + 1) + ": " + e.getMessage() + (e.fields().isEmpty() ? "" : " " + e.fields()));
            } catch (NumberFormatException e) {
                throw ApiException.badRequest("ROW_" + (i + 1), "Row " + (i + 1) + ": price, sale_price and stock must be numbers.");
            }
        }
        audit.log("PRODUCT_IMPORT", "product", created + " items", null);
        return Map.of("created", created);
    }

    private static List<String> splitList(String s) {
        List<String> out = new ArrayList<>();
        if (s == null) return out;
        for (String p : s.split(";")) if (!p.isBlank()) out.add(p.trim());
        return out;
    }

    /** Minimal RFC-4180 style parser: quoted fields, doubled quotes, commas and newlines inside quotes. */
    static List<List<String>> parseCsv(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder f = new StringBuilder();
        boolean q = false;
        String t = text.startsWith("\uFEFF") ? text.substring(1) : text;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (q) {
                if (c == '"') {
                    if (i + 1 < t.length() && t.charAt(i + 1) == '"') { f.append('"'); i++; } else q = false;
                } else f.append(c);
            } else if (c == '"') q = true;
            else if (c == ',') { row.add(f.toString()); f.setLength(0); }
            else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < t.length() && t.charAt(i + 1) == '\n') i++;
                row.add(f.toString()); f.setLength(0);
                rows.add(row); row = new ArrayList<>();
            } else f.append(c);
        }
        if (f.length() > 0 || !row.isEmpty()) { row.add(f.toString()); rows.add(row); }
        return rows;
    }

    // ---------------------------------------------------------------- inventory

    public Map<String, Object> inventory(String q, boolean lowOnly, int threshold, int page, int pageSize) {
        int size = Math.max(1, Math.min(pageSize, 100));
        List<Object> args = new ArrayList<>();
        StringBuilder w = new StringBuilder(" WHERE v.active = 1");
        if (q != null && !q.isBlank()) {
            w.append(" AND (LOWER(p.name) LIKE ? OR LOWER(v.sku) LIKE ?)");
            String like = "%" + q.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            args.add(like); args.add(like);
        }
        if (lowOnly) { w.append(" AND v.stock <= ?"); args.add(threshold); }
        String from = " FROM product_variants v JOIN products p ON p.id=v.product_id" + w;
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + from, Long.class, args.toArray());
        List<Object> pa = new ArrayList<>(args);
        pa.add(size);
        pa.add(Math.max(0, page) * size);
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT v.id, v.sku, v.size, v.color, v.stock, p.name AS product, p.id AS productId" + from + " ORDER BY v.stock ASC, v.id LIMIT ? OFFSET ?", pa.toArray());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("items", rows);
        m.put("total", total == null ? 0 : total);
        m.put("page", Math.max(0, page));
        m.put("pageSize", size);
        return m;
    }

    public record AdjustReq(Integer delta, Integer setTo, String reason) {}

    /** Manual stock change with a mandatory reason; stock never goes below zero. */
    @Transactional
    public Map<String, Object> adjust(long variantId, AdjustReq r) {
        if (r == null || (r.delta() == null) == (r.setTo() == null)) throw ApiException.badRequest("BAD_REQUEST", "Provide either delta or setTo.");
        if (r.reason() == null || r.reason().isBlank()) throw ApiException.badRequest("REASON_REQUIRED", "Enter a reason for the adjustment.");
        Integer cur;
        try {
            cur = jdbc.queryForObject("SELECT stock FROM product_variants WHERE id = ? FOR UPDATE", Integer.class, variantId);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            throw ApiException.notFound("Variant not found.");
        }
        int target = r.setTo() != null ? r.setTo() : cur + r.delta();
        if (target < 0) throw ApiException.badRequest("NEGATIVE_STOCK", "Stock cannot go below zero.");
        jdbc.update("UPDATE product_variants SET stock = ? WHERE id = ?", target, variantId);
        movement(variantId, target - cur, "ADMIN_ADJUST", "VARIANT", variantId);
        audit.log("STOCK_ADJUST", "variant", variantId, cur + " -> " + target + " (" + r.reason() + ")");
        return Map.of("stock", target);
    }

    public List<Map<String, Object>> stockHistory(long variantId) {
        return jdbc.queryForList("SELECT id, delta, reason, ref_type AS refType, ref_id AS refId, actor_email AS actor, created_at AS at "
            + "FROM stock_movements WHERE variant_id = ? ORDER BY id DESC LIMIT 200", variantId);
    }
}
