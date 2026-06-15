package com.archdox.cloud.aiharness.application;

import com.archdox.cloud.aiharness.dto.AiWorkerEvaluationCaseResponse;
import com.archdox.cloud.aiharness.dto.AiWorkerEvaluationGroupResponse;
import com.archdox.cloud.aiharness.dto.AiWorkerEvaluationSignalResponse;
import com.archdox.cloud.aiharness.dto.AiWorkerEvaluationSummaryResponse;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.platformadmin.application.PlatformAdminService;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiWorkerEvaluationReadService {
    private static final String PASS = "PASS";
    private static final String WARN = "WARN";

    private final PlatformAdminService platformAdminService;

    public AiWorkerEvaluationReadService(PlatformAdminService platformAdminService) {
        this.platformAdminService = platformAdminService;
    }

    @Transactional(readOnly = true)
    public AiWorkerEvaluationSummaryResponse summary(UserPrincipal principal) {
        platformAdminService.requirePlatformAdmin(principal);
        var groups = List.of(
                aiHarnessGroup(),
                workerControlGroup(),
                mcpEngineBoundaryGroup(),
                legalDigestGroup(),
                workerPolicyGovernanceGroup());
        var signals = signals();
        var totalCases = groups.stream().mapToInt(AiWorkerEvaluationGroupResponse::totalCases).sum();
        var automatedCases = groups.stream().mapToInt(AiWorkerEvaluationGroupResponse::automatedCases).sum();
        var passedCases = groups.stream().mapToInt(AiWorkerEvaluationGroupResponse::passedCases).sum();
        var warningCases = groups.stream().mapToInt(AiWorkerEvaluationGroupResponse::warningCases).sum();
        var failedCases = groups.stream().mapToInt(AiWorkerEvaluationGroupResponse::failedCases).sum();
        return new AiWorkerEvaluationSummaryResponse(
                OffsetDateTime.now(),
                "STATIC_BASELINE",
                "Numbers reflect deterministic Gradle test cases and existing integration/unit test coverage included in this build. Real-model evaluation runs are not executed by this endpoint.",
                totalCases,
                automatedCases,
                passedCases,
                warningCases,
                failedCases,
                percent(passedCases, totalCases),
                groups,
                signals);
    }

    private AiWorkerEvaluationGroupResponse aiHarnessGroup() {
        var cases = List.of(
                testCase("AI-H-001", "Legal digest keeps source-backed key articles", "AI_HARNESS",
                        "ArchDoxHarnessEvaluationSuiteTest.legalDigestEvaluationSetStaysSourceBacked"),
                testCase("AI-H-002", "Legal digest form/attachment change requires human review", "AI_HARNESS",
                        "ArchDoxHarnessEvaluationSuiteTest.legalDigestEvaluationSetStaysSourceBacked"),
                testCase("AI-H-003", "Conversation planner proposes only available actions", "AI_HARNESS",
                        "ArchDoxHarnessEvaluationSuiteTest.conversationPlannerEvaluationSetRespectsActionBoundary"),
                testCase("AI-H-004", "Conversation planner asks clarification when context is missing", "AI_HARNESS",
                        "ArchDoxHarnessEvaluationSuiteTest.conversationPlannerEvaluationSetRespectsActionBoundary"),
                testCase("AI-H-005", "Legal digest deleted checklist item requires human review", "AI_HARNESS",
                        "ArchDoxHarnessEvaluationSuiteTest.legalDigestEvaluationSetStaysSourceBacked"),
                testCase("AI-H-006", "Legal digest multi-article update keeps all source keys", "AI_HARNESS",
                        "ArchDoxHarnessEvaluationSuiteTest.legalDigestEvaluationSetStaysSourceBacked"),
                testCase("AI-H-007", "Conversation planner proposes generation only when action is available", "AI_HARNESS",
                        "ArchDoxHarnessEvaluationSuiteTest.conversationPlannerEvaluationSetRespectsActionBoundary"),
                testCase("AI-H-008", "Conversation planner returns no action for acknowledgement", "AI_HARNESS",
                        "ArchDoxHarnessEvaluationSuiteTest.conversationPlannerEvaluationSetRespectsActionBoundary"),
                testCase("AI-H-009", "Report preflight PASS has no findings", "AI_HARNESS",
                        "ArchDoxHarnessEvaluationSuiteTest.reportPreflightEvaluationSetKeepsFindingsStructured"),
                testCase("AI-H-010", "Report preflight preserves deterministic missing-photo finding", "AI_HARNESS",
                        "ArchDoxHarnessEvaluationSuiteTest.reportPreflightEvaluationSetKeepsFindingsStructured"),
                testCase("AI-H-011", "Report preflight fails when signature slot is missing", "AI_HARNESS",
                        "ArchDoxHarnessEvaluationSuiteTest.reportPreflightEvaluationSetKeepsFindingsStructured"),
                testCase("AI-H-012", "Report preflight warns when legal evidence context is missing", "AI_HARNESS",
                        "ArchDoxHarnessEvaluationSuiteTest.reportPreflightEvaluationSetKeepsFindingsStructured"),
                testCase("AI-H-013", "Source-backed legal review allows only cautious PASS wording", "AI_HARNESS",
                        "ArchDoxHarnessEvaluationSuiteTest.sourceBackedLegalReviewEvaluationSetKeepsDryRunBoundaries + SourceBackedLegalReviewHarnessFactoryTest.finalComplianceWordingIsRefinedBeforePass"),
                testCase("AI-H-014", "Source-backed legal review keeps candidate-only evidence insufficient", "AI_HARNESS",
                        "ArchDoxHarnessEvaluationSuiteTest.sourceBackedLegalReviewEvaluationSetKeepsDryRunBoundaries + ReportPreflightLegalReviewHarnessServiceTest.candidateOnlyCoverageIsNotPassEligible"),
                testCase("AI-H-015", "Source-backed legal review distinguishes business item anchors from search candidates", "AI_HARNESS",
                        "ReportPreflightLegalReviewHarnessServiceTest.businessItemDomainBindingCoverageIsHighStrength"),
                testCase("AI-H-016", "Source-backed legal review turns vague evidence into human-review findings", "AI_HARNESS",
                        "ArchDoxHarnessEvaluationSuiteTest.sourceBackedLegalReviewEvaluationSetKeepsDryRunBoundaries"));
        return group("AI_HARNESS_BASELINE", "AI Harness evaluation baseline", "AI_HARNESS", cases);
    }

    private AiWorkerEvaluationGroupResponse workerControlGroup() {
        var cases = List.of(
                testCase("WK-C-001", "Allowed action executes once", "WORKER_CONTROL",
                        "ArchDoxWorkerControlEvaluationSuiteTest.controlEvaluationSetMeasuresTerminalOutcomes"),
                testCase("WK-C-002", "Unknown action is rejected before policy", "WORKER_CONTROL",
                        "ArchDoxWorkerControlEvaluationSuiteTest.controlEvaluationSetMeasuresTerminalOutcomes"),
                testCase("WK-C-003", "Policy denial blocks executor", "WORKER_CONTROL",
                        "ArchDoxWorkerControlEvaluationSuiteTest.controlEvaluationSetMeasuresTerminalOutcomes"),
                testCase("WK-C-004", "Approval requirement blocks executor", "WORKER_CONTROL",
                        "ArchDoxWorkerControlEvaluationSuiteTest.controlEvaluationSetMeasuresTerminalOutcomes"),
                testCase("WK-C-005", "Run-control cancellation blocks after policy", "WORKER_CONTROL",
                        "ArchDoxWorkerControlEvaluationSuiteTest.controlEvaluationSetMeasuresTerminalOutcomes"),
                testCase("WK-C-006", "Executor failure is isolated as failed result", "WORKER_CONTROL",
                        "ArchDoxWorkerControlEvaluationSuiteTest.controlEvaluationSetMeasuresTerminalOutcomes"),
                testCase("WK-C-007", "Executor rejected result remains rejected trace", "WORKER_CONTROL",
                        "ArchDoxWorkerControlEvaluationSuiteTest.controlEvaluationSetMeasuresTerminalOutcomes"),
                testCase("WK-C-008", "Executor pending approval result remains approval trace", "WORKER_CONTROL",
                        "ArchDoxWorkerControlEvaluationSuiteTest.controlEvaluationSetMeasuresTerminalOutcomes"),
                testCase("WK-C-009", "Executor cancelled result is separated from rejection", "WORKER_CONTROL",
                        "ArchDoxWorkerControlEvaluationSuiteTest.controlEvaluationSetMeasuresTerminalOutcomes"),
                testCase("WK-C-010", "Executor exception is caught as failed result", "WORKER_CONTROL",
                        "ArchDoxWorkerControlEvaluationSuiteTest.controlEvaluationSetMeasuresTerminalOutcomes"));
        return group("WORKER_CONTROL_BASELINE", "Worker control evaluation baseline", "WORKER_CONTROL", cases);
    }

    private AiWorkerEvaluationGroupResponse mcpEngineBoundaryGroup() {
        var cases = List.of(
                testCase("MCP-E-001", "MCP gateway requires Engine API key", "MCP_ENGINE",
                        "McpGatewayIntegrationTest.mcpGatewayListsAndCallsEngineToolsWithEngineApiKey"),
                testCase("MCP-E-002", "MCP initialize returns typed protocol capabilities", "MCP_ENGINE",
                        "McpGatewayIntegrationTest.mcpGatewayListsAndCallsEngineToolsWithEngineApiKey"),
                testCase("MCP-E-003", "Unknown MCP methods return METHOD_NOT_FOUND", "MCP_ENGINE",
                        "McpGatewayIntegrationTest.mcpGatewayListsAndCallsEngineToolsWithEngineApiKey"),
                testCase("MCP-E-004", "Invalid params and unknown tools are classified separately", "MCP_ENGINE",
                        "McpGatewayIntegrationTest.mcpGatewayListsAndCallsEngineToolsWithEngineApiKey"),
                testCase("MCP-E-005", "get_legal_updates records LEGAL_UPDATES usage", "MCP_ENGINE",
                        "McpGatewayIntegrationTest.mcpGatewayListsAndCallsEngineToolsWithEngineApiKey"),
                testCase("MCP-E-006", "search_law and get_law_article record LEGAL_SEARCH usage", "MCP_ENGINE",
                        "McpGatewayIntegrationTest.mcpGatewayListsAndCallsEngineToolsWithEngineApiKey"),
                testCase("MCP-E-007", "Missing MCP tool scope returns SCOPE_REQUIRED", "MCP_ENGINE",
                        "McpGatewayIntegrationTest.mcpGatewayListsAndCallsEngineToolsWithEngineApiKey"),
                testCase("MCP-E-008", "MCP quota excess returns retryable QUOTA_EXCEEDED", "MCP_ENGINE",
                        "McpGatewayIntegrationTest.mcpGatewayListsAndCallsEngineToolsWithEngineApiKey"),
                testCase("MCP-E-009", "Engine boundary prepares typed run response without workflow execution", "MCP_ENGINE",
                        "ArchDoxEngineServiceTest.preparesBoundaryResponseWithoutExecutingBusinessWorkflow"),
                testCase("MCP-E-010", "explain_legal_change exposes source-backed digest detail", "MCP_ENGINE",
                        "McpGatewayIntegrationTest.mcpGatewayListsAndCallsEngineToolsWithEngineApiKey"));
        return group("MCP_ENGINE_BOUNDARY", "MCP / Engine boundary evaluation", "MCP_ENGINE", cases);
    }

    private AiWorkerEvaluationGroupResponse legalDigestGroup() {
        var cases = List.of(
                testCase("LEG-D-001", "Legal digest list excludes fake source", "LEGAL_DIGEST",
                        "LegalPlatformAdminServiceTest.changeDigestsExcludeFakeSource"),
                testCase("LEG-D-002", "Deterministic digest refresh skips AI and missing acts", "LEGAL_DIGEST",
                        "LegalPlatformAdminServiceTest.refreshDeterministicDigestsSkipsAiAndMissingActs"),
                testCase("LEG-D-003", "Legal diff detects added, modified, and removed articles", "LEGAL_DIGEST",
                        "LegalDiffServiceTest.detectsAddedModifiedAndRemovedArticles"),
                testCase("LEG-D-004", "User legal updates exclude fake legal source", "LEGAL_DIGEST",
                        "LegalUpdateReadServiceTest.recentExcludesFakeLegalSource"),
                testCase("LEG-D-005", "Legal update detail excludes fake legal source", "LEGAL_DIGEST",
                        "LegalUpdateReadServiceTest.detailExcludesFakeLegalSource"),
                testCase("LEG-D-006", "Legal digest AI worker rejects non-dry-run execution", "LEGAL_DIGEST",
                        "LegalDigestEnrichmentArchDoxWorkerActionExecutorTest.rejectsNonDryRunExecution"),
                testCase("LEG-D-007", "Legal digest AI worker draft does not mutate digest or corpus", "LEGAL_DIGEST",
                        "LegalDigestEnrichmentArchDoxWorkerActionExecutorTest.generatesDraftWithoutMutatingDigestOrCorpus"),
                testCase("LEG-D-008", "Admin legal digest AI draft uses Worker dry-run", "LEGAL_DIGEST",
                        "LegalPlatformAdminServiceTest.generateDigestAiDraftRunsArchDoxWorkerDryRunAndReturnsDraft"));
        return group("LEGAL_DIGEST_PIPELINE", "Legal sync / digest evaluation", "LEGAL_DIGEST", cases);
    }

    private AiWorkerEvaluationGroupResponse workerPolicyGovernanceGroup() {
        var cases = List.of(
                testCase("GOV-W-001", "Worker policy allows enabled UI action with required context", "GOVERNANCE",
                        "ArchDoxWorkerActionPolicyGateTest.allowsEnabledUiActionWithRequiredContextAndDomainState"),
                testCase("GOV-W-002", "Worker policy denies disabled action definition", "GOVERNANCE",
                        "ArchDoxWorkerActionPolicyGateTest.deniesDisabledDefinitionEvenIfActionExists"),
                testCase("GOV-W-003", "Worker policy denies disallowed request source", "GOVERNANCE",
                        "ArchDoxWorkerActionPolicyGateTest.deniesSourceThatDefinitionDoesNotAllow"),
                testCase("GOV-W-004", "Worker policy denies missing required context", "GOVERNANCE",
                        "ArchDoxWorkerActionPolicyGateTest.deniesWhenRequiredContextIsMissing"),
                testCase("GOV-W-005", "Approval-required action blocks before approved execution", "GOVERNANCE",
                        "ArchDoxWorkerActionPolicyGateTest.requiresApprovalWhenDefinitionRequiresItAndNoApprovedExecutionExists"),
                testCase("GOV-W-006", "Approved execution unlocks approval-required action", "GOVERNANCE",
                        "ArchDoxWorkerActionPolicyGateTest.allowsApprovalRequiredActionWhenApprovedExecutionMatches"),
                testCase("GOV-W-007", "Document generation is denied when preflight has not passed", "GOVERNANCE",
                        "ArchDoxWorkerActionPolicyGateTest.deniesDocumentGenerationWhenPreflightReviewHasNotPassed"),
                testCase("GOV-W-008", "Worker governance separates cancel, failure, catch, and approval rates", "GOVERNANCE",
                        "WorkerGovernanceReadServiceTest.summarizesWorkerGovernanceFromExistingOperationEvents"));
        return group("WORKER_POLICY_GOVERNANCE", "Worker policy / governance evaluation", "GOVERNANCE", cases);
    }

    private List<AiWorkerEvaluationSignalResponse> signals() {
        return List.of(
                signal("MODEL_PROVIDER_SWITCHABLE", "Provider/model can be configured per harness", PASS,
                        "AI_HARNESS", "AiHarnessPolicyExecutionService + Admin AI Harness policy"),
                signal("OUTPUT_SCHEMA_VALIDATION", "AI output schema validation exists", PASS,
                        "AI_HARNESS", "JacksonPojoSchemaValidator in ArchDox harness factories"),
                signal("REFINE_RETRY", "Invalid model output can be refined/retried", PASS,
                        "AI_HARNESS", "MaxAttemptsRefinePolicy + harness tests"),
                signal("ACTION_REGISTRY", "Worker action registry exists", PASS,
                        "WORKER_CONTROL", "ArchDoxWorkerActionRegistry"),
                signal("POLICY_GATE", "Worker policy gate exists", PASS,
                        "WORKER_CONTROL", "GateArchDoxWorkerActionStep"),
                signal("RUN_CONTROL_CANCEL", "Execution can be cancelled before executor", PASS,
                        "WORKER_CONTROL", "CheckArchDoxWorkerRunControlStep"),
                signal("MCP_SCOPE_AND_QUOTA", "MCP tool scope and quota errors are separated", PASS,
                        "MCP_ENGINE", "McpGatewayIntegrationTest.mcpGatewayListsAndCallsEngineToolsWithEngineApiKey"),
                signal("LEGAL_DRY_RUN_DRAFT", "Legal digest AI draft is dry-run before approval", PASS,
                        "LEGAL_DIGEST", "LegalDigestEnrichmentArchDoxWorkerActionExecutorTest + LegalPlatformAdminServiceTest"),
                signal("APPROVAL_INTERLOCK", "Approval interlock blocks risky actions before execution", PASS,
                        "GOVERNANCE", "ArchDoxWorkerActionPolicyGateTest"),
                signal("TRACE_AUDIT", "Harness/worker traces are observable", PASS,
                        "OBSERVABILITY", "AiHarnessTraceEvent + WorkerGovernanceReadService"),
                signal("REAL_MODEL_EVALUATION", "Real-model evaluation run history", WARN,
                        "EVALUATION", "Not yet a runtime evaluation runner. Current endpoint is a static baseline."));
    }

    private AiWorkerEvaluationCaseResponse testCase(String id, String name, String layer, String evidence) {
        return new AiWorkerEvaluationCaseResponse(
                id,
                name,
                layer,
                PASS,
                true,
                "GRADLE_TEST",
                evidence);
    }

    private AiWorkerEvaluationSignalResponse signal(
            String key,
            String displayName,
            String status,
            String layer,
            String evidence
    ) {
        return new AiWorkerEvaluationSignalResponse(key, displayName, status, layer, evidence);
    }

    private AiWorkerEvaluationGroupResponse group(
            String groupKey,
            String displayName,
            String layer,
            List<AiWorkerEvaluationCaseResponse> cases
    ) {
        var passed = (int) cases.stream().filter(item -> PASS.equals(item.status())).count();
        var warnings = (int) cases.stream().filter(item -> WARN.equals(item.status())).count();
        var failed = cases.size() - passed - warnings;
        var automated = (int) cases.stream().filter(AiWorkerEvaluationCaseResponse::automated).count();
        return new AiWorkerEvaluationGroupResponse(
                groupKey,
                displayName,
                layer,
                cases.size(),
                automated,
                passed,
                warnings,
                failed,
                percent(passed, cases.size()),
                cases);
    }

    private static int percent(int numerator, int denominator) {
        if (denominator <= 0) {
            return 0;
        }
        return (int) Math.round((numerator * 100.0d) / denominator);
    }
}
