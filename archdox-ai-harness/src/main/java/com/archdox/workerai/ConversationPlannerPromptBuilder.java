package com.archdox.workerai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.parkkevinsb.flower.ai.harness.prompt.PromptBuilder;
import io.github.parkkevinsb.flower.ai.harness.prompt.RenderedPrompt;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

public final class ConversationPlannerPromptBuilder implements PromptBuilder<ConversationPlannerInput> {
    private final ObjectMapper objectMapper;

    public ConversationPlannerPromptBuilder(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public RenderedPrompt build(ConversationPlannerInput input, AiHarnessRunContext ctx) {
        Objects.requireNonNull(input, "input must not be null");
        var system = """
                You are ArchDox Conversation Planner.
                Convert a Korean or English worker-chat message into one safe ArchDox action proposal.
                Return JSON only. Do not include markdown.

                The JSON must match:
                {
                  "decision": "PROPOSE_ACTION|ASK_CLARIFICATION|NO_ACTION",
                  "actionType": "CREATE_SITE|CREATE_REPORT|UPDATE_REPORT_STEP|SUBMIT_REPORT|RUN_PREFLIGHT_REVIEW|REQUEST_DOCUMENT_GENERATION|WORKER_CHAT_ADVANCE or empty",
                  "requiresConfirmation": true,
                  "confidence": 0.0,
                  "userMessage": "short Korean message to show the user",
                  "payload": {},
                  "rationale": "short reason"
                }

                Rules:
                - Propose only one action from availableActions.
                - Do not invent project, site, report, or step ids.
                - For CREATE_SITE, payload may contain name, address, siteType.
                - For CREATE_REPORT, payload may contain siteId, title, reportType.
                - For UPDATE_REPORT_STEP, payload must contain reportId if known, stepCode if known,
                  and payload.workerNote for free text.
                - For SUBMIT_REPORT, payload may contain reportId when known.
                - For RUN_PREFLIGHT_REVIEW, payload may contain reportId when known.
                - For REQUEST_DOCUMENT_GENERATION, payload may contain reportId, outputFormat, and workerType when known.
                - Use ASK_CLARIFICATION when the target site/report/step is ambiguous.
                - Use NO_ACTION for greetings, unrelated messages, or unsafe requests.
                - AI proposes only. ArchDox policy and Flower execution decide whether anything runs.
                """;
        var user = """
                Plan the next ArchDox worker-chat action.

                Input JSON:
                %s
                """.formatted(toJson(promptInput(input)));
        return new RenderedPrompt(List.of(
                new RenderedPrompt.Message(RenderedPrompt.Role.SYSTEM, system),
                new RenderedPrompt.Message(RenderedPrompt.Role.USER, user)), ctx.promptVersion());
    }

    private LinkedHashMap<String, Object> promptInput(ConversationPlannerInput input) {
        var value = new LinkedHashMap<String, Object>();
        value.put("officeId", input.officeId());
        value.put("projectId", input.projectId());
        value.put("siteId", input.siteId());
        value.put("reportId", input.reportId());
        value.put("stage", input.stage());
        value.put("locale", input.locale());
        value.put("userMessage", input.userMessage());
        value.put("availableActions", input.availableActions());
        value.put("sites", input.sites());
        value.put("reports", input.reports());
        value.put("workflowSteps", input.workflowSteps());
        return value;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to render conversation planner prompt input", ex);
        }
    }
}
