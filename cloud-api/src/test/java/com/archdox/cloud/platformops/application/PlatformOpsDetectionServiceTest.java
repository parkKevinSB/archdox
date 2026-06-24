package com.archdox.cloud.platformops.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.archdox.cloud.operation.application.OperationEventService;
import com.archdox.cloud.operation.domain.OperationEventSeverity;
import com.archdox.cloud.platformops.domain.PlatformOpsFinding;
import com.archdox.cloud.platformops.domain.PlatformOpsFindingSeverity;
import com.archdox.cloud.platformops.domain.PlatformOpsIncident;
import com.archdox.cloud.platformops.domain.PlatformOpsRun;
import com.archdox.cloud.platformops.infra.PlatformOpsFindingRepository;
import com.archdox.cloud.platformops.infra.PlatformOpsIncidentRepository;
import com.archdox.cloud.platformops.infra.PlatformOpsRunRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PlatformOpsDetectionServiceTest {
    private final PlatformOpsRunRepository runRepository = mock(PlatformOpsRunRepository.class);
    private final PlatformOpsIncidentRepository incidentRepository = mock(PlatformOpsIncidentRepository.class);
    private final PlatformOpsFindingRepository findingRepository = mock(PlatformOpsFindingRepository.class);
    private final OperationEventService operationEventService = mock(OperationEventService.class);

    @Test
    void detectionCreatesRunFindingIncidentAndOperationEvent() {
        var properties = new PlatformOpsDetectionProperties();
        var detector = new FixedDetector();
        var service = new PlatformOpsDetectionService(
                List.of(detector),
                PlatformOpsAutomationSettingsTestSupport.service(
                        properties,
                        new PlatformOpsDailyReportProperties(),
                        new PlatformOpsRetentionProperties()),
                runRepository,
                incidentRepository,
                findingRepository,
                operationEventService);

        when(runRepository.save(any(PlatformOpsRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(incidentRepository.findFirstByStatusInAndCategoryAndPrimaryResourceTypeAndPrimaryResourceIdOrderByLastSeenAtDesc(
                any(),
                eq("DOCUMENT_JOB_STUCK"),
                eq("DOCUMENT_JOB"),
                eq("99")))
                .thenReturn(Optional.empty());
        when(incidentRepository.save(any(PlatformOpsIncident.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(findingRepository.save(any(PlatformOpsFinding.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var summary = service.detectStuck(1L);

        assertEquals(1, summary.stuckDocumentJobs());
        assertEquals(1, summary.incidentCount());
        assertEquals(1, summary.findingCount());
        verify(runRepository).save(any(PlatformOpsRun.class));
        verify(incidentRepository).save(any(PlatformOpsIncident.class));
        verify(findingRepository).save(any(PlatformOpsFinding.class));
        verify(operationEventService).record(
                eq(7L),
                eq(OperationEventSeverity.WARN),
                eq("DOCUMENT_JOB_STUCK_DETECTED"),
                eq("document-generation"),
                eq("99"),
                eq("DOCUMENT_JOB"),
                eq("99"),
                eq(1L),
                isNull(),
                eq("Document job appears stuck"),
                anyMap());
    }

    private static class FixedDetector implements PlatformOpsDetector {
        @Override
        public String category() {
            return "DOCUMENT_JOB_STUCK";
        }

        @Override
        public List<PlatformOpsDetectionFinding> detect(PlatformOpsDetectionContext context) {
            return List.of(new PlatformOpsDetectionFinding(
                    7L,
                    PlatformOpsFindingSeverity.WARN,
                    category(),
                    "DOCUMENT_JOB_STUCK_DETECTED",
                    "Document job appears stuck",
                    "A document generation job has not progressed.",
                    "DOCUMENT_JOB",
                    "99",
                    "document-generation",
                    "99",
                    Map.of("status", "GENERATING"),
                    "Check the selected ArchDox Agent."));
        }
    }
}
