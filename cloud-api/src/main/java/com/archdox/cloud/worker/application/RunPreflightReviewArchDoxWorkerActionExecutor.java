package com.archdox.cloud.worker.application;

import com.archdox.cloud.workerchat.application.ArchDoxWorkerChatService;
import com.archdox.worker.application.ArchDoxWorkerActionExecutor;
import com.archdox.worker.application.ArchDoxWorkerExecutionContext;
import com.archdox.worker.domain.ArchDoxWorkerActionResult;
import com.archdox.worker.domain.ArchDoxWorkerActionType;
import org.springframework.stereotype.Component;

@Component
public class RunPreflightReviewArchDoxWorkerActionExecutor implements ArchDoxWorkerActionExecutor {
    private final ArchDoxWorkerDocumentActionService actionService;
    private final ArchDoxWorkerChatService chatService;

    public RunPreflightReviewArchDoxWorkerActionExecutor(
            ArchDoxWorkerDocumentActionService actionService,
            ArchDoxWorkerChatService chatService
    ) {
        this.actionService = actionService;
        this.chatService = chatService;
    }

    @Override
    public ArchDoxWorkerActionType actionType() {
        return ArchDoxWorkerActionType.RUN_PREFLIGHT_REVIEW;
    }

    @Override
    public ArchDoxWorkerActionResult execute(ArchDoxWorkerExecutionContext context) {
        var chatContext = chatContext(context);
        if (chatContext != null && !chatService.isAssistantActionPending(
                context.request().context().officeId(),
                chatContext.sessionId(),
                chatContext.assistantMessageId())) {
            return ArchDoxWorkerActionResult.cancelled(
                    "ARCHDOX_WORKER_CHAT_ACTION_NOT_PENDING",
                    "Worker Chat action was no longer pending before execution.");
        }
        var result = actionService.runPreflightReview(context);
        if (chatContext != null) {
            chatService.completePreflightReviewFromWorker(
                    context.request().context().officeId(),
                    context.request().context().userId(),
                    context.request().context().projectId(),
                    chatContext.sessionId(),
                    chatContext.assistantMessageId(),
                    result);
        }
        return ArchDoxWorkerActionResult.succeeded(result.toPayload());
    }

    private ChatContext chatContext(ArchDoxWorkerExecutionContext context) {
        var payload = context.action().payload();
        var sessionId = longValue(payload.get("sessionId"));
        var assistantMessageId = longValue(payload.get("assistantMessageId"));
        if (sessionId == null || assistantMessageId == null) {
            return null;
        }
        return new ChatContext(sessionId, assistantMessageId);
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String string && !string.isBlank()) {
            return Long.parseLong(string.trim());
        }
        return null;
    }

    private record ChatContext(Long sessionId, Long assistantMessageId) {
    }
}
