package com.archdox.cloud.engine.inspection.flow.step;

import com.archdox.cloud.engine.inspection.flow.InspectionDocumentReviewFlowService;
import com.archdox.cloud.engine.inspection.flow.InspectionDocumentReviewRequest;
import io.github.parkkevinsb.flower.core.step.Step;
import io.github.parkkevinsb.flower.core.step.StepContext;
import io.github.parkkevinsb.flower.core.step.StepResult;

public final class NormalizeInspectionDocumentContextStep extends Step {
    private final InspectionDocumentReviewFlowService flowService;
    private final InspectionDocumentReviewRequest request;

    public NormalizeInspectionDocumentContextStep(
            InspectionDocumentReviewFlowService flowService,
            InspectionDocumentReviewRequest request
    ) {
        this.flowService = flowService;
        this.request = request;
    }

    @Override
    protected StepResult onTick(StepContext ctx) {
        flowService.normalize(request);
        return StepResult.done();
    }
}
