package in.trendsfit.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.trendsfit.config.AppConfig;
import in.trendsfit.domain.Money;
import in.trendsfit.domain.OrderStatus;
import in.trendsfit.domain.RazorpaySignature;
import in.trendsfit.integration.PaymentGateway;
import in.trendsfit.model.Order;
import in.trendsfit.model.Payment;
import in.trendsfit.repo.OrderRepository;
import in.trendsfit.repo.PaymentRepository;
import in.trendsfit.web.ApiException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Razorpay payments. An order is marked PAID only after (a) a valid payment signature verified with the server-side key secret
 * or (b) a webhook whose signature verifies with the webhook secret. A "success" message from the browser alone never counts.
 */
@Service
public class PaymentService {
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final OrderService orderService;
    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final PaymentGateway gateway;
    private final JdbcTemplate jdbc;
    private final AppConfig cfg;
    private final AuditService audit;
    private final ObjectMapper json;

    public PaymentService(OrderService orderService, OrderRepository orders, PaymentRepository payments, PaymentGateway gateway,
                          JdbcTemplate jdbc, AppConfig cfg, AuditService audit, ObjectMapper json) {
        this.orderService = orderService; this.orders = orders; this.payments = payments; this.gateway = gateway;
        this.jdbc = jdbc; this.cfg = cfg; this.audit = audit; this.json = json;
    }

