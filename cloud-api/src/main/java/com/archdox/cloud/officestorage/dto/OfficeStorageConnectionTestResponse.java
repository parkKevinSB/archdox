package com.archdox.cloud.officestorage.dto;

import com.archdox.cloud.officestorage.domain.OfficeStorageConnectionTestStatus;
import java.time.OffsetDateTime;

public record OfficeStorageConnectionTestResponse(
        Long profileId,
        OfficeStorageConnectionTestStatus status,
        String message,
        long elapsedMs,
        OffsetDateTime testedAt
) {
}
