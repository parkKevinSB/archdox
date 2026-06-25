package com.archdox.cloud.officestorage.application;

import com.archdox.cloud.officestorage.domain.OfficeStorageConnectionTestStatus;

public record OfficeStorageConnectionTestResult(
        OfficeStorageConnectionTestStatus status,
        String message,
        long elapsedMs
) {
}
