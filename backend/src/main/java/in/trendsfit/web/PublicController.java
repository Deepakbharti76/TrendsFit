package in.trendsfit.web;

import in.trendsfit.config.AppConfig;
import in.trendsfit.domain.SizeRecommender;
import in.trendsfit.domain.Validators;
import in.trendsfit.service.CatalogService;
import in.trendsfit.service.OrderService;
import in.trendsfit.service.ShippingService;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class PublicController {
    private final CatalogService catalog;
    private final OrderService orders;
    private final ShippingService shipping;
    private final JdbcTemplate jdbc;
    private final AppConfig cfg;

    public PublicController(CatalogService catalog, OrderService orders, ShippingService shipping, JdbcTemplate jdbc, AppConfig cfg) {
        this.catalog = catalog; this.orders = orders; this.shipping = shipping; this.jdbc = jdbc; this.cfg = cfg;
    }

    @GetMapping("/config")
    public Map<String, Object> config() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("businessName", cfg.businessName);
        m.put("supportEmail", cfg.businessEmail);
        m.put("whatsapp", cfg.businessWhatsapp);
        m.put("codEnabled", cfg.codEnabled);
        m.put("codMaxOrderValue", cfg.codMaxOrderValue);
        m.put("onlinePaymentEnabled", cfg.razorpayConfigured());
        m.put("exchangeWindowDays", cfg.exchangeWindowDays);
        m.put("freeShipping", cfg.shippingFreeForAll);
        return m;
    }

    @GetMapping("/categories")
    public List<Map<String, Object>> categories() { return catalog.categories(); }

    @GetMapping("/home")
    public Map<String, Object> home() {
        Map<String, Object> m = catalog.home();
        try { // banners are managed in the admin panel; an empty list simply hides the banner strip
            m.put("banners", jdbc.queryForList("SELECT id, title, subtitle, image_url AS imageUrl, link_url AS linkUrl FROM banners WHERE active = 1 ORDER BY sort_order, id LIMIT 6"));
        } catch (org.springframework.dao.DataAccessException e) {
            m.put("banners", List.of());
        }
        return m;
    }

    @GetMapping("/products")
    public Map<String, Object> products(@RequestParam(required = false) String q, @RequestParam(required = false) String category,
                                        @RequestParam(required = false) String size, @RequestParam(required = false) String color,
                                        @RequestParam(required = false) BigDecimal minPrice, @RequestParam(required = false) BigDecimal maxPrice,
                                        @RequestParam(required = false) String fit, @RequestParam(required = false) Boolean inStock,
                                        @RequestParam(required = false) Boolean onSale, @RequestParam(required = false) String sort,
                                        @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "12") int pageSize) {
        return catalog.search(new CatalogService.Query(q, category, size, color, minPrice, maxPrice, fit, inStock, onSale, sort, page, pageSize));
    }

    @GetMapping("/products/{slug}")
    public Map<String, Object> product(@PathVariable String slug) { return catalog.detail(slug); }

    @GetMapping("/products/{slug}/related")
    public List<Map<String, Object>> related(@PathVariable String slug) { return catalog.related(slug, 8); }

    @GetMapping("/products/{slug}/reviews")
    public List<Map<String, Object>> reviews(@PathVariable String slug) { return catalog.reviews(slug); }

    public record QuoteReq(List<OrderService.ItemReq> items, String couponCode, String email) {}

    @PostMapping("/cart/quote")
    public Map<String, Object> quote(@RequestBody QuoteReq r) { return orders.quote(r.items(), r.couponCode(), r.email()); }

    @GetMapping("/delivery/check")
    public Map<String, Object> delivery(@RequestParam String pincode) { return shipping.checkPincode(pincode); }

    public record NewsletterReq(String email) {}

    @PostMapping("/newsletter")
    public Map<String, Object> newsletter(@RequestBody NewsletterReq r) {
        if (r == null || !Validators.email(r.email())) throw ApiException.badRequest("VALIDATION", "Enter a valid email address.");
        jdbc.update("INSERT IGNORE INTO newsletter_subscribers (email) VALUES (?)", r.email().trim().toLowerCase());
        return Map.of("message", "Thanks for subscribing!");
    }

    public record SizeReq(String kind, Double waistInches, Double chestInches, Double heightCm, Double weightKg, String fit) {}

    /** Find-My-Size helper. It is an estimate and always says so. */
    @PostMapping("/size-recommendation")
    public Map<String, Object> size(@RequestBody SizeReq r) {
        SizeRecommender.Recommendation rec;
        if ("BOTTOM".equalsIgnoreCase(r.kind())) {
            if (r.waistInches() == null || r.waistInches() < 20 || r.waistInches() > 60) throw ApiException.badRequest("VALIDATION", "Enter your waist in inches (20-60).");
            rec = SizeRecommender.forBottoms(r.waistInches(), r.fit());
        } else {
            double chest;
            if (r.chestInches() != null) chest = r.chestInches();
            else if (r.heightCm() != null && r.weightKg() != null) chest = SizeRecommender.estimateChestFromWeight(r.heightCm(), r.weightKg());
            else throw ApiException.badRequest("VALIDATION", "Enter your chest size, or your height and weight.");
            if (chest < 28 || chest > 60) throw ApiException.badRequest("VALIDATION", "Chest size looks out of range.");
            rec = SizeRecommender.forTops(chest, r.fit());
        }
        return Map.of("size", rec.size(), "disclaimer", rec.disclaimer());
    }
}
