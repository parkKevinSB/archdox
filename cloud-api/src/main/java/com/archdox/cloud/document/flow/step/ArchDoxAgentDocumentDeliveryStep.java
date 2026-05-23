package com.archdox.cloud.document.flow.step;

import com.archdox.cloud.agent.application.ArchDoxAgentCommandService;
import com.archdox.cloud.document.application.DocumentDeliveryProperties;
import com.archdox.cloud.document.application.DocumentDeliveryService;
import com.archdox.cloud.document.event.DocumentDeliveryCommandAckedEvent;
import com.archdox.cloud.document.event.DocumentDeliveryCommandCompletedEvent;
import com.archdox.cloud.document.event.DocumentDeliveryCommandFailedEvent;
import com.archdox.cloud.document.event.DocumentDeliveryRequested;
import io.github.parkkevinsb.flower.core.step.Step;
import io.github.parkkevinsb.flower.core.step.StepContext;
import io.github.parkkevinsb.flower.core.step.StepResult;

public final class ArchDoxAgentDocumentDeliveryStep extends Step {
    private static final String SIGNAL_ACK = "document-delivery-ack";
    private static final String SIGNAL_COMPLETED = "document-delivery-completed";
    private static final String SIGNAL_FAILED = "document-delivery-failed";
    private static final int DISPATCH_COMMAND = 0;
    private static final int WAIT_ACK = 10;
    private static final int WAIT_COMPLETION = 20;
    private static final int BACKING_OFF = 30;

    private final DocumentDeliveryService deliveryService;
    private final ArchDoxAgentCommandService commandService;
    private final DocumentDeliveryRequested event;
    private final DocumentDeliveryProperties properties;
    private Long commandId;
    private int attempt;

    public ArchDoxAgentDocumentDeliveryStep(
            DocumentDeliveryService deliveryService,
            ArchDoxAgentCommandService commandService,
            DocumentDeliveryRequested event,
            DocumentDeliveryProperties properties
    ) {
        this.deliveryService = deliveryService;
        this.commandService = commandService;
        this.event = event;
        this.properties = properties;
    }

    @Override
    protected void onEnter(StepContext ctx) {
        ctx.subscribe(DocumentDeliveryCommandAckedEvent.class, received -> {
            if (matchesDelivery(received.officeId(), received.deliveryRequestId())) {
                ctx.signal(SIGNAL_ACK, received);
            }
        });
        ctx.subscribe(DocumentDeliveryCommandCompletedEvent.class, received -> {
            if (matchesDelivery(received.officeId(), received.deliveryRequestId())) {
                ctx.signal(SIGNAL_COMPLETED, received);
            }
        });
        ctx.subscribe(DocumentDeliveryCommandFailedEvent.class, received -> {
            if (matchesDelivery(received.officeId(), received.deliveryRequestId())) {
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
                    "Unknown ArchDox Agent document delivery stepNo: " + ctx.stepNo()));
        };
    }

    private StepResult dispatchCommand(StepContext ctx) {
        if (deliveryService.isDeliveryCompleted(event.officeId(), event.deliveryRequestId())) {
            return StepResult.done();
        }
        attempt++;
        try {
            var artifact = deliveryService.requireAgentDeliveryArtifact(event.officeId(), event.deliveryRequestId());
            var command = commandService.enqueueDocumentArtifactDelivery(
                    event.officeId(),
                    event.deliveryRequestId(),
                    artifact,
                    attempt,
                    properties.safeMaxAttempts());
            if (command.isEmpty()) {
                return handleFailure(ctx, "No ArchDox Agent is available for document artifact delivery");
            }
            commandId = command.get();
            deliveryService.markAgentDeliveryCommand(event.officeId(), event.deliveryRequestId(), commandId);
            ctx.startTimeout(properties.safeStepTimeoutMs());
            ctx.setStepNo(WAIT_ACK);
            return StepResult.stay();
        } catch (RuntimeException ex) {
            return handleFailure(ctx, reasonOf(ex.getMessage()));
        }
    }

    private StepResult waitAck(StepContext ctx) {
        if (deliveryService.isDeliveryCompleted(event.officeId(), event.deliveryRequestId())) {
            return StepResult.done();
        }
        var failed = ctx.consumeSignal(SIGNAL_FAILED, DocumentDeliveryCommandFailedEvent.class);
        if (failed != null && matchesCommand(failed.commandId())) {
            return handleFailure(ctx, reasonOf(failed.errorMessage()));
        }
        var completed = ctx.consumeSignal(SIGNAL_COMPLETED, DocumentDeliveryCommandCompletedEvent.class);
        if (completed != null && matchesCommand(completed.commandId())) {
            return completeIfReady(ctx);
        }
        var acked = ctx.consumeSignal(SIGNAL_ACK, DocumentDeliveryCommandAckedEvent.class);
        if (acked != null && matchesCommand(acked.commandId())) {
            ctx.startTimeout(properties.safeStepTimeoutMs());
            ctx.setStepNo(WAIT_COMPLETION);
            return StepResult.stay();
        }
        if (ctx.timedOut()) {
            return handleFailure(ctx, "Timed out waiting for ArchDox Agent document delivery ACK");
        }
        return StepResult.stay();
    }

    private StepResult waitCompletion(StepContext ctx) {
        if (deliveryService.isDeliveryCompleted(event.officeId(), event.deliveryRequestId())) {
            return StepResult.done();
        }
        var failed = ctx.consumeSignal(SIGNAL_FAILED, DocumentDeliveryCommandFailedEvent.class);
        if (failed != null && matchesCommand(failed.commandId())) {
            return handleFailure(ctx, reasonOf(failed.errorMessage()));
        }
        var completed = ctx.consumeSignal(SIGNAL_COMPLETED, DocumentDeliveryCommandCompletedEvent.class);
        if (completed != null && matchesCommand(completed.commandId())) {
            return completeIfReady(ctx);
        }
        if (ctx.timedOut()) {
            return handleFailure(ctx, "Timed out waiting for ArchDox Agent document delivery completion");
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

    private StepResult completeIfReady(StepContext ctx) {
        if (deliveryService.isDeliveryCompleted(event.officeId(), event.deliveryRequestId())) {
            return StepResult.done();
        }
        return handleFailure(ctx, "ArchDox Agent reported delivery complete before Cloud received the artifact upload");
    }

    private StepResult handleFailure(StepContext ctx, String message) {
        if (attempt >= properties.safeMaxAttempts()) {
            deliveryService.markAgentDeliveryFailed(event.officeId(), event.deliveryRequestId(), message);
            return StepResult.fail(new IllegalStateException(message));
        }
        ctx.startTimeout(properties.retryDelayMs(attempt));
        ctx.setStepNo(BACKING_OFF);
        return StepResult.stay();
    }

    private boolean matchesDelivery(Long officeId, Long deliveryRequestId) {
        return event.officeId().equals(officeId) && event.deliveryRequestId().equals(deliveryRequestId);
    }

    private boolean matchesCommand(Long receivedCommandId) {
        return commandId == null || commandId.equals(receivedCommandId);
    }

    private static String reasonOf(String message) {
        if (message != null && !message.isBlank()) {
            return message;
        }
        return "ArchDox Agent document artifact delivery failed";
    }
}
