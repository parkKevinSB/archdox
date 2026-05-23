package com.archdox.cloud.photo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompletePhotoPickupRequest(
        @NotBlank @Size(max = 1000) String agentOriginalStorageRef,
        Boolean deleteTemporaryOriginal
) {
}
