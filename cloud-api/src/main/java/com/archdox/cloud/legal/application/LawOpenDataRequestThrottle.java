package com.archdox.cloud.legal.application;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import org.springframework.stereotype.Component;

@Component
public class LawOpenDataRequestThrottle {
    private final Object requestGate = new Object();
    private long lastRequestAtMillis = 0;

    public void waitForRequestSlot(long intervalMs) {
        var safeIntervalMs = Math.max(0, intervalMs);
        if (safeIntervalMs == 0) {
            return;
        }
        synchronized (requestGate) {
            var now = System.currentTimeMillis();
            var waitMs = lastRequestAtMillis + safeIntervalMs - now;
            if (waitMs > 0) {
                pause(waitMs, "Interrupted while waiting for Law Open Data API request slot");
            }
            lastRequestAtMillis = System.currentTimeMillis();
        }
    }

    public void waitBeforeRetry(int attempt) {
        pause(500L * Math.max(1, attempt), "Interrupted while waiting to retry Law Open Data API request");
    }

    private void pause(long millis, String message) {
        if (millis <= 0) {
            return;
        }
        var deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
        var remaining = deadline - System.nanoTime();
        while (remaining > 0) {
            LockSupport.parkNanos(remaining);
            if (Thread.interrupted()) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(message);
            }
            remaining = deadline - System.nanoTime();
        }
    }
}
