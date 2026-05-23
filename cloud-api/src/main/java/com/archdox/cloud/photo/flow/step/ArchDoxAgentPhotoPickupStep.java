package com.archdox.cloud.photo.flow.step;

import com.archdox.cloud.agent.application.ArchDoxAgentCommandService;
import com.archdox.cloud.photo.application.PhotoPickupProperties;
import com.archdox.cloud.photo.application.PhotoPickupService;
import com.archdox.cloud.photo.event.PhotoOriginalPickupCompleted;
import com.archdox.cloud.photo.event.PhotoOriginalPickupFailed;
import com.archdox.cloud.photo.event.PhotoPickupCommandAckedEvent;
import com.archdox.cloud.photo.event.PhotoPickupCommandCompletedEvent;
import com.archdox.cloud.photo.event.PhotoPickupCommandFailedEvent;
import com.archdox.cloud.photo.event.PhotoPickupRequested;
import io.github.parkkevinsb.flower.core.step.Step;
import io.github.parkkevinsb.flower.core.step.StepContext;
import io.github.parkkevinsb.flower.core.step.StepResult;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

public final class ArchDoxAgentPhotoPickupStep extends Step {
    private static final String SIGNAL_ACK = "photo-pickup-ack";
    private static final String SIGNAL_COMPLETED = "photo-pickup-completed";
    private static final String SIGNAL_FAILED = "photo-pickup-failed";
    private static final int DISPATCH_COMMAND = 0;
    private static final int WAIT_ACK = 10;
    private static final int WAIT_COMPLETION = 20;
    private static final int BACKING_OFF = 30;

    private final PhotoPickupService photoPickupService;
    private final ArchDoxAgentCommandService commandService;
    private final PhotoPickupRequested event;
    private final PhotoPickupProperties properties;
    private Long commandId;
    private int attempt;

    public ArchDoxAgentPhotoPickupStep(
            PhotoPickupService photoPickupService,
            ArchDoxAgentCommandService commandService,
            PhotoPickupRequested event,
            PhotoPickupProperties properties
    ) {
        this.photoPickupService = photoPickupService;
        this.commandService = commandService;
        this.event = event;
        this.properties = properties;
    }

    @Override
    protected void onEnter(StepContext ctx) {
        ctx.subscribe(PhotoPickupCommandAckedEvent.class, received -> {
            if (matchesPhoto(received.officeId(), received.photoId())) {
                ctx.signal(SIGNAL_ACK, received);
            }
        });
        ctx.subscribe(PhotoPickupCommandCompletedEvent.class, received -> {
            if (matchesPhoto(received.officeId(), received.photoId())) {
                ctx.signal(SIGNAL_COMPLETED, received);
            }
        });
        ctx.subscribe(PhotoPickupCommandFailedEvent.class, received -> {
            if (matchesPhoto(received.officeId(), received.photoId())) {
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
                    "Unknown ArchDox Agent photo pickup stepNo: " + ctx.stepNo()));
        };
    }

    private StepResult dispatchCommand(StepContext ctx) {
        if (photoPickupService.isPickedUp(event.officeId(), event.photoId())
                || !photoPickupService.requiresPickup(event.officeId(), event.photoId())) {
            return StepResult.done();
        }
        attempt++;
        try {
            var command = commandService.enqueuePhotoPickup(
                    event.officeId(),
                    event.photoId(),
                    attempt,
                    properties.safeMaxAttempts());
            if (command.isEmpty()) {
                return handleFailure(ctx, "No online ArchDox Agent is available for photo pickup");
            }
            commandId = command.get();
            ctx.startTimeout(properties.safeStepTimeoutMs());
            ctx.setStepNo(WAIT_ACK);
            return StepResult.stay();
        } catch (RuntimeException ex) {
            return handleFailure(ctx, reasonOf(ex.getMessage()));
        }
    }

