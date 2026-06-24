package com.archdox.cloud.platformops.application;

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
    private final PlatformOpsRetentionProperties properties;
    private final PlatformOpsDailyReportRepository dailyReportRepository;
    private final PlatformOpsFindingRepository findingRepository;
    private final PlatformOpsIncidentRepository incidentRepository;
    private final PlatformOpsRunRepository runRepository;

    public PlatformOpsRetentionService(
            PlatformOpsRetentionProperties properties,
            PlatformOpsDailyReportRepository dailyReportRepository,
            PlatformOpsFindingRepository findingRepository,
            PlatformOpsIncidentRepository incidentRepository,
            PlatformOpsRunRepository runRepository
    ) {
        this.properties = properties;
        this.dailyReportRepository = dailyReportRepository;
        this.findingRepository = findingRepository;
        this.incidentRepository = incidentRepository;
        this.runRepository = runRepository;
    }

    public boolean enabled() {
        return properties.isEnabled();
    }

    public long checkIntervalMs() {
        return properties.safeCheckIntervalMs();
    }

    @Transactional
    public PlatformOpsRetentionResult purgeExpired(OffsetDateTime now) {
        var retentionDays = properties.safeRetentionDays();
        var cutoff = now.minusDays(retentionDays);
        if (!properties.isEnabled()) {
            return PlatformOpsRetentionResult.disabled(retentionDays, cutoff);
        }

        var deletedDailyReports = dailyReportRepository.deleteCreatedBefore(cutoff);
        var deletedFindings = findingRepository.deleteCreatedBefore(cutoff);
        var deletedIncidents = incidentRepository.deleteStaleUnreferencedBefore(cutoff);
        var deletedRuns = runRepository.deleteUnreferencedTerminalRunsBefore(
                cutoff,
                List.of(PlatformOpsRunStatus.COMPLETED, PlatformOpsRunStatus.FAILED));

        return new PlatformOpsRetentionResult(
                true,
                retentionDays,
                cutoff,
                deletedDailyReports,
                deletedFindings,
                deletedIncidents,
                deletedRuns);
    }
}
