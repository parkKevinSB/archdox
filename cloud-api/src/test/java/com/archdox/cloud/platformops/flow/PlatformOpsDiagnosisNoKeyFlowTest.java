package com.archdox.cloud.platformops.flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.archdox.cloud.aipolicy.application.AiHarnessPolicyExecutionService;
import com.archdox.cloud.aipolicy.application.AiHarnessPolicyResolution;
import com.archdox.cloud.aipolicy.domain.AiHarnessPolicyKey;
import com.archdox.cloud.operation.application.OperationEventService;
import com.archdox.cloud.operation.domain.OperationEvent;
import com.archdox.cloud.operation.domain.OperationEventSeverity;
import com.archdox.cloud.operation.infra.OperationEventRepository;
import com.archdox.cloud.platformadmin.application.PlatformAdminService;
import com.archdox.cloud.platformops.application.PlatformOpsAiDiagnosisFindingSink;
import com.archdox.cloud.platformops.application.PlatformOpsAiDiagnosisRunStore;
import com.archdox.cloud.platformops.application.PlatformOpsDiagnosisService;
import com.archdox.cloud.platformops.domain.PlatformOpsFinding;
import com.archdox.cloud.platformops.domain.PlatformOpsFindingSeverity;
import com.archdox.cloud.platformops.domain.PlatformOpsFindingSource;
import com.archdox.cloud.platformops.domain.PlatformOpsIncident;
import com.archdox.cloud.platformops.domain.PlatformOpsRun;
import com.archdox.cloud.platformops.domain.PlatformOpsRunStatus;
import com.archdox.cloud.platformops.domain.PlatformOpsRunTriggerType;
import com.archdox.cloud.platformops.event.PlatformOpsDiagnosisRequested;
import com.archdox.cloud.platformops.infra.PlatformOpsFindingRepository;
import com.archdox.cloud.platformops.infra.PlatformOpsIncidentRepository;
import com.archdox.cloud.platformops.infra.PlatformOpsRunRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.parkkevinsb.flower.ai.harness.gateway.AiModelGateway;
import io.github.parkkevinsb.flower.ai.harness.model.AiModelRequest;
import io.github.parkkevinsb.flower.core.engine.Engine;
import io.github.parkkevinsb.flower.core.worker.Worker;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

class PlatformOpsDiagnosisNoKeyFlowTest {
    @Test
    void completesDeterministicDiagnosisWithoutCallingAiWhenNoProviderIsConfigured() {
        var platformAdminService = mock(PlatformAdminService.class);
        var runRepository = mock(PlatformOpsRunRepository.class);
        var incidentRepository = mock(PlatformOpsIncidentRepository.class);
        var findingRepository = mock(PlatformOpsFindingRepository.class);
        var operationEventRepository = mock(OperationEventRepository.class);
        var operationEventService = mock(OperationEventService.class);
        var aiRunStore = mock(PlatformOpsAiDiagnosisRunStore.class);
        var aiFindingSink = mock(PlatformOpsAiDiagnosisFindingSink.class);
        var aiModelGateway = mock(AiModelGateway.class);
        var aiWorker = mock(PlatformOpsAiDiagnosisWorker.class);
        var policyExecutionService = mock(AiHarnessPolicyExecutionService.class);
        var now = OffsetDateTime.parse("2026-05-29T10:00:00+09:00");
        var incident = incident(55L, now.minusMinutes(10));
        var run = new PlatformOpsRun(
                PlatformOpsRunTriggerType.MANUAL_DIAGNOSIS,
                1L,
                Map.of("state", "REQUESTED"),
                now);
        ReflectionTestUtils.setField(run, "id", 77L);
        run.attachIncident(55L);
        var finding = finding(now);
        var event = event(now);

        when(runRepository.findById(77L)).thenReturn(Optional.of(run));
        when(incidentRepository.findById(55L)).thenReturn(Optional.of(incident));
        when(findingRepository.findByIncidentIdOrderByCreatedAtDescIdDesc(eq(55L), any(Pageable.class)))
                .thenReturn(List.of(finding));
        when(operationEventRepository.searchPlatformEvents(eq(7L), isNull(), isNull(), isNull(), eq("DOCUMENT_JOB"), eq("99"), any(Pageable.class)))
                .thenReturn(List.of(event));
        when(findingRepository.save(any(PlatformOpsFinding.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(findingRepository.countByRunIdAndSource(77L, PlatformOpsFindingSource.AI_HARNESS)).thenReturn(0L);
        when(policyExecutionService.resolve(AiHarnessPolicyKey.PLATFORM_OPS_DIAGNOSIS))
                .thenReturn(AiHarnessPolicyResolution.unavailable(
                        AiHarnessPolicyKey.PLATFORM_OPS_DIAGNOSIS,
                        "DISABLED_OR_NOT_CONFIGURED"));

        var service = new PlatformOpsDiagnosisService(
                platformAdminService,
                runRepository,
                incidentRepository,
                findingRepository,
                operationEventRepository,
                operationEventService,
                policyExecutionService,
                aiRunStore,
                aiFindingSink,
                aiModelGateway,
                new ObjectMapper(),
                new io.github.parkkevinsb.flower.ai.harness.spi.TraceListener() {});
        var worker = Worker.builder("platform-ops-test").build();
        var engine = Engine.builder()
                .worker(worker)
                .build();
        engine.attach();

        worker.submit(new PlatformOpsDiagnosisFlowFactory(service, aiWorker)
                .create(new PlatformOpsDiagnosisRequested(77L, 55L, 1L)));
        for (int i = 0; i < 4; i++) {
            worker.tickOnce();
        }

        assertEquals(PlatformOpsRunStatus.COMPLETED, run.status());
        assertNull(run.aiHarnessRunId());
        assertEquals("SNAPSHOT_READY", run.inputSnapshotJson().get("state"));
        var nextAiHarness = (Map<?, ?>) run.inputSnapshotJson().get("nextAiHarness");
        assertEquals("SKIPPED", nextAiHarness.get("status"));
        assertEquals("DISABLED_OR_NOT_CONFIGURED", nextAiHarness.get("reason"));
        verify(aiWorker, never()).submit(any());
        verify(aiModelGateway, never()).submit(any(AiModelRequest.class));
        verify(operationEventService).record(
                eq(7L),
                eq(OperationEventSeverity.INFO),
                eq("OPS_DIAGNOSIS_AI_HARNESS_SKIPPED"),
                eq("platform-ops-diagnosis"),
                eq("77"),
                eq("PLATFORM_OPS_RUN"),
                eq(77L),
                eq(1L),
                isNull(),
                eq("Platform ops AI diagnosis harness was skipped."),
                anyMap());
    }

    private PlatformOpsIncident incident(Long id, OffsetDateTime now) {
        var incident = new PlatformOpsIncident(
                PlatformOpsFindingSeverity.WARN,
                "DOCUMENT_JOB_STUCK",
                "Document job appears stuck",
                "The job has not progressed.",
                7L,
                "DOCUMENT_JOB",
                "99",
                10L,
                now);
        ReflectionTestUtils.setField(incident, "id", id);
        return incident;
    }

    private PlatformOpsFinding finding(OffsetDateTime now) {
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
        return finding;
    }

    private OperationEvent event(OffsetDateTime now) {
        return new OperationEvent(
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
    }
}
