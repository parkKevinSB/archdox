package com.archdox.cloud.supervisionledger.dto;

import com.archdox.cloud.supervisionledger.domain.SiteSupervisionEntryStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record SiteSupervisionEntryResponse(
        Long id,
        Long officeId,
        Long projectId,
        Long siteId,
        LocalDate entryDate,
        String floorArea,
        String tradeCode,
        String tradeName,
        String processCode,
        String processName,
        String inspectionItemCode,
        String inspectionItemName,
        String supervisionContent,
        String resultStatus,
        String issueText,
        String actionResult,
        List<Long> photoIds,
        SiteSupervisionEntryStatus status,
        String sourceType,
        Long sourceReportId,
        int sourceReportRevision,
        String sourceStepCode,
        int sourceStepClientRevision,
        String sourceEntryKey,
        String catalogCode,
        Integer catalogVersion,
        OffsetDateTime updatedAt
) {
}
