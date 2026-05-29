package com.archdox.cloud.platformops.application;

import java.util.List;

public interface PlatformOpsDetector {
    String category();

    List<PlatformOpsDetectionFinding> detect(PlatformOpsDetectionContext context);
}
