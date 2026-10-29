package in.trendsfit.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Server-side coupon validation and discount maths (pure). */
public final class CouponRules {
    public enum Type { PERCENT, FIXED }

    public record Coupon(String code, Type type, BigDecimal value, BigDecimal minOrder, BigDecimal maxDiscount,
                         LocalDateTime startsAt, LocalDateTime expiresAt, Integer maxUses, Integer perUserLimit,
                         int usedCount, boolean active) {}

    public record Outcome(boolean valid, String code, String message, BigDecimal discount) {
        static Outcome ok(BigDecimal d) { return new Outcome(true, "OK", "Coupon applied.", d); }
        static Outcome fail(String code, String msg) { return new Outcome(false, code, msg, Money.ZERO); }
    }

    private CouponRules() {}

    public static Outcome evaluate(Coupon c, BigDecimal subtotal, LocalDateTime now, int usesByThisCustomer) {
        if (c == null || !c.active()) return Outcome.fail("INVALID", "This coupon code is not valid.");
        if (c.startsAt() != null && now.isBefore(c.startsAt())) return Outcome.fail("NOT_STARTED", "This coupon is not active yet.");
        if (c.expiresAt() != null && !now.isBefore(c.expiresAt())) return Outcome.fail("EXPIRED", "This coupon has expired.");
        if (c.maxUses() != null && c.usedCount() >= c.maxUses()) return Outcome.fail("USED_UP", "This coupon has reached its usage limit.");
        if (c.perUserLimit() != null && usesByThisCustomer >= c.perUserLimit()) return Outcome.fail("PER_USER_LIMIT", "You have already used this coupon.");
        if (subtotal.compareTo(c.minOrder()) < 0) {
            return Outcome.fail("MIN_ORDER", "Add items worth ₹" + Money.round(c.minOrder().subtract(subtotal)).toPlainString() + " more to use this coupon.");
        }
        BigDecimal d = c.type() == Type.PERCENT
                ? Money.round(subtotal.multiply(c.value()).divide(new BigDecimal("100"), 4, java.math.RoundingMode.HALF_UP))
                : Money.round(c.value());
        if (c.maxDiscount() != null) d = Money.min(d, Money.round(c.maxDiscount()));
        d = Money.round(Money.min(d, subtotal)); // never discount more than the items
        if (d.signum() <= 0) return Outcome.fail("NO_DISCOUNT", "This coupon gives no discount on your cart.");
        return Outcome.ok(d);
    }
}
