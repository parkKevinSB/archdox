package com.archdox.cloud.reportai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.archdox.cloud.operation.application.OperationEventService;
import com.archdox.cloud.operation.domain.OperationEventSeverity;
import com.archdox.cloud.reportai.domain.ReportPreflightReviewRun;
import com.archdox.cloud.reportai.domain.ReportPreflightReviewStatus;
import com.archdox.cloud.reportai.flow.ReportPreflightReviewFlowFactory;
import com.archdox.cloud.reportai.infra.ReportPreflightReviewRunRepository;
import io.github.parkkevinsb.flower.core.context.ExecutionContext;
import io.github.parkkevinsb.flower.core.flow.FlowId;
import io.github.parkkevinsb.flower.core.flow.FlowSnapshot;
import io.github.parkkevinsb.flower.core.flow.FlowState;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.test.util.ReflectionTestUtils;

class ReportPreflightReviewFlowFailureListenerTest {
    private final ReportPreflightReviewRunRepository runRepository = mock(ReportPreflightReviewRunRepository.class);
    private final OperationEventService operationEventService = mock(OperationEventService.class);
    private final ReportPreflightReviewFlowFailureListener listener =
            new ReportPreflightReviewFlowFailureListener(runRepository, operationEventService);

    @Test
    void marksReviewRunFailedWhenReportPreflightFlowFails() {
        var now = OffsetDateTime.parse("2026-06-10T09:00:00+09:00");
        var run = new ReportPreflightReviewRun(10L, 100L, 3, 7L, now);
        ReflectionTestUtils.setField(run, "id", 200L);
        run.markRunning(now);
        when(runRepository.findById(200L)).thenReturn(Optional.of(run));
        var flow = new FlowSnapshot(
                FlowId.of(ReportPreflightReviewFlowFactory.FLOW_TYPE, "report:100:preflight-run:200"),
                FlowState.FAILED,
                "run-source-backed-legal-review",
                4,
                new IllegalStateException("legal review input failed"),
                ExecutionContext.builder()
                        .tenantId("10")
                        .userId("7")
                        .runId("200")
                        .correlationId("report:100:preflight-run:200")
                        .build());

        listener.onFlowFailed(flow, flow.failureCause());

        assertThat(run.status()).isEqualTo(ReportPreflightReviewStatus.FAILED);
        assertThat(run.terminalReason())
                .contains("FLOW_FAILED")
                .contains("run-source-backed-legal-review")
                .contains("IllegalStateException")
                .contains("legal review input failed");
        verify(operationEventService).record(
                eq(10L),
                eq(OperationEventSeverity.ERROR),
                eq("REPORT_PREFLIGHT_REVIEW_FLOW_FAILED"),
                eq(ReportPreflightReviewFlowFactory.FLOW_TYPE),
                eq("report:100:preflight-run:200"),
                eq("REPORT_PREFLIGHT_REVIEW_RUN"),
                eq(200L),
                eq(7L),
                isNull(),
                eq("Report preflight review Flower flow failed."),
                ArgumentMatchers.<Map<String, Object>>any());
    }
}
