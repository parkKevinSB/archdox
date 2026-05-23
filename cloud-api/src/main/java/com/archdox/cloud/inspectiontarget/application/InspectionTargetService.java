package com.archdox.cloud.inspectiontarget.application;

import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.global.api.NotFoundException;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.inspection.application.InspectionReportService;
import com.archdox.cloud.inspectiontarget.domain.InspectionReportTarget;
import com.archdox.cloud.inspectiontarget.domain.InspectionReportTargetRole;
import com.archdox.cloud.inspectiontarget.domain.InspectionTarget;
import com.archdox.cloud.inspectiontarget.dto.AttachInspectionReportTargetRequest;
import com.archdox.cloud.inspectiontarget.dto.CreateInspectionTargetRequest;
import com.archdox.cloud.inspectiontarget.dto.InspectionReportTargetResponse;
import com.archdox.cloud.inspectiontarget.dto.InspectionTargetResponse;
import com.archdox.cloud.inspectiontarget.infra.InspectionReportTargetRepository;
import com.archdox.cloud.inspectiontarget.infra.InspectionTargetRepository;
import com.archdox.cloud.office.application.OfficeContext;
import com.archdox.cloud.office.application.OfficePermissionService;
import com.archdox.cloud.site.application.SiteService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InspectionTargetService {
    private static final Set<String> SUPPORTED_TARGET_TYPES = Set.of(
            "BUILDING",
            "FACILITY",
            "FLOOR",
            "ROOM",
            "ZONE",
            "STRUCTURAL_ELEMENT",
            "EQUIPMENT",
            "COMPONENT",
            "MATERIAL",
            "WORK_AREA",
            "OTHER");

    private final InspectionTargetRepository targetRepository;
    private final InspectionReportTargetRepository reportTargetRepository;
    private final SiteService siteService;
    private final InspectionReportService reportService;
    private final OfficePermissionService permissionService;

    public InspectionTargetService(
            InspectionTargetRepository targetRepository,
            InspectionReportTargetRepository reportTargetRepository,
            SiteService siteService,
            InspectionReportService reportService,
            OfficePermissionService permissionService
    ) {
        this.targetRepository = targetRepository;
        this.reportTargetRepository = reportTargetRepository;
        this.siteService = siteService;
        this.reportService = reportService;
        this.permissionService = permissionService;
    }

    @Transactional(readOnly = true)
    public List<InspectionTargetResponse> list(Long projectId, Long siteId) {
        siteService.requireSiteForProject(siteId, projectId);
        var officeId = OfficeContext.requireCurrentOfficeId();
        return targetRepository.findByOfficeIdAndSiteIdOrderById(officeId, siteId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public InspectionTargetResponse create(
            Long projectId,
            Long siteId,
            CreateInspectionTargetRequest request,
            UserPrincipal principal
    ) {
        var site = siteService.requireSiteForProject(siteId, projectId);
        permissionService.requireProjectStructureManager(principal.userId(), site.officeId(), projectId);
        if (request.parentTargetId() != null) {
            var parent = requireTarget(request.parentTargetId());
            if (!parent.siteId().equals(site.id())) {
                throw new BadRequestException("parentTargetId must belong to the same site");
            }
        }
        var now = OffsetDateTime.now();
        var target = new InspectionTarget(
                site.officeId(),
                projectId,
                siteId,
                request.parentTargetId(),
                normalizeTargetType(request.targetType()),
                trimToNull(request.code()),
                request.name().trim(),
                trimToNull(request.address()),
                request.metadata() == null ? Map.of() : request.metadata(),
                principal.userId(),
                now);
        return toResponse(targetRepository.save(target));
    }

    @Transactional
    public InspectionTargetResponse archive(Long projectId, Long siteId, Long targetId, UserPrincipal principal) {
        siteService.requireSiteForProject(siteId, projectId);
        var target = requireTarget(targetId);
        if (!target.siteId().equals(siteId)) {
            throw new NotFoundException("Inspection target not found");
        }
        permissionService.requireProjectStructureManager(principal.userId(), target.officeId(), projectId);
        target.archive(OffsetDateTime.now());
        return toResponse(target);
    }

    @Transactional(readOnly = true)
    public List<InspectionReportTargetResponse> listReportTargets(Long reportId) {
        var report = reportService.requireReport(reportId);
        return reportTargetRepository.findByOfficeIdAndReportIdOrderByRoleAscIdAsc(report.officeId(), report.id()).stream()
                .map(this::toReportTargetResponse)
                .toList();
    }

    @Transactional
    public InspectionReportTargetResponse attachReportTarget(
            Long reportId,
            AttachInspectionReportTargetRequest request,
            UserPrincipal principal
    ) {
        var report = reportService.requireReport(reportId);
        permissionService.requireReportWriter(principal.userId(), report.officeId(), report.projectId(), report.id());
        var target = requireTarget(request.targetId());
        if (!target.officeId().equals(report.officeId())
                || !target.projectId().equals(report.projectId())
                || (report.siteId() != null && !target.siteId().equals(report.siteId()))) {
            throw new BadRequestException("Target does not belong to the report context");
        }
        var role = request.normalizedRole();
        var existing = reportTargetRepository.findByOfficeIdAndReportIdAndTargetIdAndRole(
                report.officeId(),
                report.id(),
                target.id(),
                role);
        if (existing.isPresent()) {
            return toReportTargetResponse(existing.get());
        }
        var snapshot = targetSnapshot(target);
        var reportTarget = reportTargetRepository.save(new InspectionReportTarget(
                report.officeId(),
                report.id(),
                target.id(),
                role,
                snapshot,
                OffsetDateTime.now()));
        return toReportTargetResponse(reportTarget);
    }

    public InspectionTarget requireTarget(Long targetId) {
        var officeId = OfficeContext.requireCurrentOfficeId();
        return targetRepository.findByIdAndOfficeId(targetId, officeId)
                .orElseThrow(() -> new NotFoundException("Inspection target not found"));
    }

    public Map<String, Object> targetSnapshot(InspectionTarget target) {
        return Map.of(
                "id", target.id(),
                "projectId", target.projectId(),
                "siteId", target.siteId(),
                "parentTargetId", target.parentTargetId() == null ? "" : target.parentTargetId(),
                "targetType", target.targetType(),
                "code", target.code() == null ? "" : target.code(),
                "name", target.name(),
                "address", target.address() == null ? "" : target.address(),
                "metadata", target.metadataJson() == null ? Map.of() : target.metadataJson());
    }

    private InspectionTargetResponse toResponse(InspectionTarget target) {
        return new InspectionTargetResponse(
                target.id(),
                target.officeId(),
                target.projectId(),
                target.siteId(),
                target.parentTargetId(),
                target.targetType(),
                target.code(),
                target.name(),
                target.address(),
                target.metadataJson(),
                target.status());
    }

    private InspectionReportTargetResponse toReportTargetResponse(InspectionReportTarget reportTarget) {
        return new InspectionReportTargetResponse(
                reportTarget.id(),
                reportTarget.officeId(),
                reportTarget.reportId(),
                reportTarget.targetId(),
                reportTarget.role(),
                reportTarget.snapshotJson(),
                reportTarget.createdAt());
    }

    private String normalizeTargetType(String value) {
        var normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_TARGET_TYPES.contains(normalized)) {
            throw new BadRequestException("Unsupported targetType: " + value);
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
