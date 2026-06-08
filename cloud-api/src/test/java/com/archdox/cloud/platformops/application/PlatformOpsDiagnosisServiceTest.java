package com.archdox.cloud.platformops.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.aipolicy.application.AiHarnessPolicyExecutionService;
import com.archdox.cloud.aipolicy.application.AiHarnessPolicyResolution;
import com.archdox.cloud.aipolicy.domain.AiHarnessPolicyKey;
import com.archdox.cloud.operation.application.OperationEventService;
import com.archdox.cloud.operation.domain.OperationEvent;
import com.archdox.cloud.operation.domain.OperationEventSeverity;
import com.archdox.cloud.operation.infra.OperationEventRepository;
import com.archdox.cloud.platformadmin.application.PlatformAdminService;
import com.archdox.cloud.platformops.domain.PlatformOpsFinding;
import com.archdox.cloud.platformops.domain.PlatformOpsFindingSeverity;
import com.archdox.cloud.platformops.domain.PlatformOpsFindingSource;
import com.archdox.cloud.platformops.domain.PlatformOpsIncident;
import com.archdox.cloud.platformops.domain.PlatformOpsRun;
import com.archdox.cloud.platformops.domain.PlatformOpsRunStatus;
import com.archdox.cloud.platformops.domain.PlatformOpsRunTriggerType;
import com.archdox.cloud.platformops.infra.PlatformOpsFindingRepository;
import com.archdox.cloud.platformops.infra.PlatformOpsIncidentRepository;
import com.archdox.cloud.platformops.infra.PlatformOpsRunRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.parkkevinsb.flower.ai.harness.gateway.AiModelGateway;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

