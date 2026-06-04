package com.archdox.cloud.legal.dto;

import com.archdox.cloud.legal.domain.LegalChangeSetStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record LegalChangeSetResponse(
        Long id,
        Long actId,
        Long syncRunId,
        Long previousVersionId,
        Long newVersionId,
        LegalChangeSetStatus status,
        LocalDate effectiveDate,
        OffsetDateTime detectedAt,
        String summary
) {
}
