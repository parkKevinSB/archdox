package com.archdox.cloud.platformops.flow.step;

import com.archdox.cloud.platformops.application.PlatformOpsDiagnosisService;
import com.archdox.cloud.platformops.event.PlatformOpsDiagnosisRequested;
import io.github.parkkevinsb.flower.core.step.Step;
import io.github.parkkevinsb.flower.core.step.StepContext;
import io.github.parkkevinsb.flower.core.step.StepResult;

public final class SummarizePlatformOpsAiDiagnosisStep extends Step {
    private final PlatformOpsDiagnosisService diagnosisService;
    private final PlatformOpsDiagnosisRequested event;

    public SummarizePlatformOpsAiDiagnosisStep(
            PlatformOpsDiagnosisService diagnosisService,
            PlatformOpsDiagnosisRequested event
    ) {
        this.diagnosisService = diagnosisService;
        this.event = event;
    }

    @Override
    protected StepResult onTick(StepContext ctx) {
        try {
            diagnosisService.summarizeAiDiagnosis(event.opsRunId());
            return StepResult.done();
        } catch (RuntimeException ex) {
            diagnosisService.markRunFailed(event.opsRunId(), ex.getClass().getSimpleName());
            return StepResult.fail(ex);
        }
    }
}
