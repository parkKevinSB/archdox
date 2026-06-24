package com.archdox.cloud.platformops.application;

import com.archdox.cloud.operation.infra.OperationEventRepository;
import com.archdox.cloud.platformops.domain.PlatformOpsRunStatus;
import com.archdox.cloud.platformops.infra.PlatformOpsDailyReportRepository;
import com.archdox.cloud.platformops.infra.PlatformOpsFindingRepository;
import com.archdox.cloud.platformops.infra.PlatformOpsIncidentRepository;
import com.archdox.cloud.platformops.infra.PlatformOpsRunRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformOpsRetentionService {
    private final PlatformOpsAutomationSettingsService automationSettingsService;
    private final PlatformOpsDailyReportRepository dailyReportRepository;
    private final PlatformOpsFindingRepository findingRepository;
    private final PlatformOpsIncidentRepository incidentRepository;
    private final PlatformOpsRunRepository runRepository;
    private final OperationEventRepository operationEventRepository;

    public PlatformOpsRetentionService(
            PlatformOpsAutomationSettingsService automationSettingsService,
            PlatformOpsDailyReportRepository dailyReportRepository,
            PlatformOpsFindingRepository findingRepository,
            PlatformOpsIncidentRepository incidentRepository,
            PlatformOpsRunRepository runRepository,
            OperationEventRepository operationEventRepository
    ) {
        this.automationSettingsService = automationSettingsService;
        this.dailyReportRepository = dailyReportRepository;
        this.findingRepository = findingRepository;
        this.incidentRepository = incidentRepository;
        this.runRepository = runRepository;
        this.operationEventRepository = operationEventRepository;
    }

    public boolean enabled() {
        return automationSettingsService.settings().retentionEnabled();
    }

    public long checkIntervalMs() {
        return automationSettingsService.settings().retentionCheckIntervalMs();
    }

    @Transactional
    public PlatformOpsRetentionResult purgeExpired(OffsetDateTime now) {
        var settings = automationSettingsService.settings();
        var retentionDays = settings.retentionDays();
        var cutoff = now.minusDays(retentionDays);
        if (!settings.retentionEnabled()) {
            return PlatformOpsRetentionResult.disabled(retentionDays, cutoff);
        }

        var deletedDailyReports = dailyReportRepository.deleteCreatedBefore(cutoff);
        var deletedFindings = findingRepository.deleteCreatedBefore(cutoff);
        var deletedIncidents = incidentRepository.deleteStaleUnreferencedBefore(cutoff);
        var deletedRuns = runRepository.deleteUnreferencedTerminalRunsBefore(
                cutoff,
                List.of(PlatformOpsRunStatus.COMPLETED, PlatformOpsRunStatus.FAILED));
        var deletedLogProjectionEvents = operationEventRepository.deleteLogProjectionEventsCreatedBefore(cutoff);

        return new PlatformOpsRetentionResult(
                true,
                retentionDays,
                cutoff,
                deletedDailyReports,
                deletedFindings,
                deletedIncidents,
                deletedRuns,
                deletedLogProjectionEvents);
    }
}
