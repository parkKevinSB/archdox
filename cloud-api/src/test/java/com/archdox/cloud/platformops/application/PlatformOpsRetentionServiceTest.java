package com.archdox.cloud.platformops.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.archdox.cloud.platformops.domain.PlatformOpsRunStatus;
import com.archdox.cloud.platformops.infra.PlatformOpsDailyReportRepository;
import com.archdox.cloud.platformops.infra.PlatformOpsFindingRepository;
import com.archdox.cloud.platformops.infra.PlatformOpsIncidentRepository;
import com.archdox.cloud.platformops.infra.PlatformOpsRunRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlatformOpsRetentionServiceTest {
    private final PlatformOpsRetentionProperties properties = new PlatformOpsRetentionProperties();
    private final PlatformOpsDailyReportRepository dailyReportRepository = mock(PlatformOpsDailyReportRepository.class);
    private final PlatformOpsFindingRepository findingRepository = mock(PlatformOpsFindingRepository.class);
    private final PlatformOpsIncidentRepository incidentRepository = mock(PlatformOpsIncidentRepository.class);
    private final PlatformOpsRunRepository runRepository = mock(PlatformOpsRunRepository.class);

    @Test
    void purgesPlatformOpsDataOlderThanRetentionDaysInReferenceSafeOrder() {
        var now = OffsetDateTime.parse("2026-06-25T00:00:00Z");
        var cutoff = OffsetDateTime.parse("2026-05-26T00:00:00Z");
        when(dailyReportRepository.deleteCreatedBefore(cutoff)).thenReturn(3);
        when(findingRepository.deleteCreatedBefore(cutoff)).thenReturn(20);
        when(incidentRepository.deleteStaleUnreferencedBefore(cutoff)).thenReturn(4);
        when(runRepository.deleteUnreferencedTerminalRunsBefore(
                cutoff,
                List.of(PlatformOpsRunStatus.COMPLETED, PlatformOpsRunStatus.FAILED))).thenReturn(6);

        var result = service().purgeExpired(now);

        assertThat(result.enabled()).isTrue();
        assertThat(result.retentionDays()).isEqualTo(30);
        assertThat(result.cutoff()).isEqualTo(cutoff);
        assertThat(result.deletedDailyReports()).isEqualTo(3);
        assertThat(result.deletedFindings()).isEqualTo(20);
        assertThat(result.deletedIncidents()).isEqualTo(4);
        assertThat(result.deletedRuns()).isEqualTo(6);
        assertThat(result.totalDeleted()).isEqualTo(33);
        var inOrder = org.mockito.Mockito.inOrder(
                dailyReportRepository,
                findingRepository,
                incidentRepository,
                runRepository);
        inOrder.verify(dailyReportRepository).deleteCreatedBefore(cutoff);
        inOrder.verify(findingRepository).deleteCreatedBefore(cutoff);
        inOrder.verify(incidentRepository).deleteStaleUnreferencedBefore(cutoff);
        inOrder.verify(runRepository).deleteUnreferencedTerminalRunsBefore(
                cutoff,
                List.of(PlatformOpsRunStatus.COMPLETED, PlatformOpsRunStatus.FAILED));
    }

    @Test
    void disabledRetentionDoesNotDeleteAnything() {
        properties.setEnabled(false);
        var now = OffsetDateTime.parse("2026-06-25T00:00:00Z");

        var result = service().purgeExpired(now);

        assertThat(result.enabled()).isFalse();
        assertThat(result.retentionDays()).isEqualTo(30);
        assertThat(result.cutoff()).isEqualTo(OffsetDateTime.parse("2026-05-26T00:00:00Z"));
        assertThat(result.totalDeleted()).isZero();
        verify(dailyReportRepository, never()).deleteCreatedBefore(result.cutoff());
        verify(findingRepository, never()).deleteCreatedBefore(result.cutoff());
        verify(incidentRepository, never()).deleteStaleUnreferencedBefore(result.cutoff());
        verify(runRepository, never()).deleteUnreferencedTerminalRunsBefore(
                result.cutoff(),
                List.of(PlatformOpsRunStatus.COMPLETED, PlatformOpsRunStatus.FAILED));
    }

    @Test
    void checkIntervalAndRetentionDaysHaveSafeMinimums() {
        properties.setRetentionDays(0);
        properties.setCheckIntervalMs(1);

        assertThat(service().purgeExpired(OffsetDateTime.parse("2026-06-25T00:00:00Z")).retentionDays())
                .isEqualTo(1);
        assertThat(service().checkIntervalMs()).isEqualTo(60_000);
    }

    private PlatformOpsRetentionService service() {
        return new PlatformOpsRetentionService(
                PlatformOpsAutomationSettingsTestSupport.service(
                        new PlatformOpsDetectionProperties(),
                        new PlatformOpsDailyReportProperties(),
                        properties),
                dailyReportRepository,
                findingRepository,
                incidentRepository,
                runRepository);
    }
}
