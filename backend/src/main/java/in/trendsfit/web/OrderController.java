package in.trendsfit.web;

import in.trendsfit.model.Order;
import in.trendsfit.service.ExchangeService;
import in.trendsfit.service.InvoiceService;
import in.trendsfit.service.OrderService;
import in.trendsfit.service.PaymentService;
import in.trendsfit.service.UploadService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Guest and customer order endpoints. A guest proves ownership with the secret order token returned at checkout
 * (header X-Order-Token or ?token=); logged-in owners and staff need no token. There is deliberately no cancel endpoint.
 */
@RestController
@RequestMapping("/api")
public class OrderController {
    private final OrderService orders;
    private final PaymentService payments;
    private final InvoiceService invoices;
    private final ExchangeService exchanges;
    private final UploadService uploads;

    public OrderController(OrderService orders, PaymentService payments, InvoiceService invoices, ExchangeService exchanges, UploadService uploads) {
        this.orders = orders; this.payments = payments; this.invoices = invoices; this.exchanges = exchanges; this.uploads = uploads;
    }

    private static String tok(String header, String param) { return header != null && !header.isBlank() ? header : param; }

    @PostMapping("/orders")
    public Map<String, Object> create(@RequestBody OrderService.CreateReq req,
                                      @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        Order o = orders.create(req, key);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("orderNumber", o.orderNumber);
        m.put("accessToken", o.accessToken);
        m.put("status", o.status);
        m.put("paymentMethod", o.paymentMethod);
        m.put("total", o.total);
        if (o.paymentMethod.equals("ONLINE") && o.status.equals("PENDING_PAYMENT")) {
            // Start the Razorpay order right away so the browser can open checkout. It can be re-requested any time via /payment.
            m.put("payment", payments.start(o.orderNumber, o.accessToken));
        }
        return m;
    }

    @GetMapping("/orders/{orderNumber}")
    public Map<String, Object> get(@PathVariable String orderNumber, @RequestHeader(value = "X-Order-Token", required = false) String h,
                                   @RequestParam(required = false) String token) {
        return orders.view(orders.authorize(orderNumber, tok(h, token)));
    }

    @GetMapping("/orders/{orderNumber}/tracking")
    public Map<String, Object> tracking(@PathVariable String orderNumber, @RequestHeader(value = "X-Order-Token", required = false) String h,
                                        @RequestParam(required = false) String token) {
        Map<String, Object> v = orders.view(orders.authorize(orderNumber, tok(h, token)));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("orderNumber", v.get("orderNumber"));
        m.put("status", v.get("status"));
        m.put("statusLabel", v.get("statusLabel"));
        m.put("timeline", v.get("timeline"));
        m.put("shipments", v.get("shipments"));
        return m;
    }

    @GetMapping("/orders/{orderNumber}/invoice")
    public ResponseEntity<String> invoice(@PathVariable String orderNumber, @RequestHeader(value = "X-Order-Token", required = false) String h,
                                          @RequestParam(required = false) String token) {
        String html = invoices.html(orderNumber, tok(h, token));
        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_HTML)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"invoice-" + orderNumber.replaceAll("[^A-Za-z0-9-]", "") + ".html\"")
            .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; img-src data:")
            .cacheControl(CacheControl.noStore())
            .body(html);
    }

    // ---- payment

    @PostMapping("/orders/{orderNumber}/payment")
    public Map<String, Object> pay(@PathVariable String orderNumber, @RequestHeader(value = "X-Order-Token", required = false) String h,
                                   @RequestParam(required = false) String token) {
        return payments.start(orderNumber, tok(h, token));
    }

    public record VerifyReq(String razorpayOrderId, String razorpayPaymentId, String razorpaySignature) {}

    @PostMapping("/orders/{orderNumber}/payment/verify")
    public Map<String, Object> verify(@PathVariable String orderNumber, @RequestBody VerifyReq r,
                                      @RequestHeader(value = "X-Order-Token", required = false) String h, @RequestParam(required = false) String token) {
        return payments.verify(orderNumber, tok(h, token), r.razorpayOrderId(), r.razorpayPaymentId(), r.razorpaySignature());
    }

    public record FailedReq(String reason) {}

    @PostMapping("/orders/{orderNumber}/payment/failed")
    public Map<String, Object> failed(@PathVariable String orderNumber, @RequestBody(required = false) FailedReq r,
                                      @RequestHeader(value = "X-Order-Token", required = false) String h, @RequestParam(required = false) String token) {
        payments.reportFailed(orderNumber, tok(h, token), r == null ? null : r.reason());
        return Map.of("status", "PAYMENT_FAILED");
    }

    // ---- exchange (no cancel exists)

    @GetMapping("/orders/{orderNumber}/exchange-eligibility")
    public Object eligibility(@PathVariable String orderNumber, @RequestHeader(value = "X-Order-Token", required = false) String h,
                              @RequestParam(required = false) String token) {
        return orders.view(orders.authorize(orderNumber, tok(h, token))).get("exchangeEligibility");
    }

    @PostMapping("/orders/{orderNumber}/exchange")
    public Map<String, Object> exchange(@PathVariable String orderNumber, @RequestBody ExchangeService.CreateReq r,
                                        @RequestHeader(value = "X-Order-Token", required = false) String h, @RequestParam(required = false) String token) {
        return exchanges.create(orderNumber, tok(h, token), r);
    }

    /** Photo for an exchange request (guests included). Rate-limited by the general API limiter; JPG/PNG/WebP up to 3 MB. */
    @PostMapping(value = "/uploads/exchange", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> uploadExchange(@RequestParam("file") MultipartFile file) {
        return Map.of("url", uploads.saveImage(file, "exchange"));
    }
}
