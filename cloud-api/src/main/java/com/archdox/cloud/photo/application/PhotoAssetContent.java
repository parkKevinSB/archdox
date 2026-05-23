package com.archdox.cloud.photo.application;

import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

public record PhotoAssetContent(
        String fileName,
        String mimeType,
        Long bytes,
        StreamingResponseBody body
) {
}
