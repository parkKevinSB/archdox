package com.archdox.cloud.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.archdox.cloud.workerchat.domain.ArchDoxWorkerChatMessage;
import com.archdox.cloud.workerchat.domain.ArchDoxWorkerChatMessageRole;
import com.archdox.cloud.workerchat.domain.ArchDoxWorkerChatMessageStatus;
import com.archdox.cloud.workerchat.infra.ArchDoxWorkerChatMessageRepository;
import com.archdox.worker.domain.ArchDoxWorkerAction;
import com.archdox.worker.domain.ArchDoxWorkerActionOrigin;
import com.archdox.worker.domain.ArchDoxWorkerActionType;
import com.archdox.worker.domain.ArchDoxWorkerRequest;
import com.archdox.worker.domain.ArchDoxWorkerRequestContext;
import com.archdox.worker.domain.ArchDoxWorkerRequestSource;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ArchDoxWorkerChatRunControlTest {
    private final ArchDoxWorkerChatMessageRepository messageRepository = mock(ArchDoxWorkerChatMessageRepository.class);
    private final ArchDoxWorkerChatRunControl runControl = new ArchDoxWorkerChatRunControl(messageRepository);

    @Test
    void cancelsWorkerChatActionWhenAssistantMessageWasCancelled() {
        when(messageRepository.findByIdAndOfficeIdAndSessionId(20L, 2L, 10L))
                .thenReturn(Optional.of(message(ArchDoxWorkerChatMessageStatus.CANCELLED)));

        var decision = runControl.check(
                request(),
                action(Map.of("sessionId", 10L, "assistantMessageId", 20L)),
                null);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reasonCode()).isEqualTo("ARCHDOX_WORKER_CHAT_ACTION_CANCELLED");
    }

    @Test
    void allowsWorkerChatActionWhenAssistantMessageIsNotCancelled() {
        when(messageRepository.findByIdAndOfficeIdAndSessionId(20L, 2L, 10L))
                .thenReturn(Optional.of(message(ArchDoxWorkerChatMessageStatus.COMPLETED)));

        var decision = runControl.check(
                request(),
                action(Map.of("sessionId", 10L, "assistantMessageId", 20L)),
                null);

        assertThat(decision.allowed()).isTrue();
    }

    @Test
    void allowsNonWorkerChatActionWithoutChatPayload() {
        var decision = runControl.check(request(), action(Map.of("reportId", 30L)), null);

        assertThat(decision.allowed()).isTrue();
    }

    private ArchDoxWorkerRequest request() {
        return new ArchDoxWorkerRequest(
                UUID.randomUUID(),
                ArchDoxWorkerRequestSource.UI,
                "test",
                new ArchDoxWorkerRequestContext(1L, 2L, 3L, null, 30L, null, "ko-KR"),
                Instant.now());
    }

    private ArchDoxWorkerAction action(Map<String, Object> payload) {
        return new ArchDoxWorkerAction(
                ArchDoxWorkerActionType.RUN_PREFLIGHT_REVIEW,
                payload,
                "test",
                1.0d,
                ArchDoxWorkerActionOrigin.USER);
    }

    private ArchDoxWorkerChatMessage message(ArchDoxWorkerChatMessageStatus status) {
        return new ArchDoxWorkerChatMessage(
                2L,
                10L,
                1L,
                ArchDoxWorkerChatMessageRole.ASSISTANT,
                status,
                "test",
                UUID.randomUUID(),
                ArchDoxWorkerActionType.RUN_PREFLIGHT_REVIEW.name(),
                Map.of(),
                OffsetDateTime.now());
    }
}
