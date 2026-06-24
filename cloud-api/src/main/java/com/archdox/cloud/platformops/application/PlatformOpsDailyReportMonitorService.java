package com.archdox.cloud.platformops.application;

import com.archdox.cloud.platformops.domain.PlatformOpsRunStatus;
import com.archdox.cloud.platformops.domain.PlatformOpsRunTriggerType;
import com.archdox.cloud.platformops.infra.PlatformOpsRunRepository;
import java.time.Duration;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformOpsDailyReportMonitorService {
    private final PlatformOpsDailyReportProperties properties;
    private final PlatformOpsRunRepository runRepository;
    private final PlatformOpsDailyReportService reportService;

    public PlatformOpsDailyReportMonitorService(
            PlatformOpsDailyReportProperties properties,
            PlatformOpsRunRepository runRepository,
            PlatformOpsDailyReportService reportService
    ) {
        this.properties = properties;
        this.runRepository = runRepository;
        this.reportService = reportService;
    }

    @Transactional
    public PlatformOpsDailyReportDecision checkAndRequestIfDue(OffsetDateTime now) {
        if (!properties.isEnabled()) {
            return PlatformOpsDailyReportDecision.skipped("MONITOR_DISABLED", null);
        }
        var dueAt = latestDueAt(now);
        if (Duration.between(dueAt, now).toMinutes() > properties.safeCatchUpGraceMinutes()) {
            return PlatformOpsDailyReportDecision.skipped("OUTSIDE_CATCH_UP_WINDOW", dueAt);
        }
        if (runRepository.existsByTriggerTypeInAndStatus(
                java.util.List.of(
                        PlatformOpsRunTriggerType.AUTO_DAILY_REPORT,
                        PlatformOpsRunTriggerType.MANUAL_DAILY_REPORT),
                PlatformOpsRunStatus.RUNNING)) {
            return PlatformOpsDailyReportDecision.skipped("REPORT_ALREADY_RUNNING", dueAt);
        }
        var latest = runRepository.findFirstByTriggerTypeOrderByStartedAtDescIdDesc(
                PlatformOpsRunTriggerType.AUTO_DAILY_REPORT).orElse(null);
        if (latest != null && !latest.startedAt().isBefore(dueAt)) {
            return PlatformOpsDailyReportDecision.skipped("DUE_SLOT_ALREADY_HANDLED", dueAt);
        }
        var run = reportService.requestAutoDailyReport(dueAt, now);
        return PlatformOpsDailyReportDecision.requested(dueAt, run.id());
    }

    private OffsetDateTime latestDueAt(OffsetDateTime now) {
        var zone = ZoneId.of(properties.getZoneId());
        var localNow = now.atZoneSameInstant(zone).toLocalDateTime();
        var runTime = LocalTime.parse(properties.getRunTime());
        var todayDue = localNow.toLocalDate().atTime(runTime).atZone(zone).toOffsetDateTime();
        if (!todayDue.isAfter(now)) {
            return todayDue;
        }
        return localNow.toLocalDate().minusDays(1).atTime(runTime).atZone(zone).toOffsetDateTime();
    }
}
