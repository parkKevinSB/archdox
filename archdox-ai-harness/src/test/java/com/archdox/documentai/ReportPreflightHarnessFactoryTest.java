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
                      "category": "WORDING",
                      "severity": "MEDIUM",
                      "location": "REMARKS.payload.issueAndAction",
                      "message": "The safety remark is too vague for document generation.",
                      "evidence": "safetyRemark=good",
                      "suggestion": "Describe the checked condition and observed result.",
                      "replacement": "현장 안전 상태를 확인하였으며, 특이사항은 발견되지 않았습니다."
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
                    assertThat(finding.attributes()).containsEntry("category", "WORDING");
                    assertThat(finding.attributes()).containsEntry("reviewStatus", "WARN");
                    assertThat(finding.attributes()).containsEntry(
                            "replacement",
                            "현장 안전 상태를 확인하였으며, 특이사항은 발견되지 않았습니다.");
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
    void aggregateRemarksLocationIsRefinedBeforeSuccess() {
        var sink = new RecordingFindingSink();
        harness.gateway().respondToAny(new FakeResponseProgram.Sequence(List.of(
                new FakeResponseProgram.ImmediateText("""
                        {
                          "status": "WARN",
                          "summary": "Remarks need attention.",
                          "confidence": "MEDIUM",
                          "issues": [
                            {
                              "code": "VAGUE_REMARKS",
                              "category": "WORDING",
                              "severity": "LOW",
                              "location": "REMARKS.payload",
                              "message": "특기사항과 다음 조치가 구체적이지 않습니다.",
                              "evidence": "remarks payload is terse",
                              "suggestion": "특기사항과 다음 조치를 구체적으로 작성하십시오.",
                              "replacement": ""
                            }
                          ]
                        }
                        """),
                new FakeResponseProgram.ImmediateText("""
                        {
                          "status": "WARN",
                          "summary": "Remarks need attention.",
                          "confidence": "MEDIUM",
                          "issues": [
                            {
                              "code": "VAGUE_SPECIAL_NOTES",
                              "category": "WORDING",
                              "severity": "LOW",
                              "location": "REMARKS.payload.specialNotes",
                              "message": "특기사항 문장이 구체적이지 않습니다.",
                              "evidence": "specialNotes=특기사항 없이 완벽함",
                              "suggestion": "특기사항을 공식 문체로 정리합니다.",
                              "replacement": "특기사항 없이 이상 없습니다."
                            }
                          ]
                        }
                        """))));

        var spec = new ReportPreflightHarnessFactory(new ObjectMapper()).spec(sink);
        var flow = harness.runHarness(spec, input());

        assertThat(flow.flow().state()).isEqualTo(FlowState.FINISHED);
        assertThat(flow.context().attempt()).isEqualTo(2);
        assertThat(sink.findings)
                .hasSize(1)
                .first()
                .satisfies(finding -> {
                    assertThat(finding.location()).isEqualTo("REMARKS.payload.specialNotes");
                    assertThat(finding.attributes()).containsEntry("replacement", "특기사항 없이 이상 없습니다.");
                });
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

        assertThat(prompt.version().version()).isEqualTo("1.2.4");
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
                .contains("Do not decide whether supporting documents really exist")
                .contains("확인하고 첨부하였습니다")
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
                "checklistRows", List.of(Map.of(
                        "code", "RC_REBAR_COUNT_DIAMETER_PITCH",
                        "label", "배근 상태 확인",
                        "result", "COMPLIANT",
                        "referenceNote", "배근 상태 확인")),
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
