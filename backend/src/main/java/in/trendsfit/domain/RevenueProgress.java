package in.trendsfit.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/** Revenue-vs-target maths and reporting periods. Pure Java (no Spring) so it is unit-tested directly. */
public final class RevenueProgress {
    private RevenueProgress() {}

    public static final int MAX_DAYS = 366;

    /**
     * @param percent     real percentage (may exceed 100)
     * @param ringPercent percentage clamped to 0..100 for drawing the circle
     * @param targetSet   false when no target (or a zero target) is configured; then percent/remaining are 0
     */
    public record Progress(BigDecimal target, BigDecimal achieved, BigDecimal remaining, double percent, double ringPercent, boolean targetSet, boolean reached) {}

    public static Progress compute(BigDecimal achieved, BigDecimal target) {
        BigDecimal a = achieved == null || achieved.signum() < 0 ? BigDecimal.ZERO : achieved;
        BigDecimal t = target == null || target.signum() <= 0 ? BigDecimal.ZERO : target;
        if (t.signum() == 0) return new Progress(BigDecimal.ZERO, a, BigDecimal.ZERO, 0, 0, false, false);
        double pct = a.multiply(BigDecimal.valueOf(100)).divide(t, 1, RoundingMode.HALF_UP).doubleValue();
        BigDecimal remaining = t.subtract(a).max(BigDecimal.ZERO);
        return new Progress(t, a, remaining, pct, Math.min(100, Math.max(0, pct)), true, a.compareTo(t) >= 0);
    }

    /** Percentage change versus the previous period, or null when there is nothing to compare with (previous is zero). */
    public static Double changePercent(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.signum() <= 0) return null;
        BigDecimal c = current == null ? BigDecimal.ZERO : current;
        return c.subtract(previous).multiply(BigDecimal.valueOf(100)).divide(previous, 1, RoundingMode.HALF_UP).doubleValue();
    }

    /** Sums consecutive blocks of {@code size} days (size 1 = daily, 7 = weekly blocks, 30 = monthly blocks). */
    public static List<BigDecimal> bucket(List<BigDecimal> daily, int size) {
        if (size < 1) throw new IllegalArgumentException("size must be >= 1");
        List<BigDecimal> out = new ArrayList<>();
        for (int i = 0; i < daily.size(); i += size) {
            BigDecimal s = BigDecimal.ZERO;
            for (int j = i; j < Math.min(i + size, daily.size()); j++) s = s.add(daily.get(j));
            out.add(s);
        }
        return out;
    }

    public static int bucketSize(String granularity) {
        if (granularity == null) return 1;
        return switch (granularity) {
            case "day" -> 1;
            case "week" -> 7;
            case "month" -> 30;
            default -> throw new IllegalArgumentException("granularity must be day, week or month");
        };
    }

    /** Inclusive date range. */
    public record Period(LocalDate from, LocalDate to) {
        public int days() { return (int) ChronoUnit.DAYS.between(from, to) + 1; }
        /** The equally long period immediately before this one. */
        public Period previous() { return new Period(from.minusDays(days()), from.minusDays(1)); }
    }

    public static Period resolve(String kind, String from, String to, LocalDate today) {
        String k = kind == null ? "30d" : kind;
        switch (k) {
            case "today": return new Period(today, today);
            case "7d": return new Period(today.minusDays(6), today);
            case "30d": return new Period(today.minusDays(29), today);
            case "custom": {
                LocalDate f, t;
                try { f = LocalDate.parse(from); t = LocalDate.parse(to); }
                catch (RuntimeException e) { throw new IllegalArgumentException("Choose a valid start and end date."); }
                if (t.isBefore(f)) throw new IllegalArgumentException("The end date must not be before the start date.");
                if (t.isAfter(today)) throw new IllegalArgumentException("The end date cannot be in the future.");
                Period p = new Period(f, t);
                if (p.days() > MAX_DAYS) throw new IllegalArgumentException("The date range can be at most " + MAX_DAYS + " days.");
                return p;
            }
            default: throw new IllegalArgumentException("Unknown period.");
        }
    }
}
