package in.trendsfit.domain;

import java.util.List;

/** "Find My Size": an estimate from waist (bottoms) or chest (tops) plus preferred fit. Always shown with a disclaimer. */
public final class SizeRecommender {
    public static final String DISCLAIMER = "Size recommendation is an estimate. Check the size chart before ordering.";

    public record Recommendation(String size, String disclaimer) {}

    private SizeRecommender() {}

    /** @param waistInches body waist; fit one of SLIM, REGULAR, RELAXED, LOOSE */
    public static Recommendation forBottoms(double waistInches, String fit) {
        double adj = switch (fit == null ? "REGULAR" : fit.toUpperCase()) { case "SLIM" -> 0.0; case "RELAXED" -> 1.0; case "LOOSE" -> 2.0; default -> 0.5; };
        int size = (int) Math.round((waistInches + adj) / 2.0) * 2;
        size = Math.max(28, Math.min(36, size));
        return new Recommendation(String.valueOf(size), DISCLAIMER);
    }

    public static Recommendation forTops(double chestInches, String fit) {
        double adj = switch (fit == null ? "REGULAR" : fit.toUpperCase()) { case "SLIM" -> 0.0; case "RELAXED" -> 2.0; case "LOOSE", "OVERSIZED" -> 4.0; default -> 1.0; };
        double c = chestInches + adj;
        List<String> sizes = List.of("S", "M", "L", "XL", "XXL");
        int idx = c < 39 ? 0 : c < 41 ? 1 : c < 43 ? 2 : c < 45 ? 3 : 4;
        return new Recommendation(sizes.get(idx), DISCLAIMER);
    }

    /** Rough chest estimate from height/weight when only those are known. */
    public static double estimateChestFromWeight(double heightCm, double weightKg) {
        double bmi = weightKg / Math.pow(heightCm / 100.0, 2);
        return 30 + bmi * 0.55 + (heightCm - 165) * 0.04;
    }
}
