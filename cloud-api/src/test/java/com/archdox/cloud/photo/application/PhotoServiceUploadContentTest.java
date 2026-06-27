package com.archdox.cloud.photo.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.archdox.cloud.checklist.application.ChecklistService;
import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.inspection.application.InspectionReportService;
import com.archdox.cloud.office.application.OfficeContext;
import com.archdox.cloud.office.application.OfficePermissionService;
import com.archdox.cloud.photo.domain.Photo;
import com.archdox.cloud.photo.domain.PhotoAsset;
import com.archdox.cloud.photo.domain.PhotoAssetStatus;
import com.archdox.cloud.photo.domain.PhotoAssetType;
import com.archdox.cloud.photo.domain.PhotoCaptureKind;
import com.archdox.cloud.photo.domain.PhotoStorageKind;
import com.archdox.cloud.photo.domain.PhotoUploadKind;
import com.archdox.cloud.photo.domain.PhotoUploadTarget;
import com.archdox.cloud.photo.infra.PhotoAssetRepository;
import com.archdox.cloud.photo.infra.PhotoRepository;
import com.archdox.cloud.project.application.ProjectService;
import com.archdox.cloud.site.application.SiteService;
import com.archdox.cloud.supervisionledger.infra.SiteSupervisionEntryRepository;
import io.github.parkkevinsb.bloom.EventBus;
import java.io.ByteArrayInputStream;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PhotoServiceUploadContentTest {
    private final PhotoRepository photoRepository = mock(PhotoRepository.class);
    private final PhotoAssetRepository photoAssetRepository = mock(PhotoAssetRepository.class);
    private final PhotoStorageAdapterResolver storageAdapterResolver = mock(PhotoStorageAdapterResolver.class);
    private final PhotoStorageAdapter storageAdapter = mock(PhotoStorageAdapter.class);
    private final PhotoService service = new PhotoService(
            photoRepository,
            photoAssetRepository,
            mock(ProjectService.class),
            mock(SiteService.class),
            mock(InspectionReportService.class),
            mock(OfficePermissionService.class),
            mock(ChecklistService.class),
            mock(SiteSupervisionEntryRepository.class),
            mock(PhotoStorageRefFactory.class),
            storageAdapterResolver,
            new PhotoStorageProperties(),
            mock(EventBus.class));

    @AfterEach
    void clearOfficeContext() {
        OfficeContext.clear();
    }

    @Test
    void storesTemporaryOriginalThroughApiForCloudMediatedUpload() throws Exception {
        OfficeContext.set(10L);
        var photo = photo(123L);
        var asset = asset(photo, PhotoAssetType.ORIGINAL, PhotoStorageKind.S3_TEMP);
        var input = new ByteArrayInputStream("image".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        when(photoRepository.findByIdAndOfficeId(123L, 10L)).thenReturn(Optional.of(photo));
        when(photoAssetRepository.findByPhotoIdAndAssetType(123L, PhotoAssetType.ORIGINAL)).thenReturn(Optional.of(asset));
        when(storageAdapterResolver.forStorageKind(PhotoStorageKind.S3_TEMP)).thenReturn(storageAdapter);

        service.storeContent(123L, PhotoUploadKind.ORIGINAL, 5L, input);

        verify(storageAdapter).storeContent(eq(asset), eq(5L), same(input));
        assertEquals(PhotoAssetStatus.UPLOADED, asset.status());
    }

    @Test
    void rejectsApiUploadForRegularS3Asset() {
        OfficeContext.set(10L);
        var photo = photo(123L);
        var asset = asset(photo, PhotoAssetType.WORKING, PhotoStorageKind.S3);
        when(photoRepository.findByIdAndOfficeId(123L, 10L)).thenReturn(Optional.of(photo));
        when(photoAssetRepository.findByPhotoIdAndAssetType(123L, PhotoAssetType.WORKING)).thenReturn(Optional.of(asset));

        assertThrows(
                BadRequestException.class,
                () -> service.storeContent(123L, PhotoUploadKind.WORKING, 5L, new ByteArrayInputStream(new byte[0])));
        verifyNoInteractions(storageAdapterResolver);
    }

    private Photo photo(Long id) {
        var photo = new Photo(
                10L,
                100L,
                1000L,
                "DAILY_LOG",
                null,
                PhotoCaptureKind.UPLOAD,
                "image/jpeg",
                5L,
                "sha256:abc",
                PhotoStorageKind.S3,
                "offices/10/reports/1000/photos/1/working.jpg",
                "offices/10/reports/1000/photos/1/thumbnail.webp",
                PhotoUploadTarget.CLOUD_MEDIATED,
                1L,
                null,
                null,
                null,
                OffsetDateTime.now());
        ReflectionTestUtils.setField(photo, "id", id);
        return photo;
    }

    private PhotoAsset asset(Photo photo, PhotoAssetType assetType, PhotoStorageKind storageKind) {
        return new PhotoAsset(
                photo,
                assetType,
                storageKind,
                "offices/10/reports/1000/photos/1/%s.jpg".formatted(assetType.name().toLowerCase()),
                "image/jpeg",
                5L,
                "sha256:abc",
                storageKind == PhotoStorageKind.S3_TEMP,
                OffsetDateTime.now());
    }
}
