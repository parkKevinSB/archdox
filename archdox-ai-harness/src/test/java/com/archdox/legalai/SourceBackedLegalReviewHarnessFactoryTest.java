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
                      "relatedFieldPath": "DAILY_LOG.groups[0].entries[0].checklistRows[0].referenceNote",
                      "replacement": "철근 배근 상태를 관련 기준 및 설계도서 기준에 따라 확인하였으며, 사진 증빙은 별도로 보관합니다."
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
                    assertThat(finding.attributes()).containsEntry(
                            "replacement",
                            "철근 배근 상태를 관련 기준 및 설계도서 기준에 따라 확인하였으며, 사진 증빙은 별도로 보관합니다.");
                });
    }

    @Test
    void aggregateRemarksRelatedFieldIsRefinedBeforeSuccess() {
        var sink = new RecordingFindingSink();
        harness.gateway().respondToAny(new FakeResponseProgram.Sequence(List.of(
                new FakeResponseProgram.ImmediateText("""
                        {
                          "status": "WARN",
                          "summary": "One source-backed legal review item needs human review.",
                          "confidence": "MEDIUM",
                          "legalReviewScope": "Remarks were reviewed against supplied anchors.",
                          "passReason": "",
                          "limitations": "Dry-run review only.",
                          "reviewedReferenceIds": ["BUILDING_ACT:0025001@v1"],
                          "issues": [
                            {
                              "code": "VAGUE_REMARKS",
                              "category": "LEGAL_RISK",
                              "severity": "LOW",
                              "location": "REMARKS.payload",
                              "message": "특기사항과 다음 조치가 구체적이지 않습니다.",
                              "evidence": "The supplied anchor was reviewed, but the remarks payload is terse.",
                              "suggestion": "특기사항과 다음 조치를 구체적으로 작성하십시오.",
                              "legalReferenceIds": ["BUILDING_ACT:0025001@v1"],
                              "relatedFieldPath": "REMARKS.payload",
                              "replacement": ""
                            }
                          ]
                        }
                        """),
                new FakeResponseProgram.ImmediateText("""
                        {
                          "status": "WARN",
                          "summary": "One source-backed legal review item needs human review.",
                          "confidence": "MEDIUM",
                          "legalReviewScope": "Remarks were reviewed against supplied anchors.",
                          "passReason": "",
                          "limitations": "Dry-run review only.",
                          "reviewedReferenceIds": ["BUILDING_ACT:0025001@v1"],
                          "issues": [
                            {
                              "code": "VAGUE_ISSUE_AND_ACTION",
                              "category": "LEGAL_RISK",
                              "severity": "LOW",
                              "location": "REMARKS.payload.issueAndAction",
                              "message": "지적사항 및 처리결과 문장이 구체적이지 않습니다.",
                              "evidence": "The supplied anchor was reviewed, but issueAndAction is terse.",
                              "suggestion": "지적사항 및 처리결과를 공식 문체로 정리합니다.",
                              "legalReferenceIds": ["BUILDING_ACT:0025001@v1"],
                              "relatedFieldPath": "REMARKS.payload.issueAndAction",
                              "replacement": "현장 점검 결과 이상 사항은 발견되지 않았으며, 전반적으로 양호한 상태임을 확인하였습니다."
                            }
                          ]
                        }
                        """))));

        var spec = new SourceBackedLegalReviewHarnessFactory(new ObjectMapper()).spec(sink);
        var flow = harness.runHarness(spec, input());

        assertThat(flow.flow().state()).isEqualTo(FlowState.FINISHED);
        assertThat(flow.context().attempt()).isEqualTo(2);
        assertThat(sink.findings)
                .hasSize(1)
                .first()
                .satisfies(finding -> {
                    assertThat(finding.location()).isEqualTo("REMARKS.payload.issueAndAction");
                    assertThat(finding.attributes()).containsEntry("relatedFieldPath", "REMARKS.payload.issueAndAction");
                });
    }

    @Test
    void ordinaryDocumentQaIssuesAreRefinedOutOfLegalReview() {
        var sink = new RecordingFindingSink();
        harness.gateway().respondToAny(new FakeResponseProgram.Sequence(List.of(
                new FakeResponseProgram.ImmediateText("""
                        {
                          "status": "WARN",
                          "summary": "검사일자와 특기사항 문구 확인이 필요합니다.",
                          "confidence": "HIGH",
                          "legalReviewScope": "Daily supervision report was reviewed against supplied anchors.",
                          "passReason": "",
                          "limitations": "Dry-run review only.",
                          "reviewedReferenceIds": ["BUILDING_ACT:0025001@v1"],
                          "issues": [
                            {
                              "code": "DATE_INCONSISTENCY",
                              "category": "CONSISTENCY",
                              "severity": "MEDIUM",
                              "location": "BASIC_INFO.payload.inspectionDate",
                              "message": "검사일자가 저장일시보다 하루 이후입니다.",
                              "evidence": "inspectionDate is after savedAt.",
                              "suggestion": "검사일자를 확인하십시오.",
                              "legalReferenceIds": ["BUILDING_ACT:0025001@v1"],
                              "relatedFieldPath": "BASIC_INFO.payload.inspectionDate",
                              "replacement": ""
                            },
                            {
                              "code": "VAGUE_REMARKS_SPECIAL_NOTES",
                              "category": "CONSISTENCY",
                              "severity": "LOW",
                              "location": "REMARKS.payload.specialNotes",
                              "message": "특기사항 문구가 구체적이지 않습니다.",
                              "evidence": "specialNotes is terse.",
                              "suggestion": "특기사항을 다듬으십시오.",
                              "legalReferenceIds": ["BUILDING_ACT:0025001@v1"],
                              "relatedFieldPath": "REMARKS.payload.specialNotes",
                              "replacement": "특이사항 없이 양호함"
                            }
                          ]
                        }
                        """),
                new FakeResponseProgram.ImmediateText("""
                        {
                          "status": "PASS",
                          "summary": "Supplied anchors were reviewed and no additional legal-risk issue was found.",
                          "confidence": "HIGH",
                          "legalReviewScope": "Checklist evidence linkage was reviewed against supplied anchors.",
                          "passReason": "No additional legal-risk issue was detected within the supplied anchors.",
                          "limitations": "Dry-run review only. Ordinary wording issues are handled by general report QA.",
                          "reviewedReferenceIds": ["BUILDING_ACT:0025001@v1"],
                          "issues": []
                        }
                        """))));

        var spec = new SourceBackedLegalReviewHarnessFactory(new ObjectMapper()).spec(sink);
        var flow = harness.runHarness(spec, input());

        assertThat(flow.flow().state()).isEqualTo(FlowState.FINISHED);
        assertThat(flow.context().attempt()).isEqualTo(2);
        assertThat(sink.findings).isEmpty();
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

        assertThat(prompt.version().version()).isEqualTo("0.2.6");
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
                .contains("For vague material/performance notes")
                .contains("Do not decide whether those documents really exist")
                .contains("확인하고 첨부하였습니다")
                .contains("\"replacement\"")
                .contains("replacement must be final report prose")
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
