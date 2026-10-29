package in.trendsfit.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class SecurityTest {
    private static final String SECRET = "unit-test-secret-unit-test-secret-1234";
    private static final Instant NOW = Instant.parse("2026-10-15T10:00:00Z");

    @Test void razorpayPaymentSignatureAcceptsOnlyTheRightHmac() {
        String sig = Hmac.sha256Hex("key_secret_placeholder", "order_ABC|pay_XYZ");
        assertTrue(RazorpaySignature.verifyPayment("order_ABC", "pay_XYZ", sig, "key_secret_placeholder"));
        assertFalse(RazorpaySignature.verifyPayment("order_ABC", "pay_OTHER", sig, "key_secret_placeholder"));
        assertFalse(RazorpaySignature.verifyPayment("order_ABC", "pay_XYZ", sig, "wrong_secret"));
        assertFalse(RazorpaySignature.verifyPayment("order_ABC", "pay_XYZ", "", "key_secret_placeholder"));
        assertFalse(RazorpaySignature.verifyPayment(null, "pay_XYZ", sig, "key_secret_placeholder"));
        assertFalse(RazorpaySignature.verifyPayment("order_ABC", "pay_XYZ", sig, ""));
    }

    @Test void hmacMatchesAKnownVector() {
        // RFC 4231 test case 2: key "Jefe", data "what do ya want for nothing?"
        assertEquals("5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843", Hmac.sha256Hex("Jefe", "what do ya want for nothing?"));
    }

    @Test void razorpayWebhookSignatureUsesTheRawBody() {
        String body = "{\"event\":\"payment.captured\"}";
        String sig = Hmac.sha256Hex("webhook_secret_placeholder", body);
        assertTrue(RazorpaySignature.verifyWebhook(body, sig, "webhook_secret_placeholder"));
        assertFalse(RazorpaySignature.verifyWebhook(body + " ", sig, "webhook_secret_placeholder"));
        assertFalse(RazorpaySignature.verifyWebhook(body, null, "webhook_secret_placeholder"));
    }

    @Test void jwtRoundTripExpiryTamperingAndAlgNone() {
        String t = Jwt.issue(SECRET, "a@example.com", "ORDER_MANAGER", 42, 3600, NOW);
        Jwt.Claims c = Jwt.parse(SECRET, t, NOW.plusSeconds(10));
        assertNotNull(c);
        assertEquals("a@example.com", c.subject());
        assertEquals("ORDER_MANAGER", c.role());
        assertEquals(42L, c.userId());
        assertNull(Jwt.parse(SECRET, t, NOW.plusSeconds(3600)), "expired");
        assertNull(Jwt.parse("another-secret-another-secret-12345678", t, NOW), "wrong key");
        String[] p = t.split("\\.");
        String forged = p[0] + "." + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("{\"sub\":\"x\",\"role\":\"SUPER_ADMIN\",\"uid\":1,\"exp\":9999999999}".getBytes()) + "." + p[2];
        assertNull(Jwt.parse(SECRET, forged, NOW), "payload tampered");
        String none = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes()) + "." + p[1] + ".";
        assertNull(Jwt.parse(SECRET, none, NOW), "alg none");
        assertNull(Jwt.parse(SECRET, "garbage", NOW));
        assertNull(Jwt.parse(SECRET, null, NOW));
        assertThrows(IllegalStateException.class, () -> Jwt.issue("short", "a", "b", 1, 1, NOW));
    }

    @Test void jwtHandlesQuotesInSubject() {
        String t = Jwt.issue(SECRET, "o\"brien@example.com", "CUSTOMER", 7, 60, NOW);
        assertEquals("o\"brien@example.com", Jwt.parse(SECRET, t, NOW).subject());
    }

    @Test void rolesGetOnlyWhatTheyNeed() {
        assertTrue(Role.SUPER_ADMIN.can(Role.Permission.USER_ROLE_WRITE));
        assertFalse(Role.ADMIN.can(Role.Permission.USER_ROLE_WRITE));
        assertTrue(Role.PRODUCT_MANAGER.can(Role.Permission.PRODUCT_WRITE));
        assertFalse(Role.PRODUCT_MANAGER.can(Role.Permission.ORDER_WRITE));
        assertTrue(Role.ORDER_MANAGER.can(Role.Permission.SHIPMENT_WRITE));
        assertFalse(Role.ORDER_MANAGER.can(Role.Permission.PRODUCT_WRITE));
        assertTrue(Role.SUPPORT_MANAGER.can(Role.Permission.EXCHANGE_DECIDE));
        assertFalse(Role.SUPPORT_MANAGER.can(Role.Permission.COUPON_WRITE));
        assertTrue(Role.CUSTOMER.permissions().isEmpty());
        assertFalse(Role.CUSTOMER.isStaff());
    }

    @Test void tokenBucketLimitsAndRefills() {
        TokenBucket b = new TokenBucket(3, 1.0, NOW);
        assertTrue(b.tryAcquire(NOW));
        assertTrue(b.tryAcquire(NOW));
        assertTrue(b.tryAcquire(NOW));
        assertFalse(b.tryAcquire(NOW));
        assertTrue(b.tryAcquire(NOW.plusSeconds(2)));
    }
}
