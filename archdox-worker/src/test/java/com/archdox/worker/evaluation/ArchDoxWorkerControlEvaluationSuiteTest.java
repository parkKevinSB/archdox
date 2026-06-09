package com.archdox.worker.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.archdox.worker.application.ArchDoxWorkerActionExecutor;
import com.archdox.worker.application.ArchDoxWorkerActionRegistry;
import com.archdox.worker.application.ArchDoxWorkerExecutionContext;
import com.archdox.worker.application.ArchDoxWorkerPolicyGate;
import com.archdox.worker.application.ArchDoxWorkerRunControl;
import com.archdox.worker.application.ArchDoxWorkerRunControlDecision;
import com.archdox.worker.application.ArchDoxWorkerTraceEvent;
import com.archdox.worker.application.ArchDoxWorkerTraceEventType;
import com.archdox.worker.application.ArchDoxWorkerTraceSink;
import com.archdox.worker.domain.ArchDoxWorkerAction;
import com.archdox.worker.domain.ArchDoxWorkerActionExecutionStatus;
import com.archdox.worker.domain.ArchDoxWorkerActionOrigin;
import com.archdox.worker.domain.ArchDoxWorkerActionResult;
import com.archdox.worker.domain.ArchDoxWorkerActionType;
import com.archdox.worker.domain.ArchDoxWorkerPolicyDecision;
import com.archdox.worker.domain.ArchDoxWorkerRequest;
import com.archdox.worker.domain.ArchDoxWorkerRequestContext;
import com.archdox.worker.flow.ArchDoxWorkerExecutionFlowFactory;
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

