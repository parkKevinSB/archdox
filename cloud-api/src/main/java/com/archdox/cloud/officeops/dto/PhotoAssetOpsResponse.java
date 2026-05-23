package com.archdox.cloud.officeops.dto;

import com.archdox.cloud.photo.domain.PhotoAssetStatus;
import com.archdox.cloud.photo.domain.PhotoAssetType;
import com.archdox.cloud.photo.domain.PhotoStorageKind;
import java.time.OffsetDateTime;

public record PhotoAssetOpsResponse(
        Long id,
        Long photoId,
        PhotoAssetType assetType,
        PhotoAssetStatus status,
        PhotoStorageKind storageKind,
        String mimeType,
        Long bytes,
        Integer width,
        Integer height,
        String hashSha256,
        boolean temporary,
        OffsetDateTime createdAt,
        OffsetDateTime uploadedAt,
        OffsetDateTime pickedUpAt,
        OffsetDateTime deletedAt
) {
}
