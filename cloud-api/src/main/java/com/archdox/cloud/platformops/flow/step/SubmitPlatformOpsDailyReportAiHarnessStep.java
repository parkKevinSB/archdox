package com.archdox.cloud.platformops.flow.step;

import com.archdox.cloud.platformops.application.PlatformOpsDailyReportService;
import com.archdox.cloud.platformops.event.PlatformOpsDailyReportRequested;
import com.archdox.cloud.platformops.flow.PlatformOpsAiHarnessWorker;
import io.github.parkkevinsb.flower.core.step.Step;
import io.github.parkkevinsb.flower.core.step.StepContext;
import io.github.parkkevinsb.flower.core.step.StepResult;

public final class SubmitPlatformOpsDailyReportAiHarnessStep extends Step {
    private final PlatformOpsDailyReportService service;
    private final PlatformOpsAiHarnessWorker aiWorker;
    private final PlatformOpsDailyReportRequested event;

    public SubmitPlatformOpsDailyReportAiHarnessStep(
            PlatformOpsDailyReportService service,
            PlatformOpsAiHarnessWorker aiWorker,
            PlatformOpsDailyReportRequested event
    ) {
        this.service = service;
        this.aiWorker = aiWorker;
        this.event = event;
    }

    @Override
    protected StepResult onTick(StepContext ctx) {
        var flow = service.createAiDailyReportHarnessFlow(event.opsRunId());
        if (flow.isPresent()) {
            aiWorker.submit(flow.get());
            service.markAiHarnessSubmitted(event.opsRunId());
        }
        return StepResult.done();
    }
}
