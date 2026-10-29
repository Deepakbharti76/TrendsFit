package in.trendsfit.domain;

import java.time.Duration;
import java.time.LocalDateTime;

/** Seven-day exchange rule, measured from the recorded delivery time. Enforced on the server, not just in the UI. */
public final class ExchangeWindow {
    public static final String EXPIRED_MESSAGE = "Exchange period expired";
    public static final String NOT_DELIVERED_MESSAGE = "Exchange is available after your order is delivered.";

    public enum State { NOT_DELIVERED, OPEN, EXPIRED }

    public record Result(State state, boolean eligible, String message, LocalDateTime closesAt) {}

    private ExchangeWindow() {}

    public static Result check(LocalDateTime deliveredAt, LocalDateTime now, int windowDays) {
        if (deliveredAt == null) {
            return new Result(State.NOT_DELIVERED, false, NOT_DELIVERED_MESSAGE, null);
        }
        LocalDateTime closes = deliveredAt.plusDays(windowDays);
        if (!now.isBefore(closes)) { // expired exactly at delivery + N days
            return new Result(State.EXPIRED, false, EXPIRED_MESSAGE, closes);
        }
        return new Result(State.OPEN, true, "You can request an exchange until " + closes.toLocalDate() + ".", closes);
    }

    /** Whole days left, never negative. */
    public static long daysLeft(Result r, LocalDateTime now) {
        if (r.closesAt() == null || !r.eligible()) return 0;
        return Math.max(0, Duration.between(now, r.closesAt()).toDays());
    }
}
