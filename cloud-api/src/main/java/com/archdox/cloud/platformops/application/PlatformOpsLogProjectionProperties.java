package com.archdox.cloud.platformops.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "archdox.platform-ops.log-projection")
public class PlatformOpsLogProjectionProperties {
    private boolean enabled = true;
    private String sourceCode = "cloud-api";
    private String logFilePath = "logs/archdox-cloud-api/archdox-cloud-api.log";
    private long maxBytesPerScan = 262_144;
    private int maxEventsPerScan = 25;
    private int maxMessageLength = 700;
    private boolean tailOnFirstScan = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public void setSourceCode(String sourceCode) {
        this.sourceCode = sourceCode;
    }

    public String getLogFilePath() {
        return logFilePath;
    }

    public void setLogFilePath(String logFilePath) {
        this.logFilePath = logFilePath;
    }

    public long getMaxBytesPerScan() {
        return maxBytesPerScan;
    }

    public void setMaxBytesPerScan(long maxBytesPerScan) {
        this.maxBytesPerScan = maxBytesPerScan;
    }

    public int getMaxEventsPerScan() {
        return maxEventsPerScan;
    }

    public void setMaxEventsPerScan(int maxEventsPerScan) {
        this.maxEventsPerScan = maxEventsPerScan;
    }

    public int getMaxMessageLength() {
        return maxMessageLength;
    }

    public void setMaxMessageLength(int maxMessageLength) {
        this.maxMessageLength = maxMessageLength;
    }

    public boolean isTailOnFirstScan() {
        return tailOnFirstScan;
    }

    public void setTailOnFirstScan(boolean tailOnFirstScan) {
        this.tailOnFirstScan = tailOnFirstScan;
    }

    public String safeSourceCode() {
        return sourceCode == null || sourceCode.isBlank() ? "cloud-api" : sourceCode.trim();
    }

    public long safeMaxBytesPerScan() {
        return Math.max(8_192, Math.min(maxBytesPerScan, 2_097_152));
    }

    public int safeMaxEventsPerScan() {
        return Math.max(1, Math.min(maxEventsPerScan, 100));
    }

    public int safeMaxMessageLength() {
        return Math.max(120, Math.min(maxMessageLength, 2_000));
    }
}
