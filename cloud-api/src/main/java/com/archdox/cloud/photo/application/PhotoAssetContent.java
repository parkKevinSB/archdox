package com.archdox.cloud.photo.application;

public record PhotoAssetContent(
        String fileName,
        String mimeType,
        Long bytes,
        byte[] content
) {
}
