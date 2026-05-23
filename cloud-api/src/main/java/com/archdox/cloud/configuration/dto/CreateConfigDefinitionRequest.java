package com.archdox.cloud.configuration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateConfigDefinitionRequest(
        @NotBlank @Size(max = 100) String code,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 100) String reportType
) {
}
