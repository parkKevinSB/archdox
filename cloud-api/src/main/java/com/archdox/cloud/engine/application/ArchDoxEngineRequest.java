package com.archdox.cloud.engine.application;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

public record ArchDoxEngineRequest(
        ArchDoxEngineRunMode runMode,
        Set<ArchDoxEngineCapability> capabilities,
        LocalDate effectiveDate,
        String reportType,
        Long actorUserId,
        Long officeId,
        Long projectId,
        Long siteId,
        Long reportId,
        Integer reportRevision,
        Map<String, Object> providedContext
) {
    public ArchDoxEngineRequest {
        runMode = runMode == null ? ArchDoxEngineRunMode.SAAS_CONTEXT : runMode;
        capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
        reportType = reportType == null ? "" : reportType.trim();
        providedContext = providedContext == null ? Map.of() : Map.copyOf(providedContext);
    }
}
