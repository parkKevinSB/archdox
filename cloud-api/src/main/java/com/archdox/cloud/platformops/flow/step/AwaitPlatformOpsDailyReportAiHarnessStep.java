package com.archdox.cloud.platformops.flow.step;

import com.archdox.cloud.platformops.application.PlatformOpsDailyReportService;
import com.archdox.cloud.platformops.event.PlatformOpsDailyReportRequested;
import io.github.parkkevinsb.flower.core.step.Step;
import io.github.parkkevinsb.flower.core.step.StepContext;
import io.github.parkkevinsb.flower.core.step.StepResult;

public final class AwaitPlatformOpsDailyReportAiHarnessStep extends Step {
    private final PlatformOpsDailyReportService service;
    private final PlatformOpsDailyReportRequested event;

    public AwaitPlatformOpsDailyReportAiHarnessStep(
            PlatformOpsDailyReportService service,
            PlatformOpsDailyReportRequested event
    ) {
        this.service = service;
        this.event = event;
    }

    @Override
    protected StepResult onTick(StepContext ctx) {
        if (service.isAiHarnessTerminal(event.opsRunId())) {
            return StepResult.done();
        }
        return StepResult.stay();
    }
}
