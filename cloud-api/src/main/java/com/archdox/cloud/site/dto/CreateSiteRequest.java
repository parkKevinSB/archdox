package com.archdox.cloud.site.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CreateSiteRequest(
        @Size(max = 80) String siteCode,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 500) String address,
        @Size(max = 100) String siteType,
        @Size(max = 60) String supervisionWorkMode,
        LocalDate startDate,
        LocalDate endDate
) {
}
