package com.archdox.cloud.legal.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;

public record LegalDomainBindingResponse(
        Long id,
        String bindingScope,
        String bindingKey,
        Long actId,
        String actCode,
        String actName,
        String actType,
        Long articleId,
        String articleNo,
        String articleTitle,
        String reportType,
        String reportTypeLabel,
        String catalogCode,
        Integer catalogVersion,
        String catalogName,
        String checklistItemCode,
        String tradeCode,
        String tradeName,
        String processCode,
        String processName,
        String checklistItemName,
        String checklistItemBasis,
        String bindingDisplayName,
        String relevance,
        String status,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String notes,
        Map<String, Object> metadataJson,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
