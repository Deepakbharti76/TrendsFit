package in.trendsfit.integration;

/** Abstraction so tests can mock the payment provider. */
public interface PaymentGateway {
    /** @return provider order id */
    String createOrder(long amountPaise, String receipt);

    /** @return provider refund id */
    String refund(String paymentId, long amountPaise);

    /** Public (non-secret) key id the browser checkout needs. */
    String publicKeyId();
}
