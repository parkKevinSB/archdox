package com.archdox.cloud.worker.governance.dto;

import com.archdox.cloud.operation.dto.OperationEventResponse;
import java.time.OffsetDateTime;
import java.util.List;

public record WorkerGovernanceSummaryResponse(
        OffsetDateTime from,
        OffsetDateTime to,
        Long officeId,
        int days,
        long totalTraceEvents,
        long requestReceived,
        long policyAllowed,
        long policyDenied,
        long approvalRequired,
        long actionSucceeded,
        long actionFailed,
        long actionRejected,
        long actionUnknown,
        double catchRate,
        double approvalRequiredRate,
        double failureRate,
        String dataPolicy,
        List<WorkerActionDefinitionResponse> actionDefinitions,
        List<WorkerGovernanceGroupResponse> eventTypes,
        List<WorkerGovernanceGroupResponse> actionEvents,
        List<WorkerGovernanceGroupResponse> reasons,
        List<OperationEventResponse> recentEvents
) {
}
