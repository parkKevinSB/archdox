package com.archdox.cloud.monitoring.application;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Component;

@Component
public class DefaultServerRuntimeHealthProbe implements ServerRuntimeHealthProbe {
    private final OperatingSystemMXBean operatingSystemBean = ManagementFactory.getOperatingSystemMXBean();
    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();

    @Override
    public ServerRuntimeHealthMetrics sample(OffsetDateTime capturedAt) {
        Double systemCpuLoadPercent = null;
        Double processCpuLoadPercent = null;
        Long systemMemoryTotalBytes = null;
        Long systemMemoryFreeBytes = null;

        if (operatingSystemBean instanceof com.sun.management.OperatingSystemMXBean extendedBean) {
            systemCpuLoadPercent = percent(extendedBean.getCpuLoad());
            processCpuLoadPercent = percent(extendedBean.getProcessCpuLoad());
            systemMemoryTotalBytes = positiveOrNull(extendedBean.getTotalMemorySize());
            systemMemoryFreeBytes = positiveOrNull(extendedBean.getFreeMemorySize());
        }

        var heap = memoryBean.getHeapMemoryUsage();
        return new ServerRuntimeHealthMetrics(
                capturedAt,
                systemCpuLoadPercent,
                processCpuLoadPercent,
                positiveOrNull(operatingSystemBean.getSystemLoadAverage()),
                operatingSystemBean.getAvailableProcessors(),
                systemMemoryTotalBytes,
                systemMemoryFreeBytes,
                Math.max(0L, heap.getMax()),
                Math.max(0L, heap.getUsed()));
    }

    private Double percent(double value) {
        if (Double.isNaN(value) || value < 0.0d) {
            return null;
        }
        return Math.min(100.0d, value * 100.0d);
    }

    private Double positiveOrNull(double value) {
        if (Double.isNaN(value) || value < 0.0d) {
            return null;
        }
        return value;
    }

    private Long positiveOrNull(long value) {
        return value <= 0L ? null : value;
    }
}
