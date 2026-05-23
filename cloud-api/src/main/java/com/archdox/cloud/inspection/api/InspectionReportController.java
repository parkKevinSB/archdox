package com.archdox.cloud.inspection.api;

import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.inspection.application.InspectionReportService;
import com.archdox.cloud.inspection.application.ReportWorkflowDefinitionService;
import com.archdox.cloud.inspection.domain.InspectionReportStatus;
import com.archdox.cloud.inspection.dto.CreateInspectionReportRequest;
import com.archdox.cloud.inspection.dto.InspectionReportResponse;
import com.archdox.cloud.inspection.dto.InspectionStepResponse;
import com.archdox.cloud.inspection.dto.ReportWorkflowDefinitionResponse;
import com.archdox.cloud.inspection.dto.ReportSubmitValidationResponse;
import com.archdox.cloud.inspection.dto.SaveInspectionStepRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inspection-reports")
public class InspectionReportController {
    private final InspectionReportService inspectionReportService;
    private final ReportWorkflowDefinitionService reportWorkflowDefinitionService;

    public InspectionReportController(
            InspectionReportService inspectionReportService,
            ReportWorkflowDefinitionService reportWorkflowDefinitionService
    ) {
        this.inspectionReportService = inspectionReportService;
        this.reportWorkflowDefinitionService = reportWorkflowDefinitionService;
    }

    @GetMapping
    public List<InspectionReportResponse> list(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) InspectionReportStatus status,
            Authentication authentication
    ) {
        return inspectionReportService.list(projectId, status, (UserPrincipal) authentication.getPrincipal());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InspectionReportResponse create(
            @Valid @RequestBody CreateInspectionReportRequest request,
            Authentication authentication
    ) {
        return inspectionReportService.create(request, (UserPrincipal) authentication.getPrincipal());
    }

    @GetMapping("/{reportId}")
    public InspectionReportResponse get(@PathVariable Long reportId, Authentication authentication) {
        return inspectionReportService.get(reportId, (UserPrincipal) authentication.getPrincipal());
    }

    @GetMapping("/{reportId}/steps")
    public List<InspectionStepResponse> listSteps(@PathVariable Long reportId) {
        return inspectionReportService.listSteps(reportId);
    }

    @GetMapping("/{reportId}/workflow-definition")
    public ReportWorkflowDefinitionResponse workflowDefinition(@PathVariable Long reportId) {
        return reportWorkflowDefinitionService.resolve(reportId);
    }

    @GetMapping("/{reportId}/submit-validation")
    public ReportSubmitValidationResponse validateSubmit(@PathVariable Long reportId, Authentication authentication) {
        return inspectionReportService.validateSubmit(reportId, (UserPrincipal) authentication.getPrincipal());
    }

    @PutMapping("/{reportId}/steps/{stepCode}")
    public InspectionStepResponse saveStep(
            @PathVariable Long reportId,
            @PathVariable String stepCode,
            @Valid @RequestBody SaveInspectionStepRequest request,
            Authentication authentication
    ) {
        return inspectionReportService.saveStep(
                reportId,
                stepCode,
                request,
                (UserPrincipal) authentication.getPrincipal());
    }

    @PostMapping("/{reportId}/submit")
    public InspectionReportResponse submit(@PathVariable Long reportId, Authentication authentication) {
        return inspectionReportService.submit(reportId, (UserPrincipal) authentication.getPrincipal());
    }

    @PostMapping("/{reportId}/reopen")
    public InspectionReportResponse reopenForEdit(@PathVariable Long reportId, Authentication authentication) {
        return inspectionReportService.reopenForEdit(reportId, (UserPrincipal) authentication.getPrincipal());
    }

    @PostMapping("/{reportId}/cancel")
    public InspectionReportResponse cancel(@PathVariable Long reportId, Authentication authentication) {
        return inspectionReportService.cancel(reportId, (UserPrincipal) authentication.getPrincipal());
    }
}
