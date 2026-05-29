package com.archdox.cloud.platformops.flow.step;

import com.archdox.cloud.platformops.application.PlatformOpsDiagnosisService;
import com.archdox.cloud.platformops.event.PlatformOpsDiagnosisRequested;
import com.archdox.cloud.platformops.flow.PlatformOpsAiDiagnosisWorker;
import io.github.parkkevinsb.flower.core.step.Step;
import io.github.parkkevinsb.flower.core.step.StepContext;
import io.github.parkkevinsb.flower.core.step.StepResult;

public final class SubmitPlatformOpsAiDiagnosisHarnessStep extends Step {
    private final PlatformOpsDiagnosisService diagnosisService;
    private final PlatformOpsAiDiagnosisWorker aiWorker;
    private final PlatformOpsDiagnosisRequested event;
    private boolean submitted;

    public SubmitPlatformOpsAiDiagnosisHarnessStep(
            PlatformOpsDiagnosisService diagnosisService,
            PlatformOpsAiDiagnosisWorker aiWorker,
            PlatformOpsDiagnosisRequested event
    ) {
        this.diagnosisService = diagnosisService;
        this.aiWorker = aiWorker;
        this.event = event;
    }

    @Override
    protected StepResult onTick(StepContext ctx) {
        try {
            if (!submitted) {
                var flow = diagnosisService.createAiDiagnosisHarnessFlow(event.opsRunId());
                flow.ifPresent(aiWorker::submit);
                if (flow.isPresent()) {
                    diagnosisService.markAiHarnessSubmitted(event.opsRunId());
                }
                submitted = true;
            }
            return StepResult.done();
        } catch (RuntimeException ex) {
            diagnosisService.markRunFailed(event.opsRunId(), ex.getClass().getSimpleName());
            return StepResult.fail(ex);
        }
    }
}
