package com.archdox.cloud.office.dto;

import com.archdox.shared.MembershipRole;
import com.archdox.shared.OfficeType;

public record OfficeResponse(
        Long id,
        String officeCode,
        String displayName,
        OfficeType type,
        String planCode,
        MembershipRole role
) {
}
