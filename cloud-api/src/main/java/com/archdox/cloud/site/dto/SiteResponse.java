package com.archdox.cloud.site.dto;

import com.archdox.cloud.site.domain.SiteStatus;
import java.time.LocalDate;

public record SiteResponse(
        Long id,
        Long officeId,
        Long projectId,
        String siteCode,
        String name,
        String address,
        String siteType,
        LocalDate startDate,
        LocalDate endDate,
        SiteStatus status
) {
}
