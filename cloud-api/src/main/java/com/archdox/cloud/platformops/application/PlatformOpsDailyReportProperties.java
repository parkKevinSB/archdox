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
    private boolean autoDiagnosisEnabled = true;
    private int autoDiagnosisIncidentLimit = 5;
    private String autoDiagnosisMinSeverity = "WARN";

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

    public boolean isAutoDiagnosisEnabled() {
        return autoDiagnosisEnabled;
    }

    public void setAutoDiagnosisEnabled(boolean autoDiagnosisEnabled) {
        this.autoDiagnosisEnabled = autoDiagnosisEnabled;
    }

    public int getAutoDiagnosisIncidentLimit() {
        return autoDiagnosisIncidentLimit;
    }

    public void setAutoDiagnosisIncidentLimit(int autoDiagnosisIncidentLimit) {
        this.autoDiagnosisIncidentLimit = autoDiagnosisIncidentLimit;
    }

    public String getAutoDiagnosisMinSeverity() {
        return autoDiagnosisMinSeverity;
    }

    public void setAutoDiagnosisMinSeverity(String autoDiagnosisMinSeverity) {
        this.autoDiagnosisMinSeverity = autoDiagnosisMinSeverity == null || autoDiagnosisMinSeverity.isBlank()
                ? "WARN"
                : autoDiagnosisMinSeverity.trim().toUpperCase(java.util.Locale.ROOT);
    }

    public long safeCheckIntervalMs() {
        return Math.max(1_000, checkIntervalMs);
    }

    public long safeCatchUpGraceMinutes() {
        return Math.max(0, catchUpGraceMinutes);
    }

    public int safeAutoDiagnosisIncidentLimit() {
        return Math.max(0, Math.min(autoDiagnosisIncidentLimit, 5));
    }
}
