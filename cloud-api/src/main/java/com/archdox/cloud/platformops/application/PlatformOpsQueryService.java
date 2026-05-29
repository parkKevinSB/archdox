package com.archdox.cloud.platformops.application;

import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.platformadmin.application.PlatformAdminService;
import com.archdox.cloud.platformops.domain.PlatformOpsFinding;
import com.archdox.cloud.platformops.domain.PlatformOpsFindingSeverity;
import com.archdox.cloud.platformops.domain.PlatformOpsFindingSource;
import com.archdox.cloud.platformops.domain.PlatformOpsIncident;
import com.archdox.cloud.platformops.domain.PlatformOpsIncidentStatus;
import com.archdox.cloud.platformops.domain.PlatformOpsRun;
import com.archdox.cloud.platformops.domain.PlatformOpsRunStatus;
import com.archdox.cloud.platformops.domain.PlatformOpsRunTriggerType;
import com.archdox.cloud.platformops.dto.PlatformOpsFindingResponse;
import com.archdox.cloud.platformops.dto.PlatformOpsIncidentResponse;
import com.archdox.cloud.platformops.dto.PlatformOpsRunResponse;
import com.archdox.cloud.platformops.infra.PlatformOpsFindingRepository;
import com.archdox.cloud.platformops.infra.PlatformOpsIncidentRepository;
import com.archdox.cloud.platformops.infra.PlatformOpsRunRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformOpsQueryService {
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final PlatformAdminService platformAdminService;
    private final PlatformOpsRunRepository runRepository;
    private final PlatformOpsIncidentRepository incidentRepository;
    private final PlatformOpsFindingRepository findingRepository;

    public PlatformOpsQueryService(
            PlatformAdminService platformAdminService,
            PlatformOpsRunRepository runRepository,
            PlatformOpsIncidentRepository incidentRepository,
            PlatformOpsFindingRepository findingRepository
    ) {
        this.platformAdminService = platformAdminService;
        this.runRepository = runRepository;
        this.incidentRepository = incidentRepository;
        this.findingRepository = findingRepository;
    }

    @Transactional(readOnly = true)
    public List<PlatformOpsRunResponse> runs(
            UserPrincipal principal,
            PlatformOpsRunStatus status,
            PlatformOpsRunTriggerType triggerType,
            Integer limit
    ) {
        platformAdminService.requirePlatformAdmin(principal);
        return runRepository.search(status, triggerType, PageRequest.of(0, normalizeLimit(limit)))
                .stream()
                .map(this::toRun)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PlatformOpsIncidentResponse> incidents(
            UserPrincipal principal,
            Long officeId,
            PlatformOpsIncidentStatus status,
            PlatformOpsFindingSeverity severity,
            String category,
            Integer limit
    ) {
        platformAdminService.requirePlatformAdmin(principal);
        return incidentRepository.search(officeId, status, severity, blankToNull(category), PageRequest.of(0, normalizeLimit(limit)))
                .stream()
                .map(this::toIncident)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PlatformOpsFindingResponse> findings(
            UserPrincipal principal,
            Long officeId,
            Long runId,
            Long incidentId,
            PlatformOpsFindingSeverity severity,
            PlatformOpsFindingSource source,
            String category,
            Integer limit
    ) {
        platformAdminService.requirePlatformAdmin(principal);
        return findingRepository.search(
                        officeId,
                        runId,
                        incidentId,
                        severity,
                        source,
                        blankToNull(category),
                        PageRequest.of(0, normalizeLimit(limit)))
                .stream()
                .map(this::toFinding)
                .toList();
    }

    private PlatformOpsRunResponse toRun(PlatformOpsRun run) {
        return PlatformOpsRunResponse.from(run);
    }

    private PlatformOpsIncidentResponse toIncident(PlatformOpsIncident incident) {
        return new PlatformOpsIncidentResponse(
                incident.id(),
                incident.status(),
                incident.severity(),
                incident.category(),
                incident.title(),
                incident.summary(),
                incident.officeId(),
                incident.primaryResourceType(),
                incident.primaryResourceId(),
                incident.firstSeenAt(),
                incident.lastSeenAt(),
                incident.resolvedAt(),
                incident.createdByRunId());
    }

    private PlatformOpsFindingResponse toFinding(PlatformOpsFinding finding) {
        return new PlatformOpsFindingResponse(
                finding.id(),
                finding.incidentId(),
                finding.runId(),
                finding.officeId(),
                finding.severity(),
                finding.source(),
                finding.code(),
                finding.category(),
                finding.title(),
                finding.message(),
                finding.resourceType(),
                finding.resourceId(),
                finding.workflowType(),
                finding.workflowKey(),
                finding.evidenceJson(),
                finding.recommendation(),
                finding.createdAt());
    }

    private int normalizeLimit(Integer limit) {
        return Math.max(1, Math.min(limit == null ? DEFAULT_LIMIT : limit, MAX_LIMIT));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
