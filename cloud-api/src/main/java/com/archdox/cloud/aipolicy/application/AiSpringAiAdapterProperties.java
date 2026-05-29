package com.archdox.cloud.aipolicy.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "archdox.ai.spring-ai-adapter")
public class AiSpringAiAdapterProperties {
    private boolean enabled;
    private String providerCodePrefix = "spring-ai-";
    private long pollIntervalMs = 10;

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

    public long getPollIntervalMs() {
        return pollIntervalMs;
    }

    public void setPollIntervalMs(long pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
    }

    public boolean supports(String providerCode) {
        if (!enabled || providerCode == null || providerCode.isBlank()) {
            return false;
        }
        return providerCode.trim().toLowerCase().startsWith(prefix());
    }

    public long safePollIntervalMs() {
        return Math.max(1, pollIntervalMs);
    }

    private String prefix() {
        if (providerCodePrefix == null || providerCodePrefix.isBlank()) {
            return "spring-ai-";
        }
        return providerCodePrefix.trim().toLowerCase();
    }
}
