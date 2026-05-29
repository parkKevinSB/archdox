package com.archdox.cloud.documentai.flow;

import com.archdox.cloud.documentai.application.DocumentReviewFlowService;
import com.archdox.cloud.documentai.flow.step.AwaitDocumentQaHarnessStep;
import com.archdox.cloud.documentai.flow.step.CompleteDocumentReviewStep;
import com.archdox.cloud.documentai.flow.step.LoadDocumentReviewContextStep;
import com.archdox.cloud.documentai.flow.step.RunDeterministicDocumentReviewStep;
import com.archdox.cloud.documentai.flow.step.SubmitDocumentQaHarnessStep;
import com.archdox.cloud.documentai.flow.step.SummarizeDocumentQaResultStep;
import io.github.parkkevinsb.flower.ai.harness.flow.AiHarnessFlow;
import io.github.parkkevinsb.flower.core.flow.Flow;
import org.springframework.stereotype.Component;

@Component
public class DocumentReviewFlowFactory {
    public static final String FLOW_TYPE = "document-review";

    private final DocumentReviewFlowService flowService;
    private final DocumentAiReviewWorker aiReviewWorker;

    public DocumentReviewFlowFactory(
            DocumentReviewFlowService flowService,
            DocumentAiReviewWorker aiReviewWorker
    ) {
        this.flowService = flowService;
        this.aiReviewWorker = aiReviewWorker;
    }

    public Flow create(DocumentReviewRequest request, AiHarnessFlow documentQaHarnessFlow) {
        return Flow.builder(FLOW_TYPE, "document-job:" + request.documentJobId() + ":review-run:" + request.reviewRunId())
                .step("load-review-context", new LoadDocumentReviewContextStep(flowService, request))
                .step("run-deterministic-validation", new RunDeterministicDocumentReviewStep(flowService, request))
                .step("submit-document-qa-harness", new SubmitDocumentQaHarnessStep(flowService, aiReviewWorker, request, documentQaHarnessFlow))
                .step("await-document-qa-harness", new AwaitDocumentQaHarnessStep(flowService, request))
                .step("summarize-document-qa-result", new SummarizeDocumentQaResultStep(flowService, request))
                .step("complete-document-review", new CompleteDocumentReviewStep(flowService, request))
                .build();
    }
}
