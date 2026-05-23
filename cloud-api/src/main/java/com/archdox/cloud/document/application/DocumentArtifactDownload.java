package com.archdox.cloud.document.application;

import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

public record DocumentArtifactDownload(
        String fileName,
        String mimeType,
        long bytes,
        StreamingResponseBody body
) {
}
