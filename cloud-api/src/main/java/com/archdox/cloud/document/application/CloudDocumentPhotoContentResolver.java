package com.archdox.cloud.document.application;

import com.archdox.cloud.photo.application.PhotoStorageAdapterResolver;
import com.archdox.cloud.photo.domain.PhotoAssetStatus;
import com.archdox.cloud.photo.domain.PhotoAssetType;
import com.archdox.cloud.photo.domain.PhotoStorageKind;
import com.archdox.cloud.photo.infra.PhotoAssetRepository;
import com.archdox.cloud.photo.infra.PhotoLocalObjectStore;
import com.archdox.document.PhotoAsset;
import com.archdox.document.PhotoContentResolver;
import com.archdox.document.ResolvedPhotoContent;
import java.io.IOException;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class CloudDocumentPhotoContentResolver implements PhotoContentResolver {
    private final PhotoAssetRepository photoAssetRepository;
    private final PhotoStorageAdapterResolver storageAdapterResolver;
    private final PhotoLocalObjectStore localObjectStore;

    public CloudDocumentPhotoContentResolver(
            PhotoAssetRepository photoAssetRepository,
            PhotoStorageAdapterResolver storageAdapterResolver,
            PhotoLocalObjectStore localObjectStore
    ) {
        this.photoAssetRepository = photoAssetRepository;
        this.storageAdapterResolver = storageAdapterResolver;
        this.localObjectStore = localObjectStore;
    }

    @Override
    public Optional<ResolvedPhotoContent> resolve(PhotoAsset photo) throws IOException {
        var asset = resolveCloudAsset(photo);
        if (asset.isPresent()) {
            return readCloudAsset(asset.get());
        }
        return readApiLocalFallback(photo);
    }

    private Optional<com.archdox.cloud.photo.domain.PhotoAsset> resolveCloudAsset(PhotoAsset photo) {
        var photoId = parsePhotoId(photo.photoId());
        if (photoId.isEmpty()) {
            return Optional.empty();
        }
        var working = photoAssetRepository.findByPhotoIdAndAssetType(photoId.get(), PhotoAssetType.WORKING)
                .filter(this::isReadableCloudAsset);
        if (working.isPresent()) {
            return working;
        }
        return photoAssetRepository.findByPhotoIdAndAssetType(photoId.get(), PhotoAssetType.THUMBNAIL)
                .filter(this::isReadableCloudAsset);
    }

    private boolean isReadableCloudAsset(com.archdox.cloud.photo.domain.PhotoAsset asset) {
        return asset.status() == PhotoAssetStatus.UPLOADED
                && asset.storageKind() != PhotoStorageKind.AGENT_MANAGED
                && asset.storageKind() != PhotoStorageKind.DELETED
                && asset.storageRef() != null
                && !asset.storageRef().isBlank();
    }

    private Optional<ResolvedPhotoContent> readCloudAsset(
            com.archdox.cloud.photo.domain.PhotoAsset asset
    ) throws IOException {
        try (var input = storageAdapterResolver.forStorageKind(asset.storageKind()).openContent(asset)) {
            return Optional.of(new ResolvedPhotoContent(input.readAllBytes(), asset.mimeType()));
        }
    }

    private Optional<ResolvedPhotoContent> readApiLocalFallback(PhotoAsset photo) throws IOException {
        if (photo.storageRef() == null
                || photo.storageRef().isBlank()
                || !localObjectStore.exists(photo.storageRef())) {
            return Optional.empty();
        }
        try (var input = localObjectStore.open(photo.storageRef())) {
            return Optional.of(new ResolvedPhotoContent(input.readAllBytes(), photo.mimeType()));
        }
    }

    private Optional<Long> parsePhotoId(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.valueOf(value.trim()));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }
}
