package in.trendsfit.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScheduledJobs {
    private static final Logger log = LoggerFactory.getLogger(ScheduledJobs.class);
    private final OrderService orders;

    public ScheduledJobs(OrderService orders) { this.orders = orders; }

    /** Releases stock held by unpaid online orders after the payment window (system expiry, not a customer cancellation). */
    @Scheduled(fixedDelayString = "${trendsfit.payment.expiry-check-ms:120000}", initialDelay = 60000)
    public void expireUnpaid() {
        try {
            int n = orders.expireUnpaid();
            if (n > 0) log.info("Released stock for {} unpaid order(s)", n);
        } catch (RuntimeException e) {
            log.warn("Expiry job failed: {}", e.toString());
        }
    }
}
