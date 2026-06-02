package com.archdox.cloud.workerchat.application;

import com.archdox.worker.application.ArchDoxWorkerActionExecutor;
import com.archdox.worker.application.ArchDoxWorkerExecutionContext;
import com.archdox.worker.domain.ArchDoxWorkerActionResult;
import com.archdox.worker.domain.ArchDoxWorkerActionType;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class WorkerChatAdvanceArchDoxWorkerActionExecutor implements ArchDoxWorkerActionExecutor {
    private final ArchDoxWorkerChatService chatService;

    public WorkerChatAdvanceArchDoxWorkerActionExecutor(ArchDoxWorkerChatService chatService) {
        this.chatService = chatService;
    }

    @Override
    public ArchDoxWorkerActionType actionType() {
        return ArchDoxWorkerActionType.WORKER_CHAT_ADVANCE;
    }

    @Override
    public ArchDoxWorkerActionResult execute(ArchDoxWorkerExecutionContext context) {
        var officeId = context.request().context().officeId();
        var sessionId = longValue(context.action().payload().get("sessionId"));
        var assistantMessageId = longValue(context.action().payload().get("assistantMessageId"));
        var plannerEligible = booleanValue(context.action().payload().get("plannerEligible"));
        try {
            chatService.completeAssistantReply(
                    officeId,
                    sessionId,
                    assistantMessageId,
                    context.request().command(),
                    plannerEligible);
            return ArchDoxWorkerActionResult.succeeded(Map.of(
                    "sessionId", sessionId,
                    "assistantMessageId", assistantMessageId,
                    "plannerEligible", plannerEligible));
        } catch (RuntimeException ex) {
            chatService.failAssistantReply(officeId, sessionId, assistantMessageId, ex.getMessage());
            throw ex;
        }
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String string && !string.isBlank()) {
            return Long.parseLong(string);
        }
        throw new IllegalArgumentException("Required worker action payload value is missing");
    }

    private boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String string && !string.isBlank()) {
            return Boolean.parseBoolean(string);
        }
        return false;
    }
}
