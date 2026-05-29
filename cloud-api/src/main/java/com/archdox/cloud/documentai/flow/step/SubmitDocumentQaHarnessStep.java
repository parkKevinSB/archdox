package com.archdox.cloud.documentai.flow.step;

import com.archdox.cloud.documentai.application.DocumentReviewFlowService;
import com.archdox.cloud.documentai.flow.DocumentAiReviewWorker;
import com.archdox.cloud.documentai.flow.DocumentReviewRequest;
import io.github.parkkevinsb.flower.ai.harness.flow.AiHarnessFlow;
import io.github.parkkevinsb.flower.core.step.Step;
import io.github.parkkevinsb.flower.core.step.StepContext;
import io.github.parkkevinsb.flower.core.step.StepResult;

public final class SubmitDocumentQaHarnessStep extends Step {
    private final DocumentReviewFlowService flowService;
    private final DocumentAiReviewWorker aiReviewWorker;
    private final DocumentReviewRequest request;
    private final AiHarnessFlow documentQaHarnessFlow;
    private boolean submitted;

    public SubmitDocumentQaHarnessStep(
            DocumentReviewFlowService flowService,
            DocumentAiReviewWorker aiReviewWorker,
            DocumentReviewRequest request,
            AiHarnessFlow documentQaHarnessFlow
    ) {
        this.flowService = flowService;
        this.aiReviewWorker = aiReviewWorker;
        this.request = request;
        this.documentQaHarnessFlow = documentQaHarnessFlow;
    }

    @Override
    protected StepResult onTick(StepContext ctx) {
        if (flowService.isHarnessTerminal(request)) {
            return StepResult.done();
        }
        if (!submitted) {
            aiReviewWorker.submit(documentQaHarnessFlow);
            flowService.markHarnessSubmitted(request);
            submitted = true;
        }
        return StepResult.done();
    }
}
