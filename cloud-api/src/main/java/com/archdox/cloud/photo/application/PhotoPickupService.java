package com.archdox.cloud.photo.application;

import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.global.api.NotFoundException;
import com.archdox.cloud.operation.application.OperationEventService;
import com.archdox.cloud.operation.domain.OperationEventSeverity;
import com.archdox.cloud.photo.domain.PhotoAssetStatus;
import com.archdox.cloud.photo.domain.PhotoAssetType;
import com.archdox.cloud.photo.domain.PhotoPickupStatus;
import com.archdox.cloud.photo.domain.PhotoStorageKind;
import com.archdox.cloud.photo.domain.PhotoUploadTarget;
import com.archdox.cloud.photo.infra.PhotoAssetRepository;
import com.archdox.cloud.photo.infra.PhotoRepository;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PhotoPickupService {
    private final PhotoRepository photoRepository;
    private final PhotoAssetRepository photoAssetRepository;
    private final PhotoStorageAdapterResolver storageAdapterResolver;
    private final OperationEventService operationEventService;

    public PhotoPickupService(
            PhotoRepository photoRepository,
            PhotoAssetRepository photoAssetRepository,
            PhotoStorageAdapterResolver storageAdapterResolver,
            OperationEventService operationEventService
    ) {
        this.photoRepository = photoRepository;
        this.photoAssetRepository = photoAssetRepository;
        this.storageAdapterResolver = storageAdapterResolver;
        this.operationEventService = operationEventService;
    }

    @Transactional(readOnly = true)
    public boolean requiresPickup(Long officeId, Long photoId) {
        return photoRepository.findByIdAndOfficeId(photoId, officeId)
                .filter(photo -> photo.uploadTarget() == PhotoUploadTarget.CLOUD_MEDIATED)
                .filter(photo -> photo.originalPickupStatus() == PhotoPickupStatus.PENDING)
                .flatMap(photo -> photoAssetRepository.findByPhotoIdAndAssetType(photo.id(), PhotoAssetType.ORIGINAL))
                .filter(asset -> asset.status() == PhotoAssetStatus.UPLOADED)
                .filter(asset -> asset.storageKind() != PhotoStorageKind.AGENT_MANAGED)
                .isPresent();
    }

    @Transactional(readOnly = true)
    public boolean isPickedUp(Long officeId, Long photoId) {
        return photoRepository.findByIdAndOfficeId(photoId, officeId)
                .map(photo -> photo.originalPickupStatus() == PhotoPickupStatus.PICKED_UP)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> buildCommandPayload(
            Long officeId,
            Long photoId,
            int attempt,
            int maxAttempts,
            OffsetDateTime expiresAt
    ) {
        var photo = photoRepository.findByIdAndOfficeId(photoId, officeId)
                .orElseThrow(() -> new NotFoundException("Photo not found"));
        var original = photoAssetRepository.findByPhotoIdAndAssetType(photo.id(), PhotoAssetType.ORIGINAL)
                .orElseThrow(() -> new NotFoundException("Original photo asset not found"));
        if (original.status() != PhotoAssetStatus.UPLOADED) {
            throw new BadRequestException("Original photo must be uploaded before pickup");
        }
        var download = storageAdapterResolver
                .forStorageKind(original.storageKind())
                .createDownloadInstruction(original, expiresAt);
        var payload = new LinkedHashMap<String, Object>();
        payload.put("photoId", photo.id());
        payload.put("officeId", photo.officeId());
        payload.put("projectId", photo.projectId());
        payload.put("reportId", photo.reportId());
        payload.put("sourceStorageKind", original.storageKind().name());
        payload.put("sourceStorageRef", original.storageRef());
        payload.put("mime", original.mimeType());
        payload.put("bytes", original.bytes());
        payload.put("hash", original.hashSha256());
        payload.put("downloadMethod", download.method());
        payload.put("downloadUrl", download.url());
        payload.put("downloadHeaders", download.headers());
        payload.put("downloadExpiresAt", download.expiresAt());
        payload.put("suggestedAgentOriginalStorageRef", original.storageRef());
        payload.put("attempt", Math.max(1, attempt));
        payload.put("maxAttempts", Math.max(1, maxAttempts));
        payload.put("deleteTemporaryOriginal", true);
        return payload;
    }

    @Transactional
    public void completeFromAgent(Long officeId, Long photoId, Map<String, Object> result) {
        var agentOriginalStorageRef = stringValue(result.get("agentOriginalStorageRef"));
        if (agentOriginalStorageRef == null || agentOriginalStorageRef.isBlank()) {
            throw new BadRequestException("agentOriginalStorageRef is required for PHOTO_PICKUP completion");
        }
        var photo = photoRepository.findByIdAndOfficeId(photoId, officeId)
                .orElseThrow(() -> new NotFoundException("Photo not found"));
        var original = photoAssetRepository.findByPhotoIdAndAssetType(photo.id(), PhotoAssetType.ORIGINAL)
                .orElseThrow(() -> new NotFoundException("Original photo asset not found"));
        if (original.status() != PhotoAssetStatus.UPLOADED) {
            throw new BadRequestException("Original photo must be uploaded before pickup completion");
        }
        var now = OffsetDateTime.now();
        if (booleanValue(result.get("deleteTemporaryOriginal"))
                && original.temporary()
                && original.storageKind() != PhotoStorageKind.AGENT_MANAGED) {
            try {
                storageAdapterResolver.forStorageKind(original.storageKind()).deleteIfExists(original.storageRef());
                photo.markOriginalTemporaryDeleted(now);
            } catch (Exception ex) {
                throw new BadRequestException("Failed to delete temporary original: " + ex.getMessage());
            }
        }
        original.relocateToAgentManaged(agentOriginalStorageRef.trim(), now);
        photo.markOriginalPickedUp(now);
        operationEventService.record(
                officeId,
                OperationEventSeverity.INFO,
                "PHOTO_PICKUP_COMPLETED",
                "photo-pickup",
                "photo:" + photoId,
                "PHOTO",
                photoId,
                "Original photo picked up by ArchDox Agent.",
                Map.of(
                        "agentOriginalStorageRef", agentOriginalStorageRef.trim(),
                        "temporaryDeleted", photo.originalTemporaryDeletedAt() != null));
    }

    @Transactional
    public void markFailed(Long officeId, Long photoId, String errorMessage) {
        photoRepository.findByIdAndOfficeId(photoId, officeId)
                .ifPresent(photo -> photo.markOriginalPickupFailed(errorMessage, OffsetDateTime.now()));
        operationEventService.record(
                officeId,
                OperationEventSeverity.ERROR,
                "PHOTO_PICKUP_FAILED",
                "photo-pickup",
                "photo:" + photoId,
                "PHOTO",
                photoId,
                errorMessage == null || errorMessage.isBlank() ? "Original photo pickup failed." : errorMessage,
                Map.of());
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            return Boolean.parseBoolean(text);
        }
        return false;
    }
}
