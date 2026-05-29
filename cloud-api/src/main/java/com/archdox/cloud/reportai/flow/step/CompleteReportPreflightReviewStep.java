package com.archdox.cloud.reportai.flow.step;

import com.archdox.cloud.reportai.application.ReportPreflightReviewFlowService;
import com.archdox.cloud.reportai.flow.ReportPreflightReviewRequest;
import io.github.parkkevinsb.flower.core.step.Step;
import io.github.parkkevinsb.flower.core.step.StepContext;
import io.github.parkkevinsb.flower.core.step.StepResult;

public final class CompleteReportPreflightReviewStep extends Step {
    private final ReportPreflightReviewFlowService flowService;
    private final ReportPreflightReviewRequest request;

    public CompleteReportPreflightReviewStep(
            ReportPreflightReviewFlowService flowService,
            ReportPreflightReviewRequest request
    ) {
        this.flowService = flowService;
        this.request = request;
    }

    @Override
    protected StepResult onTick(StepContext ctx) {
        flowService.complete(request);
        return StepResult.done();
    }
}
