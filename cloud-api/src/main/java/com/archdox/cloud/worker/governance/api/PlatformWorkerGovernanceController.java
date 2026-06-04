package com.archdox.cloud.worker.governance.api;

import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.worker.governance.application.WorkerGovernanceReadService;
import com.archdox.cloud.worker.governance.dto.WorkerGovernanceSummaryResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform-admin/ops/worker-governance")
public class PlatformWorkerGovernanceController {
    private final WorkerGovernanceReadService service;

    public PlatformWorkerGovernanceController(WorkerGovernanceReadService service) {
        this.service = service;
    }

    @GetMapping
    public WorkerGovernanceSummaryResponse summary(
            Authentication authentication,
            @RequestParam(required = false) Long officeId,
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false) Integer recentLimit
    ) {
        return service.summary(principal(authentication), officeId, days, recentLimit);
    }

    private UserPrincipal principal(Authentication authentication) {
        return (UserPrincipal) authentication.getPrincipal();
    }
}
