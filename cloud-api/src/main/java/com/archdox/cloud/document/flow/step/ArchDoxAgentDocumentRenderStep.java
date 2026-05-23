package com.archdox.cloud.document.flow.step;

import com.archdox.cloud.agent.application.ArchDoxAgentCommandService;
import com.archdox.cloud.document.application.DocumentGenerationException;
import com.archdox.cloud.document.application.DocumentGenerationProperties;
import com.archdox.cloud.document.application.DocumentJobService;
import com.archdox.cloud.document.event.DocumentGenerationFailedEvent;
import com.archdox.cloud.document.event.DocumentGenerationRequested;
import com.archdox.cloud.document.event.DocumentRenderCommandAckedEvent;
import com.archdox.cloud.document.event.DocumentRenderCommandCompletedEvent;
import com.archdox.cloud.document.event.DocumentRenderCommandFailedEvent;
import io.github.parkkevinsb.flower.core.step.Step;
import io.github.parkkevinsb.flower.core.step.StepContext;
import io.github.parkkevinsb.flower.core.step.StepResult;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public final class ArchDoxAgentDocumentRenderStep extends Step {
    private static final String SIGNAL_ACK = "document-render-ack";
    private static final String SIGNAL_COMPLETED = "document-render-completed";
    private static final String SIGNAL_FAILED = "document-render-failed";
    private static final int DISPATCH_COMMAND = 0;
    private static final int WAIT_ACK = 10;
    private static final int WAIT_COMPLETION = 20;
    private static final int BACKING_OFF = 30;

    private final DocumentJobService documentJobService;
    private final ArchDoxAgentCommandService archDoxAgentCommandService;
    private final DocumentGenerationRequested event;
    private final DocumentGenerationProperties properties;
    private Long commandId;
    private int attempt;

    public ArchDoxAgentDocumentRenderStep(
            DocumentJobService documentJobService,
            ArchDoxAgentCommandService archDoxAgentCommandService,
            DocumentGenerationRequested event,
            DocumentGenerationProperties properties
    ) {
        this.documentJobService = documentJobService;
        this.archDoxAgentCommandService = archDoxAgentCommandService;
        this.event = event;
        this.properties = properties;
    }

    @Override
    protected void onEnter(StepContext ctx) {
        ctx.subscribe(DocumentRenderCommandAckedEvent.class, received -> {
            if (matchesJob(received.officeId(), received.documentJobId())) {
                ctx.signal(SIGNAL_ACK, received);
            }
        });
        ctx.subscribe(DocumentRenderCommandCompletedEvent.class, received -> {
            if (matchesJob(received.officeId(), received.documentJobId())) {
                ctx.signal(SIGNAL_COMPLETED, received);
            }
        });
        ctx.subscribe(DocumentRenderCommandFailedEvent.class, received -> {
            if (matchesJob(received.officeId(), received.documentJobId())) {
                ctx.signal(SIGNAL_FAILED, received);
            }
        });
    }

    @Override
    protected StepResult onTick(StepContext ctx) {
        return switch (ctx.stepNo()) {
            case DISPATCH_COMMAND -> dispatchCommand(ctx);
            case WAIT_ACK -> waitAck(ctx);
            case WAIT_COMPLETION -> waitCompletion(ctx);
            case BACKING_OFF -> waitBackoff(ctx);
            default -> StepResult.fail(new IllegalStateException(
                    "Unknown ArchDox Agent document render stepNo: " + ctx.stepNo()));
        };
    }

    private StepResult dispatchCommand(StepContext ctx) {
        attempt++;
        try {
            documentJobService.markArchDoxAgentRenderDispatched(event.officeId(), event.documentJobId());
            var payload = documentJobService.buildArchDoxAgentRenderPayload(event.officeId(), event.documentJobId());
            var command = archDoxAgentCommandService.enqueueDocumentRender(
                    event.officeId(),
                    event.documentJobId(),
                    payload,
                    attempt,
                    properties.safeMaxAttempts());
            if (command.isEmpty()) {
                return handleFailure(ctx, new DocumentGenerationException(
                        "ARCHDOX_AGENT_OFFLINE",
                        "No online ArchDox Agent is available for document rendering"));
            }
            commandId = command.get();
            ctx.startTimeout(properties.safeStepTimeoutMs());
            ctx.setStepNo(WAIT_ACK);
            return StepResult.stay();
        } catch (RuntimeException ex) {
            return handleFailure(ctx, ex);
        }
    }

    private StepResult waitAck(StepContext ctx) {
        var failed = ctx.consumeSignal(SIGNAL_FAILED, DocumentRenderCommandFailedEvent.class);
        if (failed != null && matchesCommand(failed.commandId())) {
            return failFromAgent(ctx, failed);
        }
        var completed = ctx.consumeSignal(SIGNAL_COMPLETED, DocumentRenderCommandCompletedEvent.class);
        if (completed != null && matchesCommand(completed.commandId())) {
            return completeFromAgent(ctx, completed);
        }
        var acked = ctx.consumeSignal(SIGNAL_ACK, DocumentRenderCommandAckedEvent.class);
        if (acked != null && matchesCommand(acked.commandId())) {
            documentJobService.markArchDoxAgentRenderAcked(event.officeId(), event.documentJobId());
            ctx.startTimeout(properties.safeStepTimeoutMs());
            ctx.setStepNo(WAIT_COMPLETION);
            return StepResult.stay();
        }
        if (ctx.timedOut()) {
            return handleFailure(ctx, new DocumentGenerationException(
                    "ARCHDOX_AGENT_RENDER_ACK_TIMEOUT",
                    "Timed out waiting for ArchDox Agent document render ACK"));
        }
        return StepResult.stay();
    }

    private StepResult waitCompletion(StepContext ctx) {
        var failed = ctx.consumeSignal(SIGNAL_FAILED, DocumentRenderCommandFailedEvent.class);
        if (failed != null && matchesCommand(failed.commandId())) {
            return failFromAgent(ctx, failed);
        }
        var completed = ctx.consumeSignal(SIGNAL_COMPLETED, DocumentRenderCommandCompletedEvent.class);
        if (completed != null && matchesCommand(completed.commandId())) {
            return completeFromAgent(ctx, completed);
        }
        if (ctx.timedOut()) {
            return handleFailure(ctx, new DocumentGenerationException(
                    "ARCHDOX_AGENT_RENDER_TIMEOUT",
                    "Timed out waiting for ArchDox Agent document render completion"));
        }
        return StepResult.stay();
    }

    private StepResult waitBackoff(StepContext ctx) {
        if (!ctx.timedOut()) {
            return StepResult.stay();
        }
        commandId = null;
        ctx.setStepNo(DISPATCH_COMMAND);
        return StepResult.stay();
    }

    private StepResult completeFromAgent(StepContext ctx, DocumentRenderCommandCompletedEvent completed) {
        try {
            documentJobService.completeArchDoxAgentDocument(
                    event.officeId(),
                    event.documentJobId(),
                    completed.result());
            return StepResult.done();
        } catch (RuntimeException ex) {
            return handleFailure(ctx, ex);
        }
    }

    private StepResult failFromAgent(StepContext ctx, DocumentRenderCommandFailedEvent failed) {
        var message = reasonOf(failed.errorMessage());
        documentJobService.markGenerationFailed(
                event.officeId(),
                event.documentJobId(),
                "ARCHDOX_AGENT_DOCUMENT_RENDER_FAILED",
                message);
        ctx.eventBus().publish(new DocumentGenerationFailedEvent(
                event.officeId(),
                event.reportId(),
                event.documentJobId(),
                ctx.currentStepId(),
                attempt,
                message,
                now(ctx)));
        return StepResult.fail(new DocumentGenerationException("ARCHDOX_AGENT_DOCUMENT_RENDER_FAILED", message));
    }

    private StepResult handleFailure(StepContext ctx, RuntimeException cause) {
        if (attempt >= properties.safeMaxAttempts()) {
            var reason = reasonOf(cause.getMessage());
            documentJobService.markGenerationFailed(
                    event.officeId(),
                    event.documentJobId(),
                    cause instanceof DocumentGenerationException documentGenerationException
                            ? documentGenerationException.errorCode()
                            : "ARCHDOX_AGENT_DOCUMENT_RENDER_FAILED",
                    reason);
            ctx.eventBus().publish(new DocumentGenerationFailedEvent(
                    event.officeId(),
                    event.reportId(),
                    event.documentJobId(),
                    ctx.currentStepId(),
                    attempt,
                    reason,
                    now(ctx)));
            return StepResult.fail(cause);
        }
        ctx.startTimeout(properties.retryDelayMs(attempt));
        ctx.setStepNo(BACKING_OFF);
        return StepResult.stay();
    }

    private boolean matchesJob(Long officeId, Long documentJobId) {
        return event.officeId().equals(officeId) && event.documentJobId().equals(documentJobId);
    }

    private boolean matchesCommand(Long receivedCommandId) {
        return commandId == null || commandId.equals(receivedCommandId);
    }

    private static String reasonOf(String message) {
        if (message != null && !message.isBlank()) {
            return message;
        }
        return "ArchDox Agent document render failed";
    }

    private static OffsetDateTime now(StepContext ctx) {
        return OffsetDateTime.ofInstant(
                Instant.ofEpochMilli(ctx.clock().currentTimeMillis()),
                ZoneOffset.UTC);
    }
}
