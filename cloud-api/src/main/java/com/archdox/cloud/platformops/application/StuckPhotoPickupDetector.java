package com.archdox.cloud.platformops.application;

import com.archdox.cloud.photo.domain.PhotoPickupStatus;
import com.archdox.cloud.photo.domain.PhotoStatus;
import com.archdox.cloud.photo.infra.PhotoRepository;
import com.archdox.cloud.platformops.domain.PlatformOpsFindingSeverity;
import com.archdox.cloud.platformops.domain.PlatformOpsIncident;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    @Override
    public boolean supportsAutoResolve() {
        return true;
    }

    @Override
    public Optional<PlatformOpsIncidentResolution> resolve(
            PlatformOpsIncident incident,
            PlatformOpsDetectionContext context
    ) {
        if (!category().equals(incident.category()) || !"PHOTO".equals(incident.primaryResourceType())) {
            return Optional.empty();
        }
        var photoId = parseLong(incident.primaryResourceId());
        if (photoId == null) {
            return Optional.of(resolution(
                    "PHOTO_PICKUP_STUCK_RESOLVED_INVALID_RESOURCE",
                    "Photo pickup incident was resolved because the tracked photo resource id is invalid.",
                    Map.of("resourceId", String.valueOf(incident.primaryResourceId()))));
        }
        var photo = repository.findById(photoId);
        if (photo.isEmpty()) {
            return Optional.of(resolution(
                    "PHOTO_PICKUP_STUCK_RESOLVED_PHOTO_NOT_FOUND",
                    "Photo pickup incident was resolved because the tracked photo no longer exists.",
                    Map.of("photoId", photoId)));
        }

        var current = photo.get();
        var settings = automationSettingsService.settings();
        var cutoff = context.now().minusMinutes(settings.photoPickupStuckMinutes());
        var evidence = new LinkedHashMap<String, Object>();
        evidence.put("photoId", current.id());
        evidence.put("status", current.status().name());
        evidence.put("originalPickupStatus", current.originalPickupStatus().name());
        evidence.put("updatedAt", current.updatedAt().toString());
        evidence.put("thresholdMinutes", settings.photoPickupStuckMinutes());

        if (current.status() == PhotoStatus.DELETED) {
            return Optional.of(resolution(
                    "PHOTO_PICKUP_STUCK_RESOLVED_PHOTO_DELETED",
                    "Photo pickup incident was resolved because the tracked photo was deleted.",
                    evidence));
        }
        if (current.originalPickupStatus() != PhotoPickupStatus.PENDING) {
            return Optional.of(resolution(
                    "PHOTO_PICKUP_STUCK_RESOLVED_PICKUP_NOT_PENDING",
                    "Photo pickup incident was resolved because original pickup is no longer pending.",
                    evidence));
        }
        if (current.originalTemporaryDeletedAt() != null) {
            evidence.put("originalTemporaryDeletedAt", current.originalTemporaryDeletedAt().toString());
            return Optional.of(resolution(
                    "PHOTO_PICKUP_STUCK_RESOLVED_ORIGINAL_TEMPORARY_DELETED",
                    "Photo pickup incident was resolved because the temporary original was already deleted.",
                    evidence));
        }
        if (current.status() != PhotoStatus.UPLOADED) {
            return Optional.of(resolution(
                    "PHOTO_PICKUP_STUCK_RESOLVED_PHOTO_NOT_UPLOADED",
                    "Photo pickup incident was resolved because the photo is no longer in uploaded state.",
                    evidence));
        }
        if (!current.updatedAt().isBefore(cutoff)) {
            return Optional.of(resolution(
                    "PHOTO_PICKUP_STUCK_RESOLVED_NOT_STUCK_YET",
                    "Photo pickup incident was resolved because the pending pickup is not beyond the stuck threshold anymore.",
                    evidence));
        }
        return Optional.empty();
    }

    private PlatformOpsIncidentResolution resolution(String code, String message, Map<String, Object> evidence) {
        return new PlatformOpsIncidentResolution(code, message, evidence);
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
