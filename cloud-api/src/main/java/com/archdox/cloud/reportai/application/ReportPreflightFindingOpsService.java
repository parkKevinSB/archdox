package com.archdox.cloud.reportai.application;

import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.platformadmin.application.PlatformAdminService;
import com.archdox.cloud.reportai.domain.ReportPreflightFindingResolutionStatus;
import com.archdox.cloud.reportai.domain.ReportPreflightReviewFinding;
import com.archdox.cloud.reportai.domain.ReportPreflightReviewRun;
import com.archdox.cloud.reportai.dto.PlatformReportPreflightFindingResponse;
import com.archdox.cloud.reportai.infra.ReportPreflightReviewFindingRepository;
import com.archdox.cloud.reportai.infra.ReportPreflightReviewRunRepository;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportPreflightFindingOpsService {
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 500;

    private final PlatformAdminService platformAdminService;
    private final ReportPreflightReviewFindingRepository findingRepository;
    private final ReportPreflightReviewRunRepository runRepository;

    public ReportPreflightFindingOpsService(
            PlatformAdminService platformAdminService,
            ReportPreflightReviewFindingRepository findingRepository,
            ReportPreflightReviewRunRepository runRepository
    ) {
        this.platformAdminService = platformAdminService;
        this.findingRepository = findingRepository;
        this.runRepository = runRepository;
    }

    @Transactional(readOnly = true)
    public List<PlatformReportPreflightFindingResponse> platformFindings(
            UserPrincipal principal,
            Long officeId,
            String severity,
            String resolutionStatus,
            String source,
            Integer limit
    ) {
        platformAdminService.requirePlatformAdmin(principal);
        var findings = findingRepository.searchPlatformFindings(
                officeId,
                normalizedText(severity),
                status(resolutionStatus),
                normalizedText(source),
                PageRequest.of(0, boundedLimit(limit)));
        var runsById = runRepository.findAllById(findings.stream()
                        .map(ReportPreflightReviewFinding::reviewRunId)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(ReportPreflightReviewRun::id, Function.identity()));
        return findings.stream()
                .map(finding -> toResponse(finding, runsById.get(finding.reviewRunId())))
                .toList();
    }

    private PlatformReportPreflightFindingResponse toResponse(
            ReportPreflightReviewFinding finding,
            ReportPreflightReviewRun run
    ) {
        return new PlatformReportPreflightFindingResponse(
                finding.id(),
                finding.officeId(),
                finding.reportId(),
                finding.reviewRunId(),
                run == null ? null : run.status().name(),
                run == null ? null : run.terminalReason(),
                finding.source(),
                finding.code(),
                finding.severity(),
                finding.location(),
                finding.message(),
                finding.evidence(),
                finding.attributesJson() == null ? Map.of() : finding.attributesJson(),
                finding.resolutionStatus().name(),
                finding.resolutionNote(),
                finding.resolvedBy(),
                finding.resolvedAt(),
                finding.createdAt());
    }

    private ReportPreflightFindingResolutionStatus status(String value) {
        var normalized = normalizedText(value);
        if (normalized == null) {
            return null;
        }
        try {
            return ReportPreflightFindingResolutionStatus.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid preflight finding resolution status");
        }
    }

    private String normalizedText(String value) {
        if (value == null || value.isBlank() || "ALL".equalsIgnoreCase(value.trim())) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private int boundedLimit(Integer limit) {
        return Math.max(1, Math.min(limit == null ? DEFAULT_LIMIT : limit, MAX_LIMIT));
    }
}
