package com.archdox.cloud.platformadmin.application;

import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.platformadmin.dto.PlatformHealthDetectionResponse;
import com.archdox.cloud.platformops.application.PlatformOpsDetectionService;
import com.archdox.cloud.platformops.event.PlatformOpsDetectionRequested;
import com.archdox.cloud.platformops.flow.PlatformOpsDetectionFlowFactory;
import com.archdox.cloud.platformops.flow.PlatformOpsWorker;
import org.springframework.stereotype.Service;

@Service
public class PlatformHealthDetectionService {
    private final PlatformAdminService platformAdminService;
    private final PlatformOpsDetectionService detectionService;
    private final PlatformOpsDetectionFlowFactory flowFactory;
    private final PlatformOpsWorker worker;

    public PlatformHealthDetectionService(
            PlatformAdminService platformAdminService,
            PlatformOpsDetectionService detectionService,
            PlatformOpsDetectionFlowFactory flowFactory,
            PlatformOpsWorker worker
    ) {
        this.platformAdminService = platformAdminService;
        this.detectionService = detectionService;
        this.flowFactory = flowFactory;
        this.worker = worker;
    }

    public PlatformHealthDetectionResponse detectAndRecord(UserPrincipal principal) {
        platformAdminService.requirePlatformAdmin(principal);
        var summary = detectionService.requestStuckDetection(principal.userId());
        worker.submit(flowFactory.create(new PlatformOpsDetectionRequested(
                summary.opsRunId(),
                principal.userId())));
        return new PlatformHealthDetectionResponse(
                summary.stuckDocumentJobs(),
                summary.stuckAgentCommands(),
                summary.stuckPhotoPickups(),
                summary.stuckDeliveries(),
                summary.detectedAt(),
                summary.opsRunId(),
                summary.incidentCount(),
                summary.findingCount());
    }
}
