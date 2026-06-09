package com.archdox.cloud.legal.dto;

import java.time.LocalDate;
import java.util.Map;

public record CreateLegalDomainBindingRequest(
        String bindingScope,
        String bindingKey,
        Long actId,
        Long articleId,
        String reportType,
        String catalogCode,
        Integer catalogVersion,
        String checklistItemCode,
        String relevance,
        String status,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String notes,
        Map<String, Object> metadataJson
) {
}
