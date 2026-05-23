package com.archdox.cloud.inspectiontarget.api;

import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.inspectiontarget.application.InspectionTargetService;
import com.archdox.cloud.inspectiontarget.dto.CreateInspectionTargetRequest;
import com.archdox.cloud.inspectiontarget.dto.InspectionTargetResponse;
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
@RequestMapping("/api/v1/projects/{projectId}/sites/{siteId}/targets")
public class InspectionTargetController {
    private final InspectionTargetService targetService;

    public InspectionTargetController(InspectionTargetService targetService) {
        this.targetService = targetService;
    }

    @GetMapping
    public List<InspectionTargetResponse> list(@PathVariable Long projectId, @PathVariable Long siteId) {
        return targetService.list(projectId, siteId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InspectionTargetResponse create(
            @PathVariable Long projectId,
            @PathVariable Long siteId,
            @Valid @RequestBody CreateInspectionTargetRequest request,
            Authentication authentication
    ) {
        return targetService.create(projectId, siteId, request, (UserPrincipal) authentication.getPrincipal());
    }

    @PostMapping("/{targetId}/archive")
    public InspectionTargetResponse archive(
            @PathVariable Long projectId,
            @PathVariable Long siteId,
            @PathVariable Long targetId,
            Authentication authentication
    ) {
        return targetService.archive(projectId, siteId, targetId, (UserPrincipal) authentication.getPrincipal());
    }
}
