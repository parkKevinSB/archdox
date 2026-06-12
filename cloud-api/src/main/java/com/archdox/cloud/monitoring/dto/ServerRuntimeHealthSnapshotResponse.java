package com.archdox.cloud.monitoring.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record ServerRuntimeHealthSnapshotResponse(
        OffsetDateTime capturedAt,
        String status,
        Double systemCpuLoadPercent,
        Double processCpuLoadPercent,
        Double systemLoadAverage,
        int availableProcessors,
        Long systemMemoryTotalBytes,
        Long systemMemoryUsedBytes,
        Double systemMemoryUsedPercent,
        long jvmHeapMaxBytes,
        long jvmHeapUsedBytes,
        Double jvmHeapUsedPercent,
        List<String> warnings
) {
}
