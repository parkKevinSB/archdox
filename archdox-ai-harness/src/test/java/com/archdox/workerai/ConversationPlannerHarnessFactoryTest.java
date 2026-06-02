package com.archdox.workerai;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.parkkevinsb.flower.ai.harness.finding.AiFinding;
import io.github.parkkevinsb.flower.ai.harness.finding.FindingSink;
import io.github.parkkevinsb.flower.ai.harness.test.AiHarnessTestExtension;
import io.github.parkkevinsb.flower.ai.harness.test.fake.FakeResponseProgram;
import io.github.parkkevinsb.flower.ai.harness.validate.ValidationResult;
import io.github.parkkevinsb.flower.core.flow.FlowState;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationPlannerHarnessFactoryTest {
    @RegisterExtension
    final AiHarnessTestExtension harness = new AiHarnessTestExtension();

    @Test
    void validPlannerResponseProducesActionProposal() {
        harness.gateway().respondToAny(new FakeResponseProgram.ImmediateText("""
                {
                  "decision": "PROPOSE_ACTION",
                  "actionType": "CREATE_SITE",
                  "requiresConfirmation": true,
                  "confidence": 0.86,
                  "userMessage": "현장 생성을 제안합니다.",
                  "payload": {
                    "name": "인우빌딩 현장",
                    "siteType": "CONSTRUCTION_SITE"
                  },
                  "rationale": "사용자가 새 현장 생성을 요청했습니다."
                }
                """));

        var spec = new ConversationPlannerHarnessFactory(new ObjectMapper()).spec(new RecordingFindingSink());
        var flow = harness.runHarness(spec, input());

        assertThat(flow.flow().state()).isEqualTo(FlowState.FINISHED);
        assertThat(flow.context().latestValidation()).hasValueSatisfying(validation -> {
            assertThat(validation).isInstanceOf(ValidationResult.Valid.class);
            @SuppressWarnings("unchecked")
            var valid = (ValidationResult.Valid<ConversationPlannerResult>) validation;
            assertThat(valid.value().decision()).isEqualTo(ConversationPlannerDecision.PROPOSE_ACTION);
            assertThat(valid.value().actionType()).isEqualTo("CREATE_SITE");
            assertThat(valid.value().payload()).containsEntry("name", "인우빌딩 현장");
        });
    }

    @Test
    void malformedJsonIsRefinedBeforeSuccess() {
        harness.gateway().respondToAny(new FakeResponseProgram.Sequence(List.of(
                new FakeResponseProgram.ImmediateText("{"),
                new FakeResponseProgram.ImmediateText("""
                        {
                          "decision": "ASK_CLARIFICATION",
                          "actionType": "",
                          "requiresConfirmation": false,
                          "confidence": 0.55,
                          "userMessage": "어떤 현장 작업인지 조금 더 알려주세요.",
                          "payload": {},
                          "rationale": "대상과 작업이 불명확합니다."
                        }
                        """))));

        var spec = new ConversationPlannerHarnessFactory(new ObjectMapper()).spec(new RecordingFindingSink());
        var flow = harness.runHarness(spec, input());

        assertThat(flow.flow().state()).isEqualTo(FlowState.FINISHED);
        assertThat(flow.context().attempt()).isEqualTo(2);
        assertThat(harness.gateway().recordedCalls()).hasSize(2);
    }

    private static ConversationPlannerInput input() {
        return new ConversationPlannerInput(
                "1",
                "10",
                "",
                "",
                "AWAITING_SITE",
                "ko-KR",
                "인우빌딩 현장 만들어줘",
                List.of(new ConversationPlannerActionOption(
                        "CREATE_SITE",
                        "현장 생성",
                        "Create a site in the selected project.",
                        true)),
                List.of(),
                List.of(),
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
