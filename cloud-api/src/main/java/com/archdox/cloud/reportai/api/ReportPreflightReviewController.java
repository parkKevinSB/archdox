package com.archdox.cloud.reportai.api;

import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.reportai.application.ReportPreflightReviewService;
import com.archdox.cloud.reportai.dto.ReportPreflightReviewFindingResponse;
import com.archdox.cloud.reportai.dto.ReportPreflightReviewRunResponse;
import com.archdox.cloud.reportai.dto.ResolveReportPreflightFindingRequest;
import com.archdox.cloud.reportai.flow.ReportPreflightReviewWorker;
import java.util.List;
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
@RequestMapping("/api/v1/inspection-reports/{reportId}/preflight-review-runs")
public class ReportPreflightReviewController {
    private final ReportPreflightReviewService service;
    private final ReportPreflightReviewWorker worker;

    public ReportPreflightReviewController(
            ReportPreflightReviewService service,
            ReportPreflightReviewWorker worker
    ) {
        this.service = service;
        this.worker = worker;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReportPreflightReviewRunResponse requestReview(
            @PathVariable Long reportId,
            Authentication authentication
    ) {
        var submission = service.requestReview(reportId, (UserPrincipal) authentication.getPrincipal());
        worker.submit(submission.flow());
        return submission.response();
    }

    @GetMapping
    public List<ReportPreflightReviewRunResponse> listRuns(
            @PathVariable Long reportId,
            Authentication authentication
    ) {
        return service.listRuns(reportId, (UserPrincipal) authentication.getPrincipal());
    }

    @GetMapping("/{runId}/findings")
    public List<ReportPreflightReviewFindingResponse> listFindings(
            @PathVariable Long reportId,
            @PathVariable Long runId,
            Authentication authentication
    ) {
        return service.listFindings(reportId, runId, (UserPrincipal) authentication.getPrincipal());
    }

    @PatchMapping("/{runId}/findings/{findingId}/resolution")
    public ReportPreflightReviewFindingResponse resolveFinding(
            @PathVariable Long reportId,
            @PathVariable Long runId,
            @PathVariable Long findingId,
            @RequestBody ResolveReportPreflightFindingRequest request,
            Authentication authentication
    ) {
        return service.resolveFinding(
                reportId,
                runId,
                findingId,
                request,
                (UserPrincipal) authentication.getPrincipal());
    }
}
