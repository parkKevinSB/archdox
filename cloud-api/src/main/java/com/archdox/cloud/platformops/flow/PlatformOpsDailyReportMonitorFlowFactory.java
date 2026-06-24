package com.archdox.cloud.platformops.flow;

import com.archdox.cloud.platformops.application.PlatformOpsDailyReportMonitorService;
import com.archdox.cloud.platformops.flow.step.PlatformOpsDailyReportMonitorStep;
import io.github.parkkevinsb.flower.core.flow.Flow;
import org.springframework.stereotype.Component;

@Component
public class PlatformOpsDailyReportMonitorFlowFactory {
    public static final String FLOW_TYPE = "platform-ops-daily-report-monitor";

    private final PlatformOpsDailyReportMonitorService monitorService;
    private final PlatformOpsDailyReportFlowFactory dailyReportFlowFactory;
    private final PlatformOpsWorker platformOpsWorker;

    public PlatformOpsDailyReportMonitorFlowFactory(
            PlatformOpsDailyReportMonitorService monitorService,
            PlatformOpsDailyReportFlowFactory dailyReportFlowFactory,
            PlatformOpsWorker platformOpsWorker
    ) {
        this.monitorService = monitorService;
        this.dailyReportFlowFactory = dailyReportFlowFactory;
        this.platformOpsWorker = platformOpsWorker;
    }

    public Flow create() {
        return Flow.builder(FLOW_TYPE, "global")
                .step("monitor-platform-ops-daily-report", new PlatformOpsDailyReportMonitorStep(
                        monitorService,
                        dailyReportFlowFactory,
                        platformOpsWorker))
                .build();
    }
}
