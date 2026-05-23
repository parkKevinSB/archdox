package com.archdox.cloud.photo.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.archdox.cloud.photo.domain.PhotoAsset;
import com.archdox.cloud.photo.domain.PhotoAssetType;
import com.archdox.cloud.photo.domain.PhotoStorageKind;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class S3CompatiblePhotoStorageAdapterTest {
    @Test
    void createsPresignedPutInstruction() {
        var properties = new PhotoStorageProperties();
        properties.getS3().setEndpoint("http://localhost:9000");
        properties.getS3().setBucket("archdox");
        properties.getS3().setAccessKey("test-access-key");
        properties.getS3().setSecretKey("test-secret-key");
        properties.getS3().setRegion("ap-northeast-2");
        var adapter = new S3CompatiblePhotoStorageAdapter(properties);
        var asset = new PhotoAsset(
                null,
                PhotoAssetType.WORKING,
                PhotoStorageKind.S3,
                "offices/10/reports/1000/photos/1/working.jpg",
                "image/jpeg",
                1000L,
                "sha256:abc",
                false,
                OffsetDateTime.now());

        var instructions = adapter.createUploadInstructions(
                null,
                List.of(asset),
                OffsetDateTime.now().plusMinutes(5));

        assertEquals(1, instructions.size());
        var instruction = instructions.get(0);
        assertEquals("PUT", instruction.method());
        assertEquals("image/jpeg", instruction.headers().get("Content-Type"));
        assertTrue(instruction.url().contains("offices/10/reports/1000/photos/1/working.jpg"));
    }

    @Test
    void createsPresignedGetInstructionForPickup() {
        var properties = new PhotoStorageProperties();
        properties.getS3().setEndpoint("http://localhost:9000");
        properties.getS3().setBucket("archdox");
        properties.getS3().setAccessKey("test-access-key");
        properties.getS3().setSecretKey("test-secret-key");
        properties.getS3().setRegion("ap-northeast-2");
        var adapter = new S3CompatiblePhotoStorageAdapter(properties);
        var asset = new PhotoAsset(
                null,
                PhotoAssetType.ORIGINAL,
                PhotoStorageKind.S3_TEMP,
                "offices/10/reports/1000/photos/1/original.jpg",
                "image/jpeg",
                1000L,
                "sha256:abc",
                true,
                OffsetDateTime.now());

        var instruction = adapter.createDownloadInstruction(asset, OffsetDateTime.now().plusMinutes(5));

        assertEquals("GET", instruction.method());
        assertTrue(instruction.url().contains("offices/10/reports/1000/photos/1/original.jpg"));
    }
}
