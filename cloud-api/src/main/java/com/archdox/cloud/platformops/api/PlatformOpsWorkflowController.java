package com.archdox.cloud.platformops.api;

import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.platformops.application.PlatformOpsDiagnosisService;
import com.archdox.cloud.platformops.application.PlatformOpsQueryService;
import com.archdox.cloud.platformops.domain.PlatformOpsFindingSeverity;
import com.archdox.cloud.platformops.domain.PlatformOpsFindingSource;
import com.archdox.cloud.platformops.domain.PlatformOpsIncidentStatus;
import com.archdox.cloud.platformops.domain.PlatformOpsRunStatus;
import com.archdox.cloud.platformops.domain.PlatformOpsRunTriggerType;
import com.archdox.cloud.platformops.dto.PlatformOpsFindingResponse;
import com.archdox.cloud.platformops.dto.PlatformOpsIncidentResponse;
import com.archdox.cloud.platformops.dto.PlatformOpsRunResponse;
import com.archdox.cloud.platformops.event.PlatformOpsDiagnosisRequested;
import com.archdox.cloud.platformops.flow.PlatformOpsDiagnosisFlowFactory;
import com.archdox.cloud.platformops.flow.PlatformOpsWorker;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform-admin/ops")
public class PlatformOpsWorkflowController {
    private final PlatformOpsQueryService service;
    private final PlatformOpsDiagnosisService diagnosisService;
    private final PlatformOpsDiagnosisFlowFactory diagnosisFlowFactory;
    private final PlatformOpsWorker worker;

    public PlatformOpsWorkflowController(
            PlatformOpsQueryService service,
            PlatformOpsDiagnosisService diagnosisService,
            PlatformOpsDiagnosisFlowFactory diagnosisFlowFactory,
            PlatformOpsWorker worker
    ) {
        this.service = service;
        this.diagnosisService = diagnosisService;
        this.diagnosisFlowFactory = diagnosisFlowFactory;
        this.worker = worker;
    }

    @GetMapping("/ops-runs")
    public List<PlatformOpsRunResponse> runs(
            Authentication authentication,
            @RequestParam(required = false) PlatformOpsRunStatus status,
            @RequestParam(required = false) PlatformOpsRunTriggerType triggerType,
            @RequestParam(required = false) Integer limit
    ) {
        return service.runs(principal(authentication), status, triggerType, limit);
    }

    @GetMapping("/incidents")
    public List<PlatformOpsIncidentResponse> incidents(
            Authentication authentication,
            @RequestParam(required = false) Long officeId,
            @RequestParam(required = false) PlatformOpsIncidentStatus status,
            @RequestParam(required = false) PlatformOpsFindingSeverity severity,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer limit
    ) {
        return service.incidents(principal(authentication), officeId, status, severity, category, limit);
    }

    @GetMapping("/findings")
    public List<PlatformOpsFindingResponse> findings(
            Authentication authentication,
            @RequestParam(required = false) Long officeId,
            @RequestParam(required = false) Long runId,
            @RequestParam(required = false) Long incidentId,
            @RequestParam(required = false) PlatformOpsFindingSeverity severity,
            @RequestParam(required = false) PlatformOpsFindingSource source,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer limit
    ) {
        return service.findings(principal(authentication), officeId, runId, incidentId, severity, source, category, limit);
    }

    @PostMapping("/incidents/{incidentId}/diagnose")
    public PlatformOpsRunResponse diagnoseIncident(
            Authentication authentication,
            @PathVariable Long incidentId
    ) {
        var principal = principal(authentication);
        var run = diagnosisService.requestIncidentDiagnosis(principal, incidentId);
        worker.submit(diagnosisFlowFactory.create(new PlatformOpsDiagnosisRequested(
                run.id(),
                incidentId,
                principal.userId())));
        return PlatformOpsRunResponse.from(run);
    }

    private UserPrincipal principal(Authentication authentication) {
        return (UserPrincipal) authentication.getPrincipal();
    }
}
