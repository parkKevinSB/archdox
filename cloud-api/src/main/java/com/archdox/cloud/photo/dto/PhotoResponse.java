package com.archdox.cloud.photo.dto;

import com.archdox.cloud.photo.domain.PhotoCaptureKind;
import com.archdox.cloud.photo.domain.PhotoPickupStatus;
import com.archdox.cloud.photo.domain.PhotoStatus;
import com.archdox.cloud.photo.domain.PhotoStorageKind;
import com.archdox.cloud.photo.domain.PhotoUploadTarget;
import java.time.OffsetDateTime;
import java.util.List;

public record PhotoResponse(
        Long id,
        Long officeId,
        Long projectId,
        Long siteId,
        Long reportId,
        String stepCode,
        Long checklistItemId,
        Long siteSupervisionEntryId,
        String tradeCode,
        String processCode,
        String inspectionItemCode,
        String caption,
        String locationNote,
        String drawingRef,
        String contextLabel,
        String contextDescription,
        PhotoCaptureKind captureKind,
        PhotoStatus status,
        String mime,
        Integer width,
        Integer height,
        Long bytes,
        String hash,
        PhotoStorageKind storageKind,
        String storageRef,
        String thumbnailStorageRef,
        PhotoUploadTarget uploadTarget,
        PhotoPickupStatus originalPickupStatus,
        OffsetDateTime originalPickedUpAt,
        OffsetDateTime originalTemporaryDeletedAt,
        List<PhotoAssetResponse> assets
) {
}
