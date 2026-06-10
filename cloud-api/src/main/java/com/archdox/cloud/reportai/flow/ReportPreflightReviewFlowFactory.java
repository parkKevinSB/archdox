package com.archdox.cloud.reportai.flow;

import com.archdox.cloud.reportai.application.ReportPreflightReviewFlowService;
import com.archdox.cloud.reportai.flow.step.AwaitReportPreflightAiHarnessStep;
import com.archdox.cloud.reportai.flow.step.CompleteReportPreflightReviewStep;
import com.archdox.cloud.reportai.flow.step.LoadReportPreflightReviewContextStep;
import com.archdox.cloud.reportai.flow.step.RunReportPreflightDeterministicValidationStep;
import com.archdox.cloud.reportai.flow.step.RunReportPreflightLegalReviewStep;
import com.archdox.cloud.reportai.flow.step.SubmitReportPreflightAiHarnessStep;
import com.archdox.cloud.reportai.flow.step.SummarizeReportPreflightAiResultStep;
import io.github.parkkevinsb.flower.ai.harness.flow.AiHarnessFlow;
import io.github.parkkevinsb.flower.core.flow.Flow;
import org.springframework.stereotype.Component;

@Component
public class ReportPreflightReviewFlowFactory {
    public static final String FLOW_TYPE = "report-preflight-review";

    private final ReportPreflightReviewFlowService flowService;
    private final ReportPreflightAiReviewWorker aiReviewWorker;

    public ReportPreflightReviewFlowFactory(
            ReportPreflightReviewFlowService flowService,
            ReportPreflightAiReviewWorker aiReviewWorker
    ) {
        this.flowService = flowService;
        this.aiReviewWorker = aiReviewWorker;
    }

    public Flow create(ReportPreflightReviewRequest request) {
        return create(request, null);
    }

    public Flow create(ReportPreflightReviewRequest request, AiHarnessFlow aiHarnessFlow) {
        return Flow.builder(FLOW_TYPE, "report:" + request.reportId() + ":preflight-run:" + request.reviewRunId())
                .step("load-preflight-context", new LoadReportPreflightReviewContextStep(flowService, request))
                .step("run-deterministic-preflight-validation", new RunReportPreflightDeterministicValidationStep(flowService, request))
                .step("submit-report-preflight-ai-harness", new SubmitReportPreflightAiHarnessStep(flowService, aiReviewWorker, request, aiHarnessFlow))
                .step("await-report-preflight-ai-harness", new AwaitReportPreflightAiHarnessStep(flowService, request))
                .step("run-source-backed-legal-review", new RunReportPreflightLegalReviewStep(flowService, request))
                .step("summarize-report-preflight-ai-result", new SummarizeReportPreflightAiResultStep(flowService, request))
                .step("complete-preflight-review", new CompleteReportPreflightReviewStep(flowService, request))
                .build();
    }
}
