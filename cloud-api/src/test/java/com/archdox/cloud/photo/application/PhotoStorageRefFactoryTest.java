package com.archdox.cloud.photo.application;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PhotoStorageRefFactoryTest {
    @Test
    void createsOfficeScopedReportPhotoReferences() {
        var factory = new PhotoStorageRefFactory();

        var refs = factory.create(10L, 100L, 1000L, "image/jpeg");

        assertTrue(refs.originalRef().startsWith("offices/10/reports/1000/photos/"));
        assertTrue(refs.originalRef().endsWith("/original.jpg"));
        assertTrue(refs.workingRef().startsWith("offices/10/reports/1000/photos/"));
        assertTrue(refs.workingRef().endsWith("/working.jpg"));
        assertTrue(refs.thumbnailRef().endsWith("/thumbnail.webp"));
    }
}
