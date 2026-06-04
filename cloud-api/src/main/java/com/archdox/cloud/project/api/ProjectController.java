package com.archdox.cloud.project.api;

import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.project.application.ProjectService;
import com.archdox.cloud.project.dto.CreateProjectRequest;
import com.archdox.cloud.project.dto.ProjectResponse;
import com.archdox.cloud.project.dto.UpdateProjectRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {
    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public List<ProjectResponse> list(Authentication authentication) {
        return projectService.list((UserPrincipal) authentication.getPrincipal());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse create(@Valid @RequestBody CreateProjectRequest request, Authentication authentication) {
        return projectService.create(request, (UserPrincipal) authentication.getPrincipal());
    }

    @GetMapping("/{projectId}")
    public ProjectResponse get(@PathVariable Long projectId, Authentication authentication) {
        return projectService.get(projectId, (UserPrincipal) authentication.getPrincipal());
    }

    @PatchMapping("/{projectId}")
    public ProjectResponse update(
            @PathVariable Long projectId,
            @Valid @RequestBody UpdateProjectRequest request,
            Authentication authentication
    ) {
        return projectService.update(projectId, request, (UserPrincipal) authentication.getPrincipal());
    }

    @PostMapping("/{projectId}/archive")
    public ProjectResponse archive(@PathVariable Long projectId, Authentication authentication) {
        return projectService.archive(projectId, (UserPrincipal) authentication.getPrincipal());
    }

    @DeleteMapping("/{projectId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long projectId, Authentication authentication) {
        projectService.delete(projectId, (UserPrincipal) authentication.getPrincipal());
    }
}
