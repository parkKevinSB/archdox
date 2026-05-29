package com.archdox.cloud.platformops.application;

import com.archdox.cloud.global.api.NotFoundException;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.aipolicy.application.AiModelCallMetadata;
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
import com.archdox.cloud.platformops.domain.PlatformOpsRunTriggerType;
import com.archdox.opsai.OpsDiagnosisHarnessFactory;
import com.archdox.opsai.OpsDiagnosisInput;
import com.archdox.opsai.OpsDiagnosisResult;
import com.archdox.cloud.platformops.infra.PlatformOpsFindingRepository;
import com.archdox.cloud.platformops.infra.PlatformOpsIncidentRepository;
import com.archdox.cloud.platformops.infra.PlatformOpsRunRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.parkkevinsb.flower.ai.harness.flow.AiHarnessFlow;
import io.github.parkkevinsb.flower.ai.harness.flow.AiHarnessFlowFactory;
import io.github.parkkevinsb.flower.ai.harness.gateway.AiModelGateway;
import io.github.parkkevinsb.flower.ai.harness.model.ModelId;
import io.github.parkkevinsb.flower.ai.harness.refine.MaxAttemptsRefinePolicy;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunStatus;
import io.github.parkkevinsb.flower.ai.harness.spec.AiHarnessSpec;
import io.github.parkkevinsb.flower.ai.harness.spi.TraceListener;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformOpsDiagnosisService {
    private static final int SNAPSHOT_FINDING_LIMIT = 20;
    private static final int SNAPSHOT_EVENT_LIMIT = 20;

    private final PlatformAdminService platformAdminService;
    private final PlatformOpsRunRepository runRepository;
    private final PlatformOpsIncidentRepository incidentRepository;
    private final PlatformOpsFindingRepository findingRepository;
    private final OperationEventRepository operationEventRepository;
    private final OperationEventService operationEventService;
    private final PlatformOpsAiDiagnosisProperties aiDiagnosisProperties;
    private final PlatformOpsAiDiagnosisRunStore aiDiagnosisRunStore;
    private final PlatformOpsAiDiagnosisFindingSink aiDiagnosisFindingSink;
    private final AiModelGateway aiModelGateway;
    private final ObjectMapper objectMapper;
    private final TraceListener aiHarnessTraceListener;

    public PlatformOpsDiagnosisService(
            PlatformAdminService platformAdminService,
            PlatformOpsRunRepository runRepository,
            PlatformOpsIncidentRepository incidentRepository,
            PlatformOpsFindingRepository findingRepository,
            OperationEventRepository operationEventRepository,
            OperationEventService operationEventService,
            PlatformOpsAiDiagnosisProperties aiDiagnosisProperties,
            PlatformOpsAiDiagnosisRunStore aiDiagnosisRunStore,
            PlatformOpsAiDiagnosisFindingSink aiDiagnosisFindingSink,
            AiModelGateway aiModelGateway,
            ObjectMapper objectMapper,
            TraceListener aiHarnessTraceListener
    ) {
        this.platformAdminService = platformAdminService;
        this.runRepository = runRepository;
        this.incidentRepository = incidentRepository;
        this.findingRepository = findingRepository;
        this.operationEventRepository = operationEventRepository;
        this.operationEventService = operationEventService;
        this.aiDiagnosisProperties = aiDiagnosisProperties;
        this.aiDiagnosisRunStore = aiDiagnosisRunStore;
        this.aiDiagnosisFindingSink = aiDiagnosisFindingSink;
        this.aiModelGateway = aiModelGateway;
        this.objectMapper = objectMapper;
        this.aiHarnessTraceListener = aiHarnessTraceListener;
    }

    @Transactional
    public PlatformOpsRun requestIncidentDiagnosis(UserPrincipal principal, Long incidentId) {
        platformAdminService.requirePlatformAdmin(principal);
        var incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new NotFoundException("Platform ops incident not found"));
        var run = new PlatformOpsRun(
                PlatformOpsRunTriggerType.MANUAL_DIAGNOSIS,
                principal.userId(),
                requestedSnapshot(incident),
                OffsetDateTime.now());
        run.attachIncident(incident.id());
        return runRepository.save(run);
    }

    @Transactional
    public PlatformOpsDiagnosisSummary executeIncidentDiagnosis(Long runId) {
        var summary = buildIncidentDiagnosisSnapshot(runId);
        completeIncidentDiagnosis(runId);
        return summary;
    }

    @Transactional
    public PlatformOpsDiagnosisSummary buildIncidentDiagnosisSnapshot(Long runId) {
        var now = OffsetDateTime.now();
        var run = runRepository.findById(runId)
                .orElseThrow(() -> new NotFoundException("Platform ops run not found"));
        var incidentId = run.incidentId();
        if (incidentId == null) {
            throw new IllegalStateException("Platform ops diagnosis run has no incident");
        }
        var incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new NotFoundException("Platform ops incident not found"));
        var findings = findingRepository.findByIncidentIdOrderByCreatedAtDescIdDesc(
                incident.id(),
                PageRequest.of(0, SNAPSHOT_FINDING_LIMIT));
        var events = relatedOperationEvents(incident);

        run.replaceSnapshot(diagnosisSnapshot(incident, findings, events, now));
        findingRepository.save(diagnosisFinding(run, incident, findings, events, now));
        recordDiagnosisEvent(run, incident, findings, events);
        return new PlatformOpsDiagnosisSummary(run.id(), incident.id(), findings.size(), events.size(), now);
    }

    @Transactional
    public Optional<AiHarnessFlow> createAiDiagnosisHarnessFlow(Long runId) {
        var run = runRepository.findById(runId)
                .orElseThrow(() -> new NotFoundException("Platform ops run not found"));
        if (!aiDiagnosisProperties.runnable()) {
            markAiHarnessSkipped(run, "DISABLED_OR_NOT_CONFIGURED");
            return Optional.empty();
        }
        var incident = run.incidentId() == null
                ? null
                : incidentRepository.findById(run.incidentId())
                        .orElseThrow(() -> new NotFoundException("Platform ops incident not found"));

        AiHarnessSpec<OpsDiagnosisInput, OpsDiagnosisResult> spec =
                new OpsDiagnosisHarnessFactory(objectMapper).spec(
                        aiDiagnosisFindingSink,
                        aiDiagnosisRunStore,
                        new MaxAttemptsRefinePolicy(aiDiagnosisProperties.safeMaxAttempts()),
                        aiHarnessTraceListener);
        var modelId = new ModelId(aiDiagnosisProperties.providerCode(), aiDiagnosisProperties.model());
        var overrides = AiHarnessFlowFactory.RunOverrides.builder()
                .modelId(modelId)
                .timeout(Duration.ofSeconds(aiDiagnosisProperties.safeTimeoutSeconds()))
                .providerOptions(AiModelCallMetadata.options(
                        incident == null ? null : incident.officeId(),
                        "PLATFORM_OPS_DIAGNOSIS",
                        "platform-ops-diagnosis",
                        String.valueOf(run.id()),
                        "PLATFORM_OPS_INCIDENT",
                        run.incidentId(),
                        Map.of(
                                "archdox.opsRunId", run.id(),
                                "archdox.incidentId", run.incidentId() == null ? "" : run.incidentId())))
                .build();
        var flow = new AiHarnessFlowFactory<>(aiModelGateway, spec, Instant::now)
                .createFlow(input(run, incident), overrides);
        run.attachAiHarnessRun(flow.context().runId().value());
        run.replaceSnapshot(withNextAiHarness(run.inputSnapshotJson(), "QUEUED", flow.context().runId().value(), modelId.asString()));
        return Optional.of(flow);
    }

    @Transactional
    public void markAiHarnessSubmitted(Long runId) {
        runRepository.findById(runId).ifPresent(run -> {
            run.replaceSnapshot(withNextAiHarness(
                    run.inputSnapshotJson(),
                    "SUBMITTED",
                    run.aiHarnessRunId(),
                    aiDiagnosisProperties.providerCode() + ":" + aiDiagnosisProperties.model()));
            recordAiHarnessSubmitted(run);
        });
    }

    @Transactional(readOnly = true)
    public boolean isAiHarnessTerminal(Long runId) {
        var run = runRepository.findById(runId)
                .orElseThrow(() -> new NotFoundException("Platform ops run not found"));
        if (run.aiHarnessRunId() == null || run.aiHarnessRunId().isBlank()) {
            return true;
        }
        var status = aiHarnessStatus(run.inputSnapshotJson());
        return status == AiHarnessRunStatus.SUCCEEDED
                || status == AiHarnessRunStatus.FAILED
                || status == AiHarnessRunStatus.CANCELLED;
    }

    @Transactional
    public void summarizeAiDiagnosis(Long runId) {
        var now = OffsetDateTime.now();
        var run = runRepository.findById(runId)
                .orElseThrow(() -> new NotFoundException("Platform ops run not found"));
        if (run.aiHarnessRunId() == null || run.aiHarnessRunId().isBlank()) {
            run.complete(now);
            recordDiagnosisCompleted(run, "DETERMINISTIC_ONLY", 0);
            return;
        }
        var status = aiHarnessStatus(run.inputSnapshotJson());
        if (status == AiHarnessRunStatus.FAILED || status == AiHarnessRunStatus.CANCELLED) {
            run.fail("OPS_DIAGNOSIS_AI_HARNESS_" + status.name(), now);
            recordDiagnosisCompleted(run, run.failureCode(), aiFindingCount(run.id()));
            return;
        }
        if (status == AiHarnessRunStatus.SUCCEEDED) {
            run.complete(now);
            recordDiagnosisCompleted(run, "AI_HARNESS_SUCCEEDED", aiFindingCount(run.id()));
        }
    }

    @Transactional
    public void completeIncidentDiagnosis(Long runId) {
        runRepository.findById(runId).ifPresent(run -> run.complete(OffsetDateTime.now()));
    }

    @Transactional
    public void markRunFailed(Long runId, String failureCode) {
        runRepository.findById(runId).ifPresent(run -> run.fail(failureCode, OffsetDateTime.now()));
    }

    private List<OperationEvent> relatedOperationEvents(PlatformOpsIncident incident) {
        if (blankToNull(incident.primaryResourceType()) == null || blankToNull(incident.primaryResourceId()) == null) {
            return List.of();
        }
        return operationEventRepository.searchPlatformEvents(
                incident.officeId(),
                null,
                null,
                null,
                incident.primaryResourceType(),
                incident.primaryResourceId(),
                PageRequest.of(0, SNAPSHOT_EVENT_LIMIT));
    }

    private Map<String, Object> requestedSnapshot(PlatformOpsIncident incident) {
        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("state", "REQUESTED");
        snapshot.put("diagnosisType", "DETERMINISTIC_FIRST");
        snapshot.put("incident", incidentSnapshot(incident));
        snapshot.put("redactionPolicy", redactionPolicy());
        return snapshot;
    }

    private Map<String, Object> diagnosisSnapshot(
            PlatformOpsIncident incident,
            List<PlatformOpsFinding> findings,
            List<OperationEvent> events,
            OffsetDateTime now
    ) {
        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("state", "SNAPSHOT_READY");
        snapshot.put("diagnosisType", "DETERMINISTIC_FIRST");
        snapshot.put("diagnosedAt", now.toString());
        snapshot.put("incident", incidentSnapshot(incident));
        snapshot.put("recentFindingCount", findings.size());
        snapshot.put("recentFindings", findings.stream().map(this::findingSnapshot).toList());
        snapshot.put("relatedOperationEventCount", events.size());
        snapshot.put("relatedOperationEvents", events.stream().map(this::eventSnapshot).toList());
        snapshot.put("redactionPolicy", redactionPolicy());
        snapshot.put("nextAiHarness", Map.of(
                "type", "OpsDiagnosisHarness",
                "status", "RESERVED",
                "note", "This snapshot is the deterministic input for a future AI diagnosis harness."));
        return snapshot;
    }

    private PlatformOpsFinding diagnosisFinding(
            PlatformOpsRun run,
            PlatformOpsIncident incident,
            List<PlatformOpsFinding> findings,
            List<OperationEvent> events,
            OffsetDateTime now
    ) {
        return new PlatformOpsFinding(
                incident.id(),
                run.id(),
                incident.officeId(),
                PlatformOpsFindingSeverity.INFO,
                PlatformOpsFindingSource.SYSTEM_DIAGNOSIS,
                "OPS_DIAGNOSIS_SNAPSHOT_READY",
                "OPS_DIAGNOSIS",
                "운영 진단 스냅샷 준비 완료",
                "Incident와 관련 finding, operation event를 모아 운영 진단 입력 스냅샷을 만들었습니다.",
                "PLATFORM_OPS_INCIDENT",
                String.valueOf(incident.id()),
                "platform-ops-diagnosis",
                String.valueOf(run.id()),
                Map.of(
                        "incidentId", incident.id(),
                        "recentFindingCount", findings.size(),
                        "relatedOperationEventCount", events.size()),
                "스냅샷을 확인한 뒤 필요하면 OpsDiagnosisHarness를 붙여 원인/조치안 분석을 실행합니다.",
                now);
    }

    private void recordDiagnosisEvent(
            PlatformOpsRun run,
            PlatformOpsIncident incident,
            List<PlatformOpsFinding> findings,
            List<OperationEvent> events
    ) {
        operationEventService.record(
                incident.officeId(),
                OperationEventSeverity.INFO,
                "OPS_DIAGNOSIS_SNAPSHOT_READY",
                "platform-ops-diagnosis",
                String.valueOf(run.id()),
                "PLATFORM_OPS_INCIDENT",
                incident.id(),
                run.startedByUserId(),
                null,
                "Platform ops diagnosis snapshot is ready",
                Map.of(
                        "opsRunId", run.id(),
                        "incidentId", incident.id(),
                        "recentFindingCount", findings.size(),
                        "relatedOperationEventCount", events.size()));
    }

    private Map<String, Object> incidentSnapshot(PlatformOpsIncident incident) {
        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("id", incident.id());
        snapshot.put("status", incident.status().name());
        snapshot.put("severity", incident.severity().name());
        snapshot.put("category", incident.category());
        snapshot.put("title", incident.title());
        snapshot.put("summary", incident.summary());
        snapshot.put("officeId", incident.officeId());
        snapshot.put("primaryResourceType", incident.primaryResourceType());
        snapshot.put("primaryResourceId", incident.primaryResourceId());
        snapshot.put("firstSeenAt", incident.firstSeenAt().toString());
        snapshot.put("lastSeenAt", incident.lastSeenAt().toString());
        return snapshot;
    }

    private Map<String, Object> findingSnapshot(PlatformOpsFinding finding) {
        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("id", finding.id());
        snapshot.put("severity", finding.severity().name());
        snapshot.put("source", finding.source().name());
        snapshot.put("code", finding.code());
        snapshot.put("category", finding.category());
        snapshot.put("title", finding.title());
        snapshot.put("message", finding.message());
        snapshot.put("resourceType", finding.resourceType());
        snapshot.put("resourceId", finding.resourceId());
        snapshot.put("workflowType", finding.workflowType());
        snapshot.put("workflowKey", finding.workflowKey());
        snapshot.put("evidence", finding.evidenceJson());
        snapshot.put("recommendation", finding.recommendation());
        snapshot.put("createdAt", finding.createdAt().toString());
        return snapshot;
    }

    private Map<String, Object> eventSnapshot(OperationEvent event) {
        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("id", event.id());
        snapshot.put("severity", event.severity().name());
        snapshot.put("eventType", event.eventType());
        snapshot.put("workflowType", event.workflowType());
        snapshot.put("workflowKey", event.workflowKey());
        snapshot.put("resourceType", event.resourceType());
        snapshot.put("resourceId", event.resourceId());
        snapshot.put("message", event.message());
        snapshot.put("payload", event.payloadJson());
        snapshot.put("createdAt", event.createdAt().toString());
        return snapshot;
    }

    private OpsDiagnosisInput input(PlatformOpsRun run, PlatformOpsIncident incident) {
        return new OpsDiagnosisInput(
                String.valueOf(run.id()),
                run.incidentId() == null ? "" : String.valueOf(run.incidentId()),
                incident == null || incident.officeId() == null ? "" : String.valueOf(incident.officeId()),
                incident == null ? "" : incident.category(),
                incident == null ? "" : incident.severity().name(),
                run.inputSnapshotJson());
    }

    private void markAiHarnessSkipped(PlatformOpsRun run, String reason) {
        run.replaceSnapshot(withNextAiHarness(run.inputSnapshotJson(), "SKIPPED", null, null, reason));
        operationEventService.record(
                run.incidentId() == null ? null : incidentRepository.findById(run.incidentId()).map(PlatformOpsIncident::officeId).orElse(null),
                OperationEventSeverity.INFO,
                "OPS_DIAGNOSIS_AI_HARNESS_SKIPPED",
                "platform-ops-diagnosis",
                String.valueOf(run.id()),
                "PLATFORM_OPS_RUN",
                run.id(),
                run.startedByUserId(),
                null,
                "Platform ops AI diagnosis harness was skipped.",
                payload("opsRunId", run.id(), "incidentId", run.incidentId(), "reason", reason));
    }

    private void recordAiHarnessSubmitted(PlatformOpsRun run) {
        operationEventService.record(
                run.incidentId() == null ? null : incidentRepository.findById(run.incidentId()).map(PlatformOpsIncident::officeId).orElse(null),
                OperationEventSeverity.INFO,
                "OPS_DIAGNOSIS_AI_HARNESS_SUBMITTED",
                "platform-ops-diagnosis",
                String.valueOf(run.id()),
                "PLATFORM_OPS_RUN",
                run.id(),
                run.startedByUserId(),
                null,
                "Platform ops diagnosis flow submitted the AI harness.",
                payload("opsRunId", run.id(), "incidentId", run.incidentId(), "harnessRunId", run.aiHarnessRunId()));
    }

    private void recordDiagnosisCompleted(PlatformOpsRun run, String reason, long aiFindingCount) {
        operationEventService.record(
                run.incidentId() == null ? null : incidentRepository.findById(run.incidentId()).map(PlatformOpsIncident::officeId).orElse(null),
                run.status().name().equals("FAILED") ? OperationEventSeverity.WARN : OperationEventSeverity.INFO,
                "OPS_DIAGNOSIS_COMPLETED",
                "platform-ops-diagnosis",
                String.valueOf(run.id()),
                "PLATFORM_OPS_RUN",
                run.id(),
                run.startedByUserId(),
                null,
                "Platform ops diagnosis flow completed.",
                payload(
                        "opsRunId", run.id(),
                        "incidentId", run.incidentId(),
                        "reason", reason,
                        "aiFindingCount", aiFindingCount));
    }

    private long aiFindingCount(Long runId) {
        return findingRepository.countByRunIdAndSource(runId, PlatformOpsFindingSource.AI_HARNESS);
    }

    private AiHarnessRunStatus aiHarnessStatus(Map<String, Object> snapshot) {
        var aiHarness = childMap(snapshot, "aiHarness");
        var status = stringValue(aiHarness.get("status"));
        if (status == null || status.isBlank()) {
            var nextAiHarness = childMap(snapshot, "nextAiHarness");
            status = stringValue(nextAiHarness.get("status"));
        }
        if (status == null || status.isBlank() || "SUBMITTED".equals(status) || "RESERVED".equals(status)) {
            return AiHarnessRunStatus.QUEUED;
        }
        if ("SKIPPED".equals(status)) {
            return AiHarnessRunStatus.SUCCEEDED;
        }
        return AiHarnessRunStatus.valueOf(status);
    }

    private Map<String, Object> withNextAiHarness(
            Map<String, Object> inputSnapshot,
            String status,
            String harnessRunId,
            String modelId
    ) {
        return withNextAiHarness(inputSnapshot, status, harnessRunId, modelId, null);
    }

    private Map<String, Object> withNextAiHarness(
            Map<String, Object> inputSnapshot,
            String status,
            String harnessRunId,
            String modelId,
            String reason
    ) {
        var snapshot = new LinkedHashMap<String, Object>(inputSnapshot == null ? Map.of() : inputSnapshot);
        var nextAiHarness = new LinkedHashMap<String, Object>();
        nextAiHarness.put("type", "OpsDiagnosisHarness");
        nextAiHarness.put("status", status);
        put(nextAiHarness, "harnessRunId", harnessRunId);
        put(nextAiHarness, "modelId", modelId);
        put(nextAiHarness, "reason", reason);
        snapshot.put("nextAiHarness", nextAiHarness);
        return snapshot;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> childMap(Map<String, Object> snapshot, String key) {
        if (snapshot == null) {
            return Map.of();
        }
        var value = snapshot.get(key);
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private Map<String, Object> payload(Object... values) {
        var payload = new LinkedHashMap<String, Object>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            var key = String.valueOf(values[i]);
            var value = values[i + 1];
            if (value != null) {
                payload.put(key, value);
            }
        }
        return payload;
    }

    private void put(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Map<String, Object> redactionPolicy() {
        return Map.of(
                "secrets", "excluded",
                "rawFiles", "excluded",
                "tokens", "excluded",
                "scope", "operational ids, statuses, timestamps, error categories, and redacted payload only");
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
