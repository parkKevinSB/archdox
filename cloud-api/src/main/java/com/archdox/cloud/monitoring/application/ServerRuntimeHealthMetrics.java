package com.archdox.cloud.monitoring.application;

import java.time.OffsetDateTime;

public record ServerRuntimeHealthMetrics(
        OffsetDateTime capturedAt,
        Double systemCpuLoadPercent,
        Double processCpuLoadPercent,
        Double systemLoadAverage,
        int availableProcessors,
        Long systemMemoryTotalBytes,
        Long systemMemoryFreeBytes,
        Long systemMemoryAvailableBytes,
        long jvmHeapMaxBytes,
        long jvmHeapUsedBytes
) {
}
