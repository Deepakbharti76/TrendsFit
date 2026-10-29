package in.trendsfit.web;

import in.trendsfit.config.AppConfig;
import in.trendsfit.domain.Html;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * sitemap.xml and robots.txt built from real catalog rows, plus a crawler-friendly product page (Open Graph + JSON-LD) at
 * /seo/product/{slug}. The React app is a single-page app, so social-media crawlers that do not run JavaScript should be routed
 * to /seo/product/{slug} by the reverse proxy (see docs/DEPLOYMENT.md).
 */
@RestController
public class SeoController {
    private final JdbcTemplate jdbc;
    private final AppConfig cfg;

    public SeoController(JdbcTemplate jdbc, AppConfig cfg) { this.jdbc = jdbc; this.cfg = cfg; }

    private String base() { return cfg.frontendUrl.endsWith("/") ? cfg.frontendUrl.substring(0, cfg.frontendUrl.length() - 1) : cfg.frontendUrl; }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public String sitemap() {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        for (String p : List.of("/", "/shop", "/faq", "/shipping-policy", "/exchange-policy", "/privacy-policy", "/terms", "/contact"))
            sb.append("<url><loc>").append(Html.esc(base() + p)).append("</loc></url>\n");
        for (Map<String, Object> c : jdbc.queryForList("SELECT slug FROM categories WHERE active = 1"))
            sb.append("<url><loc>").append(Html.esc(base() + "/shop?category=" + c.get("slug"))).append("</loc></url>\n");
        for (Map<String, Object> p : jdbc.queryForList("SELECT slug, updated_at FROM products WHERE status = 'ACTIVE' ORDER BY id"))
            sb.append("<url><loc>").append(Html.esc(base() + "/product/" + p.get("slug"))).append("</loc>")
              .append(p.get("updated_at") == null ? "" : "<lastmod>" + String.valueOf(p.get("updated_at")).substring(0, 10) + "</lastmod>").append("</url>\n");
        return sb.append("</urlset>\n").toString();
    }

    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public String robots() {
        return "User-agent: *\nAllow: /\nDisallow: /admin\nDisallow: /cart\nDisallow: /checkout\nDisallow: /account\nDisallow: /order/\nDisallow: /api/\n\n"
            + "Sitemap: " + base() + "/sitemap.xml\n";
    }

    @GetMapping(value = "/seo/product/{slug}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> productPage(@PathVariable String slug) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT p.name, p.slug, p.description, p.seo_title, p.seo_description, p.price, p.sale_price, p.rating_avg, p.rating_count, "
            + "(SELECT url FROM product_images i WHERE i.product_id = p.id ORDER BY i.sort_order, i.id LIMIT 1) AS image, "
            + "(SELECT COALESCE(SUM(stock),0) FROM product_variants v WHERE v.product_id = p.id AND v.active = 1) AS stock "
            + "FROM products p WHERE p.slug = ? AND p.status = 'ACTIVE'", slug);
        if (rows.isEmpty()) return ResponseEntity.notFound().build();
        Map<String, Object> p = rows.get(0);
        String name = String.valueOf(p.get("name"));
        String title = p.get("seo_title") != null ? String.valueOf(p.get("seo_title")) : name + " | " + cfg.businessName;
        String desc = p.get("seo_description") != null ? String.valueOf(p.get("seo_description"))
            : (p.get("description") == null ? name : String.valueOf(p.get("description")));
        if (desc.length() > 160) desc = desc.substring(0, 157) + "...";
        String url = base() + "/product/" + slug;
        String img = p.get("image") == null ? "" : (String.valueOf(p.get("image")).startsWith("http") ? String.valueOf(p.get("image")) : base() + p.get("image"));
        Object price = p.get("sale_price") != null ? p.get("sale_price") : p.get("price");
        boolean inStock = ((Number) p.get("stock")).intValue() > 0;
        String ld = "{\"@context\":\"https://schema.org\",\"@type\":\"Product\",\"name\":" + js(name) + ",\"image\":" + js(img) + ",\"description\":" + js(desc)
            + ",\"sku\":" + js(slug) + ",\"offers\":{\"@type\":\"Offer\",\"priceCurrency\":\"INR\",\"price\":" + js(String.valueOf(price)) + ",\"url\":" + js(url)
            + ",\"availability\":\"https://schema.org/" + (inStock ? "InStock" : "OutOfStock") + "\"}"
            + (((Number) p.get("rating_count")).intValue() > 0 ? ",\"aggregateRating\":{\"@type\":\"AggregateRating\",\"ratingValue\":" + js(String.valueOf(p.get("rating_avg")))
            + ",\"reviewCount\":" + p.get("rating_count") + "}" : "") + "}";
        String html = "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><title>" + Html.esc(title) + "</title>"
            + "<meta name=\"description\" content=\"" + Html.esc(desc) + "\"><link rel=\"canonical\" href=\"" + Html.esc(url) + "\">"
            + "<meta property=\"og:type\" content=\"product\"><meta property=\"og:title\" content=\"" + Html.esc(title) + "\">"
            + "<meta property=\"og:description\" content=\"" + Html.esc(desc) + "\"><meta property=\"og:url\" content=\"" + Html.esc(url) + "\">"
            + (img.isEmpty() ? "" : "<meta property=\"og:image\" content=\"" + Html.esc(img) + "\">")
            + "<meta name=\"twitter:card\" content=\"summary_large_image\">"
            + "<script type=\"application/ld+json\">" + ld.replace("</", "<\\/") + "</script>"
            + "<meta http-equiv=\"refresh\" content=\"0; url=" + Html.esc(url) + "\"></head><body><h1>" + Html.esc(name) + "</h1>"
            + "<p>" + Html.esc(desc) + "</p><p><a href=\"" + Html.esc(url) + "\">View product</a></p></body></html>";
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
    }

    private static String js(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : (s == null ? "" : s).toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> { if (c < 0x20) sb.append(String.format("\\u%04x", (int) c)); else sb.append(c); }
            }
        }
        return sb.append('"').toString();
    }
}
