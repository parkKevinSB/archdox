package com.archdox.cloud.storage.application;

import java.time.OffsetDateTime;
import java.util.Map;

public record StorageUploadUrl(
        String method,
        String url,
        Map<String, String> headers,
        OffsetDateTime expiresAt
) {
}
