package com.archdox.cloud.document.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "archdox.documents.delivery")
public class DocumentDeliveryProperties {
    private int maxAttempts = 3;
    private long retryBaseDelayMs = 1000;
    private long retryMaxDelayMs = 30000;
    private long stepTimeoutMs = 60000;
    private long workerIntervalMs = 100;

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public long getRetryBaseDelayMs() {
        return retryBaseDelayMs;
    }

    public void setRetryBaseDelayMs(long retryBaseDelayMs) {
        this.retryBaseDelayMs = retryBaseDelayMs;
    }

    public long getRetryMaxDelayMs() {
        return retryMaxDelayMs;
    }

    public void setRetryMaxDelayMs(long retryMaxDelayMs) {
        this.retryMaxDelayMs = retryMaxDelayMs;
    }

    public long getStepTimeoutMs() {
        return stepTimeoutMs;
    }

    public void setStepTimeoutMs(long stepTimeoutMs) {
        this.stepTimeoutMs = stepTimeoutMs;
    }

    public long getWorkerIntervalMs() {
        return workerIntervalMs;
    }

    public void setWorkerIntervalMs(long workerIntervalMs) {
        this.workerIntervalMs = workerIntervalMs;
    }

    public int safeMaxAttempts() {
        return Math.max(1, maxAttempts);
    }

    public long safeStepTimeoutMs() {
        return Math.max(1, stepTimeoutMs);
    }

    public long safeWorkerIntervalMs() {
        return Math.max(1, workerIntervalMs);
    }

    public long retryDelayMs(int completedAttempt) {
        var baseDelay = Math.max(0, retryBaseDelayMs);
        var maxDelay = Math.max(baseDelay, retryMaxDelayMs);
        var exponent = Math.max(0, completedAttempt - 1);
        var multiplier = 1L << Math.min(exponent, 10);
        return Math.min(maxDelay, baseDelay * multiplier);
    }
}
