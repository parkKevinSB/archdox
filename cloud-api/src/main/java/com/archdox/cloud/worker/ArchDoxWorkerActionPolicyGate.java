package com.archdox.cloud.worker;

import com.archdox.worker.application.ArchDoxWorkerPolicyGate;
import com.archdox.worker.domain.ArchDoxWorkerAction;
import com.archdox.worker.domain.ArchDoxWorkerActionType;
import com.archdox.worker.domain.ArchDoxWorkerPolicyDecision;
import com.archdox.worker.domain.ArchDoxWorkerRequest;
import com.archdox.worker.domain.ArchDoxWorkerRequestSource;
import org.springframework.stereotype.Component;

@Component
public class ArchDoxWorkerActionPolicyGate implements ArchDoxWorkerPolicyGate {
    @Override
    public ArchDoxWorkerPolicyDecision evaluate(ArchDoxWorkerRequest request, ArchDoxWorkerAction action) {
        if (!enabledAction(action.actionType())) {
            return ArchDoxWorkerPolicyDecision.deny(
                    "ARCHDOX_WORKER_ACTION_NOT_ENABLED",
                    "This ArchDox worker action is not enabled yet.");
        }
        if (request.source() != ArchDoxWorkerRequestSource.UI) {
            return ArchDoxWorkerPolicyDecision.deny(
                    "ARCHDOX_WORKER_UI_SOURCE_REQUIRED",
                    "Project chat worker actions must originate from the UI.");
        }
        var context = request.context();
        if (context.userId() == null || context.officeId() == null || context.projectId() == null) {
            return ArchDoxWorkerPolicyDecision.deny(
                    "ARCHDOX_WORKER_CONTEXT_REQUIRED",
                    "Project chat worker action requires user, office, and project context.");
        }
        return ArchDoxWorkerPolicyDecision.allow();
    }

    private boolean enabledAction(ArchDoxWorkerActionType actionType) {
        return actionType == ArchDoxWorkerActionType.WORKER_CHAT_ADVANCE
                || actionType == ArchDoxWorkerActionType.CREATE_SITE
                || actionType == ArchDoxWorkerActionType.CREATE_REPORT
                || actionType == ArchDoxWorkerActionType.UPDATE_REPORT_STEP
                || actionType == ArchDoxWorkerActionType.SUBMIT_REPORT
                || actionType == ArchDoxWorkerActionType.RUN_PREFLIGHT_REVIEW
                || actionType == ArchDoxWorkerActionType.REQUEST_DOCUMENT_GENERATION;
    }
}
