package com.archdox.cloud.system.dto;

public record ArchDoxSystemVersionResponse(
        String module,
        String version,
        String gitCommit,
        String gitBranch,
        String buildTime
) {
}
