package com.archdox.cloud.aiharness.api;

import com.archdox.cloud.aiharness.application.AiWorkerEvaluationRunService;
import com.archdox.cloud.aiharness.dto.AiWorkerEvaluationRunResponse;
import com.archdox.cloud.global.security.UserPrincipal;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform-admin/ai/evaluation-runs")
public class PlatformAiWorkerEvaluationRunController {
    private final AiWorkerEvaluationRunService service;

    public PlatformAiWorkerEvaluationRunController(AiWorkerEvaluationRunService service) {
        this.service = service;
    }

    @GetMapping
    public List<AiWorkerEvaluationRunResponse> runs(
            Authentication authentication,
            @RequestParam(required = false) Integer limit
    ) {
        return service.runs((UserPrincipal) authentication.getPrincipal(), limit);
    }

    @PostMapping
    public AiWorkerEvaluationRunResponse create(Authentication authentication) {
        return service.createSnapshot((UserPrincipal) authentication.getPrincipal());
    }

    @PostMapping("/runtime-probe")
    public AiWorkerEvaluationRunResponse createRuntimeProbe(Authentication authentication) {
        return service.createRuntimeProbe((UserPrincipal) authentication.getPrincipal());
    }

    @GetMapping("/{runId}")
    public AiWorkerEvaluationRunResponse run(
            Authentication authentication,
            @PathVariable Long runId
    ) {
        return service.run((UserPrincipal) authentication.getPrincipal(), runId);
    }
}
