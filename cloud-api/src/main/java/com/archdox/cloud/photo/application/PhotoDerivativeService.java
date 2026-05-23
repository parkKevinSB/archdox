package com.archdox.cloud.photo.application;

import com.archdox.cloud.global.api.NotFoundException;
import com.archdox.cloud.photo.domain.PhotoAsset;
import com.archdox.cloud.photo.domain.PhotoAssetStatus;
import com.archdox.cloud.photo.domain.PhotoAssetType;
import com.archdox.cloud.photo.infra.PhotoAssetRepository;
import com.archdox.cloud.photo.infra.PhotoRepository;
import java.io.IOException;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PhotoDerivativeService {
    private final PhotoRepository photoRepository;
    private final PhotoAssetRepository photoAssetRepository;
    private final PhotoStorageAdapterResolver storageAdapterResolver;
    private final PhotoDerivativeGenerator generator;

    public PhotoDerivativeService(
            PhotoRepository photoRepository,
            PhotoAssetRepository photoAssetRepository,
            PhotoStorageAdapterResolver storageAdapterResolver,
            PhotoDerivativeGenerator generator
    ) {
        this.photoRepository = photoRepository;
        this.photoAssetRepository = photoAssetRepository;
        this.storageAdapterResolver = storageAdapterResolver;
        this.generator = generator;
    }

    @Transactional(readOnly = true)
    public void prepare(Long officeId, Long photoId) {
        requireSourceAsset(officeId, photoId);
    }

    @Transactional(rollbackFor = IOException.class)
    public void generateWorking(Long officeId, Long photoId) throws IOException {
        var source = requireSourceAsset(officeId, photoId);
        var target = requireAsset(source.photo().id(), PhotoAssetType.WORKING);
        if (source.id().equals(target.id()) && target.status() == PhotoAssetStatus.UPLOADED) {
            return;
        }
        var sourceAdapter = storageAdapterResolver.forStorageKind(source.storageKind());
        var targetAdapter = storageAdapterResolver.forStorageKind(target.storageKind());
        try (var input = sourceAdapter.openContent(source)) {
            var generated = generator.createWorking(input, source.mimeType());
            targetAdapter.storeContent(target, generated.bytesLength(), generator.asInputStream(generated));
            markGenerated(target, generated);
        }
    }

    @Transactional(rollbackFor = IOException.class)
    public void generateThumbnail(Long officeId, Long photoId) throws IOException {
        var source = requireAsset(requirePhotoId(officeId, photoId), PhotoAssetType.WORKING);
        if (source.status() != PhotoAssetStatus.UPLOADED) {
            source = requireSourceAsset(officeId, photoId);
        }
        var target = requireAsset(source.photo().id(), PhotoAssetType.THUMBNAIL);
        var sourceAdapter = storageAdapterResolver.forStorageKind(source.storageKind());
        var targetAdapter = storageAdapterResolver.forStorageKind(target.storageKind());
        try (var input = sourceAdapter.openContent(source)) {
            var generated = generator.createThumbnail(input);
            targetAdapter.storeContent(target, generated.bytesLength(), generator.asInputStream(generated));
            markGenerated(target, generated);
        }
    }

    private Long requirePhotoId(Long officeId, Long photoId) {
        return photoRepository.findByIdAndOfficeId(photoId, officeId)
                .orElseThrow(() -> new NotFoundException("Photo not found"))
                .id();
    }

    private PhotoAsset requireSourceAsset(Long officeId, Long photoId) {
        var photoIdInOffice = requirePhotoId(officeId, photoId);
        var original = photoAssetRepository.findByPhotoIdAndAssetType(photoIdInOffice, PhotoAssetType.ORIGINAL);
        if (original.isPresent() && original.get().status() == PhotoAssetStatus.UPLOADED) {
            return original.get();
        }
        var working = requireAsset(photoIdInOffice, PhotoAssetType.WORKING);
        if (working.status() == PhotoAssetStatus.UPLOADED) {
            return working;
        }
        throw new NotFoundException("Uploaded photo source asset not found");
    }

    private PhotoAsset requireAsset(Long photoId, PhotoAssetType assetType) {
        return photoAssetRepository.findByPhotoIdAndAssetType(photoId, assetType)
                .orElseThrow(() -> new NotFoundException("Photo asset not found"));
    }

    private void markGenerated(PhotoAsset asset, PhotoDerivativeGenerator.GeneratedImage generated) {
        var now = OffsetDateTime.now();
        asset.markUploaded(generated.bytesLength(), now);
        asset.updateImageInfo(
                generated.bytesLength(),
                generated.width(),
                generated.height(),
                generated.hashSha256());
    }
}
