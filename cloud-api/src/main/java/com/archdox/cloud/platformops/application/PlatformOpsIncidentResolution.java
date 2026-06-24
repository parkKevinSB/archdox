package com.archdox.cloud.platformops.application;

import java.util.Map;

public record PlatformOpsIncidentResolution(
        String code,
        String message,
        Map<String, Object> evidenceJson
) {
    public PlatformOpsIncidentResolution {
        evidenceJson = evidenceJson == null ? Map.of() : Map.copyOf(evidenceJson);
    }
}
