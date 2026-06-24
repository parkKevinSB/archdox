package com.archdox.cloud.platformops.dto;

import java.math.BigDecimal;

public record UpdatePlatformOpsControlProfileRequest(
        String status,
        String severity,
        BigDecimal iWeight,
        String notes
) {
}
