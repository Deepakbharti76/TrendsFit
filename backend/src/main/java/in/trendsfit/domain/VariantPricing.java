package in.trendsfit.domain;

import java.math.BigDecimal;

/**
 * Rules for a variant's optional price override.
 * No override (null) means the variant sells at the product's sale price, or its regular price when there is no sale.
 * An override that is supplied for an ACTIVE variant must be above 0 and fit the DECIMAL(10,2) column.
 * Inactive variants are not sold, so a leftover non-positive value on one of them is ignored (and cleared when saved).
 */
public final class VariantPricing {
    public enum Result { OK, NON_POSITIVE, TOO_LARGE }

    public static final BigDecimal MAX = new BigDecimal("10000000");

    private VariantPricing() {}

    public static Result check(BigDecimal override, boolean active) {
        if (override == null || !active) return Result.OK;
        if (override.signum() <= 0) return Result.NON_POSITIVE;
        if (override.compareTo(MAX) > 0) return Result.TOO_LARGE;
        return Result.OK;
    }

    /** The value to store: null (inherit) for blank or non-positive input, otherwise the rounded override. */
    public static BigDecimal toStore(BigDecimal override) {
        return override == null || override.signum() <= 0 ? null : Money.round(override);
    }
}
