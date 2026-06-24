package com.archdox.cloud.monitoring.flow;

import com.archdox.cloud.aiharness.application.AiObservabilityRetentionProperties;
import com.archdox.cloud.aiharness.flow.AiObservabilityRetentionMonitorFlowFactory;
import com.archdox.cloud.legal.application.LegalSyncProperties;
import com.archdox.cloud.legal.flow.LegalSyncMonitorFlowFactory;
import com.archdox.cloud.monitoring.application.ServerRuntimeHealthProperties;
import com.archdox.cloud.platformops.application.PlatformOpsDetectionProperties;
import com.archdox.cloud.platformops.application.PlatformOpsDailyReportProperties;
import com.archdox.cloud.platformops.application.PlatformOpsRetentionProperties;
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
    private final PlatformOpsDetectionProperties platformOpsDetectionProperties;
    private final PlatformOpsDetectionMonitorFlowFactory platformOpsDetectionMonitorFlowFactory;
    private final PlatformOpsDailyReportProperties dailyReportProperties;
    private final PlatformOpsDailyReportMonitorFlowFactory dailyReportMonitorFlowFactory;
    private final PlatformOpsRetentionProperties platformOpsRetentionProperties;
    private final PlatformOpsRetentionMonitorFlowFactory platformOpsRetentionMonitorFlowFactory;
    private final MonitoringWorker monitoringWorker;

    public OperationalMonitorFlowBootstrap(
            AiObservabilityRetentionProperties aiObservabilityRetentionProperties,
            AiObservabilityRetentionMonitorFlowFactory aiObservabilityRetentionMonitorFlowFactory,
            LegalSyncProperties legalSyncProperties,
            LegalSyncMonitorFlowFactory legalSyncMonitorFlowFactory,
            ServerRuntimeHealthProperties serverRuntimeHealthProperties,
            ServerRuntimeHealthMonitorFlowFactory serverRuntimeHealthMonitorFlowFactory,
            PlatformOpsDetectionProperties platformOpsDetectionProperties,
            PlatformOpsDetectionMonitorFlowFactory platformOpsDetectionMonitorFlowFactory,
            PlatformOpsDailyReportProperties dailyReportProperties,
            PlatformOpsDailyReportMonitorFlowFactory dailyReportMonitorFlowFactory,
            PlatformOpsRetentionProperties platformOpsRetentionProperties,
            PlatformOpsRetentionMonitorFlowFactory platformOpsRetentionMonitorFlowFactory,
            MonitoringWorker monitoringWorker
    ) {
        this.aiObservabilityRetentionProperties = aiObservabilityRetentionProperties;
        this.aiObservabilityRetentionMonitorFlowFactory = aiObservabilityRetentionMonitorFlowFactory;
        this.legalSyncProperties = legalSyncProperties;
        this.legalSyncMonitorFlowFactory = legalSyncMonitorFlowFactory;
        this.serverRuntimeHealthProperties = serverRuntimeHealthProperties;
        this.serverRuntimeHealthMonitorFlowFactory = serverRuntimeHealthMonitorFlowFactory;
        this.platformOpsDetectionProperties = platformOpsDetectionProperties;
        this.platformOpsDetectionMonitorFlowFactory = platformOpsDetectionMonitorFlowFactory;
        this.dailyReportProperties = dailyReportProperties;
        this.dailyReportMonitorFlowFactory = dailyReportMonitorFlowFactory;
        this.platformOpsRetentionProperties = platformOpsRetentionProperties;
        this.platformOpsRetentionMonitorFlowFactory = platformOpsRetentionMonitorFlowFactory;
        this.monitoringWorker = monitoringWorker;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void submitOperationalMonitorFlows() {
        if (aiObservabilityRetentionProperties.isEnabled()) {
            monitoringWorker.submit(aiObservabilityRetentionMonitorFlowFactory.create());
        }
        if (legalSyncProperties.getMonitor().isEnabled()) {
            monitoringWorker.submit(legalSyncMonitorFlowFactory.create());
        }
        if (serverRuntimeHealthProperties.isEnabled()) {
            monitoringWorker.submit(serverRuntimeHealthMonitorFlowFactory.create());
        }
        if (platformOpsDetectionProperties.isEnabled()) {
            monitoringWorker.submit(platformOpsDetectionMonitorFlowFactory.create());
        }
        if (dailyReportProperties.isEnabled()) {
            monitoringWorker.submit(dailyReportMonitorFlowFactory.create());
        }
        if (platformOpsRetentionProperties.isEnabled()) {
            monitoringWorker.submit(platformOpsRetentionMonitorFlowFactory.create());
        }
    }
}
