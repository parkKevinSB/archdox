package com.archdox.cloud.worker;

import com.archdox.cloud.worker.approval.application.WorkerApprovalRequestService;
import com.archdox.cloud.document.application.DocumentPreflightGateService;
import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.inspection.domain.InspectionReport;
import com.archdox.cloud.inspection.infra.InspectionReportRepository;
import com.archdox.cloud.office.application.OfficePermissionService;
import com.archdox.worker.application.ArchDoxWorkerPolicyGate;
import com.archdox.worker.domain.ArchDoxWorkerAction;
import com.archdox.worker.domain.ArchDoxWorkerActionDefinition;
import com.archdox.worker.domain.ArchDoxWorkerActionType;
import com.archdox.worker.domain.ArchDoxWorkerPolicyDecision;
import com.archdox.worker.domain.ArchDoxWorkerRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ArchDoxWorkerActionPolicyGate implements ArchDoxWorkerPolicyGate {
    private final WorkerApprovalRequestService approvalRequestService;
    private final InspectionReportRepository reportRepository;
    private final OfficePermissionService permissionService;
    private final DocumentPreflightGateService preflightGateService;

    public ArchDoxWorkerActionPolicyGate(
            WorkerApprovalRequestService approvalRequestService,
            InspectionReportRepository reportRepository,
            OfficePermissionService permissionService,
            DocumentPreflightGateService preflightGateService
    ) {
        this.approvalRequestService = approvalRequestService;
        this.reportRepository = reportRepository;
        this.permissionService = permissionService;
        this.preflightGateService = preflightGateService;
    }

    @Override
    @Transactional(readOnly = true)
    public ArchDoxWorkerPolicyDecision evaluate(ArchDoxWorkerRequest request, ArchDoxWorkerAction action) {
        return evaluate(request, action, null);
    }

    @Override
    @Transactional(readOnly = true)
    public ArchDoxWorkerPolicyDecision evaluate(
            ArchDoxWorkerRequest request,
            ArchDoxWorkerAction action,
            ArchDoxWorkerActionDefinition definition
    ) {
        if (definition == null) {
            return ArchDoxWorkerPolicyDecision.deny(
                    "ARCHDOX_WORKER_ACTION_DEFINITION_REQUIRED",
                    "Worker action definition is required.");
        }
        if (!definition.enabled()) {
            return ArchDoxWorkerPolicyDecision.deny(
                    "ARCHDOX_WORKER_ACTION_NOT_ENABLED",
                    "This ArchDox worker action is not enabled yet.");
        }
        if (!definition.allowsSource(request.source())) {
            return ArchDoxWorkerPolicyDecision.deny(
                    "ARCHDOX_WORKER_SOURCE_NOT_ALLOWED",
                    "Worker action source is not allowed for this action.");
        }
        var missingContext = definition.requiredContextFields().stream()
                .filter(field -> contextValueMissing(request, field))
                .toList();
        if (!missingContext.isEmpty()) {
            return ArchDoxWorkerPolicyDecision.deny(
                    "ARCHDOX_WORKER_CONTEXT_REQUIRED",
                    "Worker action requires context fields: " + String.join(", ", missingContext));
        }
        var domainDecision = evaluateDomainPolicy(request, action);
        if (domainDecision != null) {
            return domainDecision;
        }
        if (definition.requiresApprovalByDefault()) {
            if (approvalRequestService.isApprovedExecution(request, action)) {
                return ArchDoxWorkerPolicyDecision.allow();
            }
            return ArchDoxWorkerPolicyDecision.requireApproval(
                    "ARCHDOX_WORKER_APPROVAL_REQUIRED",
                    "Worker action requires approval before execution.");
        }
        return ArchDoxWorkerPolicyDecision.allow();
    }

    private ArchDoxWorkerPolicyDecision evaluateDomainPolicy(
            ArchDoxWorkerRequest request,
            ArchDoxWorkerAction action
    ) {
        return switch (action.actionType()) {
            case SUBMIT_REPORT -> evaluateSubmitReportPolicy(request, action);
            case REQUEST_DOCUMENT_GENERATION -> evaluateDocumentGenerationPolicy(request, action);
            default -> null;
        };
    }

    private ArchDoxWorkerPolicyDecision evaluateSubmitReportPolicy(
            ArchDoxWorkerRequest request,
            ArchDoxWorkerAction action
    ) {
        var reportDecision = resolveReportAndWriter(request, action, action.actionType());
        if (reportDecision.decision() != null) {
            return reportDecision.decision();
        }
        var report = reportDecision.report();
        if (!report.canSubmit()) {
            return ArchDoxWorkerPolicyDecision.deny(
                    "ARCHDOX_WORKER_REPORT_NOT_SUBMITTABLE",
                    "Report can be submitted only while it is draft or step-saved.");
        }
        return null;
    }

    private ArchDoxWorkerPolicyDecision evaluateDocumentGenerationPolicy(
            ArchDoxWorkerRequest request,
            ArchDoxWorkerAction action
    ) {
        var reportDecision = resolveReportAndWriter(request, action, action.actionType());
        if (reportDecision.decision() != null) {
            return reportDecision.decision();
        }
        var report = reportDecision.report();
        if (!report.canRequestGeneration()) {
            return ArchDoxWorkerPolicyDecision.deny(
                    "ARCHDOX_WORKER_REPORT_GENERATION_NOT_ALLOWED",
                    "Report must be ready, generated, or failed before requesting document generation.");
        }
        try {
            preflightGateService.requirePassedForGeneration(report);
        } catch (BadRequestException ex) {
            return ArchDoxWorkerPolicyDecision.deny(ex.code(), ex.getMessage());
        }
        return null;
    }

    private ReportPolicyResolution resolveReportAndWriter(
            ArchDoxWorkerRequest request,
            ArchDoxWorkerAction action,
            ArchDoxWorkerActionType actionType
    ) {
        var context = request.context();
        var reportId = firstLong(context.reportId(), action.payload().get("reportId"));
        if (reportId == null) {
            return ReportPolicyResolution.denied(ArchDoxWorkerPolicyDecision.deny(
                    "ARCHDOX_WORKER_REPORT_CONTEXT_REQUIRED",
                    actionType + " requires a reportId in request context or action payload."));
        }
        var report = reportRepository.findByIdAndOfficeId(reportId, context.officeId()).orElse(null);
        if (report == null) {
            return ReportPolicyResolution.denied(ArchDoxWorkerPolicyDecision.deny(
                    "ARCHDOX_WORKER_REPORT_NOT_FOUND",
                    "Report was not found in the current office."));
        }
        if (context.projectId() != null && !context.projectId().equals(report.projectId())) {
            return ReportPolicyResolution.denied(ArchDoxWorkerPolicyDecision.deny(
                    "ARCHDOX_WORKER_REPORT_PROJECT_MISMATCH",
                    "Report does not belong to the current project context."));
        }
        if (!permissionService.canWriteReport(context.userId(), report.officeId(), report.projectId(), reportId)) {
            return ReportPolicyResolution.denied(ArchDoxWorkerPolicyDecision.deny(
                    "ARCHDOX_WORKER_REPORT_WRITE_FORBIDDEN",
                    "User is not allowed to modify this report."));
        }
        return new ReportPolicyResolution(report, null);
    }

    private boolean contextValueMissing(ArchDoxWorkerRequest request, String field) {
        if (request == null || request.context() == null || field == null) {
            return true;
        }
        var context = request.context();
        return switch (field) {
            case "userId" -> context.userId() == null;
            case "officeId" -> context.officeId() == null;
            case "projectId" -> context.projectId() == null;
            case "siteId" -> context.siteId() == null;
            case "reportId" -> context.reportId() == null;
            case "documentJobId" -> context.documentJobId() == null;
            default -> false;
        };
    }

    private Long firstLong(Long first, Object second) {
        if (first != null) {
            return first;
        }
        return longValue(second);
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private record ReportPolicyResolution(
            InspectionReport report,
            ArchDoxWorkerPolicyDecision decision
    ) {
        private static ReportPolicyResolution denied(ArchDoxWorkerPolicyDecision decision) {
            return new ReportPolicyResolution(null, decision);
        }
    }
}
