package com.archdox.cloud.platformops.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "archdox.platform-admin.ops.ai-diagnosis")
public class PlatformOpsAiDiagnosisProperties {
    private boolean enabled;
    private String providerCode;
    private String model;
    private int maxAttempts = 2;
    private long timeoutSeconds = 90;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProviderCode() {
        return providerCode;
    }

    public void setProviderCode(String providerCode) {
        this.providerCode = providerCode;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public long getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(long timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public boolean runnable() {
        return enabled && notBlank(providerCode) && notBlank(model);
    }

    public String providerCode() {
        return providerCode == null ? "" : providerCode.trim();
    }

    public String model() {
        return model == null ? "" : model.trim();
    }

    public int safeMaxAttempts() {
        return Math.max(1, maxAttempts);
    }

    public long safeTimeoutSeconds() {
        return Math.max(10, timeoutSeconds);
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
