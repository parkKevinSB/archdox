package com.archdox.cloud.officeops.dto;

import com.archdox.cloud.photo.domain.PhotoCaptureKind;
import com.archdox.cloud.photo.domain.PhotoPickupStatus;
import com.archdox.cloud.photo.domain.PhotoStatus;
import com.archdox.cloud.photo.domain.PhotoStorageKind;
import com.archdox.cloud.photo.domain.PhotoUploadTarget;
import java.time.OffsetDateTime;
import java.util.List;

public record PhotoOpsResponse(
        Long id,
        Long officeId,
        Long projectId,
        Long reportId,
        String stepCode,
        Long checklistItemId,
        PhotoCaptureKind captureKind,
        PhotoStatus status,
        String mimeType,
        Integer width,
        Integer height,
        Long bytes,
        String hashSha256,
        PhotoStorageKind storageKind,
        PhotoUploadTarget uploadTarget,
        PhotoPickupStatus originalPickupStatus,
        Long requestedBy,
        Long confirmedBy,
        OffsetDateTime takenAt,
        boolean hasGps,
        OffsetDateTime createdAt,
        OffsetDateTime confirmedAt,
        OffsetDateTime originalPickedUpAt,
        OffsetDateTime originalTemporaryDeletedAt,
        String pickupErrorMessage,
        OffsetDateTime updatedAt,
        List<PhotoAssetOpsResponse> assets
) {
}
