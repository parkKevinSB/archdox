package com.archdox.cloud.worker.approval.api;

import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.worker.approval.application.WorkerApprovalExecutionService;
import com.archdox.cloud.worker.approval.application.WorkerApprovalRequestService;
import com.archdox.cloud.worker.approval.dto.DecideWorkerApprovalRequest;
import com.archdox.cloud.worker.approval.dto.WorkerApprovalRequestResponse;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform-admin/ops/worker-approvals")
public class PlatformWorkerApprovalController {
    private final WorkerApprovalRequestService requestService;
    private final WorkerApprovalExecutionService executionService;

    public PlatformWorkerApprovalController(
            WorkerApprovalRequestService requestService,
            WorkerApprovalExecutionService executionService
    ) {
        this.requestService = requestService;
        this.executionService = executionService;
    }

    @GetMapping
    public List<WorkerApprovalRequestResponse> list(
            Authentication authentication,
            @RequestParam(required = false) Long officeId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) Integer limit
    ) {
        return requestService.list(principal(authentication), officeId, status, actionType, limit);
    }

    @PostMapping("/{approvalRequestId}/approve")
    public WorkerApprovalRequestResponse approve(
            Authentication authentication,
            @PathVariable Long approvalRequestId,
            @RequestBody(required = false) DecideWorkerApprovalRequest request
    ) {
        return executionService.approveAndSubmit(
                principal(authentication),
                approvalRequestId,
                request == null ? null : request.reason());
    }

    @PostMapping("/{approvalRequestId}/reject")
    public WorkerApprovalRequestResponse reject(
            Authentication authentication,
            @PathVariable Long approvalRequestId,
            @RequestBody(required = false) DecideWorkerApprovalRequest request
    ) {
        return executionService.reject(
                principal(authentication),
                approvalRequestId,
                request == null ? null : request.reason());
    }

    private UserPrincipal principal(Authentication authentication) {
        return (UserPrincipal) authentication.getPrincipal();
    }
}
