package com.archdox.cloud.platformadmin.dto;

import com.archdox.shared.OfficeStatus;
import com.archdox.shared.OfficeType;

public record PlatformOfficeOpsResponse(
        Long id,
        String officeCode,
        String displayName,
        OfficeType type,
        String planCode,
        OfficeStatus status
) {
}
