package in.trendsfit.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Recalculates every price on the server. The browser only sends variant ids and quantities.
 * Shipping is free by default; a rate and a free-above threshold can be configured (no invented courier prices).
 */
public final class PricingCalculator {
    public record Line(long variantId, BigDecimal unitPrice, int qty) {}

    public record ShippingPolicy(boolean freeForAll, BigDecimal freeAbove, BigDecimal flatRate) {
        public static ShippingPolicy freeForEveryone() { return new ShippingPolicy(true, Money.ZERO, Money.ZERO); }
    }

    public record Totals(BigDecimal subtotal, BigDecimal discount, BigDecimal shipping, BigDecimal total, List<BigDecimal> lineTotals) {}

    private PricingCalculator() {}

    public static Totals compute(List<Line> lines, BigDecimal discount, ShippingPolicy policy) {
        BigDecimal subtotal = Money.ZERO;
        List<BigDecimal> lineTotals = new ArrayList<>();
        for (Line l : lines) {
            if (l.qty() < 1) throw new IllegalArgumentException("Quantity must be at least 1");
            BigDecimal lt = Money.round(l.unitPrice().multiply(BigDecimal.valueOf(l.qty())));
            lineTotals.add(lt);
            subtotal = subtotal.add(lt);
        }
        BigDecimal d = discount == null ? Money.ZERO : Money.min(Money.round(discount), subtotal);
        BigDecimal afterDiscount = subtotal.subtract(d);
        BigDecimal ship = shipping(afterDiscount, policy);
        return new Totals(subtotal, d, ship, Money.round(afterDiscount.add(ship)), lineTotals);
    }

    static BigDecimal shipping(BigDecimal afterDiscount, ShippingPolicy p) {
        if (p.freeForAll() || afterDiscount.signum() == 0) return Money.ZERO;
        if (p.freeAbove() != null && p.freeAbove().signum() > 0 && afterDiscount.compareTo(p.freeAbove()) >= 0) return Money.ZERO;
        return Money.round(p.flatRate());
    }

    /** Sale price wins when it is lower than the list price; a variant override wins over both. */
    public static BigDecimal unitPrice(BigDecimal price, BigDecimal salePrice, BigDecimal variantOverride) {
        if (variantOverride != null) return Money.round(variantOverride);
        if (salePrice != null && salePrice.compareTo(price) < 0) return Money.round(salePrice);
        return Money.round(price);
    }

    public static int discountPercent(BigDecimal price, BigDecimal salePrice) {
        if (salePrice == null || price.signum() <= 0 || salePrice.compareTo(price) >= 0) return 0;
        return price.subtract(salePrice).multiply(new BigDecimal("100")).divide(price, 0, java.math.RoundingMode.HALF_UP).intValue();
    }
}
