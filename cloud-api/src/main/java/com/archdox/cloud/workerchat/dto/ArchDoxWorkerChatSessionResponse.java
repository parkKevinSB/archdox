package com.archdox.cloud.workerchat.dto;

import com.archdox.cloud.workerchat.domain.ArchDoxWorkerChatSessionStatus;
import com.archdox.cloud.workerchat.domain.ArchDoxWorkerChatStage;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record ArchDoxWorkerChatSessionResponse(
        Long id,
        Long officeId,
        Long projectId,
        Long siteId,
        Long reportId,
        Long userId,
        ArchDoxWorkerChatSessionStatus status,
        ArchDoxWorkerChatStage stage,
        String title,
        OffsetDateTime lastMessageAt,
        OffsetDateTime completedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        Map<String, Object> workflowState,
        List<ArchDoxWorkerChatMessageResponse> messages
) {
}
