package com.archdox.cloud.workerchat.dto;

import com.archdox.cloud.workerchat.domain.ArchDoxWorkerChatMessageRole;
import com.archdox.cloud.workerchat.domain.ArchDoxWorkerChatMessageStatus;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record ArchDoxWorkerChatMessageResponse(
        Long id,
        Long sessionId,
        Long userId,
        ArchDoxWorkerChatMessageRole role,
        ArchDoxWorkerChatMessageStatus status,
        String content,
        UUID workerRequestId,
        String workerActionType,
        Map<String, Object> metadata,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
