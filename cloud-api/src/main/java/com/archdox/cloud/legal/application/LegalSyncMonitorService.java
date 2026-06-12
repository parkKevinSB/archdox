package com.archdox.cloud.legal.application;

import com.archdox.cloud.legal.domain.LegalSyncRun;
import com.archdox.cloud.legal.domain.LegalSyncRunStatus;
import com.archdox.cloud.legal.event.LegalSyncRequested;
import com.archdox.cloud.legal.flow.LegalSyncFlowFactory;
import com.archdox.cloud.legal.flow.LegalSyncWorker;
import com.archdox.cloud.legal.infra.LegalSyncRunRepository;
import com.archdox.cloud.operation.application.OperationEventService;
import com.archdox.cloud.operation.domain.OperationEventSeverity;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class LegalSyncMonitorService {
    public static final String TRIGGER_TYPE = "AUTO_MONITOR";

    private final LegalSyncProperties properties;
    private final LegalSyncRunRepository syncRunRepository;
    private final LegalCorpusSyncService syncService;
    private final LegalSyncFlowFactory flowFactory;
    private final LegalSyncWorker worker;
    private final OperationEventService operationEventService;

    public LegalSyncMonitorService(
            LegalSyncProperties properties,
            LegalSyncRunRepository syncRunRepository,
            LegalCorpusSyncService syncService,
            LegalSyncFlowFactory flowFactory,
            LegalSyncWorker worker,
            OperationEventService operationEventService
    ) {
        this.properties = properties;
        this.syncRunRepository = syncRunRepository;
        this.syncService = syncService;
        this.flowFactory = flowFactory;
        this.worker = worker;
        this.operationEventService = operationEventService;
    }

    public LegalSyncMonitorDecision checkAndSubmitIfDue(OffsetDateTime now) {
        var monitor = properties.getMonitor();
        var openApi = properties.getOpenApi();
        var sourceCode = openApi.getSourceCode();
        if (!monitor.isEnabled()) {
            return LegalSyncMonitorDecision.skipped("MONITOR_DISABLED", sourceCode, null);
        }
        if (!openApiReady(openApi)) {
            return LegalSyncMonitorDecision.skipped("OPEN_API_NOT_READY", sourceCode, null);
        }
        var dueAt = latestDueAt(now, monitor);
        if (dueAt == null) {
            return LegalSyncMonitorDecision.skipped("NO_VALID_RUN_TIME", sourceCode, null);
        }
        if (Duration.between(dueAt, now).toMinutes() > monitor.safeCatchUpGraceMinutes()) {
            return LegalSyncMonitorDecision.skipped("OUTSIDE_CATCH_UP_WINDOW", sourceCode, dueAt);
        }
        if (syncRunRepository.existsBySourceCodeAndStatus(sourceCode, LegalSyncRunStatus.RUNNING)) {
            return LegalSyncMonitorDecision.skipped("SYNC_ALREADY_RUNNING", sourceCode, dueAt);
        }
        var latest = syncRunRepository.findFirstBySourceCodeOrderByStartedAtDescIdDesc(sourceCode).orElse(null);
        if (latest != null && !latest.startedAt().isBefore(dueAt)) {
            return LegalSyncMonitorDecision.skipped("DUE_SLOT_ALREADY_HANDLED", sourceCode, dueAt);
        }

        LegalSyncRun run;
        try {
            run = syncService.createRun(TRIGGER_TYPE, sourceCode, null);
        } catch (DataIntegrityViolationException ex) {
            return LegalSyncMonitorDecision.skipped("SYNC_ALREADY_RUNNING", sourceCode, dueAt);
        }
        worker.submit(flowFactory.create(new LegalSyncRequested(run.id(), run.sourceCode())));
        recordSubmitted(run.id(), sourceCode, dueAt, now);
        return LegalSyncMonitorDecision.submitted(sourceCode, dueAt, run.id());
    }

    private boolean openApiReady(LegalSyncProperties.OpenApi openApi) {
        return openApi.isEnabled()
                && openApi.getOc() != null
                && !openApi.getOc().isBlank()
                && openApi.getTargets().stream().anyMatch(target -> target.getQuery() != null && !target.getQuery().isBlank());
    }

    private OffsetDateTime latestDueAt(OffsetDateTime now, LegalSyncProperties.Monitor monitor) {
        var zone = ZoneId.of(monitor.getZoneId());
        var localNow = now.atZoneSameInstant(zone).toLocalDateTime();
        var times = runTimes(monitor.getRunTimes());
        if (times.isEmpty()) {
            return null;
        }
        return List.of(localNow.toLocalDate(), localNow.toLocalDate().minusDays(1)).stream()
                .flatMap(date -> times.stream().map(time -> dueAt(date, time, zone)))
                .filter(candidate -> !candidate.isAfter(now))
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    private OffsetDateTime dueAt(LocalDate date, LocalTime time, ZoneId zone) {
        return date.atTime(time).atZone(zone).toOffsetDateTime();
    }

    private List<LocalTime> runTimes(String runTimes) {
        if (runTimes == null || runTimes.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(runTimes.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(LocalTime::parse)
                .distinct()
                .sorted()
                .toList();
    }

    private void recordSubmitted(Long runId, String sourceCode, OffsetDateTime dueAt, OffsetDateTime now) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("syncRunId", runId);
        payload.put("sourceCode", sourceCode);
        payload.put("dueAt", dueAt.toString());
        payload.put("submittedAt", now.toString());
        payload.put("triggerType", TRIGGER_TYPE);
        operationEventService.record(
                null,
                OperationEventSeverity.INFO,
                "LEGAL_SYNC_MONITOR_SUBMITTED",
                "legal-sync-monitor",
                "source:" + sourceCode,
                "LEGAL_SYNC_RUN",
                runId,
                "Legal sync monitor submitted a due Open API sync flow.",
                Map.copyOf(payload));
    }
}
