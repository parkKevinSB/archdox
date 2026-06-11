package com.archdox.cloud.monitoring.flow;

import com.archdox.cloud.legal.application.LegalSyncProperties;
import com.archdox.cloud.legal.flow.LegalSyncMonitorFlowFactory;
import com.archdox.cloud.platformops.application.PlatformOpsDailyReportProperties;
import com.archdox.cloud.platformops.flow.PlatformOpsDailyReportMonitorFlowFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class OperationalMonitorFlowBootstrap {
    private final LegalSyncProperties legalSyncProperties;
    private final LegalSyncMonitorFlowFactory legalSyncMonitorFlowFactory;
    private final PlatformOpsDailyReportProperties dailyReportProperties;
    private final PlatformOpsDailyReportMonitorFlowFactory dailyReportMonitorFlowFactory;
    private final MonitoringWorker monitoringWorker;

    public OperationalMonitorFlowBootstrap(
            LegalSyncProperties legalSyncProperties,
            LegalSyncMonitorFlowFactory legalSyncMonitorFlowFactory,
            PlatformOpsDailyReportProperties dailyReportProperties,
            PlatformOpsDailyReportMonitorFlowFactory dailyReportMonitorFlowFactory,
            MonitoringWorker monitoringWorker
    ) {
        this.legalSyncProperties = legalSyncProperties;
        this.legalSyncMonitorFlowFactory = legalSyncMonitorFlowFactory;
        this.dailyReportProperties = dailyReportProperties;
        this.dailyReportMonitorFlowFactory = dailyReportMonitorFlowFactory;
        this.monitoringWorker = monitoringWorker;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void submitOperationalMonitorFlows() {
        if (legalSyncProperties.getMonitor().isEnabled()) {
            monitoringWorker.submit(legalSyncMonitorFlowFactory.create());
        }
        if (dailyReportProperties.isEnabled()) {
            monitoringWorker.submit(dailyReportMonitorFlowFactory.create());
        }
    }
}
