package com.archdox.cloud.engine.inspection.flow.step;

import com.archdox.cloud.engine.inspection.flow.InspectionDocumentReviewFlowService;
import com.archdox.cloud.engine.inspection.flow.InspectionDocumentReviewRequest;
import io.github.parkkevinsb.flower.core.step.Step;
import io.github.parkkevinsb.flower.core.step.StepContext;
import io.github.parkkevinsb.flower.core.step.StepResult;

public final class RunInspectionDocumentValidationStep extends Step {
    private final InspectionDocumentReviewFlowService flowService;
    private final InspectionDocumentReviewRequest request;

    public RunInspectionDocumentValidationStep(
            InspectionDocumentReviewFlowService flowService,
            InspectionDocumentReviewRequest request
    ) {
        this.flowService = flowService;
        this.request = request;
    }

    @Override
    protected StepResult onTick(StepContext ctx) {
        flowService.runValidation(request);
        return StepResult.done();
    }
}
