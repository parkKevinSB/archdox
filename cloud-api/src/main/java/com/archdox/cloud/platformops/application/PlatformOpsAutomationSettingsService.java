package com.archdox.cloud.platformops.application;

import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.platformops.domain.PlatformOpsAutomationSettings;
import com.archdox.cloud.platformops.domain.PlatformOpsFindingSeverity;
import com.archdox.cloud.platformops.dto.PlatformOpsAutomationSettingsResponse;
import com.archdox.cloud.platformops.dto.UpdatePlatformOpsAutomationSettingsRequest;
import com.archdox.cloud.platformops.infra.PlatformOpsAutomationSettingsRepository;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformOpsAutomationSettingsService {
    private final PlatformOpsDetectionProperties detectionProperties;
    private final PlatformOpsDailyReportProperties dailyReportProperties;
    private final PlatformOpsRetentionProperties retentionProperties;
    private final PlatformOpsAutomationSettingsRepository repository;

    public PlatformOpsAutomationSettingsService(
            PlatformOpsDetectionProperties detectionProperties,
            PlatformOpsDailyReportProperties dailyReportProperties,
            PlatformOpsRetentionProperties retentionProperties,
            PlatformOpsAutomationSettingsRepository repository
    ) {
        this.detectionProperties = detectionProperties;
        this.dailyReportProperties = dailyReportProperties;
        this.retentionProperties = retentionProperties;
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PlatformOpsAutomationSettingsResponse settings() {
        return repository.findById(PlatformOpsAutomationSettings.SINGLETON_KEY)
                .map(PlatformOpsAutomationSettingsResponse::from)
                .orElseGet(() -> PlatformOpsAutomationSettingsResponse.fromDefaults(
                        detectionProperties,
                        dailyReportProperties,
                        retentionProperties));
    }

    @Transactional
    public PlatformOpsAutomationSettingsResponse updateSettings(
            UpdatePlatformOpsAutomationSettingsRequest request,
            Long updatedByUserId
    ) {
        var current = settings();
        var now = OffsetDateTime.now();
        var detectionEnabled = request.detectionEnabled() == null ? current.detectionEnabled() : request.detectionEnabled();
        var detectionCheckIntervalMs = normalizeMillis(request.detectionCheckIntervalMs(), current.detectionCheckIntervalMs(), 60_000L, 86_400_000L, "detectionCheckIntervalMs");
        var documentJobStuckMinutes = normalizeLong(request.documentJobStuckMinutes(), current.documentJobStuckMinutes(), 1, 1440, "documentJobStuckMinutes");
        var agentCommandStuckMinutes = normalizeLong(request.agentCommandStuckMinutes(), current.agentCommandStuckMinutes(), 1, 1440, "agentCommandStuckMinutes");
        var photoPickupStuckMinutes = normalizeLong(request.photoPickupStuckMinutes(), current.photoPickupStuckMinutes(), 1, 1440, "photoPickupStuckMinutes");
        var deliveryStuckMinutes = normalizeLong(request.deliveryStuckMinutes(), current.deliveryStuckMinutes(), 1, 1440, "deliveryStuckMinutes");
        var maxDetectedItems = normalizeInt(request.maxDetectedItems(), current.maxDetectedItems(), 1, 500, "maxDetectedItems");
        var dailyReportEnabled = request.dailyReportEnabled() == null ? current.dailyReportEnabled() : request.dailyReportEnabled();
        var dailyReportRunTime = normalizeRunTime(request.dailyReportRunTime(), current.dailyReportRunTime());
        var dailyReportZoneId = normalizeZoneId(request.dailyReportZoneId(), current.dailyReportZoneId());
        var dailyReportCheckIntervalMs = normalizeMillis(request.dailyReportCheckIntervalMs(), current.dailyReportCheckIntervalMs(), 60_000L, 86_400_000L, "dailyReportCheckIntervalMs");
        var dailyReportCatchUpGraceMinutes = normalizeLong(request.dailyReportCatchUpGraceMinutes(), current.dailyReportCatchUpGraceMinutes(), 0, 1440, "dailyReportCatchUpGraceMinutes");
        var dailyReportAutoDiagnosisEnabled = request.dailyReportAutoDiagnosisEnabled() == null
                ? current.dailyReportAutoDiagnosisEnabled()
                : request.dailyReportAutoDiagnosisEnabled();
        var dailyReportAutoDiagnosisIncidentLimit = normalizeInt(
                request.dailyReportAutoDiagnosisIncidentLimit(),
                current.dailyReportAutoDiagnosisIncidentLimit(),
                0,
                5,
                "dailyReportAutoDiagnosisIncidentLimit");
        var dailyReportAutoDiagnosisMinSeverity = normalizeSeverity(
                request.dailyReportAutoDiagnosisMinSeverity(),
                current.dailyReportAutoDiagnosisMinSeverity());
        var dailyReportDirectory = normalizeDirectory(
                request.dailyReportDirectory(),
                current.dailyReportDirectory());
        var retentionEnabled = request.retentionEnabled() == null ? current.retentionEnabled() : request.retentionEnabled();
        var retentionDays = normalizeInt(request.retentionDays(), current.retentionDays(), 1, 365, "retentionDays");
        var retentionCheckIntervalMs = normalizeMillis(request.retentionCheckIntervalMs(), current.retentionCheckIntervalMs(), 60_000L, 86_400_000L, "retentionCheckIntervalMs");

        var settings = repository.findById(PlatformOpsAutomationSettings.SINGLETON_KEY)
                .orElseGet(() -> new PlatformOpsAutomationSettings(
                        detectionEnabled,
                        detectionCheckIntervalMs,
                        documentJobStuckMinutes,
                        agentCommandStuckMinutes,
                        photoPickupStuckMinutes,
                        deliveryStuckMinutes,
                        maxDetectedItems,
                        dailyReportEnabled,
                        dailyReportRunTime,
                        dailyReportZoneId,
                        dailyReportCheckIntervalMs,
                        dailyReportCatchUpGraceMinutes,
                        dailyReportAutoDiagnosisEnabled,
                        dailyReportAutoDiagnosisIncidentLimit,
                        dailyReportAutoDiagnosisMinSeverity,
                        dailyReportDirectory,
                        retentionEnabled,
                        retentionDays,
                        retentionCheckIntervalMs,
                        updatedByUserId,
                        now));
        settings.update(
                detectionEnabled,
                detectionCheckIntervalMs,
                documentJobStuckMinutes,
                agentCommandStuckMinutes,
                photoPickupStuckMinutes,
                deliveryStuckMinutes,
                maxDetectedItems,
                dailyReportEnabled,
                dailyReportRunTime,
                dailyReportZoneId,
                dailyReportCheckIntervalMs,
                dailyReportCatchUpGraceMinutes,
                dailyReportAutoDiagnosisEnabled,
                dailyReportAutoDiagnosisIncidentLimit,
                dailyReportAutoDiagnosisMinSeverity,
                dailyReportDirectory,
                retentionEnabled,
                retentionDays,
                retentionCheckIntervalMs,
                updatedByUserId,
                now);
        return PlatformOpsAutomationSettingsResponse.from(repository.save(settings));
    }

    private long normalizeMillis(Long value, long fallback, long min, long max, String field) {
        if (value == null) {
            return fallback;
        }
        if (value < min || value > max) {
            throw new BadRequestException(field + " must be between " + min + " and " + max + " ms.");
        }
        return value;
    }

    private long normalizeLong(Long value, long fallback, long min, long max, String field) {
        if (value == null) {
            return fallback;
        }
        if (value < min || value > max) {
            throw new BadRequestException(field + " must be between " + min + " and " + max + ".");
        }
        return value;
    }

    private int normalizeInt(Integer value, int fallback, int min, int max, String field) {
        if (value == null) {
            return fallback;
        }
        if (value < min || value > max) {
            throw new BadRequestException(field + " must be between " + min + " and " + max + ".");
        }
        return value;
    }

    private String normalizeRunTime(String value, String fallback) {
        var normalized = blankToNull(value);
        if (normalized == null) {
            return fallback;
        }
        try {
            return LocalTime.parse(normalized).toString();
        } catch (RuntimeException ex) {
            throw new BadRequestException("dailyReportRunTime must use HH:mm format.");
        }
    }

    private String normalizeZoneId(String value, String fallback) {
        var normalized = blankToNull(value);
        if (normalized == null) {
            return fallback;
        }
        try {
            ZoneId.of(normalized);
            return normalized;
        } catch (RuntimeException ex) {
            throw new BadRequestException("dailyReportZoneId is not a valid time zone.");
        }
    }

    private String normalizeSeverity(String value, String fallback) {
        var normalized = blankToNull(value);
        if (normalized == null) {
            return fallback;
        }
        var severity = normalized.toUpperCase(Locale.ROOT);
        try {
            PlatformOpsFindingSeverity.valueOf(severity);
            return severity;
        } catch (RuntimeException ex) {
            throw new BadRequestException("dailyReportAutoDiagnosisMinSeverity is invalid.");
        }
    }

    private String normalizeDirectory(String value, String fallback) {
        var normalized = blankToNull(value);
        if (normalized == null) {
            return fallback;
        }
        if (normalized.length() > 500) {
            throw new BadRequestException("dailyReportDirectory is too long.");
        }
        return normalized;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