class ArchDoxWorkerControlEvaluationSuiteTest {
    @Test
    void controlEvaluationSetMeasuresTerminalOutcomes() {
        var cases = List.of(
                new WorkerControlEvaluationCase(
                        "allowed action executes once",
                        List.of(ArchDoxWorkerActionType.RUN_PREFLIGHT_REVIEW),
                        ArchDoxWorkerPolicyGate.allowAll(),
                        ArchDoxWorkerRunControl.allowAll(),
                        ArchDoxWorkerActionType.RUN_PREFLIGHT_REVIEW,
                        ArchDoxWorkerActionResult.succeeded(Map.of("findingCount", 0)),
                        false,
                        ArchDoxWorkerActionExecutionStatus.SUCCEEDED,
                        1,
                        List.of(
                                ArchDoxWorkerTraceEventType.REQUEST_RECEIVED,
                                ArchDoxWorkerTraceEventType.POLICY_ALLOWED,
                                ArchDoxWorkerTraceEventType.ACTION_STARTED,
                                ArchDoxWorkerTraceEventType.ACTION_SUCCEEDED),
                        List.of(ArchDoxWorkerTraceEventType.ACTION_REJECTED, ArchDoxWorkerTraceEventType.ACTION_CANCELLED)),
                new WorkerControlEvaluationCase(
                        "unknown action is rejected before policy",
                        List.of(),
                        ArchDoxWorkerPolicyGate.allowAll(),
                        ArchDoxWorkerRunControl.allowAll(),
                        ArchDoxWorkerActionType.CREATE_REPORT,
                        ArchDoxWorkerActionResult.succeeded(Map.of()),
                        false,
                        ArchDoxWorkerActionExecutionStatus.REJECTED,
                        0,
                        List.of(ArchDoxWorkerTraceEventType.ACTION_UNKNOWN, ArchDoxWorkerTraceEventType.ACTION_REJECTED),
                        List.of(ArchDoxWorkerTraceEventType.POLICY_ALLOWED, ArchDoxWorkerTraceEventType.ACTION_STARTED)),
                new WorkerControlEvaluationCase(
                        "policy denial blocks execution",
                        List.of(ArchDoxWorkerActionType.REQUEST_DOCUMENT_GENERATION),
                        (request, action) -> ArchDoxWorkerPolicyDecision.deny(
                                "EVAL_POLICY_DENIED",
                                "Denied by evaluation policy."),
                        ArchDoxWorkerRunControl.allowAll(),
                        ArchDoxWorkerActionType.REQUEST_DOCUMENT_GENERATION,
                        ArchDoxWorkerActionResult.succeeded(Map.of()),
                        false,
                        ArchDoxWorkerActionExecutionStatus.REJECTED,
                        0,
                        List.of(ArchDoxWorkerTraceEventType.POLICY_DENIED, ArchDoxWorkerTraceEventType.ACTION_REJECTED),
                        List.of(ArchDoxWorkerTraceEventType.ACTION_STARTED, ArchDoxWorkerTraceEventType.ACTION_SUCCEEDED)),
                new WorkerControlEvaluationCase(
                        "approval requirement blocks execution",
                        List.of(ArchDoxWorkerActionType.REQUEST_DOCUMENT_GENERATION),
                        (request, action) -> ArchDoxWorkerPolicyDecision.requireApproval(
                                "EVAL_APPROVAL_REQUIRED",
                                "Approval required by evaluation policy."),
                        ArchDoxWorkerRunControl.allowAll(),
                        ArchDoxWorkerActionType.REQUEST_DOCUMENT_GENERATION,
                        ArchDoxWorkerActionResult.succeeded(Map.of()),
                        false,
                        ArchDoxWorkerActionExecutionStatus.PENDING_APPROVAL,
                        0,
                        List.of(ArchDoxWorkerTraceEventType.APPROVAL_REQUIRED),
                        List.of(ArchDoxWorkerTraceEventType.ACTION_STARTED, ArchDoxWorkerTraceEventType.ACTION_SUCCEEDED)),
                new WorkerControlEvaluationCase(
                        "run control cancellation blocks after policy",
                        List.of(ArchDoxWorkerActionType.RUN_PREFLIGHT_REVIEW),
                        ArchDoxWorkerPolicyGate.allowAll(),
                        (request, action, definition) -> ArchDoxWorkerRunControlDecision.cancel(
                                "EVAL_CANCELLED_BEFORE_EXECUTION",
                                "Cancelled before execution by evaluation run-control."),
                        ArchDoxWorkerActionType.RUN_PREFLIGHT_REVIEW,
                        ArchDoxWorkerActionResult.succeeded(Map.of()),
                        false,
                        ArchDoxWorkerActionExecutionStatus.CANCELLED,
                        0,
                        List.of(ArchDoxWorkerTraceEventType.POLICY_ALLOWED, ArchDoxWorkerTraceEventType.ACTION_CANCELLED),
                        List.of(ArchDoxWorkerTraceEventType.ACTION_STARTED, ArchDoxWorkerTraceEventType.ACTION_SUCCEEDED)),
                new WorkerControlEvaluationCase(
                        "executor failure is isolated as failed result",
                        List.of(ArchDoxWorkerActionType.RUN_PREFLIGHT_REVIEW),
                        ArchDoxWorkerPolicyGate.allowAll(),
                        ArchDoxWorkerRunControl.allowAll(),
                        ArchDoxWorkerActionType.RUN_PREFLIGHT_REVIEW,
                        ArchDoxWorkerActionResult.failed("EVAL_FAILED", "Evaluation executor failed."),
                        false,
                        ArchDoxWorkerActionExecutionStatus.FAILED,
                        1,
                        List.of(ArchDoxWorkerTraceEventType.ACTION_STARTED, ArchDoxWorkerTraceEventType.ACTION_FAILED),
                        List.of(ArchDoxWorkerTraceEventType.ACTION_CANCELLED)),
                new WorkerControlEvaluationCase(
                        "executor rejected result remains rejected trace",
                        List.of(ArchDoxWorkerActionType.UPDATE_REPORT_STEP),
                        ArchDoxWorkerPolicyGate.allowAll(),
                        ArchDoxWorkerRunControl.allowAll(),
                        ArchDoxWorkerActionType.UPDATE_REPORT_STEP,
                        ArchDoxWorkerActionResult.rejected("EVAL_EXECUTOR_REJECTED", "Executor rejected the action."),
                        false,
                        ArchDoxWorkerActionExecutionStatus.REJECTED,
                        1,
                        List.of(ArchDoxWorkerTraceEventType.ACTION_STARTED, ArchDoxWorkerTraceEventType.ACTION_REJECTED),
                        List.of(ArchDoxWorkerTraceEventType.ACTION_SUCCEEDED, ArchDoxWorkerTraceEventType.ACTION_CANCELLED)),
                new WorkerControlEvaluationCase(
                        "executor pending approval result remains approval trace",
                        List.of(ArchDoxWorkerActionType.REQUEST_DOCUMENT_GENERATION),
                        ArchDoxWorkerPolicyGate.allowAll(),
                        ArchDoxWorkerRunControl.allowAll(),
                        ArchDoxWorkerActionType.REQUEST_DOCUMENT_GENERATION,
                        ArchDoxWorkerActionResult.pendingApproval(
                                "EVAL_EXECUTOR_APPROVAL_REQUIRED",
                                "Executor requires approval."),
                        false,
                        ArchDoxWorkerActionExecutionStatus.PENDING_APPROVAL,
                        1,
                        List.of(ArchDoxWorkerTraceEventType.ACTION_STARTED, ArchDoxWorkerTraceEventType.APPROVAL_REQUIRED),
                        List.of(ArchDoxWorkerTraceEventType.ACTION_SUCCEEDED, ArchDoxWorkerTraceEventType.ACTION_CANCELLED)),
                new WorkerControlEvaluationCase(
                        "executor cancelled result is separated from rejection",
                        List.of(ArchDoxWorkerActionType.WORKER_CHAT_ADVANCE),
                        ArchDoxWorkerPolicyGate.allowAll(),
                        ArchDoxWorkerRunControl.allowAll(),
                        ArchDoxWorkerActionType.WORKER_CHAT_ADVANCE,
                        ArchDoxWorkerActionResult.cancelled(
                                "EVAL_EXECUTOR_CANCELLED",
                                "Executor cancelled the action."),
                        false,
                        ArchDoxWorkerActionExecutionStatus.CANCELLED,
                        1,
                        List.of(ArchDoxWorkerTraceEventType.ACTION_STARTED, ArchDoxWorkerTraceEventType.ACTION_CANCELLED),
                        List.of(ArchDoxWorkerTraceEventType.ACTION_REJECTED, ArchDoxWorkerTraceEventType.ACTION_SUCCEEDED)),
                new WorkerControlEvaluationCase(
                        "executor exception is caught as failed result",
                        List.of(ArchDoxWorkerActionType.ENRICH_LEGAL_CHANGE_DIGEST),
                        ArchDoxWorkerPolicyGate.allowAll(),
                        ArchDoxWorkerRunControl.allowAll(),
                        ArchDoxWorkerActionType.ENRICH_LEGAL_CHANGE_DIGEST,
                        ArchDoxWorkerActionResult.succeeded(Map.of()),
                        true,
                        ArchDoxWorkerActionExecutionStatus.FAILED,
                        1,
                        List.of(ArchDoxWorkerTraceEventType.ACTION_STARTED, ArchDoxWorkerTraceEventType.ACTION_FAILED),
                        List.of(ArchDoxWorkerTraceEventType.ACTION_CANCELLED)));

        for (var testCase : cases) {
            var executions = new AtomicInteger();
            var sink = new RecordingTraceSink();
            var handle = new ArchDoxWorkerExecutionFlowFactory(
                    new ArchDoxWorkerActionRegistry(executors(testCase, executions)),
                    testCase.policyGate(),
                    testCase.runControl(),
                    sink)
                    .createHandle(sampleRequest(), sampleAction(testCase.actionType()));
            var worker = attachedWorker();

            worker.submit(handle.flow());
            tickUntilTerminal(worker, handle.flow());

            assertThat(handle.flow().state()).as(testCase.name()).isEqualTo(FlowState.FINISHED);
            assertThat(handle.result().status()).as(testCase.name()).isEqualTo(testCase.expectedStatus());
            assertThat(executions).as(testCase.name()).hasValue(testCase.expectedExecutions());
            assertThat(sink.eventTypes()).as(testCase.name()).containsAll(testCase.requiredEvents());
            assertThat(sink.eventTypes()).as(testCase.name()).doesNotContainAnyElementsOf(testCase.forbiddenEvents());
            assertThat(sink.events().getLast().attributes()).as(testCase.name())
                    .containsEntry("status", testCase.expectedStatus().name());
        }
    }

