package com.archdox.cloud.photo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ConfirmPhotoUploadRequest(
        @NotBlank @Size(max = 120) String hash,
        @NotNull @Positive Long bytes,
        Integer width,
        Integer height
) {
}
