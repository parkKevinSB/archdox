package com.archdox.cloud.inspectiontarget.api;

import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.inspectiontarget.application.InspectionTargetService;
import com.archdox.cloud.inspectiontarget.dto.AttachInspectionReportTargetRequest;
import com.archdox.cloud.inspectiontarget.dto.InspectionReportTargetResponse;
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
@RequestMapping("/api/v1/inspection-reports/{reportId}/targets")
public class InspectionReportTargetController {
    private final InspectionTargetService targetService;

    public InspectionReportTargetController(InspectionTargetService targetService) {
        this.targetService = targetService;
    }

    @GetMapping
    public List<InspectionReportTargetResponse> list(@PathVariable Long reportId) {
        return targetService.listReportTargets(reportId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InspectionReportTargetResponse attach(
            @PathVariable Long reportId,
            @Valid @RequestBody AttachInspectionReportTargetRequest request,
            Authentication authentication
    ) {
        return targetService.attachReportTarget(
                reportId,
                request,
                (UserPrincipal) authentication.getPrincipal());
    }
}
