package com.archdox.cloud.legal.dto;

import java.time.LocalDate;
import java.util.List;

public record LegalLawSearchResponse(
        List<LegalLawSearchResultResponse> items,
        int count,
        String query,
        String actCode,
        String actName,
        String articleNo,
        LocalDate effectiveDate,
        int limit
) {
}
