package com.archdox.worker.flow;

import com.archdox.worker.application.ArchDoxWorkerActionExecutor;
import com.archdox.worker.domain.ArchDoxWorkerAction;
import com.archdox.worker.domain.ArchDoxWorkerActionResult;
import com.archdox.worker.domain.ArchDoxWorkerPolicyDecision;
import com.archdox.worker.domain.ArchDoxWorkerRequest;
import java.util.Objects;

public final class ArchDoxWorkerExecutionSession {
    private final ArchDoxWorkerRequest request;
    private final ArchDoxWorkerAction action;
    private ArchDoxWorkerActionExecutor executor;
    private ArchDoxWorkerPolicyDecision policyDecision;
    private ArchDoxWorkerActionResult result;

    public ArchDoxWorkerExecutionSession(ArchDoxWorkerRequest request, ArchDoxWorkerAction action) {
        this.request = Objects.requireNonNull(request, "request must not be null");
        this.action = Objects.requireNonNull(action, "action must not be null");
    }

    public ArchDoxWorkerRequest request() {
        return request;
    }

    public ArchDoxWorkerAction action() {
        return action;
    }

    public ArchDoxWorkerActionExecutor executor() {
        return executor;
    }

    public void executor(ArchDoxWorkerActionExecutor executor) {
        this.executor = executor;
    }

    public ArchDoxWorkerPolicyDecision policyDecision() {
        return policyDecision;
    }

    public void policyDecision(ArchDoxWorkerPolicyDecision policyDecision) {
        this.policyDecision = policyDecision;
    }

    public ArchDoxWorkerActionResult result() {
        return result;
    }

    public void result(ArchDoxWorkerActionResult result) {
        this.result = result;
    }

    public boolean hasTerminalResult() {
        return result != null;
    }
}
