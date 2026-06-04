package com.archdox.cloud.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.archdox.cloud.document.application.DocumentPreflightGateService;
import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.inspection.domain.InspectionReport;
import com.archdox.cloud.inspection.infra.InspectionReportRepository;
import com.archdox.cloud.office.application.OfficePermissionService;
import com.archdox.cloud.worker.approval.application.WorkerApprovalRequestService;
import com.archdox.worker.application.ArchDoxWorkerActionRegistry;
import com.archdox.worker.domain.ArchDoxWorkerAction;
import com.archdox.worker.domain.ArchDoxWorkerActionDefinition;
import com.archdox.worker.domain.ArchDoxWorkerActionOrigin;
import com.archdox.worker.domain.ArchDoxWorkerActionRiskLevel;
import com.archdox.worker.domain.ArchDoxWorkerActionType;
import com.archdox.worker.domain.ArchDoxWorkerPolicyDecisionType;
import com.archdox.worker.domain.ArchDoxWorkerRequest;
import com.archdox.worker.domain.ArchDoxWorkerRequestContext;
import com.archdox.worker.domain.ArchDoxWorkerRequestSource;
import java.time.OffsetDateTime;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ArchDoxWorkerActionPolicyGateTest {
    private final WorkerApprovalRequestService approvalRequestService = mock(WorkerApprovalRequestService.class);
    private final InspectionReportRepository reportRepository = mock(InspectionReportRepository.class);
    private final OfficePermissionService permissionService = mock(OfficePermissionService.class);
    private final DocumentPreflightGateService preflightGateService = mock(DocumentPreflightGateService.class);
    private final ArchDoxWorkerActionPolicyGate policyGate = new ArchDoxWorkerActionPolicyGate(
            approvalRequestService,
            reportRepository,
            permissionService,
            preflightGateService);
    private final ArchDoxWorkerActionRegistry registry = new ArchDoxWorkerActionRegistry(List.of());

    @Test
    void allowsEnabledUiActionWithRequiredContextAndDomainState() {
        var action = action(ArchDoxWorkerActionType.REQUEST_DOCUMENT_GENERATION);
        var definition = registry.definition(action.actionType()).orElseThrow();
        var report = readyReport();
        when(reportRepository.findByIdAndOfficeId(5L, 2L)).thenReturn(Optional.of(report));
        when(permissionService.canWriteReport(1L, 2L, 3L, 5L)).thenReturn(true);

        var decision = policyGate.evaluate(request(ArchDoxWorkerRequestSource.UI, 1L, 2L, 3L, 5L), action, definition);

        assertThat(decision.type()).isEqualTo(ArchDoxWorkerPolicyDecisionType.ALLOW);
    }

    @Test
    void deniesDisabledDefinitionEvenIfActionExists() {
        var action = action(ArchDoxWorkerActionType.CREATE_SITE);

        var decision = policyGate.evaluate(request(ArchDoxWorkerRequestSource.UI, 1L, 2L, 3L), action,
                disabledDefinition(action.actionType()));

        assertThat(decision.type()).isEqualTo(ArchDoxWorkerPolicyDecisionType.DENY);
        assertThat(decision.reasonCode()).isEqualTo("ARCHDOX_WORKER_ACTION_NOT_ENABLED");
    }

    @Test
    void deniesSourceThatDefinitionDoesNotAllow() {
        var action = action(ArchDoxWorkerActionType.CREATE_SITE);
        var definition = registry.definition(action.actionType()).orElseThrow();

        var decision = policyGate.evaluate(request(ArchDoxWorkerRequestSource.API, 1L, 2L, 3L), action, definition);

        assertThat(decision.type()).isEqualTo(ArchDoxWorkerPolicyDecisionType.DENY);
        assertThat(decision.reasonCode()).isEqualTo("ARCHDOX_WORKER_SOURCE_NOT_ALLOWED");
    }

    @Test
    void deniesWhenRequiredContextIsMissing() {
        var action = action(ArchDoxWorkerActionType.CREATE_SITE);
        var definition = registry.definition(action.actionType()).orElseThrow();

        var decision = policyGate.evaluate(request(ArchDoxWorkerRequestSource.UI, 1L, 2L, null), action, definition);

        assertThat(decision.type()).isEqualTo(ArchDoxWorkerPolicyDecisionType.DENY);
        assertThat(decision.reasonCode()).isEqualTo("ARCHDOX_WORKER_CONTEXT_REQUIRED");
        assertThat(decision.message()).contains("projectId");
    }

    @Test
    void requiresApprovalWhenDefinitionRequiresItAndNoApprovedExecutionExists() {
        var request = request(ArchDoxWorkerRequestSource.UI, 1L, 2L, 3L);
        var action = action(ArchDoxWorkerActionType.CREATE_SITE);

        var decision = policyGate.evaluate(request, action, approvalRequiredDefinition(action.actionType()));

        assertThat(decision.type()).isEqualTo(ArchDoxWorkerPolicyDecisionType.REQUIRE_APPROVAL);
        assertThat(decision.reasonCode()).isEqualTo("ARCHDOX_WORKER_APPROVAL_REQUIRED");
    }

    @Test
    void allowsApprovalRequiredActionWhenApprovedExecutionMatches() {
        var request = request(ArchDoxWorkerRequestSource.UI, 1L, 2L, 3L);
        var action = action(ArchDoxWorkerActionType.CREATE_SITE);
        when(approvalRequestService.isApprovedExecution(request, action)).thenReturn(true);

        var decision = policyGate.evaluate(request, action, approvalRequiredDefinition(action.actionType()));

        assertThat(decision.type()).isEqualTo(ArchDoxWorkerPolicyDecisionType.ALLOW);
    }

    @Test
    void deniesSubmitReportWhenReportStatusCannotBeSubmitted() {
        var action = action(ArchDoxWorkerActionType.SUBMIT_REPORT);
        var definition = registry.definition(action.actionType()).orElseThrow();
        var report = readyReport();
        when(reportRepository.findByIdAndOfficeId(5L, 2L)).thenReturn(Optional.of(report));
        when(permissionService.canWriteReport(1L, 2L, 3L, 5L)).thenReturn(true);

        var decision = policyGate.evaluate(request(ArchDoxWorkerRequestSource.UI, 1L, 2L, 3L, 5L), action, definition);

        assertThat(decision.type()).isEqualTo(ArchDoxWorkerPolicyDecisionType.DENY);
        assertThat(decision.reasonCode()).isEqualTo("ARCHDOX_WORKER_REPORT_NOT_SUBMITTABLE");
    }

    @Test
    void deniesDocumentGenerationWhenPreflightReviewHasNotPassed() {
        var action = action(ArchDoxWorkerActionType.REQUEST_DOCUMENT_GENERATION);
        var definition = registry.definition(action.actionType()).orElseThrow();
        var report = readyReport();
        when(reportRepository.findByIdAndOfficeId(5L, 2L)).thenReturn(Optional.of(report));
        when(permissionService.canWriteReport(1L, 2L, 3L, 5L)).thenReturn(true);
        doThrow(new BadRequestException(
                "REPORT_PREFLIGHT_REVIEW_REQUIRED",
                "errors.document.preflightReviewRequired",
                "Preflight review must pass before document generation."))
                .when(preflightGateService).requirePassedForGeneration(report);

        var decision = policyGate.evaluate(request(ArchDoxWorkerRequestSource.UI, 1L, 2L, 3L, 5L), action, definition);

        assertThat(decision.type()).isEqualTo(ArchDoxWorkerPolicyDecisionType.DENY);
        assertThat(decision.reasonCode()).isEqualTo("REPORT_PREFLIGHT_REVIEW_REQUIRED");
    }

    @Test
    void deniesReportActionWhenWriterPermissionIsMissing() {
        var action = action(ArchDoxWorkerActionType.SUBMIT_REPORT);
        var definition = registry.definition(action.actionType()).orElseThrow();
        when(reportRepository.findByIdAndOfficeId(5L, 2L)).thenReturn(Optional.of(draftReport()));
        when(permissionService.canWriteReport(1L, 2L, 3L, 5L)).thenReturn(false);

        var decision = policyGate.evaluate(request(ArchDoxWorkerRequestSource.UI, 1L, 2L, 3L, 5L), action, definition);

        assertThat(decision.type()).isEqualTo(ArchDoxWorkerPolicyDecisionType.DENY);
        assertThat(decision.reasonCode()).isEqualTo("ARCHDOX_WORKER_REPORT_WRITE_FORBIDDEN");
    }

    private ArchDoxWorkerAction action(ArchDoxWorkerActionType actionType) {
        return new ArchDoxWorkerAction(actionType, Map.of(), "test", 1.0d, ArchDoxWorkerActionOrigin.USER);
    }

    private ArchDoxWorkerActionDefinition approvalRequiredDefinition(ArchDoxWorkerActionType actionType) {
        return new ArchDoxWorkerActionDefinition(
                actionType,
                "TEST",
                "TestExecutor",
                true,
                false,
                ArchDoxWorkerActionRiskLevel.HIGH,
                true,
                true,
                Set.of(ArchDoxWorkerRequestSource.UI),
                Set.of("userId", "officeId", "projectId"),
                "test approval required action");
    }

    private ArchDoxWorkerActionDefinition disabledDefinition(ArchDoxWorkerActionType actionType) {
        return new ArchDoxWorkerActionDefinition(
                actionType,
                "TEST",
                "TestExecutor",
                false,
                false,
                ArchDoxWorkerActionRiskLevel.LOW,
                false,
                false,
                Set.of(ArchDoxWorkerRequestSource.UI),
                Set.of("userId", "officeId", "projectId"),
                "test disabled action");
    }

    private ArchDoxWorkerRequest request(
            ArchDoxWorkerRequestSource source,
            Long userId,
            Long officeId,
            Long projectId
    ) {
        return request(source, userId, officeId, projectId, null);
    }

    private ArchDoxWorkerRequest request(
            ArchDoxWorkerRequestSource source,
            Long userId,
            Long officeId,
            Long projectId,
            Long reportId
    ) {
        return new ArchDoxWorkerRequest(
                UUID.randomUUID(),
                source,
                "test",
                new ArchDoxWorkerRequestContext(userId, officeId, projectId, null, reportId, null, "ko-KR"),
                Instant.now());
    }

    private InspectionReport draftReport() {
        return new InspectionReport(
                2L,
                3L,
                4L,
                "RPT-TEST",
                "DAILY_SUPERVISION",
                "test",
                null,
                1L,
                OffsetDateTime.now());
    }

    private InspectionReport readyReport() {
        var report = draftReport();
        report.submit(OffsetDateTime.now());
        return report;
    }
}
