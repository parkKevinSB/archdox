package com.archdox.cloud.site.application;

import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.global.api.NotFoundException;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.office.application.OfficeContext;
import com.archdox.cloud.office.application.OfficePermissionService;
import com.archdox.cloud.project.application.ProjectService;
import com.archdox.cloud.site.domain.Site;
import com.archdox.cloud.site.dto.CreateSiteRequest;
import com.archdox.cloud.site.dto.SiteResponse;
import com.archdox.cloud.site.domain.SupervisionWorkMode;
import com.archdox.cloud.site.infra.SiteRepository;
import com.archdox.cloud.workspace.application.WorkspaceCascadeDeletionService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SiteService {
    private static final Set<String> SUPPORTED_SITE_TYPES = Set.of(
            "CONSTRUCTION_SITE",
            "BUILDING",
            "FACILITY",
            "PLANT",
            "CAMPUS",
            "WORK_AREA",
            "OTHER");

    private final SiteRepository siteRepository;
    private final ProjectService projectService;
    private final OfficePermissionService permissionService;
    private final WorkspaceCascadeDeletionService deletionService;

    public SiteService(
            SiteRepository siteRepository,
            ProjectService projectService,
            OfficePermissionService permissionService,
            WorkspaceCascadeDeletionService deletionService
    ) {
        this.siteRepository = siteRepository;
        this.projectService = projectService;
        this.permissionService = permissionService;
        this.deletionService = deletionService;
    }

    @Transactional(readOnly = true)
    public List<SiteResponse> list(Long projectId) {
        projectService.requireProject(projectId);
        var officeId = OfficeContext.requireCurrentOfficeId();
        return siteRepository.findByOfficeIdAndProjectIdOrderByUpdatedAtDesc(officeId, projectId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public SiteResponse create(Long projectId, CreateSiteRequest request, UserPrincipal principal) {
        projectService.requireProject(projectId);
        var officeId = OfficeContext.requireCurrentOfficeId();
        permissionService.requireProjectStructureManager(principal.userId(), officeId, projectId);
        var now = OffsetDateTime.now();
        var site = new Site(
                officeId,
                projectId,
                trimToNull(request.siteCode()),
                request.name().trim(),
                trimToNull(request.address()),
                normalizeSupportedCode(request.siteType(), SUPPORTED_SITE_TYPES, "siteType"),
                normalizeSupervisionWorkMode(request.supervisionWorkMode()),
                request.startDate(),
                request.endDate(),
                principal.userId(),
                now);
        return toResponse(siteRepository.save(site));
    }

    @Transactional(readOnly = true)
    public SiteResponse get(Long projectId, Long siteId) {
        return toResponse(requireSiteForProject(siteId, projectId));
    }

    @Transactional
    public SiteResponse archive(Long projectId, Long siteId, UserPrincipal principal) {
        var site = requireSiteForProject(siteId, projectId);
        permissionService.requireProjectStructureManager(principal.userId(), site.officeId(), projectId);
        site.archive(OffsetDateTime.now());
        return toResponse(site);
    }

    @Transactional
    public void delete(Long projectId, Long siteId, UserPrincipal principal) {
        var site = requireSiteForProject(siteId, projectId);
        permissionService.requireProjectStructureManager(principal.userId(), site.officeId(), projectId);
        deletionService.deleteSite(site.officeId(), projectId, site.id());
    }

    public Site requireSite(Long siteId) {
        var officeId = OfficeContext.requireCurrentOfficeId();
        return siteRepository.findByIdAndOfficeId(siteId, officeId)
                .orElseThrow(() -> new NotFoundException("Site not found"));
    }

    public Site requireSiteForProject(Long siteId, Long projectId) {
        var site = requireSite(siteId);
        if (!site.projectId().equals(projectId)) {
            throw new NotFoundException("Site not found");
        }
        return site;
    }

    private SiteResponse toResponse(Site site) {
        return new SiteResponse(
                site.id(),
                site.officeId(),
                site.projectId(),
                site.siteCode(),
                site.name(),
                site.address(),
                site.siteType(),
                site.supervisionWorkMode(),
                site.startDate(),
                site.endDate(),
                site.status());
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String normalizeSupportedCode(String value, Set<String> supportedValues, String fieldName) {
        var trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        var normalized = trimmed.toUpperCase(Locale.ROOT);
        if (!supportedValues.contains(normalized)) {
            throw new BadRequestException("Unsupported " + fieldName + ": " + value);
        }
        return normalized;
    }

    private SupervisionWorkMode normalizeSupervisionWorkMode(String value) {
        try {
            return SupervisionWorkMode.normalize(value);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException(
                    "Unsupported supervisionWorkMode: " + value);
        }
    }
}
