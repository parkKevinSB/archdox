package com.archdox.cloud.project.api;

import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.project.application.ProjectService;
import com.archdox.cloud.project.dto.CreateProjectRequest;
import com.archdox.cloud.project.dto.ProjectResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
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
    public List<ProjectResponse> list() {
        return projectService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse create(@Valid @RequestBody CreateProjectRequest request, Authentication authentication) {
        return projectService.create(request, (UserPrincipal) authentication.getPrincipal());
    }

    @GetMapping("/{projectId}")
    public ProjectResponse get(@PathVariable Long projectId) {
        return projectService.get(projectId);
    }

    @PostMapping("/{projectId}/archive")
    public ProjectResponse archive(@PathVariable Long projectId, Authentication authentication) {
        return projectService.archive(projectId, (UserPrincipal) authentication.getPrincipal());
    }
}
