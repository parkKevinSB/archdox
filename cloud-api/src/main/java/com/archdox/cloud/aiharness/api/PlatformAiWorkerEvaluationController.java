package com.archdox.cloud.aiharness.api;

import com.archdox.cloud.aiharness.application.AiWorkerEvaluationReadService;
import com.archdox.cloud.aiharness.dto.AiWorkerEvaluationSummaryResponse;
import com.archdox.cloud.global.security.UserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform-admin/ai/evaluation-summary")
public class PlatformAiWorkerEvaluationController {
    private final AiWorkerEvaluationReadService service;

    public PlatformAiWorkerEvaluationController(AiWorkerEvaluationReadService service) {
        this.service = service;
    }

    @GetMapping
    public AiWorkerEvaluationSummaryResponse summary(Authentication authentication) {
        return service.summary((UserPrincipal) authentication.getPrincipal());
    }
}
