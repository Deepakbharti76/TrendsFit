package in.trendsfit.web;

import in.trendsfit.config.AppConfig;
import in.trendsfit.model.Coupon;
import in.trendsfit.service.AdminProductService;
import in.trendsfit.service.AdminService;
import in.trendsfit.service.AuditService;
import in.trendsfit.service.ExchangeService;
import in.trendsfit.service.PaymentService;
import in.trendsfit.service.SettingsService;
import in.trendsfit.security.CurrentUser;
import in.trendsfit.service.ShippingService;
import in.trendsfit.service.UploadService;
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
 * Admin API. Every method is guarded by a permission (PERM_*) derived from the staff role, enforced on the server.
 * Hiding a button in the UI is never the only protection.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminService admin;
    private final AdminProductService products;
    private final ShippingService shipping;
    private final ExchangeService exchanges;
    private final PaymentService payments;
    private final AuditService audit;
    private final UploadService uploads;
    private final AppConfig cfg;
    private final SettingsService settings;

    public AdminController(AdminService admin, AdminProductService products, ShippingService shipping, ExchangeService exchanges,
                           PaymentService payments, AuditService audit, UploadService uploads, AppConfig cfg, SettingsService settings) {
        this.admin = admin; this.products = products; this.shipping = shipping; this.exchanges = exchanges;
        this.payments = payments; this.audit = audit; this.uploads = uploads; this.cfg = cfg; this.settings = settings;
    }

    // ---- dashboard
    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('PERM_ANALYTICS_READ')")
    public Map<String, Object> dashboard() { return admin.dashboard(); }

    // ---- settings (non-secret store settings; secrets stay in environment variables)
    @GetMapping("/settings")
    @PreAuthorize("hasAuthority('PERM_SETTINGS_WRITE')")
    public List<Map<String, Object>> getSettings() { return settings.view(); }

    @PutMapping("/settings")
    @PreAuthorize("hasAuthority('PERM_SETTINGS_WRITE')")
    public List<Map<String, Object>> saveSettings(@RequestBody Map<String, String> changes) {
        settings.update(changes, CurrentUser.require().email());
        return settings.view();
    }

    // ---- products
    @GetMapping("/categories")
    @PreAuthorize("hasAuthority('PERM_PRODUCT_READ')")
    public List<Map<String, Object>> categories() { return products.categories(); }

    @GetMapping("/products")
    @PreAuthorize("hasAuthority('PERM_PRODUCT_READ')")
    public Map<String, Object> productList(@RequestParam(required = false) String q, @RequestParam(required = false) String status,
                                           @RequestParam(required = false) String category, @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int pageSize) {
        return products.list(q, status, category, page, pageSize);
    }

    @GetMapping("/products/{id}")
    @PreAuthorize("hasAuthority('PERM_PRODUCT_READ')")
    public Map<String, Object> product(@PathVariable long id) { return products.get(id); }

    @PostMapping("/products")
    @PreAuthorize("hasAuthority('PERM_PRODUCT_WRITE')")
    public Map<String, Object> createProduct(@RequestBody AdminProductService.ProductReq r) { return products.create(r); }

    @PutMapping("/products/{id}")
    @PreAuthorize("hasAuthority('PERM_PRODUCT_WRITE')")
    public Map<String, Object> updateProduct(@PathVariable long id, @RequestBody AdminProductService.ProductReq r) { return products.update(id, r); }

    @DeleteMapping("/products/{id}")
    @PreAuthorize("hasAuthority('PERM_PRODUCT_WRITE')")
    public Map<String, Object> deleteProduct(@PathVariable long id) { return products.delete(id); }

    @PostMapping("/products/bulk")
    @PreAuthorize("hasAuthority('PERM_PRODUCT_WRITE')")
    public Map<String, Object> bulk(@RequestBody AdminProductService.BulkReq r) { return products.bulk(r); }

    @PostMapping("/products/import")
    @PreAuthorize("hasAuthority('PERM_PRODUCT_WRITE')")
    public Map<String, Object> importCsv(@RequestBody Map<String, String> body) { return products.importCsv(body.get("csv")); }

    @PostMapping(value = "/uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('PERM_PRODUCT_WRITE')")
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file) { return Map.of("url", uploads.saveImage(file, "products")); }

    // ---- inventory
    @GetMapping("/inventory")
    @PreAuthorize("hasAuthority('PERM_PRODUCT_READ')")
    public Map<String, Object> inventory(@RequestParam(required = false) String q, @RequestParam(defaultValue = "false") boolean lowOnly,
                                         @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "30") int pageSize) {
        return products.inventory(q, lowOnly, cfg.lowStockThreshold, page, pageSize);
    }

    @PostMapping("/inventory/{variantId}/adjust")
    @PreAuthorize("hasAuthority('PERM_INVENTORY_WRITE')")
    public Map<String, Object> adjust(@PathVariable long variantId, @RequestBody AdminProductService.AdjustReq r) { return products.adjust(variantId, r); }

    @GetMapping("/inventory/{variantId}/history")
    @PreAuthorize("hasAuthority('PERM_PRODUCT_READ')")
    public List<Map<String, Object>> history(@PathVariable long variantId) { return products.stockHistory(variantId); }

    // ---- orders
    @GetMapping("/orders")
    @PreAuthorize("hasAuthority('PERM_ORDER_READ')")
    public Map<String, Object> orders(@RequestParam(required = false) String status, @RequestParam(required = false) String q,
                                      @RequestParam(required = false) String from, @RequestParam(required = false) String to,
                                      @RequestParam(required = false) String paymentMethod, @RequestParam(required = false) String paymentStatus,
                                      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int pageSize) {
        return admin.orderList(status, q, from, to, paymentMethod, paymentStatus, page, pageSize);
    }

    @GetMapping("/orders/{orderNumber}")
    @PreAuthorize("hasAuthority('PERM_ORDER_READ')")
    public Map<String, Object> order(@PathVariable String orderNumber) { return admin.orderDetail(orderNumber); }

    public record StatusReq(String status, String note) {}

    @PatchMapping("/orders/{orderNumber}/status")
    @PreAuthorize("hasAuthority('PERM_ORDER_WRITE')")
    public Map<String, Object> setStatus(@PathVariable String orderNumber, @RequestBody StatusReq r) {
        admin.setOrderStatus(orderNumber, r.status(), r.note());
        return Map.of("ok", true);
    }

    @PostMapping("/orders/{orderNumber}/shipment")
    @PreAuthorize("hasAuthority('PERM_SHIPMENT_WRITE')")
    public Map<String, Object> createShipment(@PathVariable String orderNumber) { return shipping.createShipment(orderNumber); }

    @GetMapping("/shipments/{id}/label")
    @PreAuthorize("hasAuthority('PERM_SHIPMENT_WRITE')")
    public Map<String, Object> label(@PathVariable long id) { return shipping.label(id); }

    @PostMapping("/orders/{orderNumber}/refund")
    @PreAuthorize("hasAuthority('PERM_ORDER_WRITE')")
    public Map<String, Object> refund(@PathVariable String orderNumber) {
        payments.refund(orderNumber);
        return Map.of("ok", true);
    }

    // ---- exchanges
    @GetMapping("/exchanges")
    @PreAuthorize("hasAuthority('PERM_ORDER_READ')")
    public List<Map<String, Object>> exchangeList(@RequestParam(required = false) String status) { return exchanges.list(status); }

    @PatchMapping("/exchanges/{id}")
    @PreAuthorize("hasAuthority('PERM_EXCHANGE_DECIDE')")
    public Map<String, Object> exchangeStatus(@PathVariable long id, @RequestBody StatusReq r) {
        exchanges.transition(id, r.status(), r.note());
        return Map.of("ok", true);
    }

    // ---- customers
    @GetMapping("/customers")
    @PreAuthorize("hasAuthority('PERM_CUSTOMER_READ')")
    public Map<String, Object> customers(@RequestParam(required = false) String q, @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int pageSize) {
        return admin.customers(q, page, pageSize);
    }

    // ---- coupons
    @GetMapping("/coupons")
    @PreAuthorize("hasAuthority('PERM_COUPON_WRITE')")
    public List<Coupon> coupons() { return admin.couponList(); }

    @PostMapping("/coupons")
    @PreAuthorize("hasAuthority('PERM_COUPON_WRITE')")
    public Coupon createCoupon(@RequestBody AdminService.CouponReq r) { return admin.saveCoupon(null, r); }

    @PutMapping("/coupons/{id}")
    @PreAuthorize("hasAuthority('PERM_COUPON_WRITE')")
    public Coupon updateCoupon(@PathVariable long id, @RequestBody AdminService.CouponReq r) { return admin.saveCoupon(id, r); }

    @DeleteMapping("/coupons/{id}")
    @PreAuthorize("hasAuthority('PERM_COUPON_WRITE')")
    public Map<String, Object> deleteCoupon(@PathVariable long id) {
        admin.deleteCoupon(id);
        return Map.of("ok", true);
    }

    // ---- reviews
    @GetMapping("/reviews")
    @PreAuthorize("hasAuthority('PERM_REVIEW_MODERATE')")
    public List<Map<String, Object>> reviews(@RequestParam(defaultValue = "PENDING") String status) { return admin.reviews(status); }

    @PatchMapping("/reviews/{id}")
    @PreAuthorize("hasAuthority('PERM_REVIEW_MODERATE')")
    public Map<String, Object> moderate(@PathVariable long id, @RequestBody StatusReq r) {
        admin.moderate(id, r.status());
        return Map.of("ok", true);
    }

    // ---- staff / roles
    @GetMapping("/staff")
    @PreAuthorize("hasAuthority('PERM_USER_ROLE_WRITE')")
    public List<Map<String, Object>> staff() { return admin.staff(); }

    @PostMapping("/staff")
    @PreAuthorize("hasAuthority('PERM_USER_ROLE_WRITE')")
    public Map<String, Object> createStaff(@RequestBody AdminService.StaffReq r) { return admin.createStaff(r); }

    public record StaffUpdate(String role, Boolean enabled) {}

    @PatchMapping("/staff/{id}")
    @PreAuthorize("hasAuthority('PERM_USER_ROLE_WRITE')")
    public Map<String, Object> updateStaff(@PathVariable long id, @RequestBody StaffUpdate r) {
        admin.updateStaff(id, r.role(), r.enabled());
        return Map.of("ok", true);
    }

    // ---- misc
    @GetMapping("/newsletter")
    @PreAuthorize("hasAuthority('PERM_NEWSLETTER_READ')")
    public List<Map<String, Object>> newsletter() { return admin.newsletter(); }

    @GetMapping("/audit")
    @PreAuthorize("hasAuthority('PERM_AUDIT_READ')")
    public List<Map<String, Object>> audit(@RequestParam(defaultValue = "100") int limit) { return audit.recent(limit); }
}
