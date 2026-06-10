package com.archdox.cloud.worker.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.archdox.cloud.worker.application.ArchDoxWorkerDocumentActionService.DocumentGenerationActionResult;
import com.archdox.cloud.worker.application.ArchDoxWorkerDocumentActionService.PreflightReviewActionResult;
import com.archdox.cloud.workerchat.application.ArchDoxWorkerChatService;
import com.archdox.worker.application.ArchDoxWorkerExecutionContext;
import com.archdox.worker.domain.ArchDoxWorkerAction;
import com.archdox.worker.domain.ArchDoxWorkerActionExecutionStatus;
import com.archdox.worker.domain.ArchDoxWorkerActionOrigin;
import com.archdox.worker.domain.ArchDoxWorkerActionType;
import com.archdox.worker.domain.ArchDoxWorkerRequest;
import com.archdox.worker.domain.ArchDoxWorkerRequestContext;
import com.archdox.worker.domain.ArchDoxWorkerRequestSource;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ArchDoxWorkerDocumentActionExecutorTest {
    private final ArchDoxWorkerDocumentActionService actionService = mock(ArchDoxWorkerDocumentActionService.class);
    private final ArchDoxWorkerChatService chatService = mock(ArchDoxWorkerChatService.class);

    @Test
    void runPreflightReviewExecutesWithoutChatPayload() {
        var context = context(ArchDoxWorkerActionType.RUN_PREFLIGHT_REVIEW, Map.of("reportId", 5L));
        when(actionService.runPreflightReview(context))
                .thenReturn(new PreflightReviewActionResult(5L, 10L, "REQUESTED", 2));
        var executor = new RunPreflightReviewArchDoxWorkerActionExecutor(actionService, chatService);

        var result = executor.execute(context);

        assertThat(result.status()).isEqualTo(ArchDoxWorkerActionExecutionStatus.SUCCEEDED);
        assertThat(result.output())
                .containsEntry("reportId", 5L)
                .containsEntry("preflightRunId", 10L)
                .containsEntry("preflightStatus", "REQUESTED")
                .containsEntry("reportRevision", 2);
        verify(actionService).runPreflightReview(context);
        verifyNoInteractions(chatService);
    }

    @Test
    void runPreflightReviewCompletesWorkerChatWhenChatPayloadIsPresent() {
        var context = context(
                ArchDoxWorkerActionType.RUN_PREFLIGHT_REVIEW,
                Map.of("reportId", 5L, "sessionId", 20L, "assistantMessageId", 30L));
        var actionResult = new PreflightReviewActionResult(5L, 10L, "REQUESTED", 2);
        when(chatService.isAssistantActionPending(2L, 20L, 30L)).thenReturn(true);
        when(actionService.runPreflightReview(context)).thenReturn(actionResult);
        var executor = new RunPreflightReviewArchDoxWorkerActionExecutor(actionService, chatService);

        var result = executor.execute(context);

        assertThat(result.status()).isEqualTo(ArchDoxWorkerActionExecutionStatus.SUCCEEDED);
        verify(actionService).runPreflightReview(context);
        verify(chatService).completePreflightReviewFromWorker(2L, 1L, 3L, 20L, 30L, actionResult);
    }

    @Test
    void runPreflightReviewDoesNotExecuteWhenChatPayloadIsNoLongerPending() {
        var context = context(
                ArchDoxWorkerActionType.RUN_PREFLIGHT_REVIEW,
                Map.of("reportId", 5L, "sessionId", 20L, "assistantMessageId", 30L));
        when(chatService.isAssistantActionPending(2L, 20L, 30L)).thenReturn(false);
        var executor = new RunPreflightReviewArchDoxWorkerActionExecutor(actionService, chatService);

        var result = executor.execute(context);

        assertThat(result.status()).isEqualTo(ArchDoxWorkerActionExecutionStatus.CANCELLED);
        assertThat(result.resultCode()).isEqualTo("ARCHDOX_WORKER_CHAT_ACTION_NOT_PENDING");
        verifyNoInteractions(actionService);
    }

    @Test
    void requestDocumentGenerationExecutesWithoutChatPayload() {
        var context = context(ArchDoxWorkerActionType.REQUEST_DOCUMENT_GENERATION, Map.of("reportId", 5L));
        when(actionService.requestDocumentGeneration(context))
                .thenReturn(new DocumentGenerationActionResult(
                        5L,
                        11L,
                        "REQUESTED",
                        "QUEUED",
                        0,
                        "DOCX",
                        "CLOUD_MANAGED"));
        var executor = new RequestDocumentGenerationArchDoxWorkerActionExecutor(actionService, chatService);

        var result = executor.execute(context);

        assertThat(result.status()).isEqualTo(ArchDoxWorkerActionExecutionStatus.SUCCEEDED);
        assertThat(result.output())
                .containsEntry("reportId", 5L)
                .containsEntry("documentJobId", 11L)
                .containsEntry("documentJobStatus", "REQUESTED")
                .containsEntry("outputFormat", "DOCX");
        verify(actionService).requestDocumentGeneration(context);
        verifyNoInteractions(chatService);
    }

    private ArchDoxWorkerExecutionContext context(
            ArchDoxWorkerActionType actionType,
            Map<String, Object> payload
    ) {
        var request = new ArchDoxWorkerRequest(
                UUID.randomUUID(),
                ArchDoxWorkerRequestSource.UI,
                "test",
                new ArchDoxWorkerRequestContext(1L, 2L, 3L, 4L, 5L, null, "ko-KR"),
                Instant.now());
        var action = new ArchDoxWorkerAction(
                actionType,
                payload,
                "test",
                1.0d,
                ArchDoxWorkerActionOrigin.SYSTEM);
        return new ArchDoxWorkerExecutionContext(request, action);
    }
}
