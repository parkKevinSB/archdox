package com.archdox.cloud.platformops.flow.step;

import com.archdox.cloud.platformops.application.PlatformOpsDailyReportService;
import com.archdox.cloud.platformops.event.PlatformOpsDiagnosisRequested;
import com.archdox.cloud.platformops.event.PlatformOpsDailyReportRequested;
import com.archdox.cloud.platformops.flow.PlatformOpsDiagnosisFlowFactory;
import com.archdox.cloud.platformops.flow.PlatformOpsWorker;
import io.github.parkkevinsb.flower.core.step.Step;
import io.github.parkkevinsb.flower.core.step.StepContext;
import io.github.parkkevinsb.flower.core.step.StepResult;

public final class RequestPlatformOpsDailyReportAutoDiagnosisStep extends Step {
    private final PlatformOpsDailyReportService service;
    private final PlatformOpsDiagnosisFlowFactory diagnosisFlowFactory;
    private final PlatformOpsWorker platformOpsWorker;
    private final PlatformOpsDailyReportRequested event;

    public RequestPlatformOpsDailyReportAutoDiagnosisStep(
            PlatformOpsDailyReportService service,
            PlatformOpsDiagnosisFlowFactory diagnosisFlowFactory,
            PlatformOpsWorker platformOpsWorker,
            PlatformOpsDailyReportRequested event
    ) {
        this.service = service;
        this.diagnosisFlowFactory = diagnosisFlowFactory;
        this.platformOpsWorker = platformOpsWorker;
        this.event = event;
    }

    @Override
    protected StepResult onTick(StepContext ctx) {
        try {
            var diagnosisRuns = service.requestAutoDiagnosesBeforeReport(event.opsRunId());
            diagnosisRuns.forEach(run -> platformOpsWorker.submit(diagnosisFlowFactory.create(new PlatformOpsDiagnosisRequested(
                    run.id(),
                    run.incidentId(),
                    null))));
            return StepResult.done();
        } catch (RuntimeException ex) {
            service.markRunFailed(event.opsRunId(), ex.getClass().getSimpleName());
            return StepResult.fail(ex);
        }
    }
}
