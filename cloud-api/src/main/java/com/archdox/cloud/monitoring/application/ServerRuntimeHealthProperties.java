package com.archdox.cloud.monitoring.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "archdox.server-runtime.health")
public class ServerRuntimeHealthProperties {
    private boolean enabled = true;
    private long checkIntervalMs = 300_000;
    private double cpuWarnPercent = 85.0d;
    private double systemMemoryWarnPercent = 90.0d;
    private double jvmHeapWarnPercent = 90.0d;
    private long eventCooldownMs = 900_000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getCheckIntervalMs() {
        return checkIntervalMs;
    }

    public void setCheckIntervalMs(long checkIntervalMs) {
        this.checkIntervalMs = checkIntervalMs;
    }

    public double getCpuWarnPercent() {
        return cpuWarnPercent;
    }

    public void setCpuWarnPercent(double cpuWarnPercent) {
        this.cpuWarnPercent = cpuWarnPercent;
    }

    public double getSystemMemoryWarnPercent() {
        return systemMemoryWarnPercent;
    }

    public void setSystemMemoryWarnPercent(double systemMemoryWarnPercent) {
        this.systemMemoryWarnPercent = systemMemoryWarnPercent;
    }

    public double getJvmHeapWarnPercent() {
        return jvmHeapWarnPercent;
    }

    public void setJvmHeapWarnPercent(double jvmHeapWarnPercent) {
        this.jvmHeapWarnPercent = jvmHeapWarnPercent;
    }

    public long getEventCooldownMs() {
        return eventCooldownMs;
    }

    public void setEventCooldownMs(long eventCooldownMs) {
        this.eventCooldownMs = eventCooldownMs;
    }

    public long safeCheckIntervalMs() {
        return Math.max(30_000, checkIntervalMs);
    }

    public long safeEventCooldownMs() {
        return Math.max(60_000, eventCooldownMs);
    }
}
