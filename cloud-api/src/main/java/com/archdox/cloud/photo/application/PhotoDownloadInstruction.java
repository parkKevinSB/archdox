package com.archdox.cloud.photo.application;

import java.time.OffsetDateTime;
import java.util.Map;

public record PhotoDownloadInstruction(
        String method,
        String url,
        Map<String, String> headers,
        OffsetDateTime expiresAt
) {
}
