package com.archdox.workerai;

import java.util.List;
import java.util.Objects;

public record ConversationPlannerInput(
        String officeId,
        String projectId,
        String siteId,
        String reportId,
        String stage,
        String locale,
        String userMessage,
        List<ConversationPlannerActionOption> availableActions,
        List<ConversationPlannerEntityOption> sites,
        List<ConversationPlannerEntityOption> reports,
        List<ConversationPlannerWorkflowStepOption> workflowSteps
) {
    public ConversationPlannerInput {
        officeId = clean(officeId);
        projectId = clean(projectId);
        siteId = clean(siteId);
        reportId = clean(reportId);
        stage = clean(stage);
        locale = locale == null || locale.isBlank() ? "ko-KR" : locale.trim();
        userMessage = Objects.toString(userMessage, "").trim();
        availableActions = availableActions == null ? List.of() : List.copyOf(availableActions);
        sites = sites == null ? List.of() : List.copyOf(sites);
        reports = reports == null ? List.of() : List.copyOf(reports);
        workflowSteps = workflowSteps == null ? List.of() : List.copyOf(workflowSteps);
    }

    private static String clean(String value) {
        return Objects.toString(value, "").trim();
    }
}
