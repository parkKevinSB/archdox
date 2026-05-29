package com.archdox.documentai;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.parkkevinsb.flower.ai.harness.finding.AiFinding;
import io.github.parkkevinsb.flower.ai.harness.finding.FindingSink;
import io.github.parkkevinsb.flower.ai.harness.test.AiHarnessTestExtension;
import io.github.parkkevinsb.flower.ai.harness.test.fake.FakeResponseProgram;
import io.github.parkkevinsb.flower.core.flow.FlowState;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentQaHarnessFactoryTest {
    @RegisterExtension
    final AiHarnessTestExtension harness = new AiHarnessTestExtension();

    @Test
    void validQaResponseEmitsFindings() {
        var sink = new RecordingFindingSink();
        harness.gateway().respondToAny(new FakeResponseProgram.ImmediateText("""
                {
                  "status": "WARN",
                  "summary": "One issue found.",
                  "confidence": "HIGH",
                  "issues": [
                    {
                      "code": "MISSING_PHOTO_EVIDENCE",
                      "severity": "MEDIUM",
                      "location": "photos",
                      "message": "Checklist references photos but no artifact summary was provided.",
                      "evidence": "artifacts=[]",
                      "suggestion": "Attach at least one inspection photo."
                    }
                  ]
                }
                """));

        var spec = new DocumentQaHarnessFactory(new ObjectMapper()).spec(sink);
        var flow = harness.runHarness(spec, input());

        assertThat(flow.flow().state()).isEqualTo(FlowState.FINISHED);
        assertThat(flow.context().attempt()).isEqualTo(1);
        assertThat(sink.findings)
                .hasSize(1)
                .first()
                .satisfies(finding -> {
                    assertThat(finding.code()).isEqualTo("MISSING_PHOTO_EVIDENCE");
                    assertThat(finding.location()).isEqualTo("photos");
                    assertThat(finding.attributes()).containsEntry("reviewStatus", "WARN");
                });
    }

    @Test
    void malformedJsonIsRefinedBeforeSuccess() {
        var sink = new RecordingFindingSink();
        harness.gateway().respondToAny(new FakeResponseProgram.Sequence(List.of(
                new FakeResponseProgram.ImmediateText("{"),
                new FakeResponseProgram.ImmediateText("""
                        {
                          "status": "PASS",
                          "summary": "Looks good.",
                          "confidence": "MEDIUM",
                          "issues": []
                        }
                        """))));

        var spec = new DocumentQaHarnessFactory(new ObjectMapper()).spec(sink);
        var flow = harness.runHarness(spec, input());

        assertThat(flow.flow().state()).isEqualTo(FlowState.FINISHED);
        assertThat(flow.context().attempt()).isEqualTo(2);
        assertThat(sink.findings).isEmpty();
        assertThat(harness.gateway().recordedCalls()).hasSize(2);
    }

    private static DocumentQaInput input() {
        return new DocumentQaInput(
                "1",
                "10",
                "20",
                "CONSTRUCTION_DAILY_LOG",
                "Daily report",
                "DOCX",
                Map.of("report", Map.of("title", "Daily report")),
                List.of(),
                "");
    }

    private static final class RecordingFindingSink implements FindingSink {
        private final List<AiFinding> findings = new ArrayList<>();

        @Override
        public void accept(List<AiFinding> findings, io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunContext ctx) {
            this.findings.addAll(findings);
        }
    }
}
