package com.archdox.cloud.agent.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "archdox.agent.connection-health")
public class ArchDoxAgentConnectionHealthProperties {
    private boolean enabled = true;
    private long heartbeatTimeoutMs = 90_000;
    private long checkIntervalMs = 10_000;
    private long workerIntervalMs = 1_000;
    private int maxSessionsPerCheck = 200;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getHeartbeatTimeoutMs() {
        return heartbeatTimeoutMs;
    }

    public void setHeartbeatTimeoutMs(long heartbeatTimeoutMs) {
        this.heartbeatTimeoutMs = heartbeatTimeoutMs;
    }

    public long getCheckIntervalMs() {
        return checkIntervalMs;
    }

    public void setCheckIntervalMs(long checkIntervalMs) {
        this.checkIntervalMs = checkIntervalMs;
    }

    public long getWorkerIntervalMs() {
        return workerIntervalMs;
    }

    public void setWorkerIntervalMs(long workerIntervalMs) {
        this.workerIntervalMs = workerIntervalMs;
    }

    public int getMaxSessionsPerCheck() {
        return maxSessionsPerCheck;
    }

    public void setMaxSessionsPerCheck(int maxSessionsPerCheck) {
        this.maxSessionsPerCheck = maxSessionsPerCheck;
    }

    public long safeHeartbeatTimeoutMs() {
        return Math.max(1_000, heartbeatTimeoutMs);
    }

    public long safeCheckIntervalMs() {
        return Math.max(1_000, checkIntervalMs);
    }

    public long safeWorkerIntervalMs() {
        return Math.max(100, workerIntervalMs);
    }

    public int safeMaxSessionsPerCheck() {
        return Math.max(1, maxSessionsPerCheck);
    }
}
