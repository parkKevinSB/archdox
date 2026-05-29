package com.archdox.cloud.platformops.application;

import com.archdox.cloud.operation.application.OperationEventService;
import com.archdox.cloud.operation.domain.OperationEventSeverity;
import com.archdox.opsai.OpsDiagnosisHarnessFactory;
import com.archdox.cloud.platformops.infra.PlatformOpsIncidentRepository;
import com.archdox.cloud.platformops.infra.PlatformOpsRunRepository;
import io.github.parkkevinsb.flower.ai.harness.prompt.PromptVersion;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunId;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunSnapshot;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunStatus;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunStore;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PlatformOpsAiDiagnosisRunStore implements AiHarnessRunStore {
    private static final String AI_HARNESS = "aiHarness";

    private final PlatformOpsRunRepository runRepository;
    private final PlatformOpsIncidentRepository incidentRepository;
    private final OperationEventService operationEventService;

    public PlatformOpsAiDiagnosisRunStore(
            PlatformOpsRunRepository runRepository,
            PlatformOpsIncidentRepository incidentRepository,
            OperationEventService operationEventService
    ) {
        this.runRepository = runRepository;
        this.incidentRepository = incidentRepository;
        this.operationEventService = operationEventService;
    }

    @Override
    @Transactional
    public void save(AiHarnessRunSnapshot snapshot) {
        var run = runRepository.findByAiHarnessRunId(snapshot.runId().value())
                .orElseThrow(() -> new IllegalStateException("Platform ops AI diagnosis run not found: " + snapshot.runId().value()));
        var previous = aiHarnessSnapshot(run.inputSnapshotJson());
        var previousStatus = stringValue(previous.get("status"));
        run.replaceSnapshot(withAiHarnessSnapshot(run.inputSnapshotJson(), snapshot));
        if (!terminal(previousStatus) && terminal(snapshot.status().name())) {
            var officeId = run.incidentId() == null
                    ? null
                    : incidentRepository.findById(run.incidentId()).map(incident -> incident.officeId()).orElse(null);
            operationEventService.record(
                    officeId,
                    snapshot.status() == AiHarnessRunStatus.SUCCEEDED ? OperationEventSeverity.INFO : OperationEventSeverity.WARN,
                    "OPS_DIAGNOSIS_AI_HARNESS_" + snapshot.status().name(),
                    "platform-ops-diagnosis",
                    String.valueOf(run.id()),
                    "PLATFORM_OPS_RUN",
                    run.id(),
                    run.startedByUserId(),
                    null,
                    "Platform ops AI diagnosis harness finished with status " + snapshot.status().name() + ".",
                    payload("opsRunId", run.id(), "incidentId", run.incidentId(), "harnessRunId", run.aiHarnessRunId()));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AiHarnessRunSnapshot> find(AiHarnessRunId runId) {
        return runRepository.findByAiHarnessRunId(runId.value())
                .map(run -> {
                    var snapshot = aiHarnessSnapshot(run.inputSnapshotJson());
                    return new AiHarnessRunSnapshot(
                            new AiHarnessRunId(run.aiHarnessRunId()),
                            defaultString(snapshot.get("harnessId"), OpsDiagnosisHarnessFactory.HARNESS_ID),
                            promptVersion(snapshot),
                            status(snapshot),
                            intValue(snapshot.get("attempt")),
                            instantValue(snapshot.get("startedAt"), run.startedAt().toInstant()),
                            instantValue(snapshot.get("capturedAt"), run.startedAt().toInstant()),
                            Optional.empty(),
                            Optional.ofNullable(blankToNull(stringValue(snapshot.get("currentCallId")))),
                            Optional.empty(),
                            Optional.ofNullable(blankToNull(stringValue(snapshot.get("terminalReason")))));
                });
    }

    private Map<String, Object> withAiHarnessSnapshot(
            Map<String, Object> inputSnapshot,
            AiHarnessRunSnapshot snapshot
    ) {
        var next = new LinkedHashMap<String, Object>(inputSnapshot == null ? Map.of() : inputSnapshot);
        var aiHarness = new LinkedHashMap<String, Object>();
        aiHarness.put("harnessRunId", snapshot.runId().value());
        aiHarness.put("harnessId", snapshot.harnessId());
        aiHarness.put("promptId", snapshot.promptVersion().id());
        aiHarness.put("promptVersion", snapshot.promptVersion().version());
        aiHarness.put("status", snapshot.status().name());
        aiHarness.put("attempt", snapshot.attempt());
        aiHarness.put("startedAt", snapshot.startedAt().toString());
        aiHarness.put("capturedAt", snapshot.capturedAt().toString());
        snapshot.currentCallId().ifPresent(value -> aiHarness.put("currentCallId", value));
        snapshot.terminalReason().ifPresent(value -> aiHarness.put("terminalReason", value));
        next.put(AI_HARNESS, aiHarness);
        next.put("nextAiHarness", Map.of(
                "type", "OpsDiagnosisHarness",
                "status", snapshot.status().name(),
                "harnessRunId", snapshot.runId().value()));
        return next;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> aiHarnessSnapshot(Map<String, Object> inputSnapshot) {
        if (inputSnapshot == null) {
            return Map.of();
        }
        var value = inputSnapshot.get(AI_HARNESS);
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private PromptVersion promptVersion(Map<String, Object> snapshot) {
        var promptId = defaultString(snapshot.get("promptId"), OpsDiagnosisHarnessFactory.PROMPT_VERSION.id());
        var version = defaultString(snapshot.get("promptVersion"), OpsDiagnosisHarnessFactory.PROMPT_VERSION.version());
        return new PromptVersion(promptId, version);
    }

    private AiHarnessRunStatus status(Map<String, Object> snapshot) {
        var value = stringValue(snapshot.get("status"));
        if (value == null || value.isBlank()) {
            return AiHarnessRunStatus.QUEUED;
        }
        return AiHarnessRunStatus.valueOf(value);
    }

    private boolean terminal(String status) {
        return AiHarnessRunStatus.SUCCEEDED.name().equals(status)
                || AiHarnessRunStatus.FAILED.name().equals(status)
                || AiHarnessRunStatus.CANCELLED.name().equals(status);
    }

    private Instant instantValue(Object value, Instant fallback) {
        var stringValue = stringValue(value);
        if (stringValue == null || stringValue.isBlank()) {
            return fallback;
        }
        return Instant.parse(stringValue);
    }

    private int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        var stringValue = stringValue(value);
        if (stringValue == null || stringValue.isBlank()) {
            return 0;
        }
        return Integer.parseInt(stringValue);
    }

    private String defaultString(Object value, String defaultValue) {
        var stringValue = stringValue(value);
        return stringValue == null || stringValue.isBlank() ? defaultValue : stringValue;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Map<String, Object> payload(Object... values) {
        var payload = new LinkedHashMap<String, Object>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            var value = values[i + 1];
            if (value != null) {
                payload.put(String.valueOf(values[i]), value);
            }
        }
        return payload;
    }
}
