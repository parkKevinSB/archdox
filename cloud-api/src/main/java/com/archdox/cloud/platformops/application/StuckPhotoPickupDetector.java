package com.archdox.cloud.platformops.application;

import com.archdox.cloud.photo.domain.PhotoPickupStatus;
import com.archdox.cloud.photo.domain.PhotoStatus;
import com.archdox.cloud.photo.infra.PhotoRepository;
import com.archdox.cloud.platformops.domain.PlatformOpsFindingSeverity;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class StuckPhotoPickupDetector implements PlatformOpsDetector {
    private final PhotoRepository repository;
    private final PlatformOpsAutomationSettingsService automationSettingsService;

    public StuckPhotoPickupDetector(PhotoRepository repository, PlatformOpsAutomationSettingsService automationSettingsService) {
        this.repository = repository;
        this.automationSettingsService = automationSettingsService;
    }

    @Override
    public String category() {
        return "PHOTO_PICKUP_STUCK";
    }

    @Override
    public List<PlatformOpsDetectionFinding> detect(PlatformOpsDetectionContext context) {
        var settings = automationSettingsService.settings();
        var cutoff = context.now().minusMinutes(settings.photoPickupStuckMinutes());
        return repository.findByStatusAndOriginalPickupStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                        PhotoStatus.UPLOADED,
                        PhotoPickupStatus.PENDING,
                        cutoff,
                        context.page())
                .stream()
                .map(photo -> {
                    var evidence = new LinkedHashMap<String, Object>();
                    evidence.put("reportId", photo.reportId() == null ? "null" : photo.reportId());
                    evidence.put("uploadTarget", photo.uploadTarget().name());
                    evidence.put("updatedAt", photo.updatedAt().toString());
                    evidence.put("thresholdMinutes", settings.photoPickupStuckMinutes());
                    return new PlatformOpsDetectionFinding(
                            photo.officeId(),
                            PlatformOpsFindingSeverity.WARN,
                            category(),
                            "PHOTO_PICKUP_STUCK_DETECTED",
                            "Photo original pickup appears stuck",
                            "An uploaded original photo is still waiting for office/local pickup.",
                            "PHOTO",
                            String.valueOf(photo.id()),
                            "photo-pickup",
                            String.valueOf(photo.id()),
                            evidence,
                            "Check Agent pickup commands, local storage access, and temporary original retention.");
                })
                .toList();
    }
}
