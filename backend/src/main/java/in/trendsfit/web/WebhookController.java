package in.trendsfit.web;

import in.trendsfit.service.PaymentService;
import in.trendsfit.service.ShippingService;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Provider callbacks. The raw body is read as a String because the signature is computed over the exact bytes sent. */
@RestController
@RequestMapping("/api")
public class WebhookController {
    private final PaymentService payments;
    private final ShippingService shipping;

    public WebhookController(PaymentService payments, ShippingService shipping) { this.payments = payments; this.shipping = shipping; }

    @PostMapping("/payments/webhook")
    public Map<String, Object> razorpay(@RequestBody String body,
                                        @RequestHeader(value = "X-Razorpay-Signature", required = false) String sig,
                                        @RequestHeader(value = "X-Razorpay-Event-Id", required = false) String eventId) {
        payments.webhook(body, sig, eventId);
        return Map.of("ok", true);
    }

    @PostMapping("/shipping/webhook")
    public Map<String, Object> shiprocket(@RequestBody String body, @RequestHeader(value = "x-api-key", required = false) String token) {
        shipping.webhook(token, body);
        return Map.of("ok", true);
    }
}
