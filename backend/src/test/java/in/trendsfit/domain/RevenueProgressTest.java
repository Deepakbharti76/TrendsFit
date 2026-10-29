package in.trendsfit.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class RevenueProgressTest {
    private static BigDecimal b(String s) { return new BigDecimal(s); }

    @Test void halfwayToTarget() {
        var p = RevenueProgress.compute(b("25000"), b("50000"));
        assertEquals(50.0, p.percent());
        assertEquals(50.0, p.ringPercent());
        assertEquals(b("25000"), p.remaining());
        assertTrue(p.targetSet());
        assertFalse(p.reached());
    }
    @Test void aboveTargetKeepsRealPercentButClampsRing() {
        var p = RevenueProgress.compute(b("75000"), b("50000"));
        assertEquals(150.0, p.percent());
        assertEquals(100.0, p.ringPercent());
        assertEquals(BigDecimal.ZERO, p.remaining());
        assertTrue(p.reached());
    }
    @Test void zeroOrMissingTargetIsSafe() {
        for (BigDecimal t : new BigDecimal[] { null, BigDecimal.ZERO, b("-5") }) {
            var p = RevenueProgress.compute(b("100"), t);
            assertFalse(p.targetSet());
            assertEquals(0.0, p.percent());
            assertEquals(BigDecimal.ZERO, p.remaining());
        }
        assertEquals(BigDecimal.ZERO, RevenueProgress.compute(null, b("10")).achieved());
    }
    @Test void changePercentHandlesZeroPrevious() {
        assertEquals(50.0, RevenueProgress.changePercent(b("150"), b("100")));
        assertEquals(-25.0, RevenueProgress.changePercent(b("75"), b("100")));
        assertNull(RevenueProgress.changePercent(b("10"), BigDecimal.ZERO));
        assertNull(RevenueProgress.changePercent(b("10"), null));
    }
    @Test void bucketsSumBlocks() {
        List<BigDecimal> d = List.of(b("1"), b("2"), b("3"), b("4"), b("5"));
        assertEquals(List.of(b("6"), b("9")), RevenueProgress.bucket(d, 3));
        assertEquals(d, RevenueProgress.bucket(d, 1));
        assertThrows(IllegalArgumentException.class, () -> RevenueProgress.bucket(d, 0));
        assertEquals(7, RevenueProgress.bucketSize("week"));
        assertThrows(IllegalArgumentException.class, () -> RevenueProgress.bucketSize("year"));
    }
    @Test void periodsAndPrevious() {
        LocalDate today = LocalDate.of(2026, 10, 17);
        var p7 = RevenueProgress.resolve("7d", null, null, today);
        assertEquals(LocalDate.of(2026, 10, 11), p7.from());
        assertEquals(7, p7.days());
        var prev = p7.previous();
        assertEquals(LocalDate.of(2026, 10, 4), prev.from());
        assertEquals(LocalDate.of(2026, 10, 10), prev.to());
        assertEquals(1, RevenueProgress.resolve("today", null, null, today).days());
        assertEquals(30, RevenueProgress.resolve("30d", null, null, today).days());
    }
    @Test void customRangeIsValidated() {
        LocalDate today = LocalDate.of(2026, 10, 17);
        assertEquals(3, RevenueProgress.resolve("custom", "2026-10-01", "2026-10-03", today).days());
        assertThrows(IllegalArgumentException.class, () -> RevenueProgress.resolve("custom", "2026-10-05", "2026-10-01", today));
        assertThrows(IllegalArgumentException.class, () -> RevenueProgress.resolve("custom", "2026-10-01", "2026-11-01", today));
        assertThrows(IllegalArgumentException.class, () -> RevenueProgress.resolve("custom", "2024-01-01", "2026-10-01", today));
        assertThrows(IllegalArgumentException.class, () -> RevenueProgress.resolve("custom", "bad", "x", today));
        assertThrows(IllegalArgumentException.class, () -> RevenueProgress.resolve("nope", null, null, today));
    }
}
