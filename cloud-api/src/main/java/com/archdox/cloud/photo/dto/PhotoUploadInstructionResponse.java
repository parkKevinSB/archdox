package com.archdox.cloud.photo.dto;

import com.archdox.cloud.photo.domain.PhotoUploadKind;
import java.time.OffsetDateTime;
import java.util.Map;

public record PhotoUploadInstructionResponse(
        PhotoUploadKind kind,
        String method,
        String url,
        Map<String, String> fields,
        Map<String, String> headers,
        String token,
        OffsetDateTime expiresAt
) {
}
