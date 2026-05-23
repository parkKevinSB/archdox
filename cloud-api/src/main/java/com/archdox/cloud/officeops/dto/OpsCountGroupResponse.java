package com.archdox.cloud.officeops.dto;

import java.util.Map;

public record OpsCountGroupResponse(
        long total,
        Map<String, Long> byStatus
) {
}
