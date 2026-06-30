package com.archdox.cloud.photo.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class PhotoAssetWorkflowTest {
    @Test
    void originalAssetMovesFromTemporaryUploadToLocalNasAfterPickup() {
        var now = OffsetDateTime.now();
        var photo = new Photo(
                10L,
                100L,
                1000L,
                "BASIC_INFO",
                null,
                PhotoCaptureKind.CAMERA,
                "image/jpeg",
                1000L,
                "sha256:abc",
                PhotoStorageKind.API_LOCAL,
                "offices/10/reports/1000/photos/1/working.jpg",
                "offices/10/reports/1000/photos/1/thumbnail.webp",
                PhotoUploadTarget.API_LOCAL,
                1L,
                now,
                null,
                null,
                now);
        var asset = new PhotoAsset(
                photo,
                PhotoAssetType.ORIGINAL,
                PhotoStorageKind.API_LOCAL,
                "offices/10/reports/1000/photos/1/original.jpg",
                "image/jpeg",
                1000L,
                "sha256:abc",
                true,
                now);

        asset.markUploaded(1000L, now.plusMinutes(1));
        asset.relocateToAgentManaged("reports/1000/photos/1/original.jpg", now.plusMinutes(2));
        photo.markOriginalPickedUp(now.plusMinutes(2));

        assertEquals(PhotoAssetStatus.PICKED_UP, asset.status());
        assertEquals(PhotoStorageKind.AGENT_MANAGED, asset.storageKind());
        assertFalse(asset.temporary());
        assertEquals(PhotoPickupStatus.PICKED_UP, photo.originalPickupStatus());
    }

    @Test
    void deletedPendingPhotoNoLongerRequiresOriginalPickup() {
        var now = OffsetDateTime.now();
        var photo = new Photo(
                10L,
                100L,
                1000L,
                "PHOTOS",
                null,
                PhotoCaptureKind.CAMERA,
                "image/jpeg",
                1000L,
                "sha256:pending",
                PhotoStorageKind.S3,
                "offices/10/reports/1000/photos/2/working.jpg",
                "offices/10/reports/1000/photos/2/thumbnail.webp",
                PhotoUploadTarget.CLOUD_MEDIATED,
                1L,
                now,
                null,
                null,
                now);

        photo.markDeleted(now.plusMinutes(1));

        assertEquals(PhotoStatus.DELETED, photo.status());
        assertEquals(PhotoPickupStatus.NOT_REQUIRED, photo.originalPickupStatus());
    }
}
