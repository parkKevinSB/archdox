package com.archdox.cloud.platformops.application;

import com.archdox.cloud.platformops.domain.PlatformOpsIncident;
import java.util.List;
import java.util.Optional;

public interface PlatformOpsDetector {
    String category();

    List<PlatformOpsDetectionFinding> detect(PlatformOpsDetectionContext context);

    default boolean supportsAutoResolve() {
        return false;
    }

    default Optional<PlatformOpsIncidentResolution> resolve(
            PlatformOpsIncident incident,
            PlatformOpsDetectionContext context
    ) {
        return Optional.empty();
    }
}