    private static List<ArchDoxWorkerActionExecutor> executors(
            WorkerControlEvaluationCase testCase,
            AtomicInteger executions
    ) {
        return testCase.registeredActionTypes().stream()
                .map(actionType -> new StubExecutor(actionType, context -> {
                    executions.incrementAndGet();
                    if (testCase.executorThrows()) {
                        throw new IllegalStateException("Evaluation executor exception");
                    }
                    return testCase.executorResult();
                }))
                .map(ArchDoxWorkerActionExecutor.class::cast)
                .toList();
    }

    private static Worker attachedWorker() {
        var worker = Worker.builder("archdox-worker-control-evaluation").build();
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
                "evaluation request",
                new ArchDoxWorkerRequestContext(10L, 20L, 30L, 40L, 50L, 60L, "ko-KR"));
    }

    private static ArchDoxWorkerAction sampleAction(ArchDoxWorkerActionType actionType) {
        return new ArchDoxWorkerAction(
                actionType,
                Map.of("evaluation", true),
                "evaluation action",
                0.9d,
                ArchDoxWorkerActionOrigin.SYSTEM);
    }

    private record WorkerControlEvaluationCase(
            String name,
            List<ArchDoxWorkerActionType> registeredActionTypes,
            ArchDoxWorkerPolicyGate policyGate,
            ArchDoxWorkerRunControl runControl,
            ArchDoxWorkerActionType actionType,
            ArchDoxWorkerActionResult executorResult,
            boolean executorThrows,
            ArchDoxWorkerActionExecutionStatus expectedStatus,
            int expectedExecutions,
            List<ArchDoxWorkerTraceEventType> requiredEvents,
            List<ArchDoxWorkerTraceEventType> forbiddenEvents
    ) {
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
