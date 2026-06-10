package com.archdox.cloud.reportai.application;

import com.archdox.cloud.operation.application.OperationEventService;
import com.archdox.cloud.operation.domain.OperationEventSeverity;
import com.archdox.cloud.reportai.flow.ReportPreflightReviewFlowFactory;
import com.archdox.cloud.reportai.infra.ReportPreflightReviewRunRepository;
import io.github.parkkevinsb.flower.core.flow.FlowSnapshot;
import io.github.parkkevinsb.flower.core.listener.FlowerListener;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ReportPreflightReviewFlowFailureListener implements FlowerListener {
    private final ReportPreflightReviewRunRepository runRepository;
    private final OperationEventService operationEventService;

    public ReportPreflightReviewFlowFailureListener(
            ReportPreflightReviewRunRepository runRepository,
            OperationEventService operationEventService
    ) {
        this.runRepository = runRepository;
        this.operationEventService = operationEventService;
    }

    @Override
    @Transactional
    public void onFlowFailed(FlowSnapshot flow, Throwable cause) {
        if (!ReportPreflightReviewFlowFactory.FLOW_TYPE.equals(flow.flowId().flowType())) {
            return;
        }
        var reviewRunId = reviewRunId(flow);
        if (reviewRunId == null) {
            return;
        }
        runRepository.findById(reviewRunId).ifPresent(run -> {
            if (run.terminal()) {
                return;
            }
            var reason = reason(flow, cause);
            run.markFailed(reason, OffsetDateTime.now());
            operationEventService.record(
                    run.officeId(),
                    OperationEventSeverity.ERROR,
                    "REPORT_PREFLIGHT_REVIEW_FLOW_FAILED",
                    ReportPreflightReviewFlowFactory.FLOW_TYPE,
                    flow.flowId().flowKey(),
                    "REPORT_PREFLIGHT_REVIEW_RUN",
                    run.id(),
                    run.requestedBy(),
                    null,
                    "Report preflight review Flower flow failed.",
                    Map.of(
                            "reportId", run.reportId(),
                            "reviewRunId", run.id(),
                            "currentStepId", text(flow.currentStepId()),
                            "failureType", cause == null ? "" : cause.getClass().getName(),
                            "failureMessage", cause == null ? "" : text(cause.getMessage())));
        });
    }

    private Long reviewRunId(FlowSnapshot flow) {
        var contextRunId = flow.executionContext().runIdOrNull();
        if (contextRunId != null && !contextRunId.isBlank()) {
            return longOrNull(contextRunId);
        }
        var marker = ":preflight-run:";
        var flowKey = flow.flowId().flowKey();
        var index = flowKey.lastIndexOf(marker);
        if (index < 0) {
            return null;
        }
        return longOrNull(flowKey.substring(index + marker.length()));
    }

    private Long longOrNull(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String reason(FlowSnapshot flow, Throwable cause) {
        var message = cause == null ? "" : text(cause.getMessage());
        var type = cause == null ? "UNKNOWN" : cause.getClass().getSimpleName();
        var step = text(flow.currentStepId());
        var value = "FLOW_FAILED:" + step + ":" + type + (message.isBlank() ? "" : ":" + message);
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}
