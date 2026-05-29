package com.archdox.cloud.global.security;

import java.time.Clock;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class RequestRateLimiter {
    private final Clock clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public RequestRateLimiter() {
        this(Clock.systemUTC());
    }

    RequestRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public Decision consume(String key, RateLimitProperties.ResolvedRule rule, int maxTrackedKeys) {
        if (rule == null) {
            return Decision.allowed(0, 0, 0);
        }
        cleanupIfNeeded(maxTrackedKeys);
        var nowMillis = clock.millis();
        var window = windows.computeIfAbsent(key, ignored -> new Window(nowMillis + rule.windowMillis()));
        return window.consume(nowMillis, rule);
    }

    public void clear() {
        windows.clear();
    }

    private void cleanupIfNeeded(int maxTrackedKeys) {
        var safeMax = Math.max(1_000, maxTrackedKeys);
        if (windows.size() <= safeMax) {
            return;
        }
        var nowMillis = clock.millis();
        Iterator<Window> iterator = windows.values().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().isExpired(nowMillis)) {
                iterator.remove();
            }
        }
    }

    public record Decision(
            boolean allowed,
            int limit,
            int remaining,
            long resetAtMillis,
            long retryAfterSeconds
    ) {
        static Decision allowed(int limit, int remaining, long resetAtMillis) {
            return new Decision(true, limit, remaining, resetAtMillis, 0);
        }

        static Decision rejected(int limit, long resetAtMillis, long nowMillis) {
            var retryAfterSeconds = Math.max(1, (long) Math.ceil((resetAtMillis - nowMillis) / 1000.0));
            return new Decision(false, limit, 0, resetAtMillis, retryAfterSeconds);
        }
    }

    private static final class Window {
        private int count;
        private long resetAtMillis;

        private Window(long resetAtMillis) {
            this.resetAtMillis = resetAtMillis;
        }

        synchronized Decision consume(long nowMillis, RateLimitProperties.ResolvedRule rule) {
            if (nowMillis >= resetAtMillis) {
                count = 0;
                resetAtMillis = nowMillis + rule.windowMillis();
            }
            if (count >= rule.maxRequests()) {
                return Decision.rejected(rule.maxRequests(), resetAtMillis, nowMillis);
            }
            count += 1;
            return Decision.allowed(rule.maxRequests(), rule.maxRequests() - count, resetAtMillis);
        }

        synchronized boolean isExpired(long nowMillis) {
            return nowMillis >= resetAtMillis;
        }
    }
}
