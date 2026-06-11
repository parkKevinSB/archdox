package com.archdox.cloud.platformops.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "archdox.platform-ops.daily-report")
public class PlatformOpsDailyReportProperties {
    private boolean enabled = false;
    private String runTime = "00:00";
    private String zoneId = "Asia/Seoul";
    private long checkIntervalMs = 60_000;
    private long catchUpGraceMinutes = 180;
    private String reportDirectory = "build/ops-reports";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getRunTime() {
        return runTime;
    }

    public void setRunTime(String runTime) {
        this.runTime = runTime == null || runTime.isBlank() ? "00:00" : runTime;
    }

    public String getZoneId() {
        return zoneId;
    }

    public void setZoneId(String zoneId) {
        this.zoneId = zoneId == null || zoneId.isBlank() ? "Asia/Seoul" : zoneId;
    }

    public long getCheckIntervalMs() {
        return checkIntervalMs;
    }

    public void setCheckIntervalMs(long checkIntervalMs) {
        this.checkIntervalMs = checkIntervalMs;
    }

    public long getCatchUpGraceMinutes() {
        return catchUpGraceMinutes;
    }

    public void setCatchUpGraceMinutes(long catchUpGraceMinutes) {
        this.catchUpGraceMinutes = catchUpGraceMinutes;
    }

    public String getReportDirectory() {
        return reportDirectory;
    }

    public void setReportDirectory(String reportDirectory) {
        this.reportDirectory = reportDirectory == null || reportDirectory.isBlank()
                ? "build/ops-reports"
                : reportDirectory;
    }

    public long safeCheckIntervalMs() {
        return Math.max(1_000, checkIntervalMs);
    }

    public long safeCatchUpGraceMinutes() {
        return Math.max(0, catchUpGraceMinutes);
    }
}
