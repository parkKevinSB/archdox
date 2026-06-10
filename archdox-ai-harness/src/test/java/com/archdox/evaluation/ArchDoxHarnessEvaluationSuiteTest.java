package com.archdox.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.archdox.documentai.ReportPreflightFindingSummary;
import com.archdox.documentai.ReportPreflightHarnessFactory;
import com.archdox.documentai.ReportPreflightInput;
import com.archdox.documentai.ReportPreflightIssueSeverity;
import com.archdox.documentai.ReportPreflightResult;
import com.archdox.documentai.ReportPreflightStatus;
import com.archdox.legalai.LegalDigestArticleChange;
import com.archdox.legalai.LegalDigestHarnessFactory;
import com.archdox.legalai.LegalDigestInput;
import com.archdox.legalai.LegalDigestResult;
import com.archdox.legalai.LegalDigestStatus;
import com.archdox.workerai.ConversationPlannerActionOption;
import com.archdox.workerai.ConversationPlannerDecision;
import com.archdox.workerai.ConversationPlannerHarnessFactory;
import com.archdox.workerai.ConversationPlannerInput;
import com.archdox.workerai.ConversationPlannerResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.parkkevinsb.flower.ai.harness.finding.AiFinding;
import io.github.parkkevinsb.flower.ai.harness.finding.FindingSink;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunContext;
import io.github.parkkevinsb.flower.ai.harness.test.AiHarnessTestExtension;
import io.github.parkkevinsb.flower.ai.harness.test.fake.FakeResponseProgram;
import io.github.parkkevinsb.flower.ai.harness.validate.ValidationResult;
import io.github.parkkevinsb.flower.core.flow.FlowState;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class ArchDoxHarnessEvaluationSuiteTest {
    @RegisterExtension
    final AiHarnessTestExtension harness = new AiHarnessTestExtension();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void legalDigestEvaluationSetStaysSourceBacked() {
        var cases = List.of(
                new LegalDigestEvaluationCase(
                        "publishable supervision article update",
                        legalDigestInput(List.of(new LegalDigestArticleChange(
                                "BUILDING_ACT:0025001@001823:20260701",
                                "Article 25 supervision duty",
                                "MODIFIED",
                                "Supervisor shall check general construction progress.",
                                "Supervisor shall check construction progress and photo evidence retention.",
                                "001823:20260701",
                                "2026-07-01",
                                "https://www.law.go.kr/DRF/lawService.do?ID=001823"))),
                        """
                                {
                                  "status": "PUBLISHABLE",
                                  "title": "Construction supervision evidence duty updated",
                                  "summary": "The synchronized legal corpus indicates an update to supervision evidence retention language.",
                                  "impactSummary": "Review construction supervision logs and photo evidence guidance before publishing template updates.",
                                  "confidence": "HIGH",
                                  "affectedReportTypes": ["CONSTRUCTION_DAILY_SUPERVISION_LOG"],
                                  "affectedCatalogItems": ["CONSTRUCTION_SUPERVISION_LEGAL_CONTEXT"],
                                  "keyArticles": ["BUILDING_ACT:0025001@001823:20260701"],
                                  "reviewNotes": "Source-backed draft only. Platform admin approval is still required."
                                }
                                """,
                        LegalDigestStatus.PUBLISHABLE,
                        List.of("BUILDING_ACT:0025001@001823:20260701"),
                        List.of("CONSTRUCTION_DAILY_SUPERVISION_LOG"),
                        List.of("CONSTRUCTION_SUPERVISION_LEGAL_CONTEXT")),
                new LegalDigestEvaluationCase(
                        "long form attachment requires human review",
                        legalDigestInput(List.of(new LegalDigestArticleChange(
                                "CONSTRUCTION_SUPERVISION_DETAILED_STANDARD:FORM_001@2024-377:20240710",
                                "Supervision report form",
                                "MODIFIED",
                                "Old form text with table rows.",
                                "New form text with dense table rows and attachment layout.",
                                "2024-377:20240710",
                                "2024-07-10",
                                "https://www.law.go.kr/DRF/lawService.do?target=admrul&ID=2100000244148"))),
                        """
                                {
                                  "status": "NEEDS_HUMAN_REVIEW",
                                  "title": "Supervision report form changed",
                                  "summary": "A form or attachment changed, but the table layout needs human review before publishing.",
                                  "impactSummary": "Check report rendering, signature slots, and submission form mapping manually.",
                                  "confidence": "MEDIUM",
                                  "affectedReportTypes": ["CONSTRUCTION_SUPERVISION_REPORT"],
                                  "affectedCatalogItems": ["CONSTRUCTION_SUPERVISION_LEGAL_CONTEXT"],
                                  "keyArticles": ["CONSTRUCTION_SUPERVISION_DETAILED_STANDARD:FORM_001@2024-377:20240710"],
                                  "reviewNotes": "Do not auto-apply because the source text is form-like."
                                }
                                """,
                        LegalDigestStatus.NEEDS_HUMAN_REVIEW,
                        List.of("CONSTRUCTION_SUPERVISION_DETAILED_STANDARD:FORM_001@2024-377:20240710"),
                        List.of("CONSTRUCTION_SUPERVISION_REPORT"),
                        List.of("CONSTRUCTION_SUPERVISION_LEGAL_CONTEXT")),
                new LegalDigestEvaluationCase(
                        "deleted supervision checklist item requires human review",
                        legalDigestInput(List.of(new LegalDigestArticleChange(
                                "CONSTRUCTION_SUPERVISION_DETAILED_STANDARD:CHECK_004@2024-377:20240710",
                                "Supervision checklist attachment",
                                "DELETED",
                                "Previous checklist item for supervision evidence.",
                                "",
                                "2024-377:20240710",
                                "2024-07-10",
                                "https://www.law.go.kr/DRF/lawService.do?target=admrul&ID=2100000244148"))),
                        """
                                {
                                  "status": "NEEDS_HUMAN_REVIEW",
                                  "title": "Supervision checklist item removed",
                                  "summary": "A checklist-like legal source item was removed from the synchronized corpus.",
                                  "impactSummary": "Review checklist catalog mappings before changing any report templates.",
                                  "confidence": "MEDIUM",
                                  "affectedReportTypes": ["CONSTRUCTION_DAILY_SUPERVISION_LOG"],
                                  "affectedCatalogItems": ["CONSTRUCTION_SUPERVISION_LEGAL_CONTEXT"],
                                  "keyArticles": ["CONSTRUCTION_SUPERVISION_DETAILED_STANDARD:CHECK_004@2024-377:20240710"],
                                  "reviewNotes": "Deletion requires human review because it may affect existing checklist mappings."
                                }
                                """,
                        LegalDigestStatus.NEEDS_HUMAN_REVIEW,
                        List.of("CONSTRUCTION_SUPERVISION_DETAILED_STANDARD:CHECK_004@2024-377:20240710"),
                        List.of("CONSTRUCTION_DAILY_SUPERVISION_LOG"),
                        List.of("CONSTRUCTION_SUPERVISION_LEGAL_CONTEXT")),
                new LegalDigestEvaluationCase(
                        "multi article update keeps all source keys",
                        legalDigestInput(List.of(
                                new LegalDigestArticleChange(
                                        "BUILDING_ACT:0025001@001823:20260701",
                                        "Article 25 supervision duty",
                                        "MODIFIED",
                                        "Supervisor shall check construction progress.",
                                        "Supervisor shall check construction progress and evidence retention.",
                                        "001823:20260701",
                                        "2026-07-01",
                                        "https://www.law.go.kr/DRF/lawService.do?ID=001823"),
                                new LegalDigestArticleChange(
                                        "BUILDING_ACT:0062001@001823:20260701",
                                        "Article 62 safety management",
                                        "MODIFIED",
                                        "Safety management records shall be kept.",
                                        "Safety management and inspection records shall be kept.",
                                        "001823:20260701",
                                        "2026-07-01",
                                        "https://www.law.go.kr/DRF/lawService.do?ID=001823"))),
                        """
                                {
                                  "status": "PUBLISHABLE",
                                  "title": "Supervision and safety record duties updated",
                                  "summary": "The source-backed legal corpus includes supervision and safety record wording changes.",
                                  "impactSummary": "Review supervision log evidence fields and safety checklist guidance.",
                                  "confidence": "HIGH",
                                  "affectedReportTypes": ["CONSTRUCTION_DAILY_SUPERVISION_LOG", "CONSTRUCTION_SUPERVISION_REPORT"],
                                  "affectedCatalogItems": ["CONSTRUCTION_SUPERVISION_LEGAL_CONTEXT", "SAFETY_MANAGEMENT_LEGAL_CONTEXT"],
                                  "keyArticles": ["BUILDING_ACT:0025001@001823:20260701", "BUILDING_ACT:0062001@001823:20260701"],
                                  "reviewNotes": "All cited legal keys came from the supplied change set."
                                }
                                """,
                        LegalDigestStatus.PUBLISHABLE,
                        List.of("BUILDING_ACT:0025001@001823:20260701", "BUILDING_ACT:0062001@001823:20260701"),
                        List.of("CONSTRUCTION_DAILY_SUPERVISION_LOG", "CONSTRUCTION_SUPERVISION_REPORT"),
                        List.of("CONSTRUCTION_SUPERVISION_LEGAL_CONTEXT")));

        harness.gateway().respondToAny(new FakeResponseProgram.Sequence(
                cases.stream()
                        .map(testCase -> (FakeResponseProgram) new FakeResponseProgram.ImmediateText(testCase.modelResponse()))
                        .toList()));
        var spec = new LegalDigestHarnessFactory(objectMapper).spec(new RecordingFindingSink());

        for (var testCase : cases) {
            var run = harness.runHarness(spec, testCase.input());

            assertThat(run.flow().state()).as(testCase.name()).isEqualTo(FlowState.FINISHED);
            var result = validValue(run.context().latestValidation(), LegalDigestResult.class);

            assertThat(result.status()).as(testCase.name()).isEqualTo(testCase.expectedStatus());
            assertThat(result.keyArticles()).as(testCase.name()).containsAll(testCase.requiredKeyArticles());
            assertThat(result.keyArticles()).as(testCase.name()).allMatch(testCase.inputArticleKeys()::contains);
            assertThat(result.affectedReportTypes()).as(testCase.name()).containsAll(testCase.requiredReportTypes());
            assertThat(result.affectedCatalogItems()).as(testCase.name()).containsAll(testCase.requiredCatalogItems());
            if (result.status() == LegalDigestStatus.PUBLISHABLE) {
                assertThat(result.title()).as(testCase.name()).isNotBlank();
                assertThat(result.summary()).as(testCase.name()).isNotBlank();
            }
        }
    }

    @Test
    void conversationPlannerEvaluationSetRespectsActionBoundary() {
        var cases = List.of(
                new PlannerEvaluationCase(
                        "create site request proposes only available action",
                        plannerInput(
                                "Please create a site named Tower A.",
                                "AWAITING_SITE",
                                List.of(action("CREATE_SITE", true), action("CREATE_REPORT", true))),
                        """
                                {
                                  "decision": "PROPOSE_ACTION",
                                  "actionType": "CREATE_SITE",
                                  "requiresConfirmation": true,
                                  "confidence": 0.88,
                                  "userMessage": "I can prepare a site creation proposal for confirmation.",
                                  "payload": {
                                    "name": "Tower A",
                                    "siteType": "CONSTRUCTION_SITE"
                                  },
                                  "rationale": "The user asked for a site and CREATE_SITE is available."
                                }
                                """,
                        ConversationPlannerDecision.PROPOSE_ACTION,
                        "CREATE_SITE",
                        true),
                new PlannerEvaluationCase(
                        "document generation without report context asks clarification",
                        plannerInput(
                                "Generate the supervision document now.",
                                "AWAITING_REPORT",
                                List.of(action("CREATE_SITE", true), action("CREATE_REPORT", true))),
                        """
                                {
                                  "decision": "ASK_CLARIFICATION",
                                  "actionType": "",
                                  "requiresConfirmation": false,
                                  "confidence": 0.61,
                                  "userMessage": "Please select or create a report before document generation.",
                                  "payload": {},
                                  "rationale": "REQUEST_DOCUMENT_GENERATION is not available and report context is missing."
                                }
                                """,
                        ConversationPlannerDecision.ASK_CLARIFICATION,
                        "",
                        false),
                new PlannerEvaluationCase(
                        "ready report generation proposes available generation action",
                        plannerInput(
                                "Generate the supervision document for this report.",
                                "REPORT_READY",
                                List.of(action("REQUEST_DOCUMENT_GENERATION", true), action("RUN_PREFLIGHT_REVIEW", false))),
                        """
                                {
                                  "decision": "PROPOSE_ACTION",
                                  "actionType": "REQUEST_DOCUMENT_GENERATION",
                                  "requiresConfirmation": true,
                                  "confidence": 0.82,
                                  "userMessage": "I can request document generation after your confirmation.",
                                  "payload": {
                                    "outputFormats": ["DOCX", "PDF"]
                                  },
                                  "rationale": "The report is ready and REQUEST_DOCUMENT_GENERATION is available."
                                }
                                """,
                        ConversationPlannerDecision.PROPOSE_ACTION,
                        "REQUEST_DOCUMENT_GENERATION",
                        true),
                new PlannerEvaluationCase(
                        "casual acknowledgement produces no action",
                        plannerInput(
                                "Thanks, that is enough for now.",
                                "REPORT_READY",
                                List.of(action("CREATE_REPORT", true), action("REQUEST_DOCUMENT_GENERATION", true))),
                        """
                                {
                                  "decision": "NO_ACTION",
                                  "actionType": "",
                                  "requiresConfirmation": false,
                                  "confidence": 0.74,
                                  "userMessage": "Okay. I will wait for the next instruction.",
                                  "payload": {},
                                  "rationale": "The user acknowledged the result and did not request an action."
                                }
                                """,
                        ConversationPlannerDecision.NO_ACTION,
                        "",
                        false));

        harness.gateway().respondToAny(new FakeResponseProgram.Sequence(
                cases.stream()
                        .map(testCase -> (FakeResponseProgram) new FakeResponseProgram.ImmediateText(testCase.modelResponse()))
                        .toList()));
        var spec = new ConversationPlannerHarnessFactory(objectMapper).spec(new RecordingFindingSink());

        for (var testCase : cases) {
            var run = harness.runHarness(spec, testCase.input());

            assertThat(run.flow().state()).as(testCase.name()).isEqualTo(FlowState.FINISHED);
            var result = validValue(run.context().latestValidation(), ConversationPlannerResult.class);

            assertThat(result.decision()).as(testCase.name()).isEqualTo(testCase.expectedDecision());
            assertThat(result.actionType()).as(testCase.name()).isEqualTo(testCase.expectedActionType());
            assertThat(result.requiresConfirmation()).as(testCase.name()).isEqualTo(testCase.expectedConfirmation());
            if (result.decision() == ConversationPlannerDecision.PROPOSE_ACTION) {
                assertThat(testCase.availableActionTypes()).as(testCase.name()).contains(result.actionType());
            }
        }
    }

    @Test
    void reportPreflightEvaluationSetKeepsFindingsStructured() {
        var cases = List.of(
                new PreflightEvaluationCase(
                        "uploaded photo evidence passes",
                        preflightInput(
                                List.of(Map.of(
                                        "photoId", 10,
                                        "stepCode", "PHOTOS",
                                        "status", "UPLOADED",
                                        "workingImageUploaded", true)),
                                List.of()),
                        """
                                {
                                  "status": "PASS",
                                  "summary": "Required report context and uploaded photo evidence are present.",
                                  "confidence": "HIGH",
                                  "issues": []
                                }
                                """,
                        ReportPreflightStatus.PASS,
                        List.of()),
                new PreflightEvaluationCase(
                        "deterministic missing photo remains visible",
                        preflightInput(
                                List.of(),
                                List.of(new ReportPreflightFindingSummary(
                                        "DETERMINISTIC",
                                        "PHOTO_EVIDENCE_MISSING",
                                        "HIGH",
                                        "PHOTOS",
                                        "Required photo evidence is missing.",
                                        "No uploaded photo evidence is attached.",
                                        Map.of()))),
                        """
                                {
                                  "status": "WARN",
                                  "summary": "The report needs photo evidence before document generation.",
                                  "confidence": "HIGH",
                                  "issues": [
                                    {
                                      "code": "PHOTO_EVIDENCE_MISSING",
                                      "category": "EVIDENCE",
                                      "severity": "HIGH",
                                      "location": "PHOTOS",
                                      "message": "Required photo evidence is missing.",
                                      "evidence": "Deterministic finding PHOTO_EVIDENCE_MISSING is present.",
                                      "suggestion": "Attach site photo evidence or mark the evidence policy explicitly.",
                                      "replacement": ""
                                    }
                                  ]
                                }
                                """,
                        ReportPreflightStatus.WARN,
                        List.of("PHOTO_EVIDENCE_MISSING")),
                new PreflightEvaluationCase(
                        "signature evidence missing fails generation gate",
                        preflightInput(
                                List.of(Map.of(
                                        "photoId", 11,
                                        "stepCode", "PHOTOS",
                                        "status", "UPLOADED",
                                        "workingImageUploaded", true)),
                                List.of(new ReportPreflightFindingSummary(
                                        "DETERMINISTIC",
                                        "SIGNATURE_SLOT_MISSING",
                                        "HIGH",
                                        "SIGNATURES",
                                        "Required signature slot is missing.",
                                        "No supervisor signature slot is mapped.",
                                        Map.of()))),
                        """
                                {
                                  "status": "FAIL",
                                  "summary": "The report cannot be generated until required signature slots are mapped.",
                                  "confidence": "HIGH",
                                  "issues": [
                                    {
                                      "code": "SIGNATURE_SLOT_MISSING",
                                      "category": "COMPLETENESS",
                                      "severity": "HIGH",
                                      "location": "SIGNATURES",
                                      "message": "Required signature slot is missing.",
                                      "evidence": "Deterministic finding SIGNATURE_SLOT_MISSING is present.",
                                      "suggestion": "Assign the supervisor signature slot before document generation.",
                                      "replacement": ""
                                    }
                                  ]
                                }
                                """,
                        ReportPreflightStatus.FAIL,
                        List.of("SIGNATURE_SLOT_MISSING")),
                new PreflightEvaluationCase(
                        "legal evidence context missing remains warning",
                        preflightInput(
                                List.of(Map.of(
                                        "photoId", 12,
                                        "stepCode", "PHOTOS",
                                        "status", "UPLOADED",
                                        "workingImageUploaded", true)),
                                List.of(new ReportPreflightFindingSummary(
                                        "DETERMINISTIC",
                                        "LEGAL_EVIDENCE_CONTEXT_MISSING",
                                        "MEDIUM",
                                        "LEGAL_REFERENCES",
                                        "Legal evidence context is missing.",
                                        "No source-backed legal reference is attached.",
                                        Map.of()))),
                        """
                                {
                                  "status": "WARN",
                                  "summary": "Legal evidence context should be attached before final review.",
                                  "confidence": "MEDIUM",
                                  "issues": [
                                    {
                                      "code": "LEGAL_EVIDENCE_CONTEXT_MISSING",
                                      "category": "LEGAL_RISK",
                                      "severity": "MEDIUM",
                                      "location": "LEGAL_REFERENCES",
                                      "message": "Legal evidence context is missing.",
                                      "evidence": "The deterministic legal evidence finding remains unresolved.",
                                      "suggestion": "Attach source-backed legal references or mark the review as manual.",
                                      "replacement": ""
                                    }
                                  ]
                                }
                        """,
                        ReportPreflightStatus.WARN,
                        List.of("LEGAL_EVIDENCE_CONTEXT_MISSING")));

        harness.gateway().respondToAny(new FakeResponseProgram.Sequence(
                cases.stream()
                        .map(testCase -> (FakeResponseProgram) new FakeResponseProgram.ImmediateText(testCase.modelResponse()))
                        .toList()));
        var spec = new ReportPreflightHarnessFactory(objectMapper).spec(new RecordingFindingSink());

        for (var testCase : cases) {
            var run = harness.runHarness(spec, testCase.input());

            assertThat(run.flow().state()).as(testCase.name()).isEqualTo(FlowState.FINISHED);
            var result = validValue(run.context().latestValidation(), ReportPreflightResult.class);

            assertThat(result.status()).as(testCase.name()).isEqualTo(testCase.expectedStatus());
            assertThat(result.issues()).as(testCase.name()).allSatisfy(issue -> {
                assertThat(issue.code()).isNotBlank();
                assertThat(issue.message()).isNotBlank();
                assertThat(issue.severity()).isInstanceOf(ReportPreflightIssueSeverity.class);
            });
            assertThat(result.issues().stream().map(issue -> issue.code()).toList())
                    .as(testCase.name())
                    .containsAll(testCase.requiredIssueCodes());
            if (result.status() == ReportPreflightStatus.PASS) {
                assertThat(result.issues()).as(testCase.name()).isEmpty();
            }
        }
    }

    private static LegalDigestInput legalDigestInput(List<LegalDigestArticleChange> changes) {
        return new LegalDigestInput(
                "change-set-1",
                "BUILDING_ACT",
                "Building Act",
                "LAW",
                "NATIONAL_LAW_OPEN_DATA",
                "2026-07-01",
                "2026-06-09T00:00:00Z",
                "Evaluation source summary",
                changes);
    }

    private static ConversationPlannerInput plannerInput(
            String userMessage,
            String stage,
            List<ConversationPlannerActionOption> availableActions
    ) {
        return new ConversationPlannerInput(
                "1",
                "10",
                "",
                "",
                stage,
                "ko-KR",
                userMessage,
                availableActions,
                List.of(),
                List.of(),
                List.of());
    }

    private static ConversationPlannerActionOption action(String actionType, boolean requiresConfirmation) {
        return new ConversationPlannerActionOption(
                actionType,
                actionType,
                "Evaluation action " + actionType,
                requiresConfirmation);
    }

    private static ReportPreflightInput preflightInput(
            List<Map<String, Object>> photos,
            List<ReportPreflightFindingSummary> deterministicFindings
    ) {
        return new ReportPreflightInput(
                "1",
                "100",
                "CONSTRUCTION_DAILY_SUPERVISION_LOG",
                "Daily supervision log",
                "DRAFT",
                3,
                Map.of("projectName", "Tower A", "siteName", "Main Site"),
                Map.of("BASIC", Map.of("weather", "clear"), "PHOTOS", Map.of()),
                photos,
                deterministicFindings);
    }

    private static <T> T validValue(Optional<ValidationResult<?>> validation, Class<T> type) {
        assertThat(validation).isPresent();
        var value = validation.orElseThrow();
        assertThat(value).isInstanceOf(ValidationResult.Valid.class);
        var valid = (ValidationResult.Valid<?>) value;
        assertThat(valid.value()).isInstanceOf(type);
        return type.cast(valid.value());
    }

    private record LegalDigestEvaluationCase(
            String name,
            LegalDigestInput input,
            String modelResponse,
            LegalDigestStatus expectedStatus,
            List<String> requiredKeyArticles,
            List<String> requiredReportTypes,
            List<String> requiredCatalogItems
    ) {
        List<String> inputArticleKeys() {
            return input.articleChanges().stream()
                    .map(LegalDigestArticleChange::articleKey)
                    .toList();
        }
    }

    private record PlannerEvaluationCase(
            String name,
            ConversationPlannerInput input,
            String modelResponse,
            ConversationPlannerDecision expectedDecision,
            String expectedActionType,
            boolean expectedConfirmation
    ) {
        List<String> availableActionTypes() {
            return input.availableActions().stream()
                    .map(ConversationPlannerActionOption::actionType)
                    .toList();
        }
    }

    private record PreflightEvaluationCase(
            String name,
            ReportPreflightInput input,
            String modelResponse,
            ReportPreflightStatus expectedStatus,
            List<String> requiredIssueCodes
    ) {
    }

    private static final class RecordingFindingSink implements FindingSink {
        private final List<AiFinding> findings = new ArrayList<>();

        @Override
        public void accept(List<AiFinding> findings, AiHarnessRunContext ctx) {
            this.findings.addAll(findings);
        }
    }
}
