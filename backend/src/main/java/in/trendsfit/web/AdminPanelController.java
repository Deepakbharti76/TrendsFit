package in.trendsfit.web;

import in.trendsfit.security.CurrentUser;
import in.trendsfit.service.AdminOpsService;
import in.trendsfit.service.AnalyticsService;
import in.trendsfit.service.SupportService;
import in.trendsfit.service.UploadService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Admin panel upgrade endpoints: analytics, support chat, payments, shipments, categories, banners, notifications.
 * Every method is guarded by a permission on the server.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminPanelController {
    private final AnalyticsService analytics;
    private final SupportService support;
    private final AdminOpsService ops;
    private final UploadService uploads;

    public AdminPanelController(AnalyticsService analytics, SupportService support, AdminOpsService ops, UploadService uploads) {
        this.analytics = analytics; this.support = support; this.ops = ops; this.uploads = uploads;
    }

    // ---- analytics
    @GetMapping("/analytics/overview")
    @PreAuthorize("hasAuthority('PERM_ANALYTICS_READ')")
    public Map<String, Object> overview(@RequestParam(defaultValue = "30d") String period, @RequestParam(required = false) String from,
                                        @RequestParam(required = false) String to, @RequestParam(defaultValue = "day") String granularity,
                                        @RequestParam(required = false) BigDecimal target) {
        return analytics.overview(period, from, to, granularity, target);
    }

    @GetMapping("/analytics/breakdown")
    @PreAuthorize("hasAuthority('PERM_ANALYTICS_READ')")
    public Map<String, Object> breakdown(@RequestParam(defaultValue = "30d") String period, @RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        return analytics.breakdown(period, from, to);
    }

    // ---- notifications (any staff role; the content is filtered by that role's permissions)
    @GetMapping("/notifications")
    @PreAuthorize("hasAnyAuthority('PERM_ORDER_READ','PERM_PRODUCT_READ','PERM_SUPPORT_CHAT','PERM_PAYMENT_READ')")
    public Map<String, Object> notifications() { return ops.notifications(CurrentUser.require().role()); }

    // ---- customer support chat
    @GetMapping("/support/conversations")
    @PreAuthorize("hasAuthority('PERM_SUPPORT_CHAT')")
    public Map<String, Object> conversations(@RequestParam(required = false) String q, @RequestParam(required = false) String status,
                                             @RequestParam(defaultValue = "false") boolean unreadOnly, @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "30") int pageSize) {
        return support.list(q, status, unreadOnly, page, pageSize);
    }

    @GetMapping("/support/conversations/{id}")
    @PreAuthorize("hasAuthority('PERM_SUPPORT_CHAT')")
    public Map<String, Object> conversation(@PathVariable long id) { return support.adminView(id); }

    public record MessageReq(String body) {}

    @PostMapping("/support/conversations/{id}/messages")
    @PreAuthorize("hasAuthority('PERM_SUPPORT_CHAT')")
    public Map<String, Object> reply(@PathVariable long id, @RequestBody MessageReq r) { return support.reply(id, r.body(), CurrentUser.require().email()); }

    public record ChatStatusReq(String status) {}

    @PatchMapping("/support/conversations/{id}")
    @PreAuthorize("hasAuthority('PERM_SUPPORT_CHAT')")
    public Map<String, Object> chatStatus(@PathVariable long id, @RequestBody ChatStatusReq r) {
        support.setStatus(id, r.status());
        return Map.of("ok", true);
    }

    @GetMapping("/support/unread")
    @PreAuthorize("hasAuthority('PERM_SUPPORT_CHAT')")
    public Map<String, Object> unread() { return Map.of("unread", support.unreadTotal()); }

    // ---- payments, COD, shipments
    @GetMapping("/payments")
    @PreAuthorize("hasAuthority('PERM_PAYMENT_READ')")
    public Map<String, Object> payments(@RequestParam(required = false) String q, @RequestParam(required = false) String status,
                                        @RequestParam(required = false) String from, @RequestParam(required = false) String to,
                                        @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int pageSize) {
        return ops.payments(q, status, from, to, page, pageSize);
    }

    @GetMapping("/cod")
    @PreAuthorize("hasAuthority('PERM_PAYMENT_READ')")
    public Map<String, Object> cod() { return ops.cod(); }

    @GetMapping("/shipments")
    @PreAuthorize("hasAuthority('PERM_ORDER_READ')")
    public Map<String, Object> shipments(@RequestParam(required = false) String q, @RequestParam(required = false) String status,
                                         @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int pageSize) {
        return ops.shipments(q, status, page, pageSize);
    }

    // ---- categories
    @GetMapping("/categories/manage")
    @PreAuthorize("hasAuthority('PERM_PRODUCT_READ')")
    public List<Map<String, Object>> categoryList() { return ops.categoryList(); }

    @PostMapping("/categories")
    @PreAuthorize("hasAuthority('PERM_PRODUCT_WRITE')")
    public Map<String, Object> createCategory(@RequestBody AdminOpsService.CategoryReq r) { return ops.saveCategory(null, r); }

    @PutMapping("/categories/{id}")
    @PreAuthorize("hasAuthority('PERM_PRODUCT_WRITE')")
    public Map<String, Object> updateCategory(@PathVariable long id, @RequestBody AdminOpsService.CategoryReq r) { return ops.saveCategory(id, r); }

    @DeleteMapping("/categories/{id}")
    @PreAuthorize("hasAuthority('PERM_PRODUCT_WRITE')")
    public Map<String, Object> deleteCategory(@PathVariable long id) {
        ops.deleteCategory(id);
        return Map.of("ok", true);
    }

    // ---- website banners
    @GetMapping("/banners")
    @PreAuthorize("hasAuthority('PERM_CONTENT_WRITE')")
    public List<Map<String, Object>> banners() { return ops.bannerList(); }

    @PostMapping("/banners")
    @PreAuthorize("hasAuthority('PERM_CONTENT_WRITE')")
    public Map<String, Object> createBanner(@RequestBody AdminOpsService.BannerReq r) { return ops.saveBanner(null, r); }

    @PutMapping("/banners/{id}")
    @PreAuthorize("hasAuthority('PERM_CONTENT_WRITE')")
    public Map<String, Object> updateBanner(@PathVariable long id, @RequestBody AdminOpsService.BannerReq r) { return ops.saveBanner(id, r); }

    @DeleteMapping("/banners/{id}")
    @PreAuthorize("hasAuthority('PERM_CONTENT_WRITE')")
    public Map<String, Object> deleteBanner(@PathVariable long id) {
        ops.deleteBanner(id);
        return Map.of("ok", true);
    }

    @PostMapping(value = "/banners/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('PERM_CONTENT_WRITE')")
    public Map<String, Object> uploadBanner(@RequestParam("file") MultipartFile file) { return Map.of("url", uploads.saveImage(file, "banners")); }

    // ---- integration status (booleans only, never secrets)
    @GetMapping("/integrations")
    @PreAuthorize("hasAuthority('PERM_SETTINGS_WRITE')")
    public Map<String, Object> integrations() { return ops.integrations(); }
}
