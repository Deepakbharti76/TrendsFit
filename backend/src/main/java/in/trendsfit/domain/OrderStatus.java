package in.trendsfit.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Order lifecycle. There is intentionally NO cancelled status and no customer cancel transition.
 * PAYMENT_FAILED is a payment outcome (the customer can pay again), not a cancellation.
 */
public enum OrderStatus {
    PENDING_PAYMENT, PAYMENT_FAILED, PAID, PROCESSING, PACKED, SHIPPED, OUT_FOR_DELIVERY, DELIVERED,
    EXCHANGE_REQUESTED, EXCHANGE_APPROVED, EXCHANGE_REJECTED, EXCHANGE_COMPLETED;

    public Set<OrderStatus> next() {
        return switch (this) {
            case PENDING_PAYMENT -> EnumSet.of(PAID, PAYMENT_FAILED);
            case PAYMENT_FAILED -> EnumSet.of(PENDING_PAYMENT, PAID);
            case PAID -> EnumSet.of(PROCESSING);
            case PROCESSING -> EnumSet.of(PACKED, SHIPPED);
            case PACKED -> EnumSet.of(SHIPPED);
            case SHIPPED -> EnumSet.of(OUT_FOR_DELIVERY, DELIVERED);
            case OUT_FOR_DELIVERY -> EnumSet.of(DELIVERED);
            case DELIVERED -> EnumSet.of(EXCHANGE_REQUESTED);
            case EXCHANGE_REQUESTED -> EnumSet.of(EXCHANGE_APPROVED, EXCHANGE_REJECTED);
            case EXCHANGE_APPROVED -> EnumSet.of(EXCHANGE_COMPLETED);
            case EXCHANGE_REJECTED -> EnumSet.of(EXCHANGE_REQUESTED, DELIVERED);
            case EXCHANGE_COMPLETED -> EnumSet.of(EXCHANGE_REQUESTED);
        };
    }

    public boolean canMoveTo(OrderStatus target) { return next().contains(target); }

    /** Customer-facing label. */
    public String label() {
        return switch (this) {
            case PENDING_PAYMENT -> "Pending payment";
            case PAYMENT_FAILED -> "Payment failed";
            case PAID -> "Order confirmed";
            case PROCESSING -> "Processing";
            case PACKED -> "Packed";
            case SHIPPED -> "Shipped";
            case OUT_FOR_DELIVERY -> "Out for delivery";
            case DELIVERED -> "Delivered";
            case EXCHANGE_REQUESTED -> "Exchange requested";
            case EXCHANGE_APPROVED -> "Exchange approved";
            case EXCHANGE_REJECTED -> "Exchange rejected";
            case EXCHANGE_COMPLETED -> "Exchange completed";
        };
    }

    /** Rank used to stop webhooks from moving an order backwards. */
    public int rank() { return ordinal(); }
}
