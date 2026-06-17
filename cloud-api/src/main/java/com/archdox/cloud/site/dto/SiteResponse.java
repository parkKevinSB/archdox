package com.archdox.cloud.site.dto;

import com.archdox.cloud.site.domain.SiteStatus;
import com.archdox.cloud.site.domain.SupervisionWorkMode;
import java.time.LocalDate;

public record SiteResponse(
        Long id,
        Long officeId,
        Long projectId,
        String siteCode,
        String name,
        String address,
        String siteType,
        SupervisionWorkMode supervisionWorkMode,
        LocalDate startDate,
        LocalDate endDate,
        SiteStatus status
) {
}
