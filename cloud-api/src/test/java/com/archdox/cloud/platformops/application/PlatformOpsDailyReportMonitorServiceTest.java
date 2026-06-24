package com.archdox.cloud.platformops.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.archdox.cloud.platformops.domain.PlatformOpsRun;
import com.archdox.cloud.platformops.domain.PlatformOpsRunStatus;
import com.archdox.cloud.platformops.domain.PlatformOpsRunTriggerType;
import com.archdox.cloud.platformops.infra.PlatformOpsRunRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PlatformOpsDailyReportMonitorServiceTest {
    private final PlatformOpsDailyReportProperties properties = enabledProperties();
    private final PlatformOpsRunRepository runRepository = mock(PlatformOpsRunRepository.class);
    private final PlatformOpsDailyReportService reportService = mock(PlatformOpsDailyReportService.class);
    private final PlatformOpsDailyReportMonitorService service = new PlatformOpsDailyReportMonitorService(
            properties,
            runRepository,
            reportService);

    @Test
    void requestsDailyReportWhenDueSlotHasNotBeenHandled() {
        var now = OffsetDateTime.parse("2026-06-10T15:00:00Z");
        when(runRepository.existsByTriggerTypeInAndStatus(
                org.mockito.ArgumentMatchers.anyList(),
                eq(PlatformOpsRunStatus.RUNNING))).thenReturn(false);
        when(runRepository.findFirstByTriggerTypeOrderByStartedAtDescIdDesc(
                PlatformOpsRunTriggerType.AUTO_DAILY_REPORT)).thenReturn(Optional.empty());
        var run = mock(PlatformOpsRun.class);
        when(run.id()).thenReturn(21L);
        when(reportService.requestAutoDailyReport(any(), any())).thenReturn(run);

        var decision = service.checkAndRequestIfDue(now);

        assertThat(decision.status()).isEqualTo("REQUESTED");
        assertThat(decision.opsRunId()).isEqualTo(21L);
        verify(reportService).requestAutoDailyReport(any(), any());
    }

    @Test
    void skipsWhenLatestDailyReportAlreadyHandledDueSlot() {
        var now = OffsetDateTime.parse("2026-06-10T15:30:00Z");
        var latest = mock(PlatformOpsRun.class);
        when(runRepository.existsByTriggerTypeInAndStatus(
                org.mockito.ArgumentMatchers.anyList(),
                eq(PlatformOpsRunStatus.RUNNING))).thenReturn(false);
        when(latest.startedAt()).thenReturn(OffsetDateTime.parse("2026-06-10T15:05:00Z"));
        when(runRepository.findFirstByTriggerTypeOrderByStartedAtDescIdDesc(
                PlatformOpsRunTriggerType.AUTO_DAILY_REPORT)).thenReturn(Optional.of(latest));

        var decision = service.checkAndRequestIfDue(now);

        assertThat(decision.status()).isEqualTo("SKIPPED");
        assertThat(decision.reason()).isEqualTo("DUE_SLOT_ALREADY_HANDLED");
        verify(reportService, never()).requestAutoDailyReport(any(), any());
    }

    private PlatformOpsDailyReportProperties enabledProperties() {
        var props = new PlatformOpsDailyReportProperties();
        props.setEnabled(true);
        props.setRunTime("00:00");
        props.setCatchUpGraceMinutes(180);
        return props;
    }
}
