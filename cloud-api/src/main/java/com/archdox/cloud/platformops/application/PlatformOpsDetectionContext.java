package com.archdox.cloud.platformops.application;

import java.time.OffsetDateTime;
import org.springframework.data.domain.Pageable;

public record PlatformOpsDetectionContext(
        OffsetDateTime now,
        Pageable page
) {
}
