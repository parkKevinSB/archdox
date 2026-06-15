package com.archdox.cloud.engine.inspection.flow.step;

import com.archdox.cloud.engine.inspection.flow.InspectionDocumentReviewFlowService;
import com.archdox.cloud.engine.inspection.flow.InspectionDocumentReviewRequest;
import io.github.parkkevinsb.flower.core.step.Step;
import io.github.parkkevinsb.flower.core.step.StepContext;
import io.github.parkkevinsb.flower.core.step.StepResult;

public final class ExtractInspectionDocumentFactsStep extends Step {
    private final InspectionDocumentReviewFlowService flowService;
    private final InspectionDocumentReviewRequest request;

    public ExtractInspectionDocumentFactsStep(
            InspectionDocumentReviewFlowService flowService,
            InspectionDocumentReviewRequest request
    ) {
        this.flowService = flowService;
        this.request = request;
    }

    @Override
    protected StepResult onTick(StepContext ctx) {
        flowService.extractAndSubmitFacts(request);
        return StepResult.done();
    }
}
