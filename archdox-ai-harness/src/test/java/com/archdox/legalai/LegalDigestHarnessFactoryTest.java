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
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class LegalDigestHarnessFactoryTest {
    @RegisterExtension
    final AiHarnessTestExtension harness = new AiHarnessTestExtension();

    @Test
    void validDigestResponseProducesPublishableResult() {
        var sink = new RecordingFindingSink();
        harness.gateway().respondToAny(new FakeResponseProgram.ImmediateText("""
                {
                  "status": "PUBLISHABLE",
                  "title": "건축공사 감리세부기준 서식 변경",
                  "summary": "감리보고서 관련 서식과 별표 항목이 정비되었습니다.",
                  "impactSummary": "공사감리일지와 감리보고서 작성 시 사진 보관, 제출 서식 확인이 필요합니다.",
                  "confidence": "HIGH",
                  "affectedReportTypes": ["CONSTRUCTION_DAILY_SUPERVISION_LOG", "CONSTRUCTION_SUPERVISION_REPORT"],
                  "affectedCatalogItems": ["CONSTRUCTION_SUPERVISION_LEGAL_CONTEXT"],
                  "keyArticles": ["000100", "FORM_001"],
                  "reviewNotes": "원천 조문 기준으로 게시 가능합니다."
                }
                """));

        var spec = new LegalDigestHarnessFactory(new ObjectMapper()).spec(sink);
        var flow = harness.runHarness(spec, input());

        assertThat(flow.flow().state()).isEqualTo(FlowState.FINISHED);
        assertThat(sink.findings).isEmpty();
        assertThat(flow.context().latestValidation()).hasValueSatisfying(validation -> {
            assertThat(validation).isInstanceOf(ValidationResult.Valid.class);
            @SuppressWarnings("unchecked")
            var valid = (ValidationResult.Valid<LegalDigestResult>) validation;
            assertThat(valid.value().status()).isEqualTo(LegalDigestStatus.PUBLISHABLE);
            assertThat(valid.value().affectedReportTypes()).contains("CONSTRUCTION_DAILY_SUPERVISION_LOG");
        });
    }

    @Test
    void humanReviewStatusEmitsFinding() {
        var sink = new RecordingFindingSink();
        harness.gateway().respondToAny(new FakeResponseProgram.ImmediateText("""
                {
                  "status": "NEEDS_HUMAN_REVIEW",
                  "title": "",
                  "summary": "원문 구조가 불명확합니다.",
                  "impactSummary": "",
                  "confidence": "LOW",
                  "affectedReportTypes": [],
                  "affectedCatalogItems": [],
                  "keyArticles": ["BODY"],
                  "reviewNotes": "별지 서식 텍스트가 깨져 있어 관리자가 원문을 확인해야 합니다."
                }
                """));

        var spec = new LegalDigestHarnessFactory(new ObjectMapper()).spec(sink);
        var flow = harness.runHarness(spec, input());

        assertThat(flow.flow().state()).isEqualTo(FlowState.FINISHED);
        assertThat(sink.findings)
                .hasSize(1)
                .first()
                .satisfies(finding -> {
                    assertThat(finding.code()).isEqualTo("LEGAL_DIGEST_NEEDS_HUMAN_REVIEW");
                    assertThat(finding.attributes()).containsEntry("source", "AI_HARNESS");
                    assertThat(finding.attributes()).containsEntry("digestStatus", "NEEDS_HUMAN_REVIEW");
                });
    }

    @Test
    void malformedJsonIsRefinedBeforeSuccess() {
        var sink = new RecordingFindingSink();
        harness.gateway().respondToAny(new FakeResponseProgram.Sequence(List.of(
                new FakeResponseProgram.ImmediateText("{"),
                new FakeResponseProgram.ImmediateText("""
                        {
                          "status": "PUBLISHABLE",
                          "title": "건축법 조문 변경",
                          "summary": "관련 조문이 정비되었습니다.",
                          "impactSummary": "감리 업무에서 최신 조문 확인이 필요합니다.",
                          "confidence": "MEDIUM",
                          "affectedReportTypes": ["CONSTRUCTION_DAILY_SUPERVISION_LOG"],
                          "affectedCatalogItems": ["CONSTRUCTION_SUPERVISION_LEGAL_CONTEXT"],
                          "keyArticles": ["0025001"],
                          "reviewNotes": "재검증 후 정상 응답입니다."
                        }
                        """))));

        var spec = new LegalDigestHarnessFactory(new ObjectMapper()).spec(sink);
        var flow = harness.runHarness(spec, input());

        assertThat(flow.flow().state()).isEqualTo(FlowState.FINISHED);
        assertThat(flow.context().attempt()).isEqualTo(2);
    }

    @Test
    void promptUsesSourceBackedDigestRules() {
        var ctx = new AiHarnessRunContext(
                new AiHarnessRunId("test-run"),
                LegalDigestHarnessFactory.HARNESS_ID,
                LegalDigestHarnessFactory.PROMPT_VERSION,
                Instant.parse("2026-06-01T00:00:00Z"));

        var prompt = new LegalDigestPromptBuilder(new ObjectMapper()).build(input(), ctx);
        var system = prompt.messages().get(0).content();

        assertThat(prompt.version().version()).isEqualTo("0.1.0");
        assertThat(system)
                .contains("Use only legal text and metadata in the input JSON")
                .contains("Do not invent law names")
                .contains("This is not legal advice")
                .contains("tables, forms, attachments");
        assertThat(prompt.messages().get(1).content())
                .contains("\"articleChanges\"")
                .contains("\"BUILDING_ACT\"");
    }

    private static LegalDigestInput input() {
        return new LegalDigestInput(
                "8",
                "BUILDING_ACT",
                "Building Act",
                "LAW",
                "NATIONAL_LAW_OPEN_DATA",
                "2026-02-27",
                "2026-06-04T01:01:00Z",
                "Article changes: 2",
                List.of(new LegalDigestArticleChange(
                        "0025001",
                        "Article 25",
                        "MODIFIED",
                        "old supervision text",
                        "new supervision text",
                        "0018232025082621035",
                        "2026-02-27",
                        "https://www.law.go.kr")));
    }

    private static final class RecordingFindingSink implements FindingSink {
        private final List<AiFinding> findings = new ArrayList<>();

        @Override
        public void accept(List<AiFinding> findings, AiHarnessRunContext ctx) {
            this.findings.addAll(findings);
        }
    }
}
