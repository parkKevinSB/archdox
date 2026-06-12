package com.archdox.documentai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.parkkevinsb.flower.ai.harness.finding.AiFinding;
import io.github.parkkevinsb.flower.ai.harness.finding.FindingSink;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunContext;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunId;
import io.github.parkkevinsb.flower.ai.harness.test.AiHarnessTestExtension;
import io.github.parkkevinsb.flower.ai.harness.test.fake.FakeResponseProgram;
import io.github.parkkevinsb.flower.core.flow.FlowState;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class ReportPreflightHarnessFactoryTest {
    @RegisterExtension
    final AiHarnessTestExtension harness = new AiHarnessTestExtension();

    @Test
    void validPreflightResponseEmitsFindings() {
        var sink = new RecordingFindingSink();
        harness.gateway().respondToAny(new FakeResponseProgram.ImmediateText("""
                {
                  "status": "WARN",
                  "summary": "The report needs attention.",
                  "confidence": "HIGH",
                  "issues": [
                    {
                      "code": "VAGUE_SAFETY_REMARK",
                      "category": "COMPLIANCE",
                      "severity": "MEDIUM",
                      "location": "steps.CHECKLIST.payload.safetyRemark",
                      "message": "The safety remark is too vague for document generation.",
                      "evidence": "safetyRemark=good",
                      "suggestion": "Describe the checked condition and observed result.",
                      "replacement": "Safety condition was checked and no issue was found."
                    }
                  ]
                }
                """));

        var spec = new ReportPreflightHarnessFactory(new ObjectMapper()).spec(sink);
        var flow = harness.runHarness(spec, input());

        assertThat(flow.flow().state()).isEqualTo(FlowState.FINISHED);
        assertThat(sink.findings)
                .hasSize(1)
                .first()
                .satisfies(finding -> {
                    assertThat(finding.code()).isEqualTo("VAGUE_SAFETY_REMARK");
                    assertThat(finding.attributes()).containsEntry("source", "AI");
                    assertThat(finding.attributes()).containsEntry("category", "COMPLIANCE");
                    assertThat(finding.attributes()).containsEntry("reviewStatus", "WARN");
                    assertThat(finding.attributes()).containsEntry("replacement", "Safety condition was checked and no issue was found.");
                });
    }

    @Test
    void malformedJsonIsRefinedBeforeSuccess() {
        var sink = new RecordingFindingSink();
        harness.gateway().respondToAny(new FakeResponseProgram.Sequence(List.of(
                new FakeResponseProgram.ImmediateText("not-json"),
                new FakeResponseProgram.ImmediateText("""
                        {
                          "status": "PASS",
                          "summary": "Ready for generation.",
                          "confidence": "MEDIUM",
                          "issues": []
                        }
                        """))));

        var spec = new ReportPreflightHarnessFactory(new ObjectMapper()).spec(sink);
        var flow = harness.runHarness(spec, input());

        assertThat(flow.flow().state()).isEqualTo(FlowState.FINISHED);
        assertThat(flow.context().attempt()).isEqualTo(2);
        assertThat(sink.findings).isEmpty();
    }

    @Test
    void promptGuidesKoreanOutputAndSavedAtDateSeverity() {
        var ctx = new AiHarnessRunContext(
                new AiHarnessRunId("test-run"),
                ReportPreflightHarnessFactory.HARNESS_ID,
                ReportPreflightHarnessFactory.PROMPT_VERSION,
                Instant.parse("2026-06-01T00:00:00Z"));

        var prompt = new ReportPreflightPromptBuilder(new ObjectMapper()).build(input(), ctx);
        var system = prompt.messages().get(0).content();

        assertThat(prompt.version().version()).isEqualTo("1.2.1");
        assertThat(system)
                .contains("Write summary, message, evidence, and suggestion in Korean")
                .contains("top-level photos array as the source of truth")
                .contains("Use photoEvidenceSummary")
                .contains("allDailyLogPhotoRefsResolved is true")
                .contains("originalPickupStatus is NOT_REQUIRED")
                .contains("savedAt is the data-entry/save timestamp")
                .contains("normally use LOW or MEDIUM")
                .contains("Do not translate stable code/category/severity enum values")
                .contains("sourceBackedLegalReferences")
                .contains("separate ArchDox source-backed legal review harness")
                .contains("Do not claim that legal review passed")
                .contains("Technical criteria wording guidance")
                .contains("창호 자재 성능 확인시 이상 없음")
                .contains("Never claim that specifications, test reports, material approvals, certificates, or attachments were actually attached")
                .contains("별도 확인 및 보관 대상으로 기록합니다")
                .contains("replacement to the exact full Korean text")
                .contains("The replacement value must be final report prose");
        assertThat(prompt.messages().get(1).content())
                .contains("\"photos\"")
                .contains("\"photoEvidenceSummary\"")
                .contains("\"photosStepPayloadEmpty\" : true")
                .contains("\"dailyLogReferencedPhotoIds\"")
                .contains("\"missingDailyLogPhotoIds\" : [ ]")
                .contains("\"allDailyLogPhotoRefsResolved\" : true")
                .contains("\"workingUploaded\" : true")
                .contains("\"sourceBackedLegalReferences\"");
    }

    private static ReportPreflightInput input() {
        var dailyEntry = Map.<String, Object>of(
                "inspectionItemName", "철근 배근",
                "supervisionContent", "배근 상태 확인",
                "photoIds", List.of(10));
        var dailyGroup = Map.<String, Object>of("entries", List.of(dailyEntry));
        var steps = Map.<String, Object>of(
                "CHECKLIST", Map.of("payload", Map.of("safetyRemark", "good")),
                "PHOTOS", Map.of("payload", Map.of()),
                "DAILY_LOG", Map.of("payload", Map.of(
                        "dailyItems", Map.of("groups", List.of(dailyGroup)))));
        var photos = List.of(Map.<String, Object>of(
                "photoId", 10,
                "stepCode", "PHOTOS",
                "status", "UPLOADED",
                "workingUploaded", true));
        return new ReportPreflightInput(
                "1",
                "20",
                "CONSTRUCTION_DAILY_SUPERVISION_LOG",
                "Daily report",
                "READY_TO_GENERATE",
                3,
                Map.of("reportNo", "R-001"),
                steps,
                photos,
                List.of());
    }

    private static final class RecordingFindingSink implements FindingSink {
        private final List<AiFinding> findings = new ArrayList<>();

        @Override
        public void accept(List<AiFinding> findings, io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunContext ctx) {
            this.findings.addAll(findings);
        }
    }
}
