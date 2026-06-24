package com.archdox.cloud.document.dto;

public record DocumentHtmlPreviewResponse(
        Long reportId,
        String reportNo,
        String reportTitle,
        String fileName,
        String html
) {
}
