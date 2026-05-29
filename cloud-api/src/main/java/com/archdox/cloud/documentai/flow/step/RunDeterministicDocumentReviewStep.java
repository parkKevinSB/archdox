package com.archdox.cloud.documentai.flow.step;

import com.archdox.cloud.documentai.application.DocumentReviewFlowService;
import com.archdox.cloud.documentai.flow.DocumentReviewRequest;
import io.github.parkkevinsb.flower.core.step.Step;
import io.github.parkkevinsb.flower.core.step.StepContext;
import io.github.parkkevinsb.flower.core.step.StepResult;

public final class RunDeterministicDocumentReviewStep extends Step {
    private final DocumentReviewFlowService flowService;
    private final DocumentReviewRequest request;

    public RunDeterministicDocumentReviewStep(
            DocumentReviewFlowService flowService,
            DocumentReviewRequest request
    ) {
        this.flowService = flowService;
        this.request = request;
    }

    @Override
    protected StepResult onTick(StepContext ctx) {
        flowService.runDeterministicValidation(request);
        return StepResult.done();
    }
}
