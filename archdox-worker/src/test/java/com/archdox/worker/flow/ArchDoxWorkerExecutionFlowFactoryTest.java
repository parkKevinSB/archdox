package com.archdox.worker.flow;

import com.archdox.worker.application.ArchDoxWorkerActionExecutor;
import com.archdox.worker.application.ArchDoxWorkerActionRegistry;
import com.archdox.worker.application.ArchDoxWorkerExecutionContext;
import com.archdox.worker.application.ArchDoxWorkerPolicyGate;
import com.archdox.worker.application.ArchDoxWorkerRunControlDecision;
import com.archdox.worker.application.ArchDoxWorkerTraceEvent;
import com.archdox.worker.application.ArchDoxWorkerTraceEventType;
import com.archdox.worker.application.ArchDoxWorkerTraceSink;
import com.archdox.worker.domain.ArchDoxWorkerAction;
import com.archdox.worker.domain.ArchDoxWorkerActionOrigin;
import com.archdox.worker.domain.ArchDoxWorkerActionResult;
import com.archdox.worker.domain.ArchDoxWorkerActionType;
import com.archdox.worker.domain.ArchDoxWorkerPolicyDecision;
import com.archdox.worker.domain.ArchDoxWorkerRequest;
import com.archdox.worker.domain.ArchDoxWorkerRequestContext;
import io.github.parkkevinsb.flower.core.engine.Engine;
import io.github.parkkevinsb.flower.core.event.InMemoryEventBus;
import io.github.parkkevinsb.flower.core.flow.Flow;
import io.github.parkkevinsb.flower.core.flow.FlowState;
import io.github.parkkevinsb.flower.core.time.ManualClock;
import io.github.parkkevinsb.flower.core.worker.Worker;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ArchDoxWorkerExecutionFlowFactoryTest {
    @Test
    void executes_registered_action_when_policy_allows() {
        var executions = new AtomicInteger();
        var executor = new StubExecutor(ArchDoxWorkerActionType.RUN_PREFLIGHT_REVIEW, context -> {
            executions.incrementAndGet();
            return ArchDoxWorkerActionResult.succeeded(Map.of("findingCount", 0));
        });
        var sink = new RecordingTraceSink();
        var flow = new ArchDoxWorkerExecutionFlowFactory(
                new ArchDoxWorkerActionRegistry(List.of(executor)),
                ArchDoxWorkerPolicyGate.allowAll(),
                sink
        ).create(sampleRequest(), sampleAction(ArchDoxWorkerActionType.RUN_PREFLIGHT_REVIEW));
        var worker = attachedWorker();

        assertThat(flow.snapshot().executionContext().tenantId()).contains("20");
        assertThat(flow.snapshot().executionContext().userId()).contains("10");
        assertThat(flow.snapshot().executionContext().runId()).isPresent();
        assertThat(flow.snapshot().executionContext().correlationId()).isPresent();

        worker.submit(flow);
        tickUntilTerminal(worker, flow);

        assertThat(flow.state()).isEqualTo(FlowState.FINISHED);
        assertThat(executions).hasValue(1);
        assertThat(sink.eventTypes()).containsSequence(
                ArchDoxWorkerTraceEventType.REQUEST_RECEIVED,
                ArchDoxWorkerTraceEventType.POLICY_ALLOWED,
                ArchDoxWorkerTraceEventType.ACTION_STARTED,
                ArchDoxWorkerTraceEventType.ACTION_SUCCEEDED);
        assertThat(sink.events().getLast().attributes()).containsEntry("status", "SUCCEEDED");
    }

    @Test
    void rejects_action_without_registered_executor_before_policy_gate() {
        var sink = new RecordingTraceSink();
        var policyCalls = new AtomicInteger();
        var flow = new ArchDoxWorkerExecutionFlowFactory(
                new ArchDoxWorkerActionRegistry(List.of()),
                (request, action) -> {
                    policyCalls.incrementAndGet();
                    return ArchDoxWorkerPolicyDecision.allow();
                },
                sink
        ).create(sampleRequest(), sampleAction(ArchDoxWorkerActionType.CREATE_REPORT));
        var worker = attachedWorker();

        worker.submit(flow);
        tickUntilTerminal(worker, flow);

        assertThat(flow.state()).isEqualTo(FlowState.FINISHED);
        assertThat(policyCalls).hasValue(0);
        assertThat(sink.eventTypes()).contains(
                ArchDoxWorkerTraceEventType.ACTION_UNKNOWN,
                ArchDoxWorkerTraceEventType.ACTION_REJECTED);
        assertThat(sink.events().getLast().attributes()).containsEntry("status", "REJECTED");
    }

    @Test
    void policy_denial_prevents_execution() {
        var executions = new AtomicInteger();
        var executor = new StubExecutor(ArchDoxWorkerActionType.RUN_PREFLIGHT_REVIEW, context -> {
            executions.incrementAndGet();
            return ArchDoxWorkerActionResult.succeeded(Map.of());
        });
        var sink = new RecordingTraceSink();
        var flow = new ArchDoxWorkerExecutionFlowFactory(
                new ArchDoxWorkerActionRegistry(List.of(executor)),
                (request, action) -> ArchDoxWorkerPolicyDecision.deny("DENIED", "Denied by test policy"),
                sink
        ).create(sampleRequest(), sampleAction(ArchDoxWorkerActionType.RUN_PREFLIGHT_REVIEW));
        var worker = attachedWorker();

        worker.submit(flow);
        tickUntilTerminal(worker, flow);

        assertThat(flow.state()).isEqualTo(FlowState.FINISHED);
        assertThat(executions).hasValue(0);
        assertThat(sink.eventTypes()).contains(
                ArchDoxWorkerTraceEventType.POLICY_DENIED,
                ArchDoxWorkerTraceEventType.ACTION_REJECTED);
        assertThat(sink.events().getLast().attributes()).containsEntry("status", "REJECTED");
    }

    @Test
    void policy_approval_requirement_prevents_execution() {
        var executions = new AtomicInteger();
        var executor = new StubExecutor(ArchDoxWorkerActionType.RUN_PREFLIGHT_REVIEW, context -> {
            executions.incrementAndGet();
            return ArchDoxWorkerActionResult.succeeded(Map.of());
        });
        var sink = new RecordingTraceSink();
        var flow = new ArchDoxWorkerExecutionFlowFactory(
                new ArchDoxWorkerActionRegistry(List.of(executor)),
                (request, action) -> ArchDoxWorkerPolicyDecision.requireApproval(
                        "APPROVAL_REQUIRED_BY_TEST",
                        "Approval required by test policy"),
                sink
        ).create(sampleRequest(), sampleAction(ArchDoxWorkerActionType.RUN_PREFLIGHT_REVIEW));
        var worker = attachedWorker();

        worker.submit(flow);
        tickUntilTerminal(worker, flow);

        assertThat(flow.state()).isEqualTo(FlowState.FINISHED);
        assertThat(executions).hasValue(0);
        assertThat(sink.eventTypes()).contains(ArchDoxWorkerTraceEventType.APPROVAL_REQUIRED);
        assertThat(sink.events().getLast().attributes()).containsEntry("status", "PENDING_APPROVAL");
    }

    @Test
    void run_control_cancels_after_policy_and_before_execution() {
        var executions = new AtomicInteger();
        var executor = new StubExecutor(ArchDoxWorkerActionType.RUN_PREFLIGHT_REVIEW, context -> {
            executions.incrementAndGet();
            return ArchDoxWorkerActionResult.succeeded(Map.of());
        });
        var sink = new RecordingTraceSink();
        var handle = new ArchDoxWorkerExecutionFlowFactory(
                new ArchDoxWorkerActionRegistry(List.of(executor)),
                ArchDoxWorkerPolicyGate.allowAll(),
                (request, action, definition) -> ArchDoxWorkerRunControlDecision.cancel(
                        "ARCHDOX_WORKER_ACTION_CANCELLED_BEFORE_EXECUTION",
                        "Worker action was cancelled before execution."),
                sink
        ).createHandle(sampleRequest(), sampleAction(ArchDoxWorkerActionType.RUN_PREFLIGHT_REVIEW));
        var flow = handle.flow();
        var worker = attachedWorker();

        worker.submit(flow);
        tickUntilTerminal(worker, flow);

        assertThat(flow.state()).isEqualTo(FlowState.FINISHED);
        assertThat(handle.result().status().name()).isEqualTo("CANCELLED");
        assertThat(executions).hasValue(0);
        assertThat(sink.eventTypes()).containsSequence(
                ArchDoxWorkerTraceEventType.REQUEST_RECEIVED,
                ArchDoxWorkerTraceEventType.POLICY_ALLOWED,
                ArchDoxWorkerTraceEventType.ACTION_CANCELLED);
        assertThat(sink.eventTypes()).doesNotContain(ArchDoxWorkerTraceEventType.ACTION_STARTED);
        assertThat(sink.events().getLast().attributes()).containsEntry("status", "CANCELLED");
    }

    @Test
    void failed_executor_result_is_still_recorded_as_failed() {
        var executor = new StubExecutor(
                ArchDoxWorkerActionType.RUN_PREFLIGHT_REVIEW,
                context -> ArchDoxWorkerActionResult.failed("FAILED_BY_TEST", "Failed by test executor"));
        var sink = new RecordingTraceSink();
        var flow = new ArchDoxWorkerExecutionFlowFactory(
                new ArchDoxWorkerActionRegistry(List.of(executor)),
                ArchDoxWorkerPolicyGate.allowAll(),
                sink
        ).create(sampleRequest(), sampleAction(ArchDoxWorkerActionType.RUN_PREFLIGHT_REVIEW));
        var worker = attachedWorker();

        worker.submit(flow);
        tickUntilTerminal(worker, flow);

        assertThat(flow.state()).isEqualTo(FlowState.FINISHED);
        assertThat(sink.eventTypes()).contains(ArchDoxWorkerTraceEventType.ACTION_FAILED);
        assertThat(sink.events().getLast().attributes()).containsEntry("status", "FAILED");
    }

    private static Worker attachedWorker() {
        var worker = Worker.builder("archdox-worker-test").build();
        Engine.builder()
                .clock(new ManualClock())
                .eventBus(InMemoryEventBus.create())
                .worker(worker)
                .build()
                .attach();
        return worker;
    }

    private static void tickUntilTerminal(Worker worker, Flow flow) {
        for (int i = 0; i < 10; i++) {
            worker.tickOnce();
            if (flow.state().isTerminal()) {
                return;
            }
        }
        throw new AssertionError("Flow did not reach terminal state: " + flow.snapshot());
    }

    private static ArchDoxWorkerRequest sampleRequest() {
        return ArchDoxWorkerRequest.fromUi(
                "review document",
                new ArchDoxWorkerRequestContext(10L, 20L, 30L, 40L, 50L, 60L, "ko-KR"));
    }

    private static ArchDoxWorkerAction sampleAction(ArchDoxWorkerActionType actionType) {
        return new ArchDoxWorkerAction(actionType, Map.of(), "test action", 1.0d, ArchDoxWorkerActionOrigin.USER);
    }

    private record StubExecutor(
            ArchDoxWorkerActionType actionType,
            ExecutorBody body
    ) implements ArchDoxWorkerActionExecutor {
        @Override
        public ArchDoxWorkerActionResult execute(ArchDoxWorkerExecutionContext context) {
            return body.execute(context);
        }
    }

    @FunctionalInterface
    private interface ExecutorBody {
        ArchDoxWorkerActionResult execute(ArchDoxWorkerExecutionContext context);
    }

    private static final class RecordingTraceSink implements ArchDoxWorkerTraceSink {
        private final List<ArchDoxWorkerTraceEvent> events = new ArrayList<>();

        @Override
        public void record(ArchDoxWorkerTraceEvent event) {
            events.add(event);
        }

        List<ArchDoxWorkerTraceEvent> events() {
            return events;
        }

        List<ArchDoxWorkerTraceEventType> eventTypes() {
            return events.stream().map(ArchDoxWorkerTraceEvent::eventType).toList();
        }
    }
}
