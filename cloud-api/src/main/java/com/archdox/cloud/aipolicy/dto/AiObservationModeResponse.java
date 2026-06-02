package com.archdox.cloud.aipolicy.dto;

import java.time.OffsetDateTime;

public record AiObservationModeResponse(
        boolean enabled,
        int maxEntries,
        int ttlMinutes,
        int maxPromptChars,
        int maxResponseChars,
        int currentEntryCount,
        OffsetDateTime updatedAt
) {
}
