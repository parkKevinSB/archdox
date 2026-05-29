package com.archdox.cloud.documentai.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "archdox.ai-review")
public class DocumentAiReviewProperties {
    private boolean enabled;
    private long workerIntervalMs = 250;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getWorkerIntervalMs() {
        return workerIntervalMs;
    }

    public void setWorkerIntervalMs(long workerIntervalMs) {
        this.workerIntervalMs = workerIntervalMs;
    }

    public long safeWorkerIntervalMs() {
        return Math.max(1, workerIntervalMs);
    }
}
