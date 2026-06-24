package com.archdox.cloud.monitoring.flow;

import com.archdox.cloud.aiharness.application.AiObservabilityRetentionProperties;
import com.archdox.cloud.aiharness.flow.AiObservabilityRetentionMonitorFlowFactory;
import com.archdox.cloud.legal.application.LegalSyncProperties;
import com.archdox.cloud.legal.flow.LegalSyncMonitorFlowFactory;
import com.archdox.cloud.monitoring.application.ServerRuntimeHealthProperties;
import com.archdox.cloud.platformops.application.PlatformOpsRunRecoveryService;
import com.archdox.cloud.platformops.flow.PlatformOpsDetectionMonitorFlowFactory;
import com.archdox.cloud.platformops.flow.PlatformOpsDailyReportMonitorFlowFactory;
import com.archdox.cloud.platformops.flow.PlatformOpsRetentionMonitorFlowFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class OperationalMonitorFlowBootstrap {
    private final AiObservabilityRetentionProperties aiObservabilityRetentionProperties;
    private final AiObservabilityRetentionMonitorFlowFactory aiObservabilityRetentionMonitorFlowFactory;
    private final LegalSyncProperties legalSyncProperties;
    private final LegalSyncMonitorFlowFactory legalSyncMonitorFlowFactory;
    private final ServerRuntimeHealthProperties serverRuntimeHealthProperties;
    private final ServerRuntimeHealthMonitorFlowFactory serverRuntimeHealthMonitorFlowFactory;
    private final PlatformOpsDetectionMonitorFlowFactory platformOpsDetectionMonitorFlowFactory;
    private final PlatformOpsDailyReportMonitorFlowFactory dailyReportMonitorFlowFactory;
    private final PlatformOpsRetentionMonitorFlowFactory platformOpsRetentionMonitorFlowFactory;
    private final PlatformOpsRunRecoveryService platformOpsRunRecoveryService;
    private final MonitoringWorker monitoringWorker;

    public OperationalMonitorFlowBootstrap(
            AiObservabilityRetentionProperties aiObservabilityRetentionProperties,
            AiObservabilityRetentionMonitorFlowFactory aiObservabilityRetentionMonitorFlowFactory,
            LegalSyncProperties legalSyncProperties,
            LegalSyncMonitorFlowFactory legalSyncMonitorFlowFactory,
            ServerRuntimeHealthProperties serverRuntimeHealthProperties,
            ServerRuntimeHealthMonitorFlowFactory serverRuntimeHealthMonitorFlowFactory,
            PlatformOpsDetectionMonitorFlowFactory platformOpsDetectionMonitorFlowFactory,
            PlatformOpsDailyReportMonitorFlowFactory dailyReportMonitorFlowFactory,
            PlatformOpsRetentionMonitorFlowFactory platformOpsRetentionMonitorFlowFactory,
            PlatformOpsRunRecoveryService platformOpsRunRecoveryService,
            MonitoringWorker monitoringWorker
    ) {
        this.aiObservabilityRetentionProperties = aiObservabilityRetentionProperties;
        this.aiObservabilityRetentionMonitorFlowFactory = aiObservabilityRetentionMonitorFlowFactory;
        this.legalSyncProperties = legalSyncProperties;
        this.legalSyncMonitorFlowFactory = legalSyncMonitorFlowFactory;
        this.serverRuntimeHealthProperties = serverRuntimeHealthProperties;
        this.serverRuntimeHealthMonitorFlowFactory = serverRuntimeHealthMonitorFlowFactory;
        this.platformOpsDetectionMonitorFlowFactory = platformOpsDetectionMonitorFlowFactory;
        this.dailyReportMonitorFlowFactory = dailyReportMonitorFlowFactory;
        this.platformOpsRetentionMonitorFlowFactory = platformOpsRetentionMonitorFlowFactory;
        this.platformOpsRunRecoveryService = platformOpsRunRecoveryService;
        this.monitoringWorker = monitoringWorker;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void submitOperationalMonitorFlows() {
        platformOpsRunRecoveryService.failInterruptedRuns(java.time.OffsetDateTime.now());
        if (aiObservabilityRetentionProperties.isEnabled()) {
            monitoringWorker.submit(aiObservabilityRetentionMonitorFlowFactory.create());
        }
        if (legalSyncProperties.getMonitor().isEnabled()) {
            monitoringWorker.submit(legalSyncMonitorFlowFactory.create());
        }
        if (serverRuntimeHealthProperties.isEnabled()) {
            monitoringWorker.submit(serverRuntimeHealthMonitorFlowFactory.create());
        }
        monitoringWorker.submit(platformOpsDetectionMonitorFlowFactory.create());
        monitoringWorker.submit(dailyReportMonitorFlowFactory.create());
        monitoringWorker.submit(platformOpsRetentionMonitorFlowFactory.create());
    }
}
