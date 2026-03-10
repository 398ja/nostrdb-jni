package xyz.tcheeric.nostrdb.inspector.middleware;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.http.HttpStatus;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simple token bucket rate limiter per IP address.
 */
public class RateLimitMiddleware implements Handler {

    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    private final int maxTokens;
    private final long refillIntervalMs;

    /**
     * Create a rate limiter.
     *
     * @param maxRequestsPerSecond Maximum requests per second per IP
     */
    public RateLimitMiddleware(int maxRequestsPerSecond) {
        this.maxTokens = maxRequestsPerSecond;
        this.refillIntervalMs = 1000;
    }

    @Override
    public void handle(Context ctx) {
        String ip = ctx.ip();
        TokenBucket bucket = buckets.computeIfAbsent(ip, k -> new TokenBucket(maxTokens, refillIntervalMs));

        if (!bucket.tryConsume()) {
            ctx.status(HttpStatus.TOO_MANY_REQUESTS)
               .json(java.util.Map.of("error", "Rate limit exceeded"));
        }
    }

    /**
     * Create a rate limiter for flush endpoint (1 req/min).
     */
    public static RateLimitMiddleware forFlush() {
        RateLimitMiddleware limiter = new RateLimitMiddleware(1);
        limiter.buckets.clear(); // Reset with 60s interval
        return new RateLimitMiddleware(1) {
            private final Map<String, TokenBucket> flushBuckets = new ConcurrentHashMap<>();

            @Override
            public void handle(Context ctx) {
                String ip = ctx.ip();
                TokenBucket bucket = flushBuckets.computeIfAbsent(ip, k -> new TokenBucket(1, 60000));
                if (!bucket.tryConsume()) {
                    ctx.status(HttpStatus.TOO_MANY_REQUESTS)
                       .json(java.util.Map.of("error", "Rate limit exceeded. Flush allowed once per minute."));
                }
            }
        };
    }

    /**
     * Create a rate limiter for ingest endpoint (10 req/s).
     */
    public static RateLimitMiddleware forIngest() {
        return new RateLimitMiddleware(10);
    }

    private static class TokenBucket {
        private final int maxTokens;
        private final long refillIntervalMs;
        private final AtomicLong tokens;
        private volatile long lastRefill;

        TokenBucket(int maxTokens, long refillIntervalMs) {
            this.maxTokens = maxTokens;
            this.refillIntervalMs = refillIntervalMs;
            this.tokens = new AtomicLong(maxTokens);
            this.lastRefill = System.currentTimeMillis();
        }

        boolean tryConsume() {
            refill();
            long current = tokens.get();
            while (current > 0) {
                if (tokens.compareAndSet(current, current - 1)) {
                    return true;
                }
                current = tokens.get();
            }
            return false;
        }

        private void refill() {
            long now = System.currentTimeMillis();
            long elapsed = now - lastRefill;
            if (elapsed >= refillIntervalMs) {
                long tokensToAdd = (elapsed / refillIntervalMs) * maxTokens;
                long current = tokens.get();
                long newTokens = Math.min(maxTokens, current + tokensToAdd);
                if (tokens.compareAndSet(current, newTokens)) {
                    lastRefill = now;
                }
            }
        }
    }
}
