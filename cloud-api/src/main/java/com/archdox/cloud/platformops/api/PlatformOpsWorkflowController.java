package com.archdox.cloud.platformops.api;

import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.platformops.application.PlatformOpsDiagnosisService;
import com.archdox.cloud.platformops.application.PlatformOpsQueryService;
import com.archdox.cloud.platformops.application.PlatformOpsDailyReportService;
import com.archdox.cloud.platformops.application.PlatformOpsControlProfileService;
import com.archdox.cloud.platformops.domain.PlatformOpsControlProfileStatus;
import com.archdox.cloud.platformops.domain.PlatformOpsFindingSeverity;
import com.archdox.cloud.platformops.domain.PlatformOpsFindingSource;
import com.archdox.cloud.platformops.domain.PlatformOpsIncidentStatus;
import com.archdox.cloud.platformops.domain.PlatformOpsRunStatus;
import com.archdox.cloud.platformops.domain.PlatformOpsRunTriggerType;
import com.archdox.cloud.platformops.dto.CreatePlatformOpsControlProfileRequest;
import com.archdox.cloud.platformops.dto.PlatformOpsControlProfileResponse;
import com.archdox.cloud.platformops.dto.PlatformOpsFindingResponse;
import com.archdox.cloud.platformops.dto.PlatformOpsIncidentResponse;
import com.archdox.cloud.platformops.dto.PlatformOpsDailyReportResponse;
import com.archdox.cloud.platformops.dto.PlatformOpsRunResponse;
import com.archdox.cloud.platformops.dto.UpdatePlatformOpsControlProfileRequest;
import com.archdox.cloud.platformops.event.PlatformOpsDailyReportRequested;
import com.archdox.cloud.platformops.event.PlatformOpsDiagnosisRequested;
import com.archdox.cloud.platformops.flow.PlatformOpsDailyReportFlowFactory;
import com.archdox.cloud.platformops.flow.PlatformOpsDiagnosisFlowFactory;
import com.archdox.cloud.platformops.flow.PlatformOpsWorker;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform-admin/ops")
public class PlatformOpsWorkflowController {
    private final PlatformOpsQueryService service;
    private final PlatformOpsDiagnosisService diagnosisService;
    private final PlatformOpsDailyReportService dailyReportService;
    private final PlatformOpsControlProfileService controlProfileService;
    private final PlatformOpsDiagnosisFlowFactory diagnosisFlowFactory;
    private final PlatformOpsDailyReportFlowFactory dailyReportFlowFactory;
    private final PlatformOpsWorker worker;

    public PlatformOpsWorkflowController(
            PlatformOpsQueryService service,
            PlatformOpsDiagnosisService diagnosisService,
            PlatformOpsDailyReportService dailyReportService,
            PlatformOpsControlProfileService controlProfileService,
            PlatformOpsDiagnosisFlowFactory diagnosisFlowFactory,
            PlatformOpsDailyReportFlowFactory dailyReportFlowFactory,
            PlatformOpsWorker worker
    ) {
        this.service = service;
        this.diagnosisService = diagnosisService;
        this.dailyReportService = dailyReportService;
        this.controlProfileService = controlProfileService;
        this.diagnosisFlowFactory = diagnosisFlowFactory;
        this.dailyReportFlowFactory = dailyReportFlowFactory;
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

    @GetMapping("/daily-reports")
    public List<PlatformOpsDailyReportResponse> dailyReports(
            Authentication authentication,
            @RequestParam(required = false) Integer limit
    ) {
        return service.dailyReports(principal(authentication), limit);
    }

    @GetMapping("/control-profiles")
    public List<PlatformOpsControlProfileResponse> controlProfiles(
            Authentication authentication,
            @RequestParam(required = false) PlatformOpsControlProfileStatus status,
            @RequestParam(required = false) Integer limit
    ) {
        return controlProfileService.profiles(principal(authentication), status, limit);
    }

    @PostMapping("/control-profiles")
    public PlatformOpsControlProfileResponse createControlProfile(
            Authentication authentication,
            @RequestBody CreatePlatformOpsControlProfileRequest request
    ) {
        return controlProfileService.create(principal(authentication), request);
    }

    @PutMapping("/control-profiles/{profileId}")
    public PlatformOpsControlProfileResponse updateControlProfile(
            Authentication authentication,
            @PathVariable Long profileId,
            @RequestBody UpdatePlatformOpsControlProfileRequest request
    ) {
        return controlProfileService.update(principal(authentication), profileId, request);
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

    @PostMapping("/daily-reports/generate")
    public PlatformOpsRunResponse generateDailyReport(Authentication authentication) {
        var principal = principal(authentication);
        var run = dailyReportService.requestManualDailyReport(principal, OffsetDateTime.now());
        worker.submit(dailyReportFlowFactory.create(new PlatformOpsDailyReportRequested(
                run.id(),
                run.startedAt(),
                principal.userId())));
        return PlatformOpsRunResponse.from(run);
    }

    private UserPrincipal principal(Authentication authentication) {
        return (UserPrincipal) authentication.getPrincipal();
    }
}
