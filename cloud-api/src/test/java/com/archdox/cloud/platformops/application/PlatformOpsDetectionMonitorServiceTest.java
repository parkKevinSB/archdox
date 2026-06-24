package com.archdox.cloud.platformops.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.archdox.cloud.platformops.domain.PlatformOpsRun;
import com.archdox.cloud.platformops.domain.PlatformOpsRunStatus;
import com.archdox.cloud.platformops.domain.PlatformOpsRunTriggerType;
import com.archdox.cloud.platformops.infra.PlatformOpsRunRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlatformOpsDetectionMonitorServiceTest {
    private final PlatformOpsDetectionProperties properties = new PlatformOpsDetectionProperties();
    private final PlatformOpsRunRepository runRepository = mock(PlatformOpsRunRepository.class);
    private final PlatformOpsDetectionService detectionService = mock(PlatformOpsDetectionService.class);

    @Test
    void autoMonitorRequestsDetectionWhenEnabledAndNoRunIsActive() {
        var run = mock(PlatformOpsRun.class);
        when(run.id()).thenReturn(42L);
        when(detectionService.requestAutoStuckDetection(any())).thenReturn(run);

        var decision = service().checkAndRequestIfDue(OffsetDateTime.parse("2026-06-24T00:00:00Z"));

        assertEquals("REQUESTED", decision.status());
        assertEquals(42L, decision.opsRunId());
        verify(runRepository).existsByTriggerTypeInAndStatus(
                List.of(PlatformOpsRunTriggerType.AUTO_DETECT_STUCK, PlatformOpsRunTriggerType.MANUAL_DETECT_STUCK),
                PlatformOpsRunStatus.RUNNING);
        verify(detectionService).requestAutoStuckDetection(any());
    }

    @Test
    void autoMonitorSkipsWhenDetectionRunIsAlreadyActive() {
        when(runRepository.existsByTriggerTypeInAndStatus(any(), any())).thenReturn(true);

        var decision = service().checkAndRequestIfDue(OffsetDateTime.parse("2026-06-24T00:00:00Z"));

        assertEquals("SKIPPED", decision.status());
        assertEquals("DETECTION_ALREADY_RUNNING", decision.reason());
        verify(detectionService, never()).requestAutoStuckDetection(any());
    }

    @Test
    void autoMonitorSkipsWhenDisabled() {
        properties.setEnabled(false);

        var decision = service().checkAndRequestIfDue(OffsetDateTime.parse("2026-06-24T00:00:00Z"));

        assertEquals("SKIPPED", decision.status());
        assertEquals("MONITOR_DISABLED", decision.reason());
        verify(detectionService, never()).requestAutoStuckDetection(any());
    }

    private PlatformOpsDetectionMonitorService service() {
        return new PlatformOpsDetectionMonitorService(properties, runRepository, detectionService);
    }
}
