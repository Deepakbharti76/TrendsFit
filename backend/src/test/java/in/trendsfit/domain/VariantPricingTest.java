package in.trendsfit.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class VariantPricingTest {
    private static BigDecimal d(String s) { return new BigDecimal(s); }

    @Test void positiveOverrideIsAccepted() {
        assertEquals(VariantPricing.Result.OK, VariantPricing.check(d("399"), true));
        assertEquals(VariantPricing.Result.OK, VariantPricing.check(d("399.00"), true));
        assertEquals(VariantPricing.Result.OK, VariantPricing.check(d("0.01"), true));
    }

    @Test void blankOverrideInheritsProductPrice() {
        assertEquals(VariantPricing.Result.OK, VariantPricing.check(null, true));
        assertNull(VariantPricing.toStore(null));
        // sale price wins over regular price when there is no override; an override wins over both
        assertEquals(0, d("399.00").compareTo(PricingCalculator.unitPrice(d("599"), d("399"), VariantPricing.toStore(null))));
        assertEquals(0, d("350.00").compareTo(PricingCalculator.unitPrice(d("599"), d("399"), VariantPricing.toStore(d("350")))));
        assertEquals(0, d("599.00").compareTo(PricingCalculator.unitPrice(d("599"), null, VariantPricing.toStore(null))));
    }

    @Test void zeroNegativeAndHugeValuesAreRejectedForActiveVariants() {
        assertEquals(VariantPricing.Result.NON_POSITIVE, VariantPricing.check(d("0"), true));
        assertEquals(VariantPricing.Result.NON_POSITIVE, VariantPricing.check(d("0.00"), true));
        assertEquals(VariantPricing.Result.NON_POSITIVE, VariantPricing.check(d("-5"), true));
        assertEquals(VariantPricing.Result.TOO_LARGE, VariantPricing.check(d("10000000.01"), true));
    }

    @Test void inactiveVariantNeverBlocksTheSaveAndNeverStoresBadPrice() {
        assertEquals(VariantPricing.Result.OK, VariantPricing.check(d("0"), false));
        assertNull(VariantPricing.toStore(d("0")));
        assertNull(VariantPricing.toStore(d("-1")));
        assertEquals(0, d("399.00").compareTo(VariantPricing.toStore(d("399"))));
    }
}
