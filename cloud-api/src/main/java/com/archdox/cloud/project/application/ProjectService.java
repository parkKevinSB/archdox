package com.archdox.cloud.project.application;

import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.global.api.NotFoundException;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.office.application.OfficeContext;
import com.archdox.cloud.office.application.OfficePermissionService;
import com.archdox.cloud.project.domain.Project;
import com.archdox.cloud.project.dto.CreateProjectRequest;
import com.archdox.cloud.project.dto.ProjectResponse;
import com.archdox.cloud.project.infra.ProjectRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {
    private static final Set<String> SUPPORTED_BUSINESS_TYPES = Set.of(
            "CONSTRUCTION_SUPERVISION",
            "BUILDING_SAFETY_INSPECTION",
            "FACILITY_INSPECTION",
            "ASBESTOS_SUPERVISION",
            "MAINTENANCE_INSPECTION",
            "OTHER");

    private final ProjectRepository projectRepository;
    private final OfficePermissionService permissionService;

    public ProjectService(ProjectRepository projectRepository, OfficePermissionService permissionService) {
        this.projectRepository = projectRepository;
        this.permissionService = permissionService;
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> list() {
        var officeId = OfficeContext.requireCurrentOfficeId();
        return projectRepository.findByOfficeIdOrderByUpdatedAtDesc(officeId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ProjectResponse create(CreateProjectRequest request, UserPrincipal principal) {
        var officeId = OfficeContext.requireCurrentOfficeId();
        permissionService.requireProjectManager(principal.userId(), officeId);
        var now = OffsetDateTime.now();
        var project = new Project(
                officeId,
                request.name().trim(),
                trimToNull(request.address()),
                normalizeSupportedCode(request.buildingType(), SUPPORTED_BUSINESS_TYPES, "buildingType"),
                request.startDate(),
                request.endDate(),
                principal.userId(),
                now);
        return toResponse(projectRepository.save(project));
    }

    @Transactional(readOnly = true)
    public ProjectResponse get(Long projectId) {
        return toResponse(requireProject(projectId));
    }

    @Transactional
    public ProjectResponse archive(Long projectId, UserPrincipal principal) {
        var project = requireProject(projectId);
        permissionService.requireProjectManager(principal.userId(), project.officeId());
        project.archive(OffsetDateTime.now());
        return toResponse(project);
    }

    public Project requireProject(Long projectId) {
        var officeId = OfficeContext.requireCurrentOfficeId();
        return projectRepository.findByIdAndOfficeId(projectId, officeId)
                .orElseThrow(() -> new NotFoundException("Project not found"));
    }

    private ProjectResponse toResponse(Project project) {
        return new ProjectResponse(
                project.id(),
                project.officeId(),
                project.name(),
                project.address(),
                project.buildingType(),
                project.startDate(),
                project.endDate(),
                project.status());
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
}
