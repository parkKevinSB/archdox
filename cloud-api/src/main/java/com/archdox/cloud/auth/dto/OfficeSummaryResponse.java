package com.archdox.cloud.auth.dto;

import com.archdox.shared.MembershipRole;
import com.archdox.shared.OfficeType;

public record OfficeSummaryResponse(
        Long id,
        String officeCode,
        String displayName,
        OfficeType type,
        String planCode,
        MembershipRole role,
        OfficePermissionSummaryResponse permissions
) {
}
