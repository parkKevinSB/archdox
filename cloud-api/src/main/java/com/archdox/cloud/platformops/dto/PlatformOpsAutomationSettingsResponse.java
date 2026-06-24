package com.archdox.cloud.platformops.dto;

import com.archdox.cloud.platformops.application.PlatformOpsDailyReportProperties;
import com.archdox.cloud.platformops.application.PlatformOpsDetectionProperties;
import com.archdox.cloud.platformops.application.PlatformOpsRetentionProperties;
import com.archdox.cloud.platformops.domain.PlatformOpsAutomationSettings;
import java.time.OffsetDateTime;

public record PlatformOpsAutomationSettingsResponse(
        boolean detectionEnabled,
        long detectionCheckIntervalMs,
        long documentJobStuckMinutes,
        long agentCommandStuckMinutes,
        long photoPickupStuckMinutes,
        long deliveryStuckMinutes,
        int maxDetectedItems,
        boolean dailyReportEnabled,
        String dailyReportRunTime,
        String dailyReportZoneId,
        long dailyReportCheckIntervalMs,
        long dailyReportCatchUpGraceMinutes,
        boolean dailyReportAutoDiagnosisEnabled,
        int dailyReportAutoDiagnosisIncidentLimit,
        String dailyReportAutoDiagnosisMinSeverity,
        String dailyReportDirectory,
        boolean retentionEnabled,
        int retentionDays,
        long retentionCheckIntervalMs,
        Long updatedByUserId,
        OffsetDateTime updatedAt
) {
    public static PlatformOpsAutomationSettingsResponse from(PlatformOpsAutomationSettings settings) {
        return new PlatformOpsAutomationSettingsResponse(
                settings.detectionEnabled(),
                settings.detectionCheckIntervalMs(),
                settings.documentJobStuckMinutes(),
                settings.agentCommandStuckMinutes(),
                settings.photoPickupStuckMinutes(),
                settings.deliveryStuckMinutes(),
                settings.maxDetectedItems(),
                settings.dailyReportEnabled(),
                settings.dailyReportRunTime(),
                settings.dailyReportZoneId(),
                settings.dailyReportCheckIntervalMs(),
                settings.dailyReportCatchUpGraceMinutes(),
                settings.dailyReportAutoDiagnosisEnabled(),
                settings.dailyReportAutoDiagnosisIncidentLimit(),
                settings.dailyReportAutoDiagnosisMinSeverity(),
                settings.dailyReportDirectory(),
                settings.retentionEnabled(),
                settings.retentionDays(),
                settings.retentionCheckIntervalMs(),
                settings.updatedByUserId(),
                settings.updatedAt());
    }

    public static PlatformOpsAutomationSettingsResponse fromDefaults(
            PlatformOpsDetectionProperties detection,
            PlatformOpsDailyReportProperties dailyReport,
            PlatformOpsRetentionProperties retention
    ) {
        return new PlatformOpsAutomationSettingsResponse(
                detection.isEnabled(),
                detection.safeDetectionCheckIntervalMs(),
                Math.max(1, detection.getDocumentJobStuckMinutes()),
                Math.max(1, detection.getAgentCommandStuckMinutes()),
                Math.max(1, detection.getPhotoPickupStuckMinutes()),
                Math.max(1, detection.getDeliveryStuckMinutes()),
                Math.max(1, detection.getMaxDetectedItems()),
                dailyReport.isEnabled(),
                dailyReport.getRunTime(),
                dailyReport.getZoneId(),
                dailyReport.safeCheckIntervalMs(),
                dailyReport.safeCatchUpGraceMinutes(),
                dailyReport.isAutoDiagnosisEnabled(),
                dailyReport.safeAutoDiagnosisIncidentLimit(),
                dailyReport.getAutoDiagnosisMinSeverity(),
                dailyReport.getReportDirectory(),
                retention.isEnabled(),
                retention.safeRetentionDays(),
                retention.safeCheckIntervalMs(),
                null,
                null);
    }
}
