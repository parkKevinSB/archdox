package com.archdox.cloud.monitoring.dto;

public record UpdateServerRuntimeHealthSettingsRequest(
        Boolean enabled,
        Long checkIntervalMs,
        Double cpuWarnPercent,
        Double systemMemoryWarnPercent,
        Double jvmHeapWarnPercent,
        Long eventCooldownMs
) {
}
