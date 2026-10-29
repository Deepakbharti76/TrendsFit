package in.trendsfit.security;

import in.trendsfit.config.AppConfig;
import in.trendsfit.domain.TokenBucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Per-IP token buckets: a strict one for /api/auth/**, a general one for the rest of /api. In-memory (single instance). */
@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private final AppConfig cfg;
    private final ConcurrentHashMap<String, TokenBucket> general = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, TokenBucket> auth = new ConcurrentHashMap<>();

    public RateLimitFilter(AppConfig cfg) { this.cfg = cfg; }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        String p = req.getRequestURI();
        return !p.startsWith("/api/") || p.startsWith("/api/payments/webhook") || p.startsWith("/api/shipping/webhook");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String ip = clientIp(req);
        boolean isAuth = req.getRequestURI().startsWith("/api/auth/");
        var map = isAuth ? auth : general;
        if (map.size() > 50_000) map.clear(); // crude memory guard
        TokenBucket b = map.computeIfAbsent(ip, k -> isAuth
                ? new TokenBucket(cfg.authRateCapacity, cfg.authRateRefill, Instant.now())
                : new TokenBucket(cfg.rateCapacity, cfg.rateRefill, Instant.now()));
        if (!b.tryAcquire(Instant.now())) {
            res.setStatus(429);
            res.setContentType("application/json");
            res.setHeader("Retry-After", "10");
            res.getWriter().write("{\"error\":{\"code\":\"RATE_LIMITED\",\"message\":\"Too many requests. Please try again shortly.\"}}");
            return;
        }
        chain.doFilter(req, res);
    }

    /** Uses the socket address. Behind a trusted reverse proxy set server.forward-headers-strategy=native so this reflects X-Forwarded-For. */
    static String clientIp(HttpServletRequest req) { return req.getRemoteAddr(); }
}
