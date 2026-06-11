package com.archdox.cloud.platformops.application;

import com.archdox.cloud.aipolicy.domain.AiModelCallLogStatus;
import com.archdox.cloud.aipolicy.infra.AiModelCallLogRepository;
import com.archdox.cloud.engine.usage.infra.EngineApiUsageEventRepository;
import com.archdox.cloud.operation.application.OperationEventService;
import com.archdox.cloud.operation.domain.OperationEventSeverity;
import com.archdox.cloud.operation.infra.OperationEventRepository;
import com.archdox.cloud.platformops.domain.PlatformOpsFinding;
import com.archdox.cloud.platformops.domain.PlatformOpsFindingSeverity;
import com.archdox.cloud.platformops.domain.PlatformOpsFindingSource;
import com.archdox.cloud.platformops.domain.PlatformOpsIncidentStatus;
import com.archdox.cloud.platformops.domain.PlatformOpsRun;
import com.archdox.cloud.platformops.domain.PlatformOpsRunStatus;
import com.archdox.cloud.platformops.domain.PlatformOpsRunTriggerType;
import com.archdox.cloud.platformops.infra.PlatformOpsFindingRepository;
import com.archdox.cloud.platformops.infra.PlatformOpsIncidentRepository;
import com.archdox.cloud.platformops.infra.PlatformOpsRunRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformOpsDailyReportService {
    private static final int RECENT_EVENT_LIMIT = 20;
    private static final int TOP_GROUP_LIMIT = 8;
    private static final List<PlatformOpsIncidentStatus> ACTIVE_INCIDENT_STATUSES = List.of(
            PlatformOpsIncidentStatus.OPEN,
            PlatformOpsIncidentStatus.ACKNOWLEDGED);

    private final PlatformOpsDailyReportProperties properties;
    private final PlatformOpsRunRepository runRepository;
    private final PlatformOpsIncidentRepository incidentRepository;
    private final PlatformOpsFindingRepository findingRepository;
    private final OperationEventRepository operationEventRepository;
    private final AiModelCallLogRepository aiModelCallLogRepository;
    private final EngineApiUsageEventRepository engineApiUsageEventRepository;
    private final OperationEventService operationEventService;

    public PlatformOpsDailyReportService(
            PlatformOpsDailyReportProperties properties,
            PlatformOpsRunRepository runRepository,
            PlatformOpsIncidentRepository incidentRepository,
            PlatformOpsFindingRepository findingRepository,
            OperationEventRepository operationEventRepository,
            AiModelCallLogRepository aiModelCallLogRepository,
            EngineApiUsageEventRepository engineApiUsageEventRepository,
            OperationEventService operationEventService
    ) {
        this.properties = properties;
        this.runRepository = runRepository;
        this.incidentRepository = incidentRepository;
        this.findingRepository = findingRepository;
        this.operationEventRepository = operationEventRepository;
        this.aiModelCallLogRepository = aiModelCallLogRepository;
        this.engineApiUsageEventRepository = engineApiUsageEventRepository;
        this.operationEventService = operationEventService;
    }

    @Transactional
    public PlatformOpsDailyReportDecision generate(OffsetDateTime dueAt, OffsetDateTime now) {
        var from = now.minusDays(1);
        var run = runRepository.save(new PlatformOpsRun(
                PlatformOpsRunTriggerType.AUTO_DAILY_REPORT,
                null,
                Map.of(
                        "state", "REQUESTED",
                        "dueAt", dueAt.toString(),
                        "periodFrom", from.toString(),
                        "periodTo", now.toString()),
                now));
        try {
            var snapshot = snapshot(run.id(), dueAt, from, now);
            var reportPath = writeReport(run.id(), snapshot);
            snapshot.put("reportPath", reportPath.toString());
            snapshot.put("state", "REPORT_READY");
            snapshot.put("aiHarnessStatus", "NOT_ATTACHED");
            run.replaceSnapshot(snapshot);
            findingRepository.save(reportFinding(run, reportPath, snapshot, now));
            run.complete(now);
            recordReportEvent(run, reportPath, snapshot);
            return PlatformOpsDailyReportDecision.generated(dueAt, run.id(), reportPath.toString());
        } catch (RuntimeException ex) {
            run.fail("DAILY_REPORT_FAILED", now);
            throw ex;
        }
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

        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("runId", runId);
        snapshot.put("dueAt", dueAt.toString());
        snapshot.put("periodFrom", from.toString());
        snapshot.put("periodTo", to.toString());
        snapshot.put("openIncidentCount", incidentRepository.countByStatusIn(ACTIVE_INCIDENT_STATUSES));
        snapshot.put("failedOpsRunCount", runRepository.countByStatusAndStartedAtGreaterThanEqualAndStartedAtLessThan(
                PlatformOpsRunStatus.FAILED,
                from,
                to));
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
        snapshot.put("redactionPolicy", "Counts and operational metadata only. Secrets, raw files, tokens, and raw payloads are excluded.");
        return snapshot;
    }

    private Path writeReport(Long runId, Map<String, Object> snapshot) {
        try {
            var directory = Path.of(properties.getReportDirectory()).toAbsolutePath().normalize();
            Files.createDirectories(directory);
            var timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(OffsetDateTime.parse(String.valueOf(snapshot.get("periodTo"))));
            var path = directory.resolve("platform-ops-daily-" + timestamp + "-run-" + runId + ".md");
            Files.writeString(path, renderMarkdown(snapshot), StandardCharsets.UTF_8);
            return path;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write platform ops daily report", ex);
        }
    }

    private String renderMarkdown(Map<String, Object> snapshot) {
        var builder = new StringBuilder();
        builder.append("# ArchDox Platform Ops Daily Report\n\n");
        builder.append("- Run ID: ").append(snapshot.get("runId")).append('\n');
        builder.append("- Due at: ").append(snapshot.get("dueAt")).append('\n');
        builder.append("- Period: ").append(snapshot.get("periodFrom")).append(" ~ ").append(snapshot.get("periodTo")).append('\n');
        builder.append("- Open incidents: ").append(snapshot.get("openIncidentCount")).append('\n');
        builder.append("- Failed ops runs: ").append(snapshot.get("failedOpsRunCount")).append('\n');
        builder.append("- Operation events: ").append(snapshot.get("operationEventCount")).append('\n');
        builder.append("- Platform ops findings: ").append(snapshot.get("platformOpsFindingCount")).append('\n');
        builder.append("\n## AI Usage\n\n");
        appendMap(builder, childMap(snapshot, "aiUsage"), List.of("callCount", "succeededCount", "failedCount", "inputTokens", "outputTokens", "estimatedTotalCost"));
        builder.append("\n## Engine/MCP Usage\n\n");
        appendMap(builder, childMap(snapshot, "engineUsage"), List.of("eventCount", "requestUnits"));
        builder.append("\n## Redaction\n\n");
        builder.append(snapshot.get("redactionPolicy")).append('\n');
        return builder.toString();
    }

    private void appendMap(StringBuilder builder, Map<String, Object> source, List<String> keys) {
        for (var key : keys) {
            builder.append("- ").append(key).append(": ").append(source.getOrDefault(key, 0)).append('\n');
        }
    }

    private PlatformOpsFinding reportFinding(
            PlatformOpsRun run,
            Path reportPath,
            Map<String, Object> snapshot,
            OffsetDateTime now
    ) {
        return new PlatformOpsFinding(
                null,
                run.id(),
                null,
                PlatformOpsFindingSeverity.INFO,
                PlatformOpsFindingSource.SYSTEM_DIAGNOSIS,
                "PLATFORM_OPS_DAILY_REPORT_READY",
                "OPS_DAILY_REPORT",
                "Platform ops daily report is ready",
                "A sanitized daily operations report was generated by the Flower monitor flow.",
                "PLATFORM_OPS_RUN",
                String.valueOf(run.id()),
                "platform-ops-daily-report",
                String.valueOf(run.id()),
                Map.of(
                        "reportPath", reportPath.toString(),
                        "periodFrom", snapshot.get("periodFrom"),
                        "periodTo", snapshot.get("periodTo"),
                        "openIncidentCount", snapshot.get("openIncidentCount"),
                        "failedOpsRunCount", snapshot.get("failedOpsRunCount")),
                "Review the daily report if WARN/ERROR counts or open incidents increased.",
                now);
    }

    private void recordReportEvent(PlatformOpsRun run, Path reportPath, Map<String, Object> snapshot) {
        operationEventService.record(
                null,
                OperationEventSeverity.INFO,
                "PLATFORM_OPS_DAILY_REPORT_READY",
                "platform-ops-daily-report",
                String.valueOf(run.id()),
                "PLATFORM_OPS_RUN",
                run.id(),
                "Platform ops daily report was generated.",
                Map.of(
                        "opsRunId", run.id(),
                        "reportPath", reportPath.toString(),
                        "periodFrom", snapshot.get("periodFrom"),
                        "periodTo", snapshot.get("periodTo"),
                        "openIncidentCount", snapshot.get("openIncidentCount"),
                        "failedOpsRunCount", snapshot.get("failedOpsRunCount")));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> childMap(Map<String, Object> snapshot, String key) {
        var value = snapshot.get(key);
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private long value(Number value) {
        return value == null ? 0 : value.longValue();
    }

    private Object nullable(Object value) {
        return value == null ? "" : value;
    }
}
