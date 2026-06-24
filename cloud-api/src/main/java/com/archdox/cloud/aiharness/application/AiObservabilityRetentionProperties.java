package com.archdox.cloud.aiharness.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "archdox.ai-observability.retention")
public class AiObservabilityRetentionProperties {
    private boolean enabled = true;
    private int retentionDays = 30;
    private long checkIntervalMs = 3_600_000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }

    public long getCheckIntervalMs() {
        return checkIntervalMs;
    }

    public void setCheckIntervalMs(long checkIntervalMs) {
        this.checkIntervalMs = checkIntervalMs;
    }

    public int safeRetentionDays() {
        return Math.max(1, retentionDays);
    }

    public long safeCheckIntervalMs() {
        return Math.max(60_000, checkIntervalMs);
    }
}
