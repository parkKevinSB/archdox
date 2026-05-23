package com.archdox.cloud.photo.application;

import com.archdox.cloud.photo.domain.Photo;
import com.archdox.cloud.photo.domain.PhotoAsset;
import com.archdox.cloud.photo.domain.PhotoAssetType;
import com.archdox.cloud.photo.domain.PhotoStorageKind;
import com.archdox.cloud.photo.domain.PhotoUploadKind;
import com.archdox.cloud.photo.domain.PhotoUploadTarget;
import com.archdox.cloud.photo.dto.PhotoUploadInstructionResponse;
import com.archdox.cloud.photo.infra.PhotoLocalObjectStore;
import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ApiLocalPhotoStorageAdapter implements PhotoStorageAdapter {
    private final PhotoLocalObjectStore objectStore;

    public ApiLocalPhotoStorageAdapter(PhotoLocalObjectStore objectStore) {
        this.objectStore = objectStore;
    }

    @Override
    public boolean supports(PhotoUploadTarget target) {
        return target == PhotoUploadTarget.API_LOCAL;
    }

    @Override
    public boolean supports(PhotoStorageKind storageKind) {
        return storageKind == PhotoStorageKind.API_LOCAL;
    }

    @Override
    public PhotoStorageKind storageKindFor(PhotoUploadTarget target, PhotoAssetType assetType) {
        return PhotoStorageKind.API_LOCAL;
    }

    @Override
    public List<PhotoUploadInstructionResponse> createUploadInstructions(
            Photo photo,
            List<PhotoAsset> assets,
            OffsetDateTime expiresAt
    ) {
        return assets.stream()
                .map(asset -> new PhotoUploadInstructionResponse(
                        uploadKind(asset.assetType()),
                        "PUT",
                        "/api/v1/photos/%d/content/%s".formatted(photo.id(), uploadKind(asset.assetType())),
                        Map.of(),
                        Map.of(),
                        null,
                        expiresAt))
                .toList();
    }

    @Override
    public PhotoDownloadInstruction createDownloadInstruction(PhotoAsset asset, OffsetDateTime expiresAt) {
        return new PhotoDownloadInstruction(
                "GET",
                "/agent/api/v1/photos/%d/assets/%s/content".formatted(asset.photo().id(), asset.assetType()),
                Map.of(),
                expiresAt);
    }

    @Override
    public void storeContent(PhotoAsset asset, Long contentLength, InputStream input) throws IOException {
        objectStore.write(asset.storageRef(), input);
    }

    @Override
    public InputStream openContent(PhotoAsset asset) throws IOException {
        return objectStore.open(asset.storageRef());
    }

    @Override
    public void deleteIfExists(String storageRef) throws IOException {
        objectStore.deleteIfExists(storageRef);
    }

    private PhotoUploadKind uploadKind(PhotoAssetType assetType) {
        return switch (assetType) {
            case ORIGINAL -> PhotoUploadKind.ORIGINAL;
            case WORKING -> PhotoUploadKind.WORKING;
            case THUMBNAIL -> PhotoUploadKind.THUMBNAIL;
        };
    }
}
