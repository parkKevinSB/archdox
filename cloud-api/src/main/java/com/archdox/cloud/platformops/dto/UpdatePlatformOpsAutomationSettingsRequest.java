package com.archdox.cloud.platformops.dto;

public record UpdatePlatformOpsAutomationSettingsRequest(
        Boolean detectionEnabled,
        Long detectionCheckIntervalMs,
        Long documentJobStuckMinutes,
        Long agentCommandStuckMinutes,
        Long photoPickupStuckMinutes,
        Long deliveryStuckMinutes,
        Integer maxDetectedItems,
        Boolean dailyReportEnabled,
        String dailyReportRunTime,
        String dailyReportZoneId,
        Long dailyReportCheckIntervalMs,
        Long dailyReportCatchUpGraceMinutes,
        Boolean dailyReportAutoDiagnosisEnabled,
        Integer dailyReportAutoDiagnosisIncidentLimit,
        String dailyReportAutoDiagnosisMinSeverity,
        String dailyReportDirectory,
        Boolean retentionEnabled,
        Integer retentionDays,
        Long retentionCheckIntervalMs
) {
}
