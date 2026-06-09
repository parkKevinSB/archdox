package com.archdox.cloud.legal.dto;

import java.util.List;

public record LegalDomainBindingAutoGenerateResponse(
        String mode,
        String catalogCode,
        Integer catalogVersion,
        Integer catalogItemCount,
        Integer createdCount,
        Integer skippedCount,
        Integer reportTypeCreatedCount,
        Integer reportTypeSkippedCount,
        String primaryReference,
        String supportingReference,
        List<LegalDomainBindingResponse> createdSamples,
        String message
) {
}
