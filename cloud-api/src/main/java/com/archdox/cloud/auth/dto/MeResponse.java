package com.archdox.cloud.auth.dto;

import java.util.List;

public record MeResponse(
        Long id,
        String email,
        String name,
        List<OfficeSummaryResponse> offices
) {
}
