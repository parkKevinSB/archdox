package com.archdox.cloud.assignment.api;

import com.archdox.cloud.assignment.application.AssignmentService;
import com.archdox.cloud.assignment.dto.ProjectAssignmentResponse;
import com.archdox.cloud.assignment.dto.ReportAssignmentResponse;
import com.archdox.cloud.assignment.dto.UpsertProjectAssignmentRequest;
import com.archdox.cloud.assignment.dto.UpsertReportAssignmentRequest;
import com.archdox.cloud.global.security.UserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AssignmentController {
    private final AssignmentService assignmentService;

    public AssignmentController(AssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @GetMapping("/projects/{projectId}/assignments")
    public List<ProjectAssignmentResponse> listProjectAssignments(
            @PathVariable Long projectId,
            Authentication authentication
    ) {
        return assignmentService.listProjectAssignments(projectId, principal(authentication));
    }

    @PutMapping("/projects/{projectId}/assignments")
    @ResponseStatus(HttpStatus.OK)
    public ProjectAssignmentResponse upsertProjectAssignment(
            @PathVariable Long projectId,
            @Valid @RequestBody UpsertProjectAssignmentRequest request,
            Authentication authentication
    ) {
        return assignmentService.upsertProjectAssignment(projectId, request, principal(authentication));
    }

    @DeleteMapping("/projects/{projectId}/assignments/{userId}")
    public ProjectAssignmentResponse removeProjectAssignment(
            @PathVariable Long projectId,
            @PathVariable Long userId,
            Authentication authentication
    ) {
        return assignmentService.removeProjectAssignment(projectId, userId, principal(authentication));
    }

    @GetMapping("/inspection-reports/{reportId}/assignments")
    public List<ReportAssignmentResponse> listReportAssignments(
            @PathVariable Long reportId,
            Authentication authentication
    ) {
        return assignmentService.listReportAssignments(reportId, principal(authentication));
    }

    @PutMapping("/inspection-reports/{reportId}/assignments")
    @ResponseStatus(HttpStatus.OK)
    public ReportAssignmentResponse upsertReportAssignment(
            @PathVariable Long reportId,
            @Valid @RequestBody UpsertReportAssignmentRequest request,
            Authentication authentication
    ) {
        return assignmentService.upsertReportAssignment(reportId, request, principal(authentication));
    }

    @DeleteMapping("/inspection-reports/{reportId}/assignments/{userId}")
    public ReportAssignmentResponse removeReportAssignment(
            @PathVariable Long reportId,
            @PathVariable Long userId,
            Authentication authentication
    ) {
        return assignmentService.removeReportAssignment(reportId, userId, principal(authentication));
    }

    private UserPrincipal principal(Authentication authentication) {
        return (UserPrincipal) authentication.getPrincipal();
    }
}
