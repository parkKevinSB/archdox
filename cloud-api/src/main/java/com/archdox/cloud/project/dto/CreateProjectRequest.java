package com.archdox.cloud.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CreateProjectRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 500) String address,
        @Size(max = 100) String buildingType,
        LocalDate startDate,
        LocalDate endDate
) {
}
