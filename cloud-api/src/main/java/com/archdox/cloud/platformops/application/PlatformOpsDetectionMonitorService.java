package com.archdox.cloud.platformops.application;

import com.archdox.cloud.platformops.domain.PlatformOpsRunStatus;
import com.archdox.cloud.platformops.domain.PlatformOpsRunTriggerType;
import com.archdox.cloud.platformops.infra.PlatformOpsRunRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformOpsDetectionMonitorService {
    private static final List<PlatformOpsRunTriggerType> DETECTION_TRIGGER_TYPES = List.of(
            PlatformOpsRunTriggerType.AUTO_DETECT_STUCK,
            PlatformOpsRunTriggerType.MANUAL_DETECT_STUCK);

    private final PlatformOpsDetectionProperties properties;
    private final PlatformOpsRunRepository runRepository;
    private final PlatformOpsDetectionService detectionService;

    public PlatformOpsDetectionMonitorService(
            PlatformOpsDetectionProperties properties,
            PlatformOpsRunRepository runRepository,
            PlatformOpsDetectionService detectionService
    ) {
        this.properties = properties;
        this.runRepository = runRepository;
        this.detectionService = detectionService;
    }

    @Transactional
    public PlatformOpsDetectionMonitorDecision checkAndRequestIfDue(OffsetDateTime now) {
        if (!properties.isEnabled()) {
            return PlatformOpsDetectionMonitorDecision.skipped("MONITOR_DISABLED");
        }
        if (runRepository.existsByTriggerTypeInAndStatus(DETECTION_TRIGGER_TYPES, PlatformOpsRunStatus.RUNNING)) {
            return PlatformOpsDetectionMonitorDecision.skipped("DETECTION_ALREADY_RUNNING");
        }
        var run = detectionService.requestAutoStuckDetection(now);
        return PlatformOpsDetectionMonitorDecision.requested(run.id());
    }
}