    private StepResult waitAck(StepContext ctx) {
        if (photoPickupService.isPickedUp(event.officeId(), event.photoId())) {
            publishCompleted(ctx);
            return StepResult.done();
        }
        var failed = ctx.consumeSignal(SIGNAL_FAILED, PhotoPickupCommandFailedEvent.class);
        if (failed != null && matchesCommand(failed.commandId())) {
            return handleFailure(ctx, reasonOf(failed.errorMessage()));
        }
        var completed = ctx.consumeSignal(SIGNAL_COMPLETED, PhotoPickupCommandCompletedEvent.class);
        if (completed != null && matchesCommand(completed.commandId())) {
            return completeFromAgent(ctx, completed.result());
        }
        var acked = ctx.consumeSignal(SIGNAL_ACK, PhotoPickupCommandAckedEvent.class);
        if (acked != null && matchesCommand(acked.commandId())) {
            ctx.startTimeout(properties.safeStepTimeoutMs());
            ctx.setStepNo(WAIT_COMPLETION);
            return StepResult.stay();
        }
        if (ctx.timedOut()) {
            return handleFailure(ctx, "Timed out waiting for ArchDox Agent photo pickup ACK");
        }
        return StepResult.stay();
    }

    private StepResult waitCompletion(StepContext ctx) {
        if (photoPickupService.isPickedUp(event.officeId(), event.photoId())) {
            publishCompleted(ctx);
            return StepResult.done();
        }
        var failed = ctx.consumeSignal(SIGNAL_FAILED, PhotoPickupCommandFailedEvent.class);
        if (failed != null && matchesCommand(failed.commandId())) {
            return handleFailure(ctx, reasonOf(failed.errorMessage()));
        }
        var completed = ctx.consumeSignal(SIGNAL_COMPLETED, PhotoPickupCommandCompletedEvent.class);
        if (completed != null && matchesCommand(completed.commandId())) {
            return completeFromAgent(ctx, completed.result());
        }
        if (ctx.timedOut()) {
            return handleFailure(ctx, "Timed out waiting for ArchDox Agent photo pickup completion");
        }
        return StepResult.stay();
    }

    private StepResult waitBackoff(StepContext ctx) {
        if (photoPickupService.isPickedUp(event.officeId(), event.photoId())) {
            publishCompleted(ctx);
            return StepResult.done();
        }
        var completed = ctx.consumeSignal(SIGNAL_COMPLETED, PhotoPickupCommandCompletedEvent.class);
        if (completed != null && matchesCommand(completed.commandId())) {
            return completeFromAgent(ctx, completed.result());
        }
        if (!ctx.timedOut()) {
            return StepResult.stay();
        }
        commandId = null;
        ctx.setStepNo(DISPATCH_COMMAND);
        return StepResult.stay();
    }

    private StepResult completeFromAgent(StepContext ctx, Map<String, Object> result) {
        try {
            photoPickupService.completeFromAgent(
                    event.officeId(),
                    event.photoId(),
                    result == null ? Map.of() : result);
            publishCompleted(ctx);
            return StepResult.done();
        } catch (RuntimeException ex) {
            return handleFailure(ctx, reasonOf(ex.getMessage()));
        }
    }

    private StepResult handleFailure(StepContext ctx, String message) {
        if (photoPickupService.isPickedUp(event.officeId(), event.photoId())) {
            publishCompleted(ctx);
            return StepResult.done();
        }
        if (attempt >= properties.safeMaxAttempts()) {
            photoPickupService.markFailed(event.officeId(), event.photoId(), message);
            ctx.eventBus().publish(new PhotoOriginalPickupFailed(
                    event.officeId(),
                    event.photoId(),
                    message,
                    attempt,
                    now(ctx)));
            return StepResult.fail(new IllegalStateException(message));
        }
        ctx.startTimeout(properties.retryDelayMs(attempt));
        ctx.setStepNo(BACKING_OFF);
        return StepResult.stay();
    }

    private void publishCompleted(StepContext ctx) {
        ctx.eventBus().publish(new PhotoOriginalPickupCompleted(
                event.officeId(),
                event.photoId(),
                commandId,
                now(ctx)));
    }

    private boolean matchesPhoto(Long officeId, Long photoId) {
        return event.officeId().equals(officeId) && event.photoId().equals(photoId);
    }

    private boolean matchesCommand(Long receivedCommandId) {
        return commandId != null && commandId.equals(receivedCommandId);
    }

    private static String reasonOf(String message) {
        if (message != null && !message.isBlank()) {
            return message;
        }
        return "ArchDox Agent photo pickup failed";
    }

    private static OffsetDateTime now(StepContext ctx) {
        return OffsetDateTime.ofInstant(
                Instant.ofEpochMilli(ctx.clock().currentTimeMillis()),
                ZoneOffset.UTC);
    }
}
