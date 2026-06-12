package com.archdox.legalai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.parkkevinsb.flower.ai.harness.finding.AiFinding;
import io.github.parkkevinsb.flower.ai.harness.finding.FindingSink;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunContext;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunId;
import io.github.parkkevinsb.flower.ai.harness.test.AiHarnessTestExtension;
import io.github.parkkevinsb.flower.ai.harness.test.fake.FakeResponseProgram;
import io.github.parkkevinsb.flower.ai.harness.validate.ValidationResult;
import io.github.parkkevinsb.flower.core.flow.FlowState;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class SourceBackedLegalReviewHarnessFactoryTest {
    @RegisterExtension
    final AiHarnessTestExtension harness = new AiHarnessTestExtension();

    @Test
    void passReviewRequiresReviewedReferencesAndPassReason() {
        var sink = new RecordingFindingSink();
        harness.gateway().respondToAny(new FakeResponseProgram.ImmediateText("""
                {
                  "status": "PASS",
                  "summary": "Supplied legal anchors were reviewed and no legal-risk issue was found.",
                  "confidence": "HIGH",
                  "legalReviewScope": "Daily supervision report checklist and evidence context were reviewed.",
                  "passReason": "The selected checklist item, supervision content, and photo evidence are aligned with supplied anchors.",
                  "limitations": "Dry-run review only. Human review is still recommended.",
                  "reviewedReferenceIds": ["BUILDING_ACT:0025001@v1"],
                  "issues": []
                }
                """));

        var spec = new SourceBackedLegalReviewHarnessFactory(new ObjectMapper()).spec(sink);
        var flow = harness.runHarness(spec, input());

        assertThat(flow.flow().state()).isEqualTo(FlowState.FINISHED);
        assertThat(sink.findings).isEmpty();
        assertThat(flow.context().latestValidation()).hasValueSatisfying(validation -> {
            assertThat(validation).isInstanceOf(ValidationResult.Valid.class);
            @SuppressWarnings("unchecked")
            var valid = (ValidationResult.Valid<SourceBackedLegalReviewResult>) validation;
            assertThat(valid.value().status()).isEqualTo(SourceBackedLegalReviewStatus.PASS);
            assertThat(valid.value().reviewedReferenceIds()).containsExactly("BUILDING_ACT:0025001@v1");
        });
    }

    @Test
    void warningReviewEmitsLegalReviewFinding() {
        var sink = new RecordingFindingSink();
        harness.gateway().respondToAny(new FakeResponseProgram.ImmediateText("""
                {
                  "status": "WARN",
                  "summary": "One source-backed legal review item needs human review.",
                  "confidence": "MEDIUM",
                  "legalReviewScope": "Checklist evidence was reviewed against supplied anchors.",
                  "passReason": "",
                  "limitations": "The review cannot determine field facts beyond the submitted report input.",
                  "reviewedReferenceIds": ["BUILDING_ACT:0025001@v1"],
                  "issues": [
                    {
                      "code": "LEGAL_EVIDENCE_NEEDS_REVIEW",
                      "category": "EVIDENCE",
                      "severity": "MEDIUM",
                      "location": "DAILY_LOG.groups[0].entries[0]",
                      "message": "The report text references inspection but evidence context is too vague.",
                      "evidence": "The supplied legal anchor is linked to the checklist item, but field evidence text is generic.",
                      "suggestion": "Add the inspected member, location, and photo evidence context.",
                      "legalReferenceIds": ["BUILDING_ACT:0025001@v1"],
                      "relatedFieldPath": "DAILY_LOG.groups[0].entries[0].supervisionContent"
                    }
                  ]
                }
                """));

        var spec = new SourceBackedLegalReviewHarnessFactory(new ObjectMapper()).spec(sink);
        var flow = harness.runHarness(spec, input());

        assertThat(flow.flow().state()).isEqualTo(FlowState.FINISHED);
        assertThat(sink.findings)
                .hasSize(1)
                .first()
                .satisfies(finding -> {
                    assertThat(finding.code()).isEqualTo("LEGAL_EVIDENCE_NEEDS_REVIEW");
                    assertThat(finding.attributes()).containsEntry("source", "LEGAL_REVIEW");
                    assertThat(finding.attributes()).containsEntry("legalReferences", "BUILDING_ACT:0025001@v1");
                    assertThat(finding.attributes()).containsEntry("approvalRequired", "true");
                });
    }

    @Test
    void promptStatesSourceBackedRules() {
        var ctx = new AiHarnessRunContext(
                new AiHarnessRunId("test-run"),
                SourceBackedLegalReviewHarnessFactory.HARNESS_ID,
                SourceBackedLegalReviewHarnessFactory.PROMPT_VERSION,
                Instant.parse("2026-06-01T00:00:00Z"));

        var prompt = new SourceBackedLegalReviewPromptBuilder(new ObjectMapper()).build(input(), ctx);
        var system = prompt.messages().get(0).content();

        assertThat(prompt.version().version()).isEqualTo("0.2.1");
        assertThat(system)
                .contains("Use only sourceBackedLegalReferences")
                .contains("Never invent law names")
                .contains("referenceCoverage.passEligibleForPass=false means PASS is not allowed")
                .contains("referenceCoverage.passEligibility.finalEligible=false means PASS is not allowed")
                .contains("referenceCoverage.passBlockers as deterministic server blockers")
                .contains("legalReferenceGrade uses A/B/C/D/X")
                .contains("technicalCriteriaReviewRequired=true")
                .contains("do not claim technical-standard compliance")
                .contains("actual technical criteria were not verified")
                .contains("REPORT_TYPE_ANCHOR alone is broad report-type context")
                .contains("Treat SEARCH_CANDIDATE or LEGAL_CORPUS_SEARCH references as 후보 근거")
                .contains("PASS must explain the legal review scope")
                .contains("PASS does not mean final legal compliance")
                .contains("Never say the report \"meets legal requirements\"");
        assertThat(prompt.messages().get(1).content())
                .contains("\"sourceBackedLegalReferences\"")
                .contains("\"BUILDING_ACT:0025001@v1\"");
    }

    @Test
    void finalComplianceWordingIsRefinedBeforePass() {
        var sink = new RecordingFindingSink();
        harness.gateway().respondToAny(new FakeResponseProgram.Sequence(List.of(
                new FakeResponseProgram.ImmediateText("""
                        {
                          "status": "PASS",
                          "summary": "공사감리일지가 제공된 법령 근거에 부합하여 법적 위험이 없습니다.",
                          "confidence": "HIGH",
                          "legalReviewScope": "감리일지 체크리스트와 사진 증거를 검토했습니다.",
                          "passReason": "제공된 입력은 법적 요구사항을 충족합니다.",
                          "limitations": "Dry-run review only.",
                          "reviewedReferenceIds": ["BUILDING_ACT:0025001@v1"],
                          "issues": []
                        }
                        """),
                new FakeResponseProgram.ImmediateText("""
                        {
                          "status": "PASS",
                          "summary": "제공된 근거와 입력 범위에서는 추가 법률 리스크가 표시되지 않습니다.",
                          "confidence": "HIGH",
                          "legalReviewScope": "감리일지 체크리스트, 감리내용, 사진 증거 연결성을 제공된 근거 범위에서 검토했습니다.",
                          "passReason": "제공된 주요 근거와 입력 범위 안에서는 추가 확인 필요 항목이 표시되지 않았습니다.",
                          "limitations": "제공된 근거와 입력에 한정한 dry-run 검토이며 최종 법률 판단은 아닙니다.",
                          "reviewedReferenceIds": ["BUILDING_ACT:0025001@v1"],
                          "issues": []
                        }
                        """))));

        var spec = new SourceBackedLegalReviewHarnessFactory(new ObjectMapper()).spec(sink);
        var flow = harness.runHarness(spec, input());

        assertThat(flow.flow().state()).isEqualTo(FlowState.FINISHED);
        assertThat(flow.context().attempt()).isEqualTo(2);
        assertThat(flow.context().latestValidation()).hasValueSatisfying(validation -> {
            assertThat(validation).isInstanceOf(ValidationResult.Valid.class);
            @SuppressWarnings("unchecked")
            var valid = (ValidationResult.Valid<SourceBackedLegalReviewResult>) validation;
            assertThat(valid.value().passReason()).contains("추가 확인 필요 항목");
        });
    }

    private static SourceBackedLegalReviewInput input() {
        var reference = new LinkedHashMap<String, Object>();
        reference.put("referenceId", "BUILDING_ACT:0025001@v1");
        reference.put("label", "Building Act Article 25");
        reference.put("resolutionSource", "LEGAL_DOMAIN_BINDING");
        reference.put("bindingScope", "CATALOG_ITEM");
        reference.put("bindingKey", "STEEL_MEMBER_SYMBOL");
        reference.put("relevance", "PRIMARY");
        reference.put("catalogCode", "CONSTRUCTION_SUPERVISION");
        reference.put("catalogVersion", "1");
        reference.put("checklistItemCode", "STEEL_MEMBER_SYMBOL");
        return new SourceBackedLegalReviewInput(
                "10",
                "100",
                "CONSTRUCTION_DAILY_SUPERVISION_LOG",
                "Daily supervision log",
                2,
                Map.of("photoEvidenceStatus", Map.of("allDailyLogPhotoRefsResolved", true)),
                Map.of("DAILY_LOG", Map.of("payload", Map.of("dailyItems", Map.of()))),
                List.of(),
                List.of(Map.copyOf(reference)),
                Map.of("purpose", "SOURCE_BACKED_LEGAL_RISK_REVIEW_DRY_RUN"));
    }

    private static final class RecordingFindingSink implements FindingSink {
        private final List<AiFinding> findings = new ArrayList<>();

        @Override
        public void accept(List<AiFinding> findings, AiHarnessRunContext ctx) {
            this.findings.addAll(findings);
        }
    }
}
