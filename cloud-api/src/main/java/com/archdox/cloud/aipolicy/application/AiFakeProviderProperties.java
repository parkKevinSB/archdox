package com.archdox.cloud.aipolicy.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "archdox.ai.fake-provider")
public class AiFakeProviderProperties {
    private boolean enabled;
    private String providerCodePrefix = "fake-";
    private long latencyMs = 5;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProviderCodePrefix() {
        return providerCodePrefix;
    }

    public void setProviderCodePrefix(String providerCodePrefix) {
        this.providerCodePrefix = providerCodePrefix;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public boolean supports(String providerCode) {
        if (!enabled || providerCode == null || providerCode.isBlank()) {
            return false;
        }
        return providerCode.trim().toLowerCase().startsWith(prefix());
    }

    public long safeLatencyMs() {
        return Math.max(0, latencyMs);
    }

    private String prefix() {
        if (providerCodePrefix == null || providerCodePrefix.isBlank()) {
            return "fake-";
        }
        return providerCodePrefix.trim().toLowerCase();
    }
}
