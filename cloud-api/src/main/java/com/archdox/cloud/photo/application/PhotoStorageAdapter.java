package com.archdox.cloud.photo.application;

import com.archdox.cloud.photo.domain.Photo;
import com.archdox.cloud.photo.domain.PhotoAsset;
import com.archdox.cloud.photo.domain.PhotoAssetType;
import com.archdox.cloud.photo.domain.PhotoStorageKind;
import com.archdox.cloud.photo.domain.PhotoUploadTarget;
import com.archdox.cloud.photo.dto.PhotoUploadInstructionResponse;
import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.List;

public interface PhotoStorageAdapter {
    boolean supports(PhotoUploadTarget target);

    boolean supports(PhotoStorageKind storageKind);

    PhotoStorageKind storageKindFor(PhotoUploadTarget target, PhotoAssetType assetType);

    List<PhotoUploadInstructionResponse> createUploadInstructions(
            Photo photo,
            List<PhotoAsset> assets,
            OffsetDateTime expiresAt);

    default PhotoDownloadInstruction createDownloadInstruction(PhotoAsset asset, OffsetDateTime expiresAt) {
        throw new UnsupportedOperationException("Photo download is not supported");
    }

    default void storeContent(PhotoAsset asset, Long contentLength, InputStream input) throws IOException {
        throw new UnsupportedOperationException("Direct API content upload is not supported");
    }

    default InputStream openContent(PhotoAsset asset) throws IOException {
        throw new UnsupportedOperationException("Photo content download is not supported");
    }

    default void deleteIfExists(String storageRef) throws IOException {
    }
}
