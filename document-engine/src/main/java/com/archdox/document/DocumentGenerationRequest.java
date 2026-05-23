package com.archdox.document;

import java.util.List;
import java.util.Map;

public record DocumentGenerationRequest(
        String jobId,
        String officeCode,
        String reportId,
        TemplateSpec template,
        Map<String, Object> payload,
        List<PhotoAsset> photos,
        OutputFormat outputFormat
) {
}
