package com.archdox.cloud.monitoring.dto;

public record PlatformServerRuntimeHealthResponse(
        ServerRuntimeHealthSnapshotResponse snapshot,
        ServerRuntimeHealthSettingsResponse settings
) {
}
