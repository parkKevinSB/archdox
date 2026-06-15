package com.archdox.cloud.engine.inspection.flow;

import com.archdox.cloud.engine.inspection.flow.step.CompleteInspectionDocumentReviewStep;
import com.archdox.cloud.engine.inspection.flow.step.CreateInspectionDocumentReviewSessionStep;
import com.archdox.cloud.engine.inspection.flow.step.ExtractInspectionDocumentFactsStep;
import com.archdox.cloud.engine.inspection.flow.step.NormalizeInspectionDocumentContextStep;
import com.archdox.cloud.engine.inspection.flow.step.RunInspectionDocumentValidationStep;
import io.github.parkkevinsb.flower.core.flow.Flow;
import org.springframework.stereotype.Component;

@Component
public class InspectionDocumentReviewFlowFactory {
    public static final String FLOW_TYPE = "inspection-document-review";

    private final InspectionDocumentReviewFlowService flowService;

    public InspectionDocumentReviewFlowFactory(InspectionDocumentReviewFlowService flowService) {
        this.flowService = flowService;
    }

    public Flow create(InspectionDocumentReviewRequest request) {
        return Flow.builder(FLOW_TYPE, "mcp-inspection-document:" + request.requestId())
                .step("create-review-session", new CreateInspectionDocumentReviewSessionStep(flowService, request))
                .step("extract-inspection-document-facts", new ExtractInspectionDocumentFactsStep(flowService, request))
                .step("normalize-inspection-document-context", new NormalizeInspectionDocumentContextStep(flowService, request))
                .step("run-inspection-document-validation", new RunInspectionDocumentValidationStep(flowService, request))
                .step("complete-inspection-document-review", new CompleteInspectionDocumentReviewStep(flowService, request))
                .build();
    }
}
