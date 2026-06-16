package com.archdox.cloud.engine.inspection.flow.step;

import com.archdox.cloud.engine.inspection.flow.InspectionDocumentReviewFlowService;
import com.archdox.cloud.engine.inspection.flow.InspectionDocumentReviewRequest;
import io.github.parkkevinsb.flower.core.step.Step;
import io.github.parkkevinsb.flower.core.step.StepContext;
import io.github.parkkevinsb.flower.core.step.StepResult;

public final class CheckInspectionDocumentReviewInputGateStep extends Step {
    public static final String COMPLETE_STEP_ID = "complete-inspection-document-review";

    private final InspectionDocumentReviewFlowService flowService;
    private final InspectionDocumentReviewRequest request;

    public CheckInspectionDocumentReviewInputGateStep(
            InspectionDocumentReviewFlowService flowService,
            InspectionDocumentReviewRequest request
    ) {
        this.flowService = flowService;
        this.request = request;
    }

    @Override
    protected StepResult onTick(StepContext ctx) {
        if (flowService.requiresInputGateBeforeValidation(request)) {
            return StepResult.goTo(COMPLETE_STEP_ID);
        }
        return StepResult.done();
    }
}
