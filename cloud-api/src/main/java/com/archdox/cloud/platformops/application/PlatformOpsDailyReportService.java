package com.archdox.cloud.platformops.application;

import com.archdox.cloud.aipolicy.application.AiHarnessPolicyExecutionService;
import com.archdox.cloud.aipolicy.application.AiModelCallMetadata;
import com.archdox.cloud.aipolicy.domain.AiHarnessPolicyKey;
import com.archdox.cloud.aipolicy.domain.AiModelCallLogStatus;
import com.archdox.cloud.aipolicy.infra.AiModelCallLogRepository;
import com.archdox.cloud.engine.usage.infra.EngineApiUsageEventRepository;
import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.global.api.NotFoundException;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.operation.application.OperationEventService;
import com.archdox.cloud.operation.domain.OperationEventSeverity;
import com.archdox.cloud.operation.infra.OperationEventRepository;
import com.archdox.cloud.photo.domain.PhotoPickupStatus;
import com.archdox.cloud.photo.domain.PhotoStatus;
import com.archdox.cloud.photo.infra.PhotoRepository;
import com.archdox.cloud.platformadmin.application.PlatformAdminService;
import com.archdox.cloud.platformops.domain.PlatformOpsDailyReport;
import com.archdox.cloud.platformops.domain.PlatformOpsFinding;
import com.archdox.cloud.platformops.domain.PlatformOpsFindingSeverity;
import com.archdox.cloud.platformops.domain.PlatformOpsFindingSource;
import com.archdox.cloud.platformops.domain.PlatformOpsIncident;
import com.archdox.cloud.platformops.domain.PlatformOpsIncidentStatus;
import com.archdox.cloud.platformops.domain.PlatformOpsRun;
import com.archdox.cloud.platformops.domain.PlatformOpsRunStatus;
import com.archdox.cloud.platformops.domain.PlatformOpsRunTriggerType;
import com.archdox.cloud.platformops.infra.PlatformOpsDailyReportRepository;
import com.archdox.cloud.platformops.infra.PlatformOpsFindingRepository;
import com.archdox.cloud.platformops.infra.PlatformOpsIncidentRepository;
import com.archdox.cloud.platformops.infra.PlatformOpsRunRepository;
import com.archdox.opsai.OpsDailyReportHarnessFactory;
import com.archdox.opsai.OpsDailyReportInput;
import com.archdox.opsai.OpsDailyReportResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.parkkevinsb.flower.ai.harness.flow.AiHarnessFlow;
import io.github.parkkevinsb.flower.ai.harness.flow.AiHarnessFlowFactory;
import io.github.parkkevinsb.flower.ai.harness.gateway.AiModelGateway;
import io.github.parkkevinsb.flower.ai.harness.refine.MaxAttemptsRefinePolicy;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunStatus;
import io.github.parkkevinsb.flower.ai.harness.spec.AiHarnessSpec;
import io.github.parkkevinsb.flower.ai.harness.spi.TraceListener;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformOpsDailyReportService {
    private static final int RECENT_EVENT_LIMIT = 20;
    private static final int TOP_GROUP_LIMIT = 8;
    private static final int INCIDENT_BREAKDOWN_LIMIT = 500;
    private static final String FLOW_INTERRUPTED_BY_RESTART = "FLOW_INTERRUPTED_BY_RESTART";
    private static final String ACTION_TYPE = "GENERATE_PLATFORM_OPS_DAILY_REPORT";
    private static final List<PlatformOpsIncidentStatus> ACTIVE_INCIDENT_STATUSES = List.of(
            PlatformOpsIncidentStatus.OPEN,
            PlatformOpsIncidentStatus.ACKNOWLEDGED);
    private static final List<PlatformOpsRunTriggerType> INCIDENT_DIAGNOSIS_TRIGGER_TYPES = List.of(
            PlatformOpsRunTriggerType.AUTO_DIAGNOSIS,
            PlatformOpsRunTriggerType.MANUAL_DIAGNOSIS);

    private final PlatformAdminService platformAdminService;
    private final PlatformOpsAutomationSettingsService automationSettingsService;
    private final PlatformOpsRunRepository runRepository;
    private final PlatformOpsIncidentRepository incidentRepository;
    private final PlatformOpsFindingRepository findingRepository;
    private final PlatformOpsDailyReportRepository dailyReportRepository;
    private final OperationEventRepository operationEventRepository;
    private final PhotoRepository photoRepository;
    private final AiModelCallLogRepository aiModelCallLogRepository;
    private final EngineApiUsageEventRepository engineApiUsageEventRepository;
    private final OperationEventService operationEventService;
    private final AiHarnessPolicyExecutionService aiHarnessPolicyExecutionService;
    private final PlatformOpsDiagnosisService diagnosisService;
    private final PlatformOpsDailyReportRunStore aiRunStore;
    private final PlatformOpsDailyReportFindingSink aiFindingSink;
    private final AiModelGateway aiModelGateway;
    private final ObjectMapper objectMapper;
    private final TraceListener aiHarnessTraceListener;

    public PlatformOpsDailyReportService(
            PlatformAdminService platformAdminService,
            PlatformOpsAutomationSettingsService automationSettingsService,
            PlatformOpsRunRepository runRepository,
            PlatformOpsIncidentRepository incidentRepository,
            PlatformOpsFindingRepository findingRepository,
            PlatformOpsDailyReportRepository dailyReportRepository,
            OperationEventRepository operationEventRepository,
            PhotoRepository photoRepository,
            AiModelCallLogRepository aiModelCallLogRepository,
            EngineApiUsageEventRepository engineApiUsageEventRepository,
            OperationEventService operationEventService,
            AiHarnessPolicyExecutionService aiHarnessPolicyExecutionService,
            PlatformOpsDiagnosisService diagnosisService,
            PlatformOpsDailyReportRunStore aiRunStore,
            PlatformOpsDailyReportFindingSink aiFindingSink,
            AiModelGateway aiModelGateway,
            ObjectMapper objectMapper,
            TraceListener aiHarnessTraceListener
    ) {
        this.platformAdminService = platformAdminService;
        this.automationSettingsService = automationSettingsService;
        this.runRepository = runRepository;
        this.incidentRepository = incidentRepository;
        this.findingRepository = findingRepository;
        this.dailyReportRepository = dailyReportRepository;
        this.operationEventRepository = operationEventRepository;
        this.photoRepository = photoRepository;
        this.aiModelCallLogRepository = aiModelCallLogRepository;
        this.engineApiUsageEventRepository = engineApiUsageEventRepository;
        this.operationEventService = operationEventService;
        this.aiHarnessPolicyExecutionService = aiHarnessPolicyExecutionService;
        this.diagnosisService = diagnosisService;
        this.aiRunStore = aiRunStore;
        this.aiFindingSink = aiFindingSink;
        this.aiModelGateway = aiModelGateway;
        this.objectMapper = objectMapper;
        this.aiHarnessTraceListener = aiHarnessTraceListener;
    }

    @Transactional
    public PlatformOpsRun requestAutoDailyReport(OffsetDateTime dueAt, OffsetDateTime now) {
        return requestDailyReport(PlatformOpsRunTriggerType.AUTO_DAILY_REPORT, null, dueAt, now);
    }

    @Transactional
    public PlatformOpsRun requestManualDailyReport(UserPrincipal principal, OffsetDateTime now) {
        platformAdminService.requirePlatformAdmin(principal);
        return requestDailyReport(PlatformOpsRunTriggerType.MANUAL_DAILY_REPORT, principal.userId(), now, now);
    }

    @Transactional
    public List<PlatformOpsRun> requestAutoDiagnosesBeforeReport(Long runId) {
        var run = run(runId);
        var snapshot = new LinkedHashMap<String, Object>(run.inputSnapshotJson());
        var settings = automationSettingsService.settings();
        if (!settings.dailyReportAutoDiagnosisEnabled()) {
            snapshot.put("autoDiagnosis", autoDiagnosisSnapshot(
                    "SKIPPED",
                    "AUTO_DIAGNOSIS_DISABLED",
                    null,
                    List.of(),
                    List.of(),
                    0,
                    autoDiagnosisMinSeverity(settings.dailyReportAutoDiagnosisMinSeverity())));
            run.replaceSnapshot(snapshot);
            return List.of();
        }

        var now = OffsetDateTime.now();
        var limit = settings.dailyReportAutoDiagnosisIncidentLimit();
        var minSeverity = autoDiagnosisMinSeverity(settings.dailyReportAutoDiagnosisMinSeverity());
        var incidentTargets = selectAutoDiagnosisTargets(limit, minSeverity);
        var diagnosisRuns = new ArrayList<PlatformOpsRun>();
        var systemRun = diagnosisService.requestAutoSystemDiagnosis(run.id(), now);
        diagnosisRuns.add(systemRun);
        var incidentRuns = incidentTargets.stream()
                .map(target -> diagnosisService.requestAutoIncidentDiagnosis(target.incident().id(), run.id(), now))
                .toList();
        diagnosisRuns.addAll(incidentRuns);
        snapshot.put("autoDiagnosis", autoDiagnosisSnapshot(
                "REQUESTED",
                "DUE",
                systemRun,
                incidentRuns,
                incidentTargets,
                limit,
                minSeverity));
        run.replaceSnapshot(snapshot);
        recordAutoDiagnosisRequested(run, diagnosisRuns);
        return diagnosisRuns;
    }

    @Transactional(readOnly = true)
    public boolean areAutoDiagnosesTerminal(Long runId) {
        var snapshot = run(runId).inputSnapshotJson();
        var ids = longList(childMap(snapshot, "autoDiagnosis").get("runIds"));
        if (ids.isEmpty()) {
            return true;
        }
        return runRepository.findAllById(ids).stream()
                .allMatch(run -> run.status() == PlatformOpsRunStatus.COMPLETED
                        || run.status() == PlatformOpsRunStatus.FAILED);
    }

    @Transactional
    public void buildDailyReportSnapshot(Long runId) {
        var run = run(runId);
        var requested = run.inputSnapshotJson();
        var dueAt = offsetDateTime(requested.get("dueAt"), run.startedAt());
        var from = offsetDateTime(requested.get("periodFrom"), run.startedAt().minusDays(1));
        var to = offsetDateTime(requested.get("periodTo"), run.startedAt());
        var snapshot = snapshot(run.id(), dueAt, from, to);
        put(snapshot, "autoDiagnosis", requested.get("autoDiagnosis"));
        snapshot.put("state", "SNAPSHOT_READY");
        snapshot.put("nextAiHarness", Map.of(
                "type", "OpsDailyReportHarness",
                "status", "RESERVED",
                "note", "The AI harness may summarize only this redacted evidence bundle."));
        run.replaceSnapshot(snapshot);
        findingRepository.save(snapshotFinding(run, snapshot, OffsetDateTime.now()));
        recordSnapshotEvent(run, snapshot);
    }

    @Transactional
    public Optional<AiHarnessFlow> createAiDailyReportHarnessFlow(Long runId) {
        var run = run(runId);
        var policy = aiHarnessPolicyExecutionService.resolve(AiHarnessPolicyKey.PLATFORM_OPS_DAILY_REPORT);
        if (!policy.runnable()) {
            markAiHarnessSkipped(run, policy.unavailableReason());
            return Optional.empty();
        }
        var plan = policy.plan();
        try {
            aiHarnessPolicyExecutionService.requireWithinBudget(plan);
        } catch (BadRequestException ex) {
            markAiHarnessSkipped(run, ex.code() + ": " + ex.getMessage());
            return Optional.empty();
        }

        AiHarnessSpec<OpsDailyReportInput, OpsDailyReportResult> spec =
                new OpsDailyReportHarnessFactory(objectMapper).spec(
                        aiFindingSink,
                        aiRunStore,
                        new MaxAttemptsRefinePolicy(plan.maxAttempts()),
                        aiHarnessTraceListener);
        var overrides = AiHarnessFlowFactory.RunOverrides.builder()
                .modelId(plan.modelId())
                .timeout(plan.timeout())
                .providerOptions(AiModelCallMetadata.options(
                        null,
                        run.startedByUserId(),
                        "PLATFORM_OPS_DAILY_REPORT",
                        "platform-ops-daily-report",
                        String.valueOf(run.id()),
                        "PLATFORM_OPS_RUN",
                        run.id(),
                        Map.of(
                                "archdox.opsRunId", run.id(),
                                "archdox.feature", "PLATFORM_OPS_DAILY_REPORT",
                                "archdox.actionType", ACTION_TYPE),
                        plan.maxOutputTokens()))
                .build();
        var flow = new AiHarnessFlowFactory<>(aiModelGateway, spec, Instant::now)
                .createFlow(input(run), overrides);
        run.attachAiHarnessRun(flow.context().runId().value());
        run.replaceSnapshot(withNextAiHarness(run.inputSnapshotJson(), "QUEUED", flow.context().runId().value(), plan.modelId().asString()));
        return Optional.of(flow);
    }

    @Transactional
    public void markAiHarnessSubmitted(Long runId) {
        runRepository.findById(runId).ifPresent(run -> {
            run.replaceSnapshot(withNextAiHarness(
                    run.inputSnapshotJson(),
                    "SUBMITTED",
                    run.aiHarnessRunId(),
                    modelIdFromHarnessPolicy()));
            recordAiHarnessSubmitted(run);
        });
    }

    @Transactional(readOnly = true)
    public boolean isAiHarnessTerminal(Long runId) {
        var run = run(runId);
        if (run.aiHarnessRunId() == null || run.aiHarnessRunId().isBlank()) {
            return true;
        }
        var status = aiHarnessStatus(run.inputSnapshotJson());
        return status == AiHarnessRunStatus.SUCCEEDED
                || status == AiHarnessRunStatus.FAILED
                || status == AiHarnessRunStatus.CANCELLED;
    }

    @Transactional
    public void finalizeDailyReport(Long runId) {
        var now = OffsetDateTime.now();
        var run = run(runId);
        var status = aiHarnessStatus(run.inputSnapshotJson());
        var snapshot = new LinkedHashMap<String, Object>(run.inputSnapshotJson());
        snapshot.put("state", "REPORT_READY");
        snapshot.put("aiHarnessStatus", status.name());
        var findings = findingRepository.findByRunIdOrderByCreatedAtAscIdAsc(run.id());
        var reportPath = writeReport(run.id(), snapshot, findings);
        snapshot.put("reportPath", reportPath.toString());
        run.replaceSnapshot(snapshot);
        dailyReportRepository.findByRunId(run.id()).orElseGet(() -> dailyReportRepository.save(dailyReport(
                run,
                snapshot,
                findings,
                reportPath,
                now)));
        findingRepository.save(reportFinding(run, reportPath, snapshot, findings, now));
        run.complete(now);
        recordReportEvent(run, reportPath, snapshot, findings);
    }

    @Transactional
    public void markRunFailed(Long runId, String failureCode) {
        runRepository.findById(runId).ifPresent(run -> run.fail(failureCode, OffsetDateTime.now()));
    }

    private PlatformOpsRun requestDailyReport(
            PlatformOpsRunTriggerType triggerType,
            Long userId,
            OffsetDateTime dueAt,
            OffsetDateTime now
    ) {
        var from = now.minusDays(1);
        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("state", "REQUESTED");
        snapshot.put("actionType", ACTION_TYPE);
        snapshot.put("riskClass", "READ_RECOMMEND_WRITE_ARTIFACT");
        snapshot.put("dueAt", dueAt.toString());
        snapshot.put("periodFrom", from.toString());
        snapshot.put("periodTo", now.toString());
        snapshot.put("controlBoundary", controlBoundary());
        snapshot.put("redactionPolicy", redactionPolicy());
        var run = runRepository.save(new PlatformOpsRun(triggerType, userId, snapshot, now));
        operationEventService.record(
                null,
                OperationEventSeverity.INFO,
                "OPS_DAILY_REPORT_REQUESTED",
                "platform-ops-daily-report",
                String.valueOf(run.id()),
                "PLATFORM_OPS_RUN",
                run.id(),
                userId,
                null,
                "Platform ops daily report action was requested.",
                payload("opsRunId", run.id(), "triggerType", triggerType.name(), "dueAt", dueAt.toString()));
        return run;
    }

    private LinkedHashMap<String, Object> snapshot(
            Long runId,
            OffsetDateTime dueAt,
            OffsetDateTime from,
            OffsetDateTime to
    ) {
        var recentEvents = operationEventRepository.findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(
                from,
                to,
                PageRequest.of(0, RECENT_EVENT_LIMIT));
        var aiUsageGroups = aiModelCallLogRepository.usageByOfficeAndFeature(
                from,
                to,
                AiModelCallLogStatus.SUCCEEDED,
                AiModelCallLogStatus.FAILED);
        var engineUsage = engineApiUsageEventRepository.summarizePlatformUsage(
                null,
                null,
                null,
                null,
                from,
                to);
        var diagnosisFindings = findingRepository
                .findBySourceAndWorkflowTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(
                        PlatformOpsFindingSource.AI_HARNESS,
                        "platform-ops-diagnosis",
                        from,
                        to,
                        PageRequest.of(0, RECENT_EVENT_LIMIT));
        var incidentBreakdown = incidentBreakdown(from, to);
        var failedOpsRunBreakdown = failedOpsRunBreakdown(from, to);

        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("runId", runId);
        snapshot.put("actionType", ACTION_TYPE);
        snapshot.put("riskClass", "READ_RECOMMEND_WRITE_ARTIFACT");
        snapshot.put("dueAt", dueAt.toString());
        snapshot.put("periodFrom", from.toString());
        snapshot.put("periodTo", to.toString());
        snapshot.put("controlBoundary", controlBoundary());
        snapshot.put("openIncidentCount", incidentBreakdown.get("openTotal"));
        snapshot.put("incidentBreakdown", incidentBreakdown);
        snapshot.put("failedOpsRunCount", runRepository.countByStatusAndStartedAtGreaterThanEqualAndStartedAtLessThan(
                PlatformOpsRunStatus.FAILED,
                from,
                to));
        snapshot.put("failedOpsRunBreakdown", failedOpsRunBreakdown);
        snapshot.put("photoPickup", photoPickupSnapshot());
        snapshot.put("operationEventCount", operationEventRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(from, to));
        snapshot.put("operationEventsBySeverity", operationEventRepository.summarizeSeverity(from, to).stream()
                .map(item -> Map.of(
                        "severity", item.getSeverity().name(),
                        "count", item.getEventCount()))
                .toList());
        snapshot.put("platformOpsFindingCount", findingRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(from, to));
        snapshot.put("platformOpsFindingsBySeverity", findingRepository.summarizeSeverity(from, to).stream()
                .map(item -> Map.of(
                        "severity", item.getSeverity().name(),
                        "count", item.getFindingCount()))
                .toList());
        snapshot.put("recentDiagnosisFindings", diagnosisFindings.stream()
                .map(finding -> Map.of(
                        "id", finding.id(),
                        "incidentId", nullable(finding.incidentId()),
                        "severity", finding.severity().name(),
                        "code", finding.code(),
                        "category", finding.category(),
                        "title", finding.title(),
                        "message", finding.message(),
                        "recommendation", nullable(finding.recommendation()),
                        "createdAt", finding.createdAt().toString()))
                .toList());
        snapshot.put("aiUsage", Map.of(
                "callCount", aiUsageGroups.stream().mapToLong(group -> value(group.getCallCount())).sum(),
                "succeededCount", aiUsageGroups.stream().mapToLong(group -> value(group.getSucceededCount())).sum(),
                "failedCount", aiUsageGroups.stream().mapToLong(group -> value(group.getFailedCount())).sum(),
                "inputTokens", aiUsageGroups.stream().mapToLong(group -> value(group.getInputTokens())).sum(),
                "outputTokens", aiUsageGroups.stream().mapToLong(group -> value(group.getOutputTokens())).sum(),
                "estimatedTotalCost", aiUsageGroups.stream()
                        .map(group -> group.getEstimatedTotalCost() == null ? BigDecimal.ZERO : group.getEstimatedTotalCost())
                        .reduce(BigDecimal.ZERO, BigDecimal::add).toPlainString(),
                "topGroups", aiUsageGroups.stream()
                        .sorted(Comparator.comparingLong(group -> -value(group.getCallCount())))
                        .limit(TOP_GROUP_LIMIT)
                        .map(group -> Map.of(
                                "officeId", nullable(group.getOfficeId()),
                                "feature", nullable(group.getFeature()),
                                "callCount", value(group.getCallCount()),
                                "inputTokens", value(group.getInputTokens()),
                                "outputTokens", value(group.getOutputTokens())))
                        .toList()));
        snapshot.put("engineUsage", Map.of(
                "eventCount", engineUsage.stream().mapToLong(group -> value(group.getEventCount())).sum(),
                "requestUnits", engineUsage.stream().mapToLong(group -> value(group.getRequestUnits())).sum(),
                "topGroups", engineUsage.stream()
                        .sorted(Comparator.comparingLong(group -> -value(group.getEventCount())))
                        .limit(TOP_GROUP_LIMIT)
                        .map(group -> Map.of(
                                "capability", nullable(group.getCapability()),
                                "operation", nullable(group.getOperation()),
                                "eventCount", value(group.getEventCount()),
                                "requestUnits", value(group.getRequestUnits())))
                        .toList()));
        snapshot.put("recentEvents", recentEvents.stream()
                .map(event -> Map.of(
                        "id", event.id(),
                        "severity", event.severity().name(),
                        "eventType", event.eventType(),
                        "workflowType", nullable(event.workflowType()),
                        "resourceType", nullable(event.resourceType()),
                        "resourceId", nullable(event.resourceId()),
                        "createdAt", event.createdAt().toString()))
                .toList());
        snapshot.put("pControlActions", pControlActions(snapshot));
        snapshot.put("deterministicSignals", deterministicSignals(snapshot));
        snapshot.put("redactionPolicy", redactionPolicy());
        return snapshot;
    }

    private Map<String, Object> incidentBreakdown(OffsetDateTime from, OffsetDateTime to) {
        var activeIncidents = incidentRepository.findByStatusInOrderByLastSeenAtDescIdDesc(
                ACTIVE_INCIDENT_STATUSES,
                PageRequest.of(0, INCIDENT_BREAKDOWN_LIMIT));
        var openTotal = incidentRepository.countByStatusIn(ACTIVE_INCIDENT_STATUSES);
        var realActive = activeIncidents.stream()
                .filter(incident -> !incident.lastSeenAt().isBefore(from))
                .count();
        var loadedStale = activeIncidents.stream()
                .filter(incident -> incident.lastSeenAt().isBefore(from))
                .count();
        var notLoaded = Math.max(0, openTotal - activeIncidents.size());
        var newToday = activeIncidents.stream()
                .filter(incident -> !incident.firstSeenAt().isBefore(from) && incident.firstSeenAt().isBefore(to))
                .count();
        var resolvedToday = incidentRepository.countByStatusAndResolvedAtGreaterThanEqualAndResolvedAtLessThan(
                PlatformOpsIncidentStatus.RESOLVED,
                from,
                to);
        var byCategory = new LinkedHashMap<String, Long>();
        activeIncidents.forEach(incident -> byCategory.merge(incident.category(), 1L, Long::sum));

        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("openTotal", openTotal);
        snapshot.put("realActive", realActive);
        snapshot.put("stale", loadedStale + notLoaded);
        snapshot.put("newToday", newToday);
        snapshot.put("resolvedToday", resolvedToday);
        snapshot.put("loadedOpenIncidentCount", activeIncidents.size());
        snapshot.put("classification", "realActive=lastSeenAt within report period, stale=open but not re-detected in report period");
        snapshot.put("byCategory", byCategory);
        return snapshot;
    }

    private Map<String, Object> failedOpsRunBreakdown(OffsetDateTime from, OffsetDateTime to) {
        var total = runRepository.countByStatusAndStartedAtGreaterThanEqualAndStartedAtLessThan(
                PlatformOpsRunStatus.FAILED,
                from,
                to);
        var restartRelated = runRepository.countByStatusAndFailureCodeAndStartedAtGreaterThanEqualAndStartedAtLessThan(
                PlatformOpsRunStatus.FAILED,
                FLOW_INTERRUPTED_BY_RESTART,
                from,
                to);
        var actionable = runRepository.countByStatusAndFailureCodeOtherThan(
                PlatformOpsRunStatus.FAILED,
                FLOW_INTERRUPTED_BY_RESTART,
                from,
                to);

        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("total", total);
        snapshot.put("restartRelated", restartRelated);
        snapshot.put("actionable", actionable);
        snapshot.put("restartFailureCode", FLOW_INTERRUPTED_BY_RESTART);
        snapshot.put("classification", "restartRelated failed runs are deployment/restart effects unless they repeat after restart grace.");
        return snapshot;
    }

    private Map<String, Object> photoPickupSnapshot() {
        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("uploadedPendingOriginalPickup", photoRepository.countByStatusAndOriginalPickupStatus(
                PhotoStatus.UPLOADED,
                PhotoPickupStatus.PENDING));
        snapshot.put("allPendingOriginalPickup", photoRepository.countByOriginalPickupStatus(PhotoPickupStatus.PENDING));
        snapshot.put("note", "uploadedPendingOriginalPickup is the real photo original pickup backlog candidate.");
        return snapshot;
    }

    private PlatformOpsFinding snapshotFinding(
            PlatformOpsRun run,
            Map<String, Object> snapshot,
            OffsetDateTime now
    ) {
        return new PlatformOpsFinding(
                null,
                run.id(),
                null,
                PlatformOpsFindingSeverity.INFO,
                PlatformOpsFindingSource.SYSTEM_DIAGNOSIS,
                "OPS_DAILY_REPORT_SNAPSHOT_READY",
                "OPS_DAILY_REPORT",
                "Platform ops daily report evidence snapshot is ready",
                "The Flower platform-ops flow collected redacted deterministic evidence for a daily operations report.",
                "PLATFORM_OPS_RUN",
                String.valueOf(run.id()),
                "platform-ops-daily-report",
                String.valueOf(run.id()),
                Map.of(
                        "periodFrom", snapshot.get("periodFrom"),
                        "periodTo", snapshot.get("periodTo"),
                        "openIncidentCount", snapshot.get("openIncidentCount"),
                        "incidentBreakdown", snapshot.get("incidentBreakdown"),
                        "failedOpsRunCount", snapshot.get("failedOpsRunCount"),
                        "failedOpsRunBreakdown", snapshot.get("failedOpsRunBreakdown"),
                        "photoPickup", snapshot.get("photoPickup")),
                "Review the daily report if WARN/ERROR counts, open incidents, or usage spikes increased.",
                now);
    }

    private PlatformOpsFinding reportFinding(
            PlatformOpsRun run,
            Path reportPath,
            Map<String, Object> snapshot,
            List<PlatformOpsFinding> findings,
            OffsetDateTime now
    ) {
        return new PlatformOpsFinding(
                null,
                run.id(),
                null,
                severity(findings),
                PlatformOpsFindingSource.SYSTEM_DIAGNOSIS,
                "PLATFORM_OPS_DAILY_REPORT_READY",
                "OPS_DAILY_REPORT",
                "Platform ops daily report is ready",
                "A sanitized daily operations report was generated by the controlled platform-ops action flow.",
                "PLATFORM_OPS_RUN",
                String.valueOf(run.id()),
                "platform-ops-daily-report",
                String.valueOf(run.id()),
                Map.of(
                        "reportPath", reportPath.toString(),
                        "periodFrom", snapshot.get("periodFrom"),
                        "periodTo", snapshot.get("periodTo"),
                        "openIncidentCount", snapshot.get("openIncidentCount"),
                        "incidentBreakdown", snapshot.get("incidentBreakdown"),
                        "failedOpsRunCount", snapshot.get("failedOpsRunCount"),
                        "failedOpsRunBreakdown", snapshot.get("failedOpsRunBreakdown"),
                        "photoPickup", snapshot.get("photoPickup"),
                        "pControlActions", snapshot.get("pControlActions"),
                        "aiHarnessStatus", snapshot.get("aiHarnessStatus")),
                "Review the daily report and decide whether any suggested action should be submitted as a separate controlled action.",
                now);
    }

    private PlatformOpsDailyReport dailyReport(
            PlatformOpsRun run,
            Map<String, Object> snapshot,
            List<PlatformOpsFinding> findings,
            Path reportPath,
            OffsetDateTime now
    ) {
        var summary = aiSummary(findings);
        return new PlatformOpsDailyReport(
                run.id(),
                offsetDateTime(snapshot.get("dueAt"), run.startedAt()),
                offsetDateTime(snapshot.get("periodFrom"), run.startedAt().minusDays(1)),
                offsetDateTime(snapshot.get("periodTo"), run.startedAt()),
                aiStatus(findings),
                severity(findings),
                "ArchDox platform operations daily report",
                summary == null ? deterministicSummary(snapshot) : summary,
                reportPath.toString(),
                run.aiHarnessRunId(),
                signalList(findings, "pLikeCurrentFindings"),
                signalList(findings, "iLikeAccumulatedSignals"),
                signalList(findings, "dLikeTrendSignals"),
                signalList(findings, "recommendations"),
                Map.of(
                        "runId", run.id(),
                        "aiHarnessStatus", snapshot.getOrDefault("aiHarnessStatus", ""),
                        "deterministicSignals", snapshot.getOrDefault("deterministicSignals", List.of()),
                        "pControlActions", snapshot.getOrDefault("pControlActions", List.of())),
                now);
    }

    private Path writeReport(Long runId, Map<String, Object> snapshot, List<PlatformOpsFinding> findings) {
        try {
            var directory = Path.of(automationSettingsService.settings().dailyReportDirectory()).toAbsolutePath().normalize();
            Files.createDirectories(directory);
            var timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(offsetDateTime(snapshot.get("periodTo"), OffsetDateTime.now()));
            var path = directory.resolve("platform-ops-daily-" + timestamp + "-run-" + runId + ".md");
            Files.writeString(path, renderMarkdown(snapshot, findings), StandardCharsets.UTF_8);
            return path;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write platform ops daily report", ex);
        }
    }

    private String renderMarkdown(Map<String, Object> snapshot, List<PlatformOpsFinding> findings) {
        var builder = new StringBuilder();
        builder.append("# ArchDox Platform Ops Daily Report\n\n");
        builder.append("- Run ID: ").append(snapshot.get("runId")).append('\n');
        builder.append("- Action: ").append(snapshot.get("actionType")).append('\n');
        builder.append("- Due at: ").append(snapshot.get("dueAt")).append('\n');
        builder.append("- Period: ").append(snapshot.get("periodFrom")).append(" ~ ").append(snapshot.get("periodTo")).append('\n');
        builder.append("- Open incidents: ").append(snapshot.get("openIncidentCount")).append('\n');
        builder.append("- Failed ops runs: ").append(snapshot.get("failedOpsRunCount")).append('\n');
        builder.append("- Real active incidents: ").append(childMap(snapshot, "incidentBreakdown").getOrDefault("realActive", 0)).append('\n');
        builder.append("- Stale open incidents: ").append(childMap(snapshot, "incidentBreakdown").getOrDefault("stale", 0)).append('\n');
        builder.append("- Restart-related failed ops runs: ").append(childMap(snapshot, "failedOpsRunBreakdown").getOrDefault("restartRelated", 0)).append('\n');
        builder.append("- Photo pickup pending originals: ").append(childMap(snapshot, "photoPickup").getOrDefault("uploadedPendingOriginalPickup", 0)).append('\n');
        builder.append("- Operation events: ").append(snapshot.get("operationEventCount")).append('\n');
        builder.append("- Platform ops findings: ").append(snapshot.get("platformOpsFindingCount")).append('\n');
        builder.append("- AI harness status: ").append(snapshot.getOrDefault("aiHarnessStatus", "UNKNOWN")).append('\n');

        builder.append("\n## Operator Summary\n\n");
        builder.append(aiSummary(findings) == null ? deterministicSummary(snapshot) : aiSummary(findings)).append("\n");

        appendSignalSection(builder, "P-like Current Signals", signalList(findings, "pLikeCurrentFindings"));
        appendSignalSection(builder, "I-like Accumulated Signals", signalList(findings, "iLikeAccumulatedSignals"));
        appendSignalSection(builder, "D-like Trend Signals", signalList(findings, "dLikeTrendSignals"));
        appendSignalSection(builder, "Recommendations", signalList(findings, "recommendations"));
        appendActionSection(builder, actionList(snapshot));

        builder.append("\n## AI Findings\n\n");
        var aiFindings = findings.stream()
                .filter(finding -> finding.source() == PlatformOpsFindingSource.AI_HARNESS)
                .filter(finding -> !"OPS_DAILY_REPORT_AI_SUMMARY".equals(finding.code()))
                .toList();
        if (aiFindings.isEmpty()) {
            builder.append("- No AI issue finding was recorded.\n");
        } else {
            for (var finding : aiFindings) {
                builder.append("- [").append(finding.severity()).append("] ")
                        .append(finding.title()).append(" — ")
                        .append(finding.message()).append('\n');
                if (finding.recommendation() != null && !finding.recommendation().isBlank()) {
                    builder.append("  - Recommendation: ").append(finding.recommendation()).append('\n');
                }
            }
        }

        builder.append("\n## AI Usage\n\n");
        appendMap(builder, childMap(snapshot, "aiUsage"), List.of("callCount", "succeededCount", "failedCount", "inputTokens", "outputTokens", "estimatedTotalCost"));
        builder.append("\n## Incident Breakdown\n\n");
        appendMap(builder, childMap(snapshot, "incidentBreakdown"), List.of("openTotal", "realActive", "stale", "newToday", "resolvedToday"));
        builder.append("\n## Failed Ops Run Breakdown\n\n");
        appendMap(builder, childMap(snapshot, "failedOpsRunBreakdown"), List.of("total", "actionable", "restartRelated"));
        builder.append("\n## Photo Pickup\n\n");
        appendMap(builder, childMap(snapshot, "photoPickup"), List.of("uploadedPendingOriginalPickup", "allPendingOriginalPickup"));
        builder.append("\n## Engine/MCP Usage\n\n");
        appendMap(builder, childMap(snapshot, "engineUsage"), List.of("eventCount", "requestUnits"));
        builder.append("\n## Redaction\n\n");
        builder.append(snapshot.get("redactionPolicy")).append('\n');
        return builder.toString();
    }

    private void appendSignalSection(StringBuilder builder, String title, List<String> values) {
        builder.append("\n## ").append(title).append("\n\n");
        if (values.isEmpty()) {
            builder.append("- None recorded.\n");
            return;
        }
        for (var value : values) {
            builder.append("- ").append(value).append('\n');
        }
    }

    private void appendActionSection(StringBuilder builder, List<Map<String, Object>> actions) {
        builder.append("\n## P-like Control Action Candidates\n\n");
        if (actions.isEmpty()) {
            builder.append("- No P-like control action candidate was generated.\n");
            return;
        }
        for (var action : actions) {
            builder.append("- [")
                    .append(action.getOrDefault("executionMode", "MANUAL_REVIEW"))
                    .append("] ")
                    .append(action.getOrDefault("title", action.getOrDefault("code", "Action candidate")))
                    .append(" - ")
                    .append(action.getOrDefault("reason", "Review this P-like signal."))
                    .append('\n');
            builder.append("  - Signal: ").append(action.getOrDefault("signalKey", "-")).append('\n');
            builder.append("  - Expected effect: ").append(action.getOrDefault("expectedEffect", "-")).append('\n');
        }
    }

    private void appendMap(StringBuilder builder, Map<String, Object> source, List<String> keys) {
        for (var key : keys) {
            builder.append("- ").append(key).append(": ").append(source.getOrDefault(key, 0)).append('\n');
        }
    }

    private void recordSnapshotEvent(PlatformOpsRun run, Map<String, Object> snapshot) {
        operationEventService.record(
                null,
                OperationEventSeverity.INFO,
                "OPS_DAILY_REPORT_SNAPSHOT_READY",
                "platform-ops-daily-report",
                String.valueOf(run.id()),
                "PLATFORM_OPS_RUN",
                run.id(),
                run.startedByUserId(),
                null,
                "Platform ops daily report evidence snapshot is ready.",
                payload(
                        "opsRunId", run.id(),
                        "periodFrom", snapshot.get("periodFrom"),
                        "periodTo", snapshot.get("periodTo"),
                        "openIncidentCount", snapshot.get("openIncidentCount"),
                        "incidentBreakdown", snapshot.get("incidentBreakdown"),
                        "failedOpsRunCount", snapshot.get("failedOpsRunCount"),
                        "pControlActions", snapshot.get("pControlActions")));
    }

    private void recordReportEvent(
            PlatformOpsRun run,
            Path reportPath,
            Map<String, Object> snapshot,
            List<PlatformOpsFinding> findings
    ) {
        operationEventService.record(
                null,
                severity(findings) == PlatformOpsFindingSeverity.CRITICAL ? OperationEventSeverity.WARN : OperationEventSeverity.INFO,
                "PLATFORM_OPS_DAILY_REPORT_READY",
                "platform-ops-daily-report",
                String.valueOf(run.id()),
                "PLATFORM_OPS_RUN",
                run.id(),
                run.startedByUserId(),
                null,
                "Platform ops daily report was generated.",
                payload(
                        "opsRunId", run.id(),
                        "reportPath", reportPath.toString(),
                        "periodFrom", snapshot.get("periodFrom"),
                        "periodTo", snapshot.get("periodTo"),
                        "openIncidentCount", snapshot.get("openIncidentCount"),
                        "incidentBreakdown", snapshot.get("incidentBreakdown"),
                        "failedOpsRunCount", snapshot.get("failedOpsRunCount"),
                        "failedOpsRunBreakdown", snapshot.get("failedOpsRunBreakdown"),
                        "photoPickup", snapshot.get("photoPickup"),
                        "pControlActions", snapshot.get("pControlActions"),
                        "aiHarnessStatus", snapshot.get("aiHarnessStatus")));
    }

    private void markAiHarnessSkipped(PlatformOpsRun run, String reason) {
        run.replaceSnapshot(withNextAiHarness(run.inputSnapshotJson(), "SKIPPED", null, null, reason));
        operationEventService.record(
                null,
                OperationEventSeverity.INFO,
                "OPS_DAILY_REPORT_AI_HARNESS_SKIPPED",
                "platform-ops-daily-report",
                String.valueOf(run.id()),
                "PLATFORM_OPS_RUN",
                run.id(),
                run.startedByUserId(),
                null,
                "Platform ops daily report AI harness was skipped.",
                payload("opsRunId", run.id(), "reason", reason));
    }

    private void recordAiHarnessSubmitted(PlatformOpsRun run) {
        operationEventService.record(
                null,
                OperationEventSeverity.INFO,
                "OPS_DAILY_REPORT_AI_HARNESS_SUBMITTED",
                "platform-ops-daily-report",
                String.valueOf(run.id()),
                "PLATFORM_OPS_RUN",
                run.id(),
                run.startedByUserId(),
                null,
                "Platform ops daily report flow submitted the AI harness.",
                payload("opsRunId", run.id(), "harnessRunId", run.aiHarnessRunId()));
    }

    private void recordAutoDiagnosisRequested(PlatformOpsRun run, List<PlatformOpsRun> diagnosisRuns) {
        operationEventService.record(
                null,
                OperationEventSeverity.INFO,
                "OPS_DAILY_REPORT_AUTO_DIAGNOSIS_REQUESTED",
                "platform-ops-daily-report",
                String.valueOf(run.id()),
                "PLATFORM_OPS_RUN",
                run.id(),
                run.startedByUserId(),
                null,
                "Platform ops daily report flow requested incident diagnoses before report generation.",
                payload(
                        "opsRunId", run.id(),
                        "diagnosisRunIds", diagnosisRuns.stream().map(PlatformOpsRun::id).toList(),
                        "incidentIds", diagnosisRuns.stream().map(PlatformOpsRun::incidentId).toList()));
    }

    private OpsDailyReportInput input(PlatformOpsRun run) {
        var snapshot = run.inputSnapshotJson();
        return new OpsDailyReportInput(
                String.valueOf(run.id()),
                stringValue(snapshot.get("dueAt")),
                stringValue(snapshot.get("periodFrom")),
                stringValue(snapshot.get("periodTo")),
                snapshot);
    }

    private PlatformOpsRun run(Long runId) {
        return runRepository.findById(runId)
                .orElseThrow(() -> new NotFoundException("Platform ops run not found"));
    }

    private PlatformOpsFindingSeverity autoDiagnosisMinSeverity(String configuredSeverity) {
        try {
            return PlatformOpsFindingSeverity.valueOf(configuredSeverity);
        } catch (RuntimeException ex) {
            return PlatformOpsFindingSeverity.WARN;
        }
    }

    private boolean atLeastSeverity(PlatformOpsFindingSeverity value, PlatformOpsFindingSeverity minimum) {
        return value != null && value.ordinal() >= minimum.ordinal();
    }

    private List<IncidentDiagnosisTarget> selectAutoDiagnosisTargets(int limit, PlatformOpsFindingSeverity minSeverity) {
        if (limit <= 0) {
            return List.of();
        }
        var incidents = incidentRepository.findByStatusInOrderByLastSeenAtDescIdDesc(
                ACTIVE_INCIDENT_STATUSES,
                PageRequest.of(0, Math.max(50, limit * 10)));
        var grouped = new LinkedHashMap<IncidentGroupKey, List<PlatformOpsIncident>>();
        incidents.stream()
                .filter(incident -> atLeastSeverity(incident.severity(), minSeverity))
                .filter(incident -> !alreadyRunningOrFreshlyDiagnosed(incident))
                .forEach(incident -> grouped.computeIfAbsent(IncidentGroupKey.from(incident), key -> new ArrayList<>())
                        .add(incident));

        return grouped.entrySet().stream()
                .map(entry -> new IncidentDiagnosisTarget(
                        representativeIncident(entry.getValue()),
                        entry.getKey().asText(),
                        entry.getValue().size()))
                .sorted(Comparator
                        .comparingInt((IncidentDiagnosisTarget target) -> target.incident().severity().ordinal())
                        .reversed()
                        .thenComparing(target -> target.incident().lastSeenAt(), Comparator.reverseOrder())
                        .thenComparing(target -> target.incident().id() == null ? 0L : target.incident().id(), Comparator.reverseOrder()))
                .limit(limit)
                .toList();
    }

    private boolean alreadyRunningOrFreshlyDiagnosed(PlatformOpsIncident incident) {
        return runRepository.existsByIncidentIdAndTriggerTypeInAndStatus(
                incident.id(),
                INCIDENT_DIAGNOSIS_TRIGGER_TYPES,
                PlatformOpsRunStatus.RUNNING)
                || runRepository.existsByIncidentIdAndTriggerTypeInAndStartedAtGreaterThanEqual(
                incident.id(),
                INCIDENT_DIAGNOSIS_TRIGGER_TYPES,
                incident.lastSeenAt());
    }

    private PlatformOpsIncident representativeIncident(List<PlatformOpsIncident> incidents) {
        return incidents.stream()
                .max(Comparator
                        .comparingInt((PlatformOpsIncident incident) -> incident.severity().ordinal())
                        .thenComparing(PlatformOpsIncident::lastSeenAt)
                        .thenComparing(incident -> incident.id() == null ? 0L : incident.id()))
                .orElseThrow();
    }

    private Map<String, Object> autoDiagnosisSnapshot(
            String status,
            String reason,
            PlatformOpsRun systemRun,
            List<PlatformOpsRun> incidentRuns,
            List<IncidentDiagnosisTarget> incidentTargets,
            int incidentLimit,
            PlatformOpsFindingSeverity minSeverity
    ) {
        var runIds = new ArrayList<Long>();
        if (systemRun != null) {
            runIds.add(systemRun.id());
        }
        runIds.addAll(incidentRuns.stream().map(PlatformOpsRun::id).toList());

        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("status", status);
        snapshot.put("reason", reason);
        snapshot.put("systemDiagnosisRunId", systemRun == null ? null : systemRun.id());
        snapshot.put("incidentDiagnosisRunIds", incidentRuns.stream().map(PlatformOpsRun::id).toList());
        snapshot.put("runIds", runIds);
        snapshot.put("incidentIds", incidentRuns.stream().map(PlatformOpsRun::incidentId).toList());
        snapshot.put("incidentLimit", incidentLimit);
        snapshot.put("minSeverity", minSeverity.name());
        snapshot.put("groupingPolicy", "category+office+primaryResource");
        snapshot.put("representativeIncidentCount", incidentTargets.size());
        snapshot.put("requestedCount", runIds.size());
        snapshot.put("representativeIncidents", incidentTargets.stream()
                .map(target -> Map.of(
                        "incidentId", target.incident().id(),
                        "groupKey", target.groupKey(),
                        "groupSize", target.groupSize(),
                        "severity", target.incident().severity().name(),
                        "category", target.incident().category(),
                        "officeId", nullable(target.incident().officeId()),
                        "resourceType", nullable(target.incident().primaryResourceType()),
                        "resourceId", nullable(target.incident().primaryResourceId()),
                        "lastSeenAt", target.incident().lastSeenAt().toString()))
                .toList());
        return snapshot;
    }

    private String modelIdFromHarnessPolicy() {
        var policy = aiHarnessPolicyExecutionService.resolve(AiHarnessPolicyKey.PLATFORM_OPS_DAILY_REPORT);
        return policy.runnable() ? policy.plan().modelId().asString() : policy.unavailableReason();
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
        nextAiHarness.put("type", "OpsDailyReportHarness");
        nextAiHarness.put("status", status);
        put(nextAiHarness, "harnessRunId", harnessRunId);
        put(nextAiHarness, "modelId", modelId);
        put(nextAiHarness, "reason", reason);
        snapshot.put("nextAiHarness", nextAiHarness);
        return snapshot;
    }

    private Map<String, Object> deterministicSignals(Map<String, Object> snapshot) {
        var pLike = new LinkedHashMap<String, Object>();
        pLike.put("openIncidentCount", snapshot.get("openIncidentCount"));
        pLike.put("incidentBreakdown", snapshot.get("incidentBreakdown"));
        pLike.put("failedOpsRunCount", snapshot.get("failedOpsRunCount"));
        pLike.put("failedOpsRunBreakdown", snapshot.get("failedOpsRunBreakdown"));
        pLike.put("photoPickup", snapshot.get("photoPickup"));
        pLike.put("operationEventCount", snapshot.get("operationEventCount"));
        var iLike = new LinkedHashMap<String, Object>();
        iLike.put("aiUsage", snapshot.get("aiUsage"));
        iLike.put("engineUsage", snapshot.get("engineUsage"));
        var dLike = new LinkedHashMap<String, Object>();
        dLike.put("note", "Trend is based on the current evidence window until a longer baseline is added.");
        return Map.of("pLike", pLike, "iLike", iLike, "dLike", dLike);
    }

    private List<Map<String, Object>> pControlActions(Map<String, Object> snapshot) {
        var actions = new ArrayList<Map<String, Object>>();
        var incidentBreakdown = childMap(snapshot, "incidentBreakdown");
        var failedOpsRunBreakdown = childMap(snapshot, "failedOpsRunBreakdown");
        var photoPickup = childMap(snapshot, "photoPickup");

        var realActiveIncidents = numeric(incidentBreakdown.get("realActive"));
        if (realActiveIncidents > 0) {
            actions.add(actionCandidate(
                    "REVIEW_ACTIVE_INCIDENTS",
                    "활성 운영 이슈 확인",
                    "openIncidentCount",
                    realActiveIncidents,
                    "MANUAL_REVIEW",
                    "LOW",
                    "최근 리포트 기간에 다시 감지된 incident가 있습니다.",
                    "이슈/진단 화면에서 실제 활성 incident를 확인하고 필요한 경우 incident diagnosis를 실행합니다.",
                    "활성 incident가 사용자가 체감하는 문제인지, 자동 해소 대상인지, 사람 조치가 필요한지 분리합니다.",
                    "platform-ops-incidents",
                    null));
        }

        var staleIncidents = numeric(incidentBreakdown.get("stale"));
        if (staleIncidents > 0) {
            actions.add(actionCandidate(
                    "RUN_DETECTION_FOR_STALE_INCIDENTS",
                    "오래된 이슈 재감지",
                    "incidentBreakdown.stale",
                    staleIncidents,
                    "MANUAL_FLOW",
                    "LOW",
                    "열려 있지만 최근 리포트 기간에 다시 감지되지 않은 incident가 남아 있습니다.",
                    "운영 감지를 다시 실행해 더 이상 문제가 아닌 incident를 자동 해소 대상으로 확인합니다.",
                    "stale incident가 계속 열린 채로 남아 운영 리포트를 과장하는 일을 줄입니다.",
                    "platform-ops-detection",
                    "detectPlatformStuckHealth"));
        }

        var actionableFailedRuns = numeric(failedOpsRunBreakdown.get("actionable"));
        if (actionableFailedRuns > 0) {
            actions.add(actionCandidate(
                    "REVIEW_ACTIONABLE_FAILED_OPS_RUNS",
                    "실패 운영 Flow 확인",
                    "failedOpsRunBreakdown.actionable",
                    actionableFailedRuns,
                    "MANUAL_REVIEW",
                    "LOW",
                    "재시작 영향으로 분류되지 않은 운영 Flow 실패가 있습니다.",
                    "운영 자동화 화면에서 실패 run의 failureCode와 snapshot을 확인합니다.",
                    "배포 영향이 아닌 실제 실행 실패를 찾아 다음 flow 재시도 또는 코드 수정 대상으로 분리합니다.",
                    "platform-ops-automation",
                    null));
        }

        var restartRelatedRuns = numeric(failedOpsRunBreakdown.get("restartRelated"));
        if (restartRelatedRuns > 0) {
            actions.add(actionCandidate(
                    "OBSERVE_RESTART_RELATED_FAILURES",
                    "재시작 영향 실패 관찰",
                    "failedOpsRunBreakdown.restartRelated",
                    restartRelatedRuns,
                    "OBSERVE",
                    "INFO",
                    "배포 또는 서버 재시작 영향으로 분류된 운영 Flow 실패가 있습니다.",
                    "같은 실패가 재시작 이후에도 반복되는지 다음 리포트에서 확인합니다.",
                    "배포 노이즈를 장애로 과장하지 않고, 반복될 때만 조치 대상으로 올립니다.",
                    "platform-ops-automation",
                    null));
        }

        var pendingPhotoPickup = numeric(photoPickup.get("uploadedPendingOriginalPickup"));
        if (pendingPhotoPickup > 0) {
            actions.add(actionCandidate(
                    "RUN_DETECTION_FOR_PHOTO_PICKUP",
                    "사진 원본 수거 상태 재감지",
                    "photoPickup.uploadedPendingOriginalPickup",
                    pendingPhotoPickup,
                    "MANUAL_FLOW",
                    "LOW",
                    "업로드된 사진 중 원본 pickup이 아직 대기 중인 항목이 있습니다.",
                    "운영 감지를 다시 실행해 삭제/완료/비업로드 상태로 바뀐 사진 이슈를 자동 해소하고 실제 stuck 항목만 남깁니다.",
                    "PHOTO_PICKUP_STUCK 오탐을 줄이고, 실제 Agent pickup 지연만 운영자가 보게 합니다.",
                    "platform-ops-detection",
                    "detectPlatformStuckHealth"));
        }

        var errorEvents = severityCount(snapshot, "ERROR", "CRITICAL");
        if (errorEvents > 0) {
            actions.add(actionCandidate(
                    "REVIEW_ERROR_OPERATION_EVENTS",
                    "오류 운영 이벤트 확인",
                    "operationEventsBySeverity.ERROR",
                    errorEvents,
                    "MANUAL_REVIEW",
                    "LOW",
                    "리포트 기간에 오류 또는 긴급 운영 이벤트가 기록되었습니다.",
                    "이벤트/로그 화면에서 workflowType, resourceType, eventType 기준으로 반복 원인을 확인합니다.",
                    "단순 이벤트 총량이 아니라 실제 오류 이벤트가 사용자 영향으로 이어지는지 판단합니다.",
                    "platform-ops-events",
                    null));
        }

        return actions;
    }

    private Map<String, Object> actionCandidate(
            String code,
            String title,
            String signalKey,
            long signalValue,
            String executionMode,
            String riskLevel,
            String reason,
            String operatorAction,
            String expectedEffect,
            String uiTarget,
            String existingAdminAction
    ) {
        var action = new LinkedHashMap<String, Object>();
        action.put("code", code);
        action.put("title", title);
        action.put("signalKey", signalKey);
        action.put("signalValue", signalValue);
        action.put("executionMode", executionMode);
        action.put("riskLevel", riskLevel);
        action.put("allowed", true);
        action.put("reason", reason);
        action.put("operatorAction", operatorAction);
        action.put("expectedEffect", expectedEffect);
        action.put("uiTarget", uiTarget);
        action.put("existingAdminAction", nullable(existingAdminAction));
        return action;
    }

    private Map<String, Object> controlBoundary() {
        return Map.of(
                "l2Orchestration", "Flower platform-ops flow owns request, wait, timeout, and recovery state.",
                "l3Cognitive", "OpsDailyReportHarness may summarize and propose only.",
                "l4DecisionControl", "AI policy, scope, quota, and budget are checked before model execution.",
                "l6RuntimeInterlock", "The action uses a single ops run id, duplicate running checks, and final stale state checks.",
                "l7ActionExecution", "Only PlatformOpsDailyReportService writes the DB report and markdown artifact.",
                "l9Feedback", "Run snapshot, findings, operation events, AI usage, and report table are recorded.");
    }

    private Map<String, Object> redactionPolicy() {
        return Map.of(
                "secrets", "excluded",
                "rawFiles", "excluded",
                "tokens", "excluded",
                "scope", "operational ids, statuses, timestamps, error categories, counts, and redacted payload only");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> childMap(Map<String, Object> snapshot, String key) {
        var value = snapshot.get(key);
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> actionList(Map<String, Object> snapshot) {
        var value = snapshot.get("pControlActions");
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        return values.stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item)
                .toList();
    }

    @SuppressWarnings("unchecked")
    private long severityCount(Map<String, Object> snapshot, String... severities) {
        var value = snapshot.get("operationEventsBySeverity");
        if (!(value instanceof List<?> values)) {
            return 0;
        }
        var severitySet = List.of(severities);
        return values.stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item)
                .filter(item -> severitySet.contains(String.valueOf(item.get("severity"))))
                .mapToLong(item -> numeric(item.get("count")))
                .sum();
    }

    private long numeric(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private String deterministicSummary(Map<String, Object> snapshot) {
        var incidentBreakdown = childMap(snapshot, "incidentBreakdown");
        var failedBreakdown = childMap(snapshot, "failedOpsRunBreakdown");
        var photoPickup = childMap(snapshot, "photoPickup");
        return "Daily report generated from deterministic platform operations evidence. Real active incidents: "
                + incidentBreakdown.getOrDefault("realActive", 0)
                + ", stale open incidents: "
                + incidentBreakdown.getOrDefault("stale", 0)
                + ", restart-related failed ops runs: "
                + failedBreakdown.getOrDefault("restartRelated", 0)
                + ", photo pickup pending originals: "
                + photoPickup.getOrDefault("uploadedPendingOriginalPickup", 0)
                + ", operation events: "
                + snapshot.getOrDefault("operationEventCount", 0)
                + ".";
    }

    private String aiSummary(List<PlatformOpsFinding> findings) {
        return findings.stream()
                .filter(finding -> "OPS_DAILY_REPORT_AI_SUMMARY".equals(finding.code()))
                .map(PlatformOpsFinding::message)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }

    private String aiStatus(List<PlatformOpsFinding> findings) {
        return findings.stream()
                .filter(finding -> "OPS_DAILY_REPORT_AI_SUMMARY".equals(finding.code()))
                .map(finding -> stringValue(finding.evidenceJson().get("reviewStatus")))
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse("WATCH");
    }

    private List<String> signalList(List<PlatformOpsFinding> findings, String key) {
        return findings.stream()
                .filter(finding -> "OPS_DAILY_REPORT_AI_SUMMARY".equals(finding.code()))
                .map(finding -> stringValue(finding.evidenceJson().get(key)))
                .filter(value -> value != null && !value.isBlank())
                .flatMap(value -> value.lines().filter(line -> !line.isBlank()).map(String::trim))
                .toList();
    }

    private PlatformOpsFindingSeverity severity(List<PlatformOpsFinding> findings) {
        if (findings.stream().anyMatch(finding -> finding.severity() == PlatformOpsFindingSeverity.CRITICAL)) {
            return PlatformOpsFindingSeverity.CRITICAL;
        }
        if (findings.stream().anyMatch(finding -> finding.severity() == PlatformOpsFindingSeverity.ERROR)) {
            return PlatformOpsFindingSeverity.ERROR;
        }
        if (findings.stream().anyMatch(finding -> finding.severity() == PlatformOpsFindingSeverity.WARN)) {
            return PlatformOpsFindingSeverity.WARN;
        }
        return PlatformOpsFindingSeverity.INFO;
    }

    private long value(Number value) {
        return value == null ? 0 : value.longValue();
    }

    private Object nullable(Object value) {
        return value == null ? "" : value;
    }

    private OffsetDateTime offsetDateTime(Object value, OffsetDateTime fallback) {
        if (value == null || String.valueOf(value).isBlank()) {
            return fallback;
        }
        return OffsetDateTime.parse(String.valueOf(value));
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private List<Long> longList(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        return values.stream()
                .map(this::longValue)
                .filter(item -> item != null)
                .toList();
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void put(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private record IncidentDiagnosisTarget(
            PlatformOpsIncident incident,
            String groupKey,
            int groupSize
    ) {
    }

    private record IncidentGroupKey(
            Long officeId,
            String category,
            String resourceType,
            String resourceId
    ) {
        static IncidentGroupKey from(PlatformOpsIncident incident) {
            return new IncidentGroupKey(
                    incident.officeId(),
                    blankToGroupValue(incident.category()),
                    blankToGroupValue(incident.primaryResourceType()),
                    blankToGroupValue(incident.primaryResourceId()));
        }

        String asText() {
            return "office=" + (officeId == null ? "platform" : officeId)
                    + "|category=" + category
                    + "|resource=" + resourceType + ":" + resourceId;
        }

        private static String blankToGroupValue(String value) {
            return value == null || value.isBlank() ? "GLOBAL" : value.trim();
        }
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