class PlatformOpsDiagnosisServiceTest {
    private final PlatformAdminService platformAdminService = mock(PlatformAdminService.class);
    private final PlatformOpsRunRepository runRepository = mock(PlatformOpsRunRepository.class);
    private final PlatformOpsIncidentRepository incidentRepository = mock(PlatformOpsIncidentRepository.class);
    private final PlatformOpsFindingRepository findingRepository = mock(PlatformOpsFindingRepository.class);
    private final OperationEventRepository operationEventRepository = mock(OperationEventRepository.class);
    private final OperationEventService operationEventService = mock(OperationEventService.class);
    private final AiHarnessPolicyExecutionService policyExecutionService = mock(AiHarnessPolicyExecutionService.class);
    private final PlatformOpsAiDiagnosisRunStore aiDiagnosisRunStore = mock(PlatformOpsAiDiagnosisRunStore.class);
    private final PlatformOpsAiDiagnosisFindingSink aiDiagnosisFindingSink = mock(PlatformOpsAiDiagnosisFindingSink.class);
    private final AiModelGateway aiModelGateway = mock(AiModelGateway.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void requestIncidentDiagnosisCreatesManualDiagnosisRun() {
        var service = service();
        var incident = incident(55L);
        when(incidentRepository.findById(55L)).thenReturn(Optional.of(incident));
        when(runRepository.save(any(PlatformOpsRun.class))).thenAnswer(invocation -> {
            PlatformOpsRun run = invocation.getArgument(0);
            ReflectionTestUtils.setField(run, "id", 77L);
            return run;
        });

        var run = service.requestIncidentDiagnosis(new UserPrincipal(1L, "admin@test.co.kr"), 55L);

        assertEquals(77L, run.id());
        assertEquals(55L, run.incidentId());
        assertEquals(PlatformOpsRunTriggerType.MANUAL_DIAGNOSIS, run.triggerType());
        assertEquals(PlatformOpsRunStatus.RUNNING, run.status());
        verify(platformAdminService).requirePlatformAdmin(any(UserPrincipal.class));
    }

    @Test
    void executeIncidentDiagnosisBuildsSnapshotAndFinding() {
        var service = service();
        var now = OffsetDateTime.parse("2026-05-29T10:00:00+09:00");
        var incident = incident(55L);
        var run = new PlatformOpsRun(
                PlatformOpsRunTriggerType.MANUAL_DIAGNOSIS,
                1L,
                Map.of("state", "REQUESTED"),
                now);
        ReflectionTestUtils.setField(run, "id", 77L);
        run.attachIncident(55L);
        var finding = new PlatformOpsFinding(
                55L,
                10L,
                7L,
                PlatformOpsFindingSeverity.WARN,
                PlatformOpsFindingSource.DETECTOR,
                "DOCUMENT_JOB_STUCK_DETECTED",
                "DOCUMENT_JOB_STUCK",
                "Document job appears stuck",
                "The job is still generating.",
                "DOCUMENT_JOB",
                "99",
                "document-generation",
                "99",
                Map.of("status", "GENERATING"),
                "Check the selected Agent.",
                now);
        ReflectionTestUtils.setField(finding, "id", 88L);
        var event = new OperationEvent(
                7L,
                OperationEventSeverity.WARN,
                "DOCUMENT_JOB_STUCK_DETECTED",
                "document-generation",
                "99",
                "DOCUMENT_JOB",
                "99",
                1L,
                "corr-1",
                "Document job appears stuck",
                Map.of("status", "GENERATING"),
                now);

        when(runRepository.findById(77L)).thenReturn(Optional.of(run));
        when(incidentRepository.findById(55L)).thenReturn(Optional.of(incident));
        when(findingRepository.findByIncidentIdOrderByCreatedAtDescIdDesc(eq(55L), any(Pageable.class)))
                .thenReturn(List.of(finding));
        when(operationEventRepository.searchPlatformEvents(eq(7L), isNull(), isNull(), isNull(), eq("DOCUMENT_JOB"), eq("99"), any(Pageable.class)))
                .thenReturn(List.of(event));
        when(findingRepository.save(any(PlatformOpsFinding.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var summary = service.executeIncidentDiagnosis(77L);

        assertEquals(77L, summary.opsRunId());
        assertEquals(55L, summary.incidentId());
        assertEquals(1, summary.findingCount());
        assertEquals(1, summary.operationEventCount());
        assertEquals(PlatformOpsRunStatus.COMPLETED, run.status());
        assertEquals("SNAPSHOT_READY", run.inputSnapshotJson().get("state"));
        verify(findingRepository).save(any(PlatformOpsFinding.class));
        verify(operationEventService).record(
                eq(7L),
                eq(OperationEventSeverity.INFO),
                eq("OPS_DIAGNOSIS_SNAPSHOT_READY"),
                eq("platform-ops-diagnosis"),
                eq("77"),
                eq("PLATFORM_OPS_INCIDENT"),
                eq(55L),
                eq(1L),
                isNull(),
                eq("Platform ops diagnosis snapshot is ready"),
                anyMap());
    }

    @Test
    void createAiDiagnosisHarnessFlowSkipsWhenDisabled() {
        var service = service();
        var now = OffsetDateTime.parse("2026-05-29T10:00:00+09:00");
        var incident = incident(55L);
        var run = new PlatformOpsRun(
                PlatformOpsRunTriggerType.MANUAL_DIAGNOSIS,
                1L,
                Map.of("state", "SNAPSHOT_READY"),
                now);
        ReflectionTestUtils.setField(run, "id", 77L);
        run.attachIncident(55L);

        when(runRepository.findById(77L)).thenReturn(Optional.of(run));
        when(incidentRepository.findById(55L)).thenReturn(Optional.of(incident));
        when(policyExecutionService.resolve(AiHarnessPolicyKey.PLATFORM_OPS_DIAGNOSIS))
                .thenReturn(AiHarnessPolicyResolution.unavailable(
                        AiHarnessPolicyKey.PLATFORM_OPS_DIAGNOSIS,
                        "DISABLED_OR_NOT_CONFIGURED"));

        var flow = service.createAiDiagnosisHarnessFlow(77L);

        assertTrue(flow.isEmpty());
        var nextAiHarness = (Map<?, ?>) run.inputSnapshotJson().get("nextAiHarness");
        assertEquals("SKIPPED", nextAiHarness.get("status"));
        assertEquals("DISABLED_OR_NOT_CONFIGURED", nextAiHarness.get("reason"));
    }

    private PlatformOpsDiagnosisService service() {
        return new PlatformOpsDiagnosisService(
                platformAdminService,
                runRepository,
                incidentRepository,
                findingRepository,
                operationEventRepository,
                operationEventService,
                policyExecutionService,
                aiDiagnosisRunStore,
                aiDiagnosisFindingSink,
                aiModelGateway,
                objectMapper,
                new io.github.parkkevinsb.flower.ai.harness.spi.TraceListener() {});
    }

    private PlatformOpsIncident incident(Long id) {
        var incident = new PlatformOpsIncident(
                PlatformOpsFindingSeverity.WARN,
                "DOCUMENT_JOB_STUCK",
                "Document job appears stuck",
                "The job has not progressed.",
                7L,
                "DOCUMENT_JOB",
                "99",
                10L,
                OffsetDateTime.parse("2026-05-29T09:00:00+09:00"));
        ReflectionTestUtils.setField(incident, "id", id);
        return incident;
    }
}
