package com.archdox.cloud.reportai.flow.step;

import com.archdox.cloud.reportai.application.ReportPreflightReviewFlowService;
import com.archdox.cloud.reportai.flow.ReportPreflightReviewRequest;
import io.github.parkkevinsb.flower.ai.harness.flow.AiHarnessFlow;
import io.github.parkkevinsb.flower.core.step.Step;
import io.github.parkkevinsb.flower.core.step.StepContext;
import io.github.parkkevinsb.flower.core.step.StepResult;

public final class RunReportPreflightLegalReviewStep extends Step {
    private static final int READY_TO_SUBMIT = 0;
    private static final int WAITING_FOR_HARNESS = 10;

    private final ReportPreflightReviewFlowService flowService;
    private final ReportPreflightReviewRequest request;
    private AiHarnessFlow flow;

    public RunReportPreflightLegalReviewStep(
            ReportPreflightReviewFlowService flowService,
            ReportPreflightReviewRequest request
    ) {
        this.flowService = flowService;
        this.request = request;
    }

    @Override
    protected StepResult onTick(StepContext ctx) {
        try {
            if (!flowService.shouldRunSourceBackedLegalReview(request)) {
                return StepResult.done();
            }
            return switch (ctx.stepNo()) {
                case READY_TO_SUBMIT -> submit(ctx);
                case WAITING_FOR_HARNESS -> observe(ctx);
                default -> StepResult.fail(new IllegalStateException(
                        "Unknown report preflight legal review stepNo: " + ctx.stepNo()));
            };
        } catch (RuntimeException ex) {
            flowService.fail(request, ex.getMessage());
            return StepResult.fail(ex);
        }
    }

    private StepResult submit(StepContext ctx) {
        var submission = flowService.submitSourceBackedLegalReview(request);
        if (!submission.submitted()) {
            return StepResult.done();
        }
        this.flow = submission.flow();
        ctx.startTimeout(Math.max(1L, submission.timeout().toMillis()));
        ctx.setStepNo(WAITING_FOR_HARNESS);
        return StepResult.stay();
    }

    private StepResult observe(StepContext ctx) {
        if (flow == null) {
            return StepResult.done();
        }
        if (flowService.isSourceBackedLegalReviewTerminal(flow)) {
            flowService.completeSourceBackedLegalReview(request, flow);
            return StepResult.done();
        }
        if (ctx.timedOut()) {
            flowService.timeoutSourceBackedLegalReview(request, flow);
            return StepResult.done();
        }
        return StepResult.stay();
    }
}
