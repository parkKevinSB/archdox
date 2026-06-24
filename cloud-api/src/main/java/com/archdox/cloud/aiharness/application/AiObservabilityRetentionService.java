package com.archdox.cloud.aiharness.application;

import com.archdox.cloud.aiharness.infra.AiHarnessTraceEventRepository;
import com.archdox.cloud.aipolicy.infra.AiModelCallLogRepository;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiObservabilityRetentionService {
    private final AiObservabilityRetentionProperties properties;
    private final AiHarnessTraceEventRepository traceEventRepository;
    private final AiModelCallLogRepository modelCallLogRepository;

    public AiObservabilityRetentionService(
            AiObservabilityRetentionProperties properties,
            AiHarnessTraceEventRepository traceEventRepository,
            AiModelCallLogRepository modelCallLogRepository
    ) {
        this.properties = properties;
        this.traceEventRepository = traceEventRepository;
        this.modelCallLogRepository = modelCallLogRepository;
    }

    public boolean enabled() {
        return properties.isEnabled();
    }

    public long checkIntervalMs() {
        return properties.safeCheckIntervalMs();
    }

    @Transactional
    public AiObservabilityRetentionResult purgeExpired(OffsetDateTime now) {
        var retentionDays = properties.safeRetentionDays();
        var cutoff = now.minusDays(retentionDays);
        if (!properties.isEnabled()) {
            return AiObservabilityRetentionResult.disabled(retentionDays, cutoff);
        }
        var deletedTraceEvents = traceEventRepository.deleteCreatedBefore(cutoff);
        var deletedModelCallLogs = modelCallLogRepository.deleteCompletedBefore(cutoff);
        return new AiObservabilityRetentionResult(
                true,
                retentionDays,
                cutoff,
                deletedTraceEvents,
                deletedModelCallLogs);
    }
}