    /** Creates (or reuses) the Razorpay order for an online order and returns what the browser checkout needs. */
    public Map<String, Object> start(String orderNumber, String token) {
        if (!cfg.razorpayConfigured()) throw ApiException.unavailable("Online payment is not configured.");
        Order o = orderService.authorize(orderNumber, token);
        if (!o.paymentMethod.equals("ONLINE")) throw ApiException.badRequest("NOT_ONLINE", "This order is not an online-payment order.");
        OrderStatus st = o.statusEnum();
        if (st != OrderStatus.PENDING_PAYMENT && st != OrderStatus.PAYMENT_FAILED)
            throw ApiException.conflict("ALREADY_PAID", "This order has already been paid.");
        o = orderService.prepareRetry(o);
        long paise = Money.toPaise(o.total);
        Optional<Payment> last = payments.findByOrderIdOrderByIdDesc(o.id).stream().findFirst();
        String providerOrderId;
        if (last.isPresent() && last.get().status.equals("CREATED") && Money.toPaise(last.get().amount) == paise) {
            providerOrderId = last.get().providerOrderId;
        } else {
            providerOrderId = gateway.createOrder(paise, o.orderNumber);
            Payment p = new Payment();
            p.orderId = o.id;
            p.providerOrderId = providerOrderId;
            p.amount = o.total;
            payments.save(p);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("keyId", gateway.publicKeyId());
        m.put("razorpayOrderId", providerOrderId);
        m.put("amount", paise);
        m.put("currency", "INR");
        m.put("orderNumber", o.orderNumber);
        m.put("name", cfg.businessName);
        m.put("prefill", Map.of("name", o.customerName, "email", o.email, "contact", o.phone));
        return m;
    }

    /** Browser callback after checkout: verifies the HMAC signature server-side before touching the order. */
    @Transactional(noRollbackFor = ApiException.class) // one transaction so settle() (self-call) is atomic; keeps the audit row on a bad signature
    public Map<String, Object> verify(String orderNumber, String token, String rpOrderId, String rpPaymentId, String signature) {
        Order o = orderService.authorize(orderNumber, token);
        if (rpOrderId == null || rpPaymentId == null || signature == null)
            throw ApiException.badRequest("BAD_PAYMENT", "Incomplete payment details.");
        Payment p = payments.findByProviderOrderId(rpOrderId).orElseThrow(() -> ApiException.badRequest("BAD_PAYMENT", "Unknown payment."));
        if (!p.orderId.equals(o.id)) throw ApiException.badRequest("BAD_PAYMENT", "This payment does not belong to this order.");
        if (!RazorpaySignature.verifyPayment(rpOrderId, rpPaymentId, signature, cfg.razorpayKeySecret)) {
            audit.logAs("system", "PAYMENT_SIGNATURE_INVALID", "order", o.orderNumber, "provider order " + rpOrderId);
            throw ApiException.badRequest("BAD_SIGNATURE", "Payment could not be verified. If money was debited it will be reconciled automatically; contact support.");
        }
        settle(rpOrderId, rpPaymentId, null);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("orderNumber", o.orderNumber);
        m.put("status", "PAID");
        return m;
    }

    /** Browser reports a failed attempt. The order can still be paid later (and a late webhook can still mark it paid). */
    @Transactional
    public void reportFailed(String orderNumber, String token, String reason) {
        Order o = orderService.authorize(orderNumber, token);
        Order m = orders.findById(o.id).orElseThrow(() -> ApiException.notFound("Order not found."));
        if (m.statusEnum() != OrderStatus.PENDING_PAYMENT) return;
        jdbc.update("UPDATE payments SET status = 'FAILED', failure_reason = ? WHERE order_id = ? AND status = 'CREATED'",
            trim(reason, 190), m.id);
        m.paymentStatus = "FAILED";
        orderService.changeStatus(m, OrderStatus.PAYMENT_FAILED, "Payment attempt failed");
    }

    /**
     * Idempotent: the first caller (browser verify or webhook) wins through an atomic UPDATE; later callers do nothing.
     * @param expectedPaise when non-null (webhook) the captured amount must equal what we asked Razorpay to charge.
     */
    @Transactional
    public boolean settle(String rpOrderId, String rpPaymentId, Long expectedPaise) {
        Payment p = payments.findByProviderOrderId(rpOrderId).orElse(null);
        if (p == null) { log.warn("Payment for unknown provider order {}", rpOrderId); return false; }
        if (expectedPaise != null && expectedPaise != Money.toPaise(p.amount)) {
            audit.logAs("system", "PAYMENT_AMOUNT_MISMATCH", "payment", p.id, "expected " + Money.toPaise(p.amount) + " got " + expectedPaise);
            return false;
        }
        int won = jdbc.update("UPDATE payments SET status = 'PAID', provider_payment_id = ? WHERE id = ? AND status <> 'PAID' AND (provider_payment_id IS NULL OR provider_payment_id = ?)",
            rpPaymentId, p.id, rpPaymentId);
        if (won == 0) return false; // already settled (duplicate verify / webhook)
        Order o = orders.findById(p.orderId).orElseThrow();
        OrderStatus st = o.statusEnum();
        if (st == OrderStatus.PENDING_PAYMENT || st == OrderStatus.PAYMENT_FAILED) {
            String note = "Payment received";
            if (!o.stockHeld) {
                List<String> shortages = orderService.reserveBestEffort(o);
                if (!shortages.isEmpty()) {
                    note = "Payment received after the hold expired; stock short for: " + String.join(", ", shortages);
                    audit.logAs("system", "PAID_STOCK_SHORT", "order", o.orderNumber, note);
                }
            }
            o.paymentStatus = "PAID";
            orderService.changeStatus(o, OrderStatus.PAID, note);
        }
        return true;
    }

    // ------------------------------------------------------------------ webhook

    /** @return true when handled (or safely ignored); signature failures throw 400 so Razorpay does not treat them as delivered. */
    @Transactional
    public boolean webhook(String rawBody, String signature, String eventIdHeader) {
        if (cfg.razorpayWebhookSecret == null || cfg.razorpayWebhookSecret.isBlank())
            throw ApiException.unavailable("Webhook secret is not configured.");
        if (!RazorpaySignature.verifyWebhook(rawBody, signature, cfg.razorpayWebhookSecret))
            throw ApiException.badRequest("BAD_SIGNATURE", "Invalid signature.");
        JsonNode root;
        try {
            root = json.readTree(rawBody);
        } catch (Exception e) {
            throw ApiException.badRequest("BAD_PAYLOAD", "Invalid payload.");
        }
        String event = root.path("event").asText("");
        JsonNode pay = root.path("payload").path("payment").path("entity");
        String eventId = eventIdHeader != null && !eventIdHeader.isBlank() ? eventIdHeader
            : event + ":" + pay.path("id").asText(root.path("payload").path("order").path("entity").path("id").asText(""));
        try {
            jdbc.update("INSERT INTO webhook_events (source, event_id, event_type) VALUES ('RAZORPAY', ?, ?)", trim(eventId, 120), trim(event, 60));
        } catch (DuplicateKeyException dup) {
            return true; // already processed
        }
        switch (event) {
            case "payment.captured", "order.paid" -> {
                String orderId = pay.path("order_id").asText(root.path("payload").path("order").path("entity").path("id").asText(""));
                String payId = pay.path("id").asText("");
                if (!orderId.isEmpty() && !payId.isEmpty() && pay.has("amount")
                        && "INR".equals(pay.path("currency").asText("INR")))
                    settle(orderId, payId, pay.path("amount").asLong());
            }
            case "payment.failed" -> {
                String orderId = pay.path("order_id").asText("");
                jdbc.update("UPDATE payments SET status = 'FAILED', failure_reason = ? WHERE provider_order_id = ? AND status = 'CREATED'",
                    trim(pay.path("error_description").asText(null), 190), orderId);
            }
            case "refund.processed" -> {
                String payId = pay.path("id").asText(root.path("payload").path("refund").path("entity").path("payment_id").asText(""));
                payments.findByProviderPaymentId(payId).ifPresent(p -> {
                    jdbc.update("UPDATE payments SET status = 'REFUNDED' WHERE id = ?", p.id);
                    jdbc.update("UPDATE orders SET payment_status = 'REFUNDED' WHERE id = ?", p.orderId);
                    orderService.history(p.orderId, orders.findById(p.orderId).map(x -> x.status).orElse("PAID"), "Refund processed by payment provider");
                });
            }
            default -> { /* other events are recorded but ignored */ }
        }
        return true;
    }

    // ------------------------------------------------------------------ admin refund

    /** Full refund request for a paid online order. Final status arrives through the refund.processed webhook. */
    @Transactional
    public void refund(String orderNumber) {
        Order o = orderService.requireByNumber(orderNumber);
        Payment p = payments.findByOrderIdOrderByIdDesc(o.id).stream().filter(x -> x.status.equals("PAID")).findFirst()
            .orElseThrow(() -> ApiException.badRequest("NO_PAID_PAYMENT", "No captured online payment found for this order."));
        String refundId = gateway.refund(p.providerPaymentId, Money.toPaise(p.amount));
        jdbc.update("UPDATE payments SET status = 'REFUND_SENT' WHERE id = ?", p.id);
        orderService.history(o.id, o.status, "Refund requested" + (refundId == null ? "" : " (" + refundId + ")"));
        audit.log("REFUND_REQUESTED", "order", o.orderNumber, refundId);
    }

    private static String trim(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
