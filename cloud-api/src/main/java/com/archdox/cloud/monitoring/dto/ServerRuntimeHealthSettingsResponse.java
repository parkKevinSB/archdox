package com.archdox.cloud.monitoring.dto;

import com.archdox.cloud.monitoring.application.ServerRuntimeHealthProperties;
import com.archdox.cloud.monitoring.domain.ServerRuntimeHealthSettings;
import java.time.OffsetDateTime;

public record ServerRuntimeHealthSettingsResponse(
        boolean enabled,
        long checkIntervalMs,
        double cpuWarnPercent,
        double systemMemoryWarnPercent,
        double jvmHeapWarnPercent,
        long eventCooldownMs,
        Long updatedByUserId,
        OffsetDateTime updatedAt
) {
    public static ServerRuntimeHealthSettingsResponse from(ServerRuntimeHealthSettings settings) {
        return new ServerRuntimeHealthSettingsResponse(
                settings.enabled(),
                settings.checkIntervalMs(),
                settings.cpuWarnPercent(),
                settings.systemMemoryWarnPercent(),
                settings.jvmHeapWarnPercent(),
                settings.eventCooldownMs(),
                settings.updatedByUserId(),
                settings.updatedAt());
    }

    public static ServerRuntimeHealthSettingsResponse fromDefaults(ServerRuntimeHealthProperties properties) {
        return new ServerRuntimeHealthSettingsResponse(
                properties.isEnabled(),
                properties.getCheckIntervalMs(),
                properties.getCpuWarnPercent(),
                properties.getSystemMemoryWarnPercent(),
                properties.getJvmHeapWarnPercent(),
                properties.getEventCooldownMs(),
                null,
                null);
    }
}
