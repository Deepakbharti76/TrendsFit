package in.trendsfit.domain;

import java.time.Instant;

/** Simple token bucket for rate limiting (one instance per client key). Thread-safe. */
public final class TokenBucket {
    private final double capacity;
    private final double refillPerSecond;
    private double tokens;
    private long lastNanos;

    public TokenBucket(int capacity, double refillPerSecond, Instant now) {
        this.capacity = capacity;
        this.refillPerSecond = refillPerSecond;
        this.tokens = capacity;
        this.lastNanos = now.getEpochSecond() * 1_000_000_000L + now.getNano();
    }

    public synchronized boolean tryAcquire(Instant now) {
        long n = now.getEpochSecond() * 1_000_000_000L + now.getNano();
        double elapsed = Math.max(0, n - lastNanos) / 1_000_000_000.0;
        tokens = Math.min(capacity, tokens + elapsed * refillPerSecond);
        lastNanos = n;
        if (tokens >= 1.0) { tokens -= 1.0; return true; }
        return false;
    }
}
