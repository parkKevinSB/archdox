package com.archdox.cloud.reportai.flow.step;

import com.archdox.cloud.reportai.application.ReportPreflightReviewFlowService;
import com.archdox.cloud.reportai.flow.ReportPreflightAiReviewWorker;
import com.archdox.cloud.reportai.flow.ReportPreflightReviewRequest;
import io.github.parkkevinsb.flower.ai.harness.flow.AiHarnessFlow;
import io.github.parkkevinsb.flower.core.step.Step;
import io.github.parkkevinsb.flower.core.step.StepContext;
import io.github.parkkevinsb.flower.core.step.StepResult;

public final class SubmitReportPreflightAiHarnessStep extends Step {
    private final ReportPreflightReviewFlowService flowService;
    private final ReportPreflightAiReviewWorker aiReviewWorker;
    private final ReportPreflightReviewRequest request;
    private final AiHarnessFlow aiHarnessFlow;
    private boolean submitted;

    public SubmitReportPreflightAiHarnessStep(
            ReportPreflightReviewFlowService flowService,
            ReportPreflightAiReviewWorker aiReviewWorker,
            ReportPreflightReviewRequest request,
            AiHarnessFlow aiHarnessFlow
    ) {
        this.flowService = flowService;
        this.aiReviewWorker = aiReviewWorker;
        this.request = request;
        this.aiHarnessFlow = aiHarnessFlow;
    }

    @Override
    protected StepResult onTick(StepContext ctx) {
        if (aiHarnessFlow == null || !flowService.canSubmitAiHarness(request)) {
            return StepResult.done();
        }
        if (!submitted) {
            aiReviewWorker.submit(aiHarnessFlow);
            flowService.markAiHarnessSubmitted(request);
            submitted = true;
        }
        return StepResult.done();
    }
}
