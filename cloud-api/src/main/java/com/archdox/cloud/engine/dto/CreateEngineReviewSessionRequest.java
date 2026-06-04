package com.archdox.cloud.engine.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateEngineReviewSessionRequest(
        String customerProjectRef,
        @NotBlank String reviewPurpose
) {
}
