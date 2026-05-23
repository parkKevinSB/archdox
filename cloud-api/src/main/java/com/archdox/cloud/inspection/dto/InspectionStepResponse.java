package com.archdox.cloud.inspection.dto;

import com.archdox.cloud.inspection.domain.PayloadStorageMode;
import java.time.OffsetDateTime;
import java.util.Map;

public record InspectionStepResponse(
        String stepCode,
        PayloadStorageMode payloadStorageMode,
        Map<String, Object> payload,
        int clientRevision,
        OffsetDateTime savedAt
) {
}
