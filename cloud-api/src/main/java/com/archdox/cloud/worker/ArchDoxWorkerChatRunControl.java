package com.archdox.cloud.worker;

import com.archdox.cloud.workerchat.domain.ArchDoxWorkerChatMessageStatus;
import com.archdox.cloud.workerchat.infra.ArchDoxWorkerChatMessageRepository;
import com.archdox.worker.application.ArchDoxWorkerRunControl;
import com.archdox.worker.application.ArchDoxWorkerRunControlDecision;
import com.archdox.worker.domain.ArchDoxWorkerAction;
import com.archdox.worker.domain.ArchDoxWorkerActionDefinition;
import com.archdox.worker.domain.ArchDoxWorkerRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ArchDoxWorkerChatRunControl implements ArchDoxWorkerRunControl {
    private final ArchDoxWorkerChatMessageRepository messageRepository;

    public ArchDoxWorkerChatRunControl(ArchDoxWorkerChatMessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public ArchDoxWorkerRunControlDecision check(
            ArchDoxWorkerRequest request,
            ArchDoxWorkerAction action,
            ArchDoxWorkerActionDefinition definition
    ) {
        var payload = action.payload();
        var sessionId = longValue(payload.get("sessionId"));
        var assistantMessageId = longValue(payload.get("assistantMessageId"));
        var officeId = request.context().officeId();
        if (officeId == null || sessionId == null || assistantMessageId == null) {
            return ArchDoxWorkerRunControlDecision.allow();
        }
        return messageRepository.findByIdAndOfficeIdAndSessionId(assistantMessageId, officeId, sessionId)
                .filter(message -> message.status() == ArchDoxWorkerChatMessageStatus.CANCELLED)
                .map(message -> ArchDoxWorkerRunControlDecision.cancel(
                        "ARCHDOX_WORKER_CHAT_ACTION_CANCELLED",
                        "Worker Chat action was cancelled before execution."))
                .orElseGet(ArchDoxWorkerRunControlDecision::allow);
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
