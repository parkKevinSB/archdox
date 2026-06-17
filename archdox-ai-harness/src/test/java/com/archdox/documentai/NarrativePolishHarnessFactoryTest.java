package com.archdox.documentai;

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

import static org.assertj.core.api.Assertions.assertThat;

class NarrativePolishHarnessFactoryTest {
    @RegisterExtension
    final AiHarnessTestExtension harness = new AiHarnessTestExtension();

    @Test
    void validNarrativePolishResponseReturnsDraftSuggestionsWithoutFindings() {
        var sink = new RecordingFindingSink();
        harness.gateway().respondToAny(new FakeResponseProgram.ImmediateText("""
                {
                  "status": "DRAFTED",
                  "summary": "2개 문장을 보고서 문체로 다듬었습니다.",
                  "suggestions": [
                    {
                      "path": "steps.REMARKS.payload.issueAndAction",
                      "label": "지적사항 및 처리결과",
                      "originalText": "철근 갯수 확인함",
                      "polishedText": "철근 배근 수량을 확인하였으며 특이사항은 없습니다.",
                      "reason": "보고서 문체로 정리",
                      "confidence": "HIGH",
                      "applicable": true
                    }
                  ]
                }
                """));

        var spec = new NarrativePolishHarnessFactory(new ObjectMapper()).spec(sink);
        var flow = harness.runHarness(spec, input());

        assertThat(flow.flow().state()).isEqualTo(FlowState.FINISHED);
        assertThat(flow.context().latestValidation()).isPresent();
        assertThat(flow.context().latestValidation().get().isValid()).isTrue();
        assertThat(sink.findings).isEmpty();
    }

    @Test
    void noChangesResponseMustNotContainSuggestions() {
        harness.gateway().respondToAny(new FakeResponseProgram.ImmediateText("""
                {
                  "status": "NO_CHANGES",
                  "summary": "수정할 문장이 없습니다.",
                  "suggestions": []
                }
                """));

        var spec = new NarrativePolishHarnessFactory(new ObjectMapper()).spec((findings, ctx) -> {
        });
        var flow = harness.runHarness(spec, input());

        assertThat(flow.flow().state()).isEqualTo(FlowState.FINISHED);
        assertThat(flow.context().latestValidation()).isPresent();
        assertThat(flow.context().latestValidation().get().isValid()).isTrue();
    }

    @Test
    void promptGuidesConciseDailyLogTone() {
        var ctx = new AiHarnessRunContext(
                new AiHarnessRunId("test-run"),
                NarrativePolishHarnessFactory.HARNESS_ID,
                NarrativePolishHarnessFactory.PROMPT_VERSION,
                Instant.parse("2026-06-01T00:00:00Z"));

        var prompt = new NarrativePolishPromptBuilder(new ObjectMapper()).build(input(), ctx);
        var system = prompt.messages().get(0).content();

        assertThat(prompt.version().version()).isEqualTo("1.0.1");
        assertThat(system)
                .contains("Avoid ceremonial or self-reporting endings")
                .contains("\"보고합니다\"")
                .contains("지적사항이 없습니다.")
                .contains("특기사항이 없습니다.")
                .contains("Do not expand simple no-issue fields");
    }

    private static NarrativePolishInput input() {
        return new NarrativePolishInput(
                "1",
                "10",
                "CONSTRUCTION_DAILY_SUPERVISION_LOG",
                "공사감리일지",
                "DOCUMENT_RENDER_DRAFT",
                List.of(new NarrativePolishField(
                        "steps.REMARKS.payload.issueAndAction",
                        "지적사항 및 처리결과",
                        "철근 갯수 확인함")),
                Map.of("status", "READY_TO_GENERATE"));
    }

    private static final class RecordingFindingSink implements FindingSink {
        private final List<AiFinding> findings = new ArrayList<>();

        @Override
        public void accept(List<AiFinding> findings, io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunContext ctx) {
            this.findings.addAll(findings);
        }
    }
}
