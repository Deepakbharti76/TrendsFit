package in.trendsfit.domain;

import java.util.EnumSet;
import java.util.Set;

public enum ExchangeStatus {
    PENDING, INFO_REQUESTED, APPROVED, REJECTED, PICKUP_SCHEDULED, RECEIVED, EXCHANGE_SHIPPED, COMPLETED;

    public Set<ExchangeStatus> next() {
        return switch (this) {
            case PENDING -> EnumSet.of(APPROVED, REJECTED, INFO_REQUESTED);
            case INFO_REQUESTED -> EnumSet.of(PENDING, APPROVED, REJECTED);
            case APPROVED -> EnumSet.of(PICKUP_SCHEDULED, RECEIVED);
            case PICKUP_SCHEDULED -> EnumSet.of(RECEIVED);
            case RECEIVED -> EnumSet.of(EXCHANGE_SHIPPED);
            case EXCHANGE_SHIPPED -> EnumSet.of(COMPLETED);
            case REJECTED, COMPLETED -> EnumSet.noneOf(ExchangeStatus.class);
        };
    }

    public boolean canMoveTo(ExchangeStatus t) { return next().contains(t); }

    /** Still counts against the "one open exchange per item" rule. */
    public boolean isOpen() { return this != REJECTED && this != COMPLETED; }
}
