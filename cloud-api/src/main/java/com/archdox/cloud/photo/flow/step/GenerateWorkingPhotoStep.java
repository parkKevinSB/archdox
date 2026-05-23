package com.archdox.cloud.photo.flow.step;

import com.archdox.cloud.photo.application.PhotoDerivativeProperties;
import com.archdox.cloud.photo.application.PhotoDerivativeService;
import com.archdox.cloud.photo.event.PhotoUploadConfirmed;
import io.github.parkkevinsb.flower.core.step.Step;
import io.github.parkkevinsb.flower.core.step.StepContext;
import io.github.parkkevinsb.flower.core.step.StepResult;
import java.util.concurrent.Executor;

public final class GenerateWorkingPhotoStep extends Step {
    private final PhotoDerivativeService derivativeService;
    private final PhotoUploadConfirmed event;
    private final PhotoDerivativeStepTask task;

    public GenerateWorkingPhotoStep(
            PhotoDerivativeService derivativeService,
            PhotoUploadConfirmed event,
            Executor executor,
            PhotoDerivativeProperties properties
    ) {
        this.derivativeService = derivativeService;
        this.event = event;
        this.task = new PhotoDerivativeStepTask("generate-working", event, executor, properties);
    }

    @Override
    protected StepResult onTick(StepContext ctx) {
        return task.tick(ctx, () -> derivativeService.generateWorking(event.officeId(), event.photoId()));
    }

    @Override
    protected void onExit(StepContext ctx) {
        task.dispose();
    }

    @Override
    protected void onReset(StepContext ctx) {
        task.dispose();
    }
}
