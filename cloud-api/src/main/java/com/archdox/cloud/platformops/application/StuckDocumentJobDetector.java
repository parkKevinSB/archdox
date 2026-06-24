package com.archdox.cloud.platformops.application;

import com.archdox.cloud.document.domain.DocumentJobStatus;
import com.archdox.cloud.document.infra.DocumentJobRepository;
import com.archdox.cloud.platformops.domain.PlatformOpsFindingSeverity;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class StuckDocumentJobDetector implements PlatformOpsDetector {
    private final DocumentJobRepository repository;
    private final PlatformOpsAutomationSettingsService automationSettingsService;

    public StuckDocumentJobDetector(DocumentJobRepository repository, PlatformOpsAutomationSettingsService automationSettingsService) {
        this.repository = repository;
        this.automationSettingsService = automationSettingsService;
    }

    @Override
    public String category() {
        return "DOCUMENT_JOB_STUCK";
    }

    @Override
    public List<PlatformOpsDetectionFinding> detect(PlatformOpsDetectionContext context) {
        var settings = automationSettingsService.settings();
        var cutoff = context.now().minusMinutes(settings.documentJobStuckMinutes());
        return repository.findByStatusInAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                        List.of(DocumentJobStatus.REQUESTED, DocumentJobStatus.GENERATING),
                        cutoff,
                        context.page())
                .stream()
                .map(job -> {
                    var evidence = new LinkedHashMap<String, Object>();
                    evidence.put("status", job.status().name());
                    evidence.put("progressStep", job.progressStep().name());
                    evidence.put("progressPercent", job.progressPercent());
                    evidence.put("updatedAt", job.updatedAt().toString());
                    evidence.put("thresholdMinutes", settings.documentJobStuckMinutes());
                    return new PlatformOpsDetectionFinding(
                            job.officeId(),
                            PlatformOpsFindingSeverity.WARN,
                            category(),
                            "DOCUMENT_JOB_STUCK_DETECTED",
                            "Document job appears stuck",
                            "A document generation job has not progressed within the expected time window.",
                            "DOCUMENT_JOB",
                            String.valueOf(job.id()),
                            "document-generation",
                            String.valueOf(job.id()),
                            evidence,
                            "Check the selected ArchDox Agent session, command state, and recent document generation errors.");
                })
                .toList();
    }
}
