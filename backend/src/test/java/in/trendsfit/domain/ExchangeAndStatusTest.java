package in.trendsfit.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ExchangeAndStatusTest {
    private static final LocalDateTime DELIVERED = LocalDateTime.of(2026, 10, 1, 10, 0);

    @Test void notAvailableBeforeDelivery() {
        var r = ExchangeWindow.check(null, DELIVERED, 7);
        assertFalse(r.eligible());
        assertEquals(ExchangeWindow.State.NOT_DELIVERED, r.state());
    }

    @Test void openUntilSevenDaysAfterDeliveryThenExpired() {
        assertTrue(ExchangeWindow.check(DELIVERED, DELIVERED.plusDays(6), 7).eligible());
        assertTrue(ExchangeWindow.check(DELIVERED, DELIVERED.plusDays(7).minusSeconds(1), 7).eligible());
        var expired = ExchangeWindow.check(DELIVERED, DELIVERED.plusDays(7), 7);
        assertFalse(expired.eligible());
        assertEquals(ExchangeWindow.State.EXPIRED, expired.state());
        assertEquals("Exchange period expired", expired.message());
        assertFalse(ExchangeWindow.check(DELIVERED, DELIVERED.plusDays(30), 7).eligible());
    }

    @Test void windowIsConfigurable() {
        assertTrue(ExchangeWindow.check(DELIVERED, DELIVERED.plusDays(9), 10).eligible());
    }

    @Test void orderStatusHasNoCancelAndFollowsTheLifecycle() {
        for (OrderStatus s : OrderStatus.values()) {
            assertFalse(s.name().contains("CANCEL"), s.name());
            assertFalse(s.label().toLowerCase().contains("cancel"), s.label());
            for (OrderStatus n : s.next()) assertFalse(n.name().contains("CANCEL"));
        }
        assertTrue(OrderStatus.PENDING_PAYMENT.canMoveTo(OrderStatus.PAID));
        assertTrue(OrderStatus.SHIPPED.canMoveTo(OrderStatus.DELIVERED));
        assertFalse(OrderStatus.PENDING_PAYMENT.canMoveTo(OrderStatus.SHIPPED));
        assertFalse(OrderStatus.DELIVERED.canMoveTo(OrderStatus.PROCESSING));
        assertTrue(OrderStatus.DELIVERED.canMoveTo(OrderStatus.EXCHANGE_REQUESTED));
    }

    @Test void exchangeStatusFlow() {
        assertTrue(ExchangeStatus.PENDING.canMoveTo(ExchangeStatus.APPROVED));
        assertTrue(ExchangeStatus.PENDING.canMoveTo(ExchangeStatus.INFO_REQUESTED));
        assertFalse(ExchangeStatus.REJECTED.canMoveTo(ExchangeStatus.APPROVED));
        assertFalse(ExchangeStatus.COMPLETED.isOpen());
        assertTrue(ExchangeStatus.PICKUP_SCHEDULED.isOpen());
    }
}
