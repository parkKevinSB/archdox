package com.archdox.cloud.engine.dto;

import jakarta.validation.constraints.NotBlank;

public record SubmitEngineReviewDocumentRequest(
        String documentTypeHint,
        String fileName,
        @NotBlank String contentText
) {
}
