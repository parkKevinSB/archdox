package com.archdox.cloud.photo.dto;

import com.archdox.cloud.photo.domain.PhotoAssetStatus;
import com.archdox.cloud.photo.domain.PhotoAssetType;
import com.archdox.cloud.photo.domain.PhotoStorageKind;

public record PhotoAssetResponse(
        PhotoAssetType assetType,
        PhotoAssetStatus status,
        PhotoStorageKind storageKind,
        String storageRef,
        String mime,
        Long bytes,
        Integer width,
        Integer height,
        String hash,
        boolean temporary
) {
}
