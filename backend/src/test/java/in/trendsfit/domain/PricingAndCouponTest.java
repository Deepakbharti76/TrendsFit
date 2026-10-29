package in.trendsfit.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class PricingAndCouponTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 15, 12, 0);

    private static CouponRules.Coupon coupon(CouponRules.Type t, String v, String min, String max, LocalDateTime exp, Integer maxUses, Integer perUser, int used, boolean active) {
        return new CouponRules.Coupon("SAVE", t, new BigDecimal(v), new BigDecimal(min), max == null ? null : new BigDecimal(max), null, exp, maxUses, perUser, used, active);
    }

    @Test void unitPricePrefersVariantOverrideThenSaleThenList() {
        assertEquals(new BigDecimal("999.00"), PricingCalculator.unitPrice(new BigDecimal("1499"), new BigDecimal("1199"), new BigDecimal("999")));
        assertEquals(new BigDecimal("1199.00"), PricingCalculator.unitPrice(new BigDecimal("1499"), new BigDecimal("1199"), null));
        assertEquals(new BigDecimal("1499.00"), PricingCalculator.unitPrice(new BigDecimal("1499"), null, null));
        assertEquals(new BigDecimal("1499.00"), PricingCalculator.unitPrice(new BigDecimal("1499"), new BigDecimal("1600"), null), "a sale price above list is ignored");
    }

    @Test void discountPercentIsRounded() {
        assertEquals(20, PricingCalculator.discountPercent(new BigDecimal("1499"), new BigDecimal("1199")));
        assertEquals(0, PricingCalculator.discountPercent(new BigDecimal("1499"), null));
    }

    @Test void totalsAreRecalculatedFromUnitPriceAndQuantity() {
        var t = PricingCalculator.compute(List.of(new PricingCalculator.Line(1, new BigDecimal("1199.00"), 2), new PricingCalculator.Line(2, new BigDecimal("499.00"), 1)),
                new BigDecimal("100"), PricingCalculator.ShippingPolicy.freeForEveryone());
        assertEquals(new BigDecimal("2897.00"), t.subtotal());
        assertEquals(new BigDecimal("2797.00"), t.total());
        assertEquals(Money.ZERO, t.shipping());
        assertEquals(List.of(new BigDecimal("2398.00"), new BigDecimal("499.00")), t.lineTotals());
    }

    @Test void discountNeverExceedsSubtotalAndQuantityMustBePositive() {
        var t = PricingCalculator.compute(List.of(new PricingCalculator.Line(1, new BigDecimal("100"), 1)), new BigDecimal("500"), PricingCalculator.ShippingPolicy.freeForEveryone());
        assertEquals(Money.ZERO, t.total());
        assertThrows(IllegalArgumentException.class, () -> PricingCalculator.compute(List.of(new PricingCalculator.Line(1, new BigDecimal("100"), 0)), null, PricingCalculator.ShippingPolicy.freeForEveryone()));
    }

    @Test void configurableShippingIsOnlyAppliedWhenFreeShippingIsOff() {
        var paid = new PricingCalculator.ShippingPolicy(false, new BigDecimal("999"), new BigDecimal("60"));
        var small = PricingCalculator.compute(List.of(new PricingCalculator.Line(1, new BigDecimal("500"), 1)), null, paid);
        var big = PricingCalculator.compute(List.of(new PricingCalculator.Line(1, new BigDecimal("1000"), 1)), null, paid);
        assertEquals(new BigDecimal("560.00"), small.total());
        assertEquals(new BigDecimal("1000.00"), big.total());
    }

    @Test void moneyConvertsToPaiseExactly() {
        assertEquals(279700L, Money.toPaise(new BigDecimal("2797.00")));
        assertEquals(1999L, Money.toPaise(new BigDecimal("19.99")));
        assertEquals(new BigDecimal("0.30"), Money.round(new BigDecimal("0.295")));
    }

    @Test void percentCouponWithCap() {
        var o = CouponRules.evaluate(coupon(CouponRules.Type.PERCENT, "20", "0", "300", null, null, null, 0, true), new BigDecimal("2000"), NOW, 0);
        assertTrue(o.valid());
        assertEquals(new BigDecimal("300.00"), o.discount());
    }

    @Test void fixedCouponAndMinimumOrder() {
        var c = coupon(CouponRules.Type.FIXED, "150", "1000", null, null, null, null, 0, true);
        assertEquals(new BigDecimal("150.00"), CouponRules.evaluate(c, new BigDecimal("1200"), NOW, 0).discount());
        var low = CouponRules.evaluate(c, new BigDecimal("900"), NOW, 0);
        assertFalse(low.valid());
        assertEquals("MIN_ORDER", low.code());
        assertTrue(low.message().contains("100.00"));
    }

    @Test void expiredInactiveUsedUpAndPerUserLimit() {
        assertEquals("EXPIRED", CouponRules.evaluate(coupon(CouponRules.Type.FIXED, "50", "0", null, NOW.minusSeconds(1), null, null, 0, true), new BigDecimal("500"), NOW, 0).code());
        assertEquals("INVALID", CouponRules.evaluate(coupon(CouponRules.Type.FIXED, "50", "0", null, null, null, null, 0, false), new BigDecimal("500"), NOW, 0).code());
        assertEquals("USED_UP", CouponRules.evaluate(coupon(CouponRules.Type.FIXED, "50", "0", null, null, 10, null, 10, true), new BigDecimal("500"), NOW, 0).code());
        assertEquals("PER_USER_LIMIT", CouponRules.evaluate(coupon(CouponRules.Type.FIXED, "50", "0", null, null, null, 1, 3, true), new BigDecimal("500"), NOW, 1).code());
        assertEquals("INVALID", CouponRules.evaluate(null, new BigDecimal("500"), NOW, 0).code());
    }

    @Test void couponCannotDiscountMoreThanTheCart() {
        var o = CouponRules.evaluate(coupon(CouponRules.Type.FIXED, "900", "0", null, null, null, null, 0, true), new BigDecimal("400"), NOW, 0);
        assertEquals(new BigDecimal("400.00"), o.discount());
    }
}
