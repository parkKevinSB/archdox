package com.archdox.cloud.platformops.flow.step;

import com.archdox.cloud.platformops.application.PlatformOpsDetectionService;
import com.archdox.cloud.platformops.event.PlatformOpsDetectionRequested;
import io.github.parkkevinsb.flower.core.step.Step;
import io.github.parkkevinsb.flower.core.step.StepContext;
import io.github.parkkevinsb.flower.core.step.StepResult;

public final class RunPlatformOpsDetectorsStep extends Step {
    private final PlatformOpsDetectionService detectionService;
    private final PlatformOpsDetectionRequested event;

    public RunPlatformOpsDetectorsStep(
            PlatformOpsDetectionService detectionService,
            PlatformOpsDetectionRequested event
    ) {
        this.detectionService = detectionService;
        this.event = event;
    }

    @Override
    protected StepResult onTick(StepContext ctx) {
        try {
            detectionService.executeStuckDetection(event.opsRunId());
            return StepResult.done();
        } catch (RuntimeException ex) {
            detectionService.markRunFailed(event.opsRunId(), ex.getClass().getSimpleName());
            return StepResult.fail(ex);
        }
    }
}
