package com.archdox.worker.application;

import com.archdox.worker.domain.ArchDoxWorkerActionDefinition;
import com.archdox.worker.domain.ArchDoxWorkerActionRiskLevel;
import com.archdox.worker.domain.ArchDoxWorkerActionType;
import com.archdox.worker.domain.ArchDoxWorkerRequestSource;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class ArchDoxWorkerActionRegistry {
    private final Map<ArchDoxWorkerActionType, ArchDoxWorkerActionDefinition> definitions;
    private final Map<ArchDoxWorkerActionType, ArchDoxWorkerActionExecutor> executors;

    public ArchDoxWorkerActionRegistry(Collection<? extends ArchDoxWorkerActionExecutor> executors) {
        this.definitions = defaultDefinitions();
        var byType = new EnumMap<ArchDoxWorkerActionType, ArchDoxWorkerActionExecutor>(ArchDoxWorkerActionType.class);
        if (executors != null) {
            for (var executor : executors) {
                if (executor == null) {
                    continue;
                }
                if (!definitions.containsKey(executor.actionType())) {
                    throw new IllegalArgumentException("Missing ArchDox Worker action definition: " + executor.actionType());
                }
                var previous = byType.putIfAbsent(executor.actionType(), executor);
                if (previous != null) {
                    throw new IllegalArgumentException("Duplicate ArchDox Worker action executor: " + executor.actionType());
                }
            }
        }
        this.executors = Map.copyOf(byType);
    }

    public Optional<ArchDoxWorkerActionExecutor> resolve(ArchDoxWorkerActionType actionType) {
        return Optional.ofNullable(executors.get(actionType));
    }

    public Optional<ArchDoxWorkerActionDefinition> definition(ArchDoxWorkerActionType actionType) {
        return Optional.ofNullable(definitions.get(actionType));
    }

    public Set<ArchDoxWorkerActionType> registeredActionTypes() {
        return executors.keySet();
    }

    public List<Map<String, Object>> actionDefinitionMetadata() {
        return definitions.values().stream()
                .map(ArchDoxWorkerActionDefinition::toMetadata)
                .toList();
    }

    private Map<ArchDoxWorkerActionType, ArchDoxWorkerActionDefinition> defaultDefinitions() {
        var map = new EnumMap<ArchDoxWorkerActionType, ArchDoxWorkerActionDefinition>(ArchDoxWorkerActionType.class);
        put(map, ArchDoxWorkerActionType.WORKER_CHAT_ADVANCE,
                "WORKER_CHAT",
                "WorkerChatAdvanceArchDoxWorkerActionExecutor",
                true,
                false,
                ArchDoxWorkerActionRiskLevel.LOW,
                false,
                false,
                required("userId", "officeId", "projectId"),
                "Advance the current project-scoped worker chat session.");
        put(map, ArchDoxWorkerActionType.CREATE_SITE,
                "WORKER_CHAT",
                "WorkerChatCreateSiteArchDoxWorkerActionExecutor",
                true,
                false,
                ArchDoxWorkerActionRiskLevel.MEDIUM,
                false,
                true,
                required("userId", "officeId", "projectId"),
                "Create a site through the normal SiteService path.");
        put(map, ArchDoxWorkerActionType.CREATE_REPORT,
                "WORKER_CHAT",
                "WorkerChatCreateReportArchDoxWorkerActionExecutor",
                true,
                false,
                ArchDoxWorkerActionRiskLevel.MEDIUM,
                false,
                true,
                required("userId", "officeId", "projectId"),
                "Create a report through the normal InspectionReportService path.");
        put(map, ArchDoxWorkerActionType.UPDATE_REPORT_STEP,
                "WORKER_CHAT",
                "WorkerChatUpdateReportStepArchDoxWorkerActionExecutor",
                true,
                false,
                ArchDoxWorkerActionRiskLevel.MEDIUM,
                false,
                true,
                required("userId", "officeId", "projectId"),
                "Save a report step through the normal report step path.");
        put(map, ArchDoxWorkerActionType.SUBMIT_REPORT,
                "WORKER_CHAT",
                "WorkerChatSubmitReportArchDoxWorkerActionExecutor",
                true,
                false,
                ArchDoxWorkerActionRiskLevel.HIGH,
                false,
                true,
                required("userId", "officeId", "projectId"),
                "Submit a report through the normal submit validation path.");
        put(map, ArchDoxWorkerActionType.RUN_PREFLIGHT_REVIEW,
                "REPORT_REVIEW",
                "RunPreflightReviewArchDoxWorkerActionExecutor",
                true,
                false,
                ArchDoxWorkerActionRiskLevel.MEDIUM,
                false,
                false,
                Set.of(ArchDoxWorkerRequestSource.UI, ArchDoxWorkerRequestSource.API),
                required("userId", "officeId", "projectId"),
                "Request the same preflight review used by the document tab.");
        put(map, ArchDoxWorkerActionType.REQUEST_DOCUMENT_GENERATION,
                "DOCUMENT_WORKFLOW",
                "RequestDocumentGenerationArchDoxWorkerActionExecutor",
                true,
                false,
                ArchDoxWorkerActionRiskLevel.HIGH,
                false,
                true,
                Set.of(ArchDoxWorkerRequestSource.UI, ArchDoxWorkerRequestSource.API),
                required("userId", "officeId", "projectId"),
                "Request document generation through the normal DocumentGenerationRequestService path.");
        put(map, ArchDoxWorkerActionType.ENRICH_LEGAL_CHANGE_DIGEST,
                "LEGAL",
                "LegalDigestEnrichmentArchDoxWorkerActionExecutor",
                true,
                true,
                ArchDoxWorkerActionRiskLevel.MEDIUM,
                false,
                true,
                Set.of(ArchDoxWorkerRequestSource.UI, ArchDoxWorkerRequestSource.SYSTEM),
                required("userId"),
                "Enrich a legal change digest through source-backed legal corpus context and the ArchDox legal AI harness.");
        return Map.copyOf(map);
    }

    private void put(
            Map<ArchDoxWorkerActionType, ArchDoxWorkerActionDefinition> map,
            ArchDoxWorkerActionType actionType,
            String owner,
            String executorName,
            boolean enabled,
            boolean readOnly,
            ArchDoxWorkerActionRiskLevel riskLevel,
            boolean requiresApprovalByDefault,
            boolean supportsDryRun,
            Set<String> requiredContextFields,
            String description
    ) {
        put(map, actionType, owner, executorName, enabled, readOnly, riskLevel, requiresApprovalByDefault,
                supportsDryRun, Set.of(ArchDoxWorkerRequestSource.UI), requiredContextFields, description);
    }

    private void put(
            Map<ArchDoxWorkerActionType, ArchDoxWorkerActionDefinition> map,
            ArchDoxWorkerActionType actionType,
            String owner,
            String executorName,
            boolean enabled,
            boolean readOnly,
            ArchDoxWorkerActionRiskLevel riskLevel,
            boolean requiresApprovalByDefault,
            boolean supportsDryRun,
            Set<ArchDoxWorkerRequestSource> allowedSources,
            Set<String> requiredContextFields,
            String description
    ) {
        map.put(actionType, new ArchDoxWorkerActionDefinition(
                actionType,
                owner,
                executorName,
                enabled,
                readOnly,
                riskLevel,
                requiresApprovalByDefault,
                supportsDryRun,
                allowedSources,
                requiredContextFields,
                description));
    }

    private Set<String> required(String... fields) {
        return Set.of(fields);
    }
}
