package com.archdox.cloud.platformops.dto;

import com.archdox.cloud.platformops.domain.PlatformOpsRun;
import com.archdox.cloud.platformops.domain.PlatformOpsRunStatus;
import com.archdox.cloud.platformops.domain.PlatformOpsRunTriggerType;
import java.time.OffsetDateTime;
import java.util.Map;

public record PlatformOpsRunResponse(
        Long id,
        PlatformOpsRunTriggerType triggerType,
        PlatformOpsRunStatus status,
        Long startedByUserId,
        Long incidentId,
        Map<String, Object> inputSnapshotJson,
        String aiHarnessRunId,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        String failureCode
) {
    public static PlatformOpsRunResponse from(PlatformOpsRun run) {
        return new PlatformOpsRunResponse(
                run.id(),
                run.triggerType(),
                run.status(),
                run.startedByUserId(),
                run.incidentId(),
                run.inputSnapshotJson(),
                run.aiHarnessRunId(),
                run.startedAt(),
                run.completedAt(),
                run.failureCode());
    }
}
