package in.trendsfit.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Rupee arithmetic helpers. All amounts are BigDecimal with 2 decimals, rounded HALF_UP. */
public final class Money {
    public static final BigDecimal ZERO = new BigDecimal("0.00");

    private Money() {}

    public static BigDecimal of(String v) { return round(new BigDecimal(v)); }

    public static BigDecimal round(BigDecimal v) { return v.setScale(2, RoundingMode.HALF_UP); }

    /** Rupees to paise for Razorpay (exact, no floating point). */
    public static long toPaise(BigDecimal rupees) {
        return round(rupees).movePointRight(2).longValueExact();
    }

    public static BigDecimal min(BigDecimal a, BigDecimal b) { return a.compareTo(b) <= 0 ? a : b; }

    public static BigDecimal max(BigDecimal a, BigDecimal b) { return a.compareTo(b) >= 0 ? a : b; }
}
