package in.trendsfit.domain;

/** Razorpay signature rules. An order is never marked paid from a browser message alone. */
public final class RazorpaySignature {
    private RazorpaySignature() {}

    /** Checkout callback: HMAC_SHA256(razorpay_order_id + "|" + razorpay_payment_id, key_secret). */
    public static boolean verifyPayment(String razorpayOrderId, String razorpayPaymentId, String signature, String keySecret) {
        if (isBlank(razorpayOrderId) || isBlank(razorpayPaymentId) || isBlank(signature) || isBlank(keySecret)) return false;
        return Hmac.safeEquals(Hmac.sha256Hex(keySecret, razorpayOrderId + "|" + razorpayPaymentId), signature.toLowerCase());
    }

    /** Webhook: HMAC_SHA256(raw request body, webhook_secret) compared with the X-Razorpay-Signature header. */
    public static boolean verifyWebhook(String rawBody, String signatureHeader, String webhookSecret) {
        if (rawBody == null || isBlank(signatureHeader) || isBlank(webhookSecret)) return false;
        return Hmac.safeEquals(Hmac.sha256Hex(webhookSecret, rawBody), signatureHeader.toLowerCase());
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }
}
