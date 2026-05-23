package com.archdox.cloud.photo.flow.step;

import com.archdox.cloud.photo.event.PhotoDerivativesGenerated;
import com.archdox.cloud.photo.event.PhotoUploadConfirmed;
import io.github.parkkevinsb.flower.core.step.Step;
import io.github.parkkevinsb.flower.core.step.StepContext;
import io.github.parkkevinsb.flower.core.step.StepResult;
import java.time.OffsetDateTime;

public final class FinalizePhotoDerivativesStep extends Step {
    private final PhotoUploadConfirmed event;

    public FinalizePhotoDerivativesStep(PhotoUploadConfirmed event) {
        this.event = event;
    }

    @Override
    protected StepResult onTick(StepContext ctx) {
        ctx.eventBus().publish(new PhotoDerivativesGenerated(
                event.officeId(),
                event.photoId(),
                OffsetDateTime.now()));
        return StepResult.finish();
    }
}
