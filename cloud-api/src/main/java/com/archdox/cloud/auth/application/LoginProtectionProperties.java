package com.archdox.cloud.auth.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "archdox.security.login-protection")
public class LoginProtectionProperties {
    private boolean enabled = true;
    private int maxFailuresPerEmail = 5;
    private int maxFailuresPerIp = 25;
    private Duration failureWindow = Duration.ofMinutes(15);
    private Duration lockDuration = Duration.ofMinutes(15);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxFailuresPerEmail() {
        return maxFailuresPerEmail;
    }

    public void setMaxFailuresPerEmail(int maxFailuresPerEmail) {
        this.maxFailuresPerEmail = maxFailuresPerEmail;
    }

    public int getMaxFailuresPerIp() {
        return maxFailuresPerIp;
    }

    public void setMaxFailuresPerIp(int maxFailuresPerIp) {
        this.maxFailuresPerIp = maxFailuresPerIp;
    }

    public Duration getFailureWindow() {
        return failureWindow;
    }

    public void setFailureWindow(Duration failureWindow) {
        this.failureWindow = failureWindow;
    }

    public Duration getLockDuration() {
        return lockDuration;
    }

    public void setLockDuration(Duration lockDuration) {
        this.lockDuration = lockDuration;
    }

    int safeMaxFailuresPerEmail() {
        return Math.max(1, maxFailuresPerEmail);
    }

    int safeMaxFailuresPerIp() {
        return Math.max(1, maxFailuresPerIp);
    }

    Duration safeFailureWindow() {
        return failureWindow == null ? Duration.ofMinutes(15) : failureWindow;
    }

    Duration safeLockDuration() {
        return lockDuration == null ? Duration.ofMinutes(15) : lockDuration;
    }
}
