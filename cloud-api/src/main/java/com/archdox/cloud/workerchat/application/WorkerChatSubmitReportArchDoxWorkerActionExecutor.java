package com.archdox.cloud.workerchat.application;

import com.archdox.worker.application.ArchDoxWorkerActionExecutor;
import com.archdox.worker.application.ArchDoxWorkerExecutionContext;
import com.archdox.worker.domain.ArchDoxWorkerActionResult;
import com.archdox.worker.domain.ArchDoxWorkerActionType;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class WorkerChatSubmitReportArchDoxWorkerActionExecutor implements ArchDoxWorkerActionExecutor {
    private final ArchDoxWorkerChatService chatService;

    public WorkerChatSubmitReportArchDoxWorkerActionExecutor(ArchDoxWorkerChatService chatService) {
        this.chatService = chatService;
    }

    @Override
    public ArchDoxWorkerActionType actionType() {
        return ArchDoxWorkerActionType.SUBMIT_REPORT;
    }

    @Override
    public ArchDoxWorkerActionResult execute(ArchDoxWorkerExecutionContext context) {
        var officeId = context.request().context().officeId();
        var userId = context.request().context().userId();
        var projectId = context.request().context().projectId();
        var sessionId = longValue(context.action().payload().get("sessionId"));
        var assistantMessageId = longValue(context.action().payload().get("assistantMessageId"));
        try {
            chatService.submitReportFromWorker(officeId, userId, projectId, sessionId, assistantMessageId, context.action().payload());
            return ArchDoxWorkerActionResult.succeeded(Map.of(
                    "sessionId", sessionId,
                    "assistantMessageId", assistantMessageId));
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
}
