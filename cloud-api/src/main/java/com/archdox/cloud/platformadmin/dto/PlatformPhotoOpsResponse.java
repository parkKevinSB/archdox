package com.archdox.cloud.platformadmin.dto;

import com.archdox.cloud.photo.domain.PhotoPickupStatus;
import com.archdox.cloud.photo.domain.PhotoStatus;
import com.archdox.cloud.photo.domain.PhotoStorageKind;
import com.archdox.cloud.photo.domain.PhotoUploadTarget;
import java.time.OffsetDateTime;

public record PlatformPhotoOpsResponse(
        Long id,
        Long officeId,
        Long projectId,
        Long reportId,
        String stepCode,
        PhotoStatus status,
        PhotoPickupStatus originalPickupStatus,
        PhotoUploadTarget uploadTarget,
        PhotoStorageKind storageKind,
        Long bytes,
        String pickupErrorMessage,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
