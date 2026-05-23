package com.archdox.cloud.office.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateOfficeRequest(
        @NotBlank @Size(max = 100) String displayName
) {
}
