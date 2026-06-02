package com.archdox.cloud.aipolicy.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "archdox.ai.observation")
public class AiObservationProperties {
    private boolean enabled = false;
    private int maxEntries = 100;
    private int ttlMinutes = 60;
    private int maxPromptChars = 40_000;
    private int maxResponseChars = 40_000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxEntries() {
        return maxEntries;
    }

    public void setMaxEntries(int maxEntries) {
        this.maxEntries = maxEntries;
    }

    public int getTtlMinutes() {
        return ttlMinutes;
    }

    public void setTtlMinutes(int ttlMinutes) {
        this.ttlMinutes = ttlMinutes;
    }

    public int getMaxPromptChars() {
        return maxPromptChars;
    }

    public void setMaxPromptChars(int maxPromptChars) {
        this.maxPromptChars = maxPromptChars;
    }

    public int getMaxResponseChars() {
        return maxResponseChars;
    }

    public void setMaxResponseChars(int maxResponseChars) {
        this.maxResponseChars = maxResponseChars;
    }

    public int safeMaxEntries() {
        return Math.max(1, Math.min(maxEntries, 500));
    }

    public int safeTtlMinutes() {
        return Math.max(1, Math.min(ttlMinutes, 24 * 60));
    }

    public int safeMaxPromptChars() {
        return Math.max(1_000, Math.min(maxPromptChars, 200_000));
    }

    public int safeMaxResponseChars() {
        return Math.max(1_000, Math.min(maxResponseChars, 200_000));
    }
}
