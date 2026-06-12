package com.archdox.cloud.legal.application;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class LawOpenDataRequestThrottle {
    private final Object requestGate = new Object();
    private long lastRequestAtMillis = 0;

    public CompletableFuture<Void> requestSlotAsync(long intervalMs) {
        var safeIntervalMs = Math.max(0, intervalMs);
        if (safeIntervalMs == 0) {
            return CompletableFuture.completedFuture(null);
        }
        long delayMs;
        synchronized (requestGate) {
            var now = System.currentTimeMillis();
            var scheduledAt = Math.max(now, lastRequestAtMillis + safeIntervalMs);
            delayMs = scheduledAt - now;
            lastRequestAtMillis = scheduledAt;
        }
        return delayAsync(delayMs);
    }

    public CompletableFuture<Void> retryDelayAsync(int attempt) {
        return delayAsync(500L * Math.max(1, attempt));
    }

    private CompletableFuture<Void> delayAsync(long delayMs) {
        if (delayMs <= 0) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(
                () -> {
                },
                CompletableFuture.delayedExecutor(delayMs, TimeUnit.MILLISECONDS));
    }
}
