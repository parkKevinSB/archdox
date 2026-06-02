package com.archdox.cloud.aipolicy.api;

import com.archdox.cloud.aiharness.application.AiHarnessTraceEventService;
import com.archdox.cloud.aiharness.dto.AiHarnessTraceEventResponse;
import com.archdox.cloud.aipolicy.application.AiModelCallLogService;
import com.archdox.cloud.aipolicy.application.AiModelPricingRuleService;
import com.archdox.cloud.aipolicy.application.AiObservationBufferService;
import com.archdox.cloud.aipolicy.application.AiPolicyManagementService;
import com.archdox.cloud.aipolicy.application.AiProviderConnectionTestService;
import com.archdox.cloud.aipolicy.application.AiUsageReadService;
import com.archdox.cloud.aipolicy.dto.AiModelCallLogResponse;
import com.archdox.cloud.aipolicy.dto.AiModelPricingRuleResponse;
import com.archdox.cloud.aipolicy.dto.AiObservationModeResponse;
import com.archdox.cloud.aipolicy.dto.AiObservationResponse;
import com.archdox.cloud.aipolicy.dto.AiProviderConnectionTestResponse;
import com.archdox.cloud.aipolicy.dto.AiProviderCredentialResponse;
import com.archdox.cloud.aipolicy.dto.AiUsageSummaryResponse;
import com.archdox.cloud.aipolicy.dto.CreateAiModelPricingRuleRequest;
import com.archdox.cloud.aipolicy.dto.CreateAiProviderCredentialRequest;
import com.archdox.cloud.aipolicy.dto.OfficeAiPolicyResponse;
import com.archdox.cloud.aipolicy.dto.UpdateAiObservationModeRequest;
import com.archdox.cloud.aipolicy.dto.UpdateAiProviderCredentialRequest;
import com.archdox.cloud.aipolicy.dto.UpdateOfficeAiPolicyRequest;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.reportai.application.ReportPreflightFindingOpsService;
import com.archdox.cloud.reportai.dto.PlatformReportPreflightFindingResponse;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/v1/platform-admin/ai")
public class PlatformAiPolicyController {
    private final AiPolicyManagementService service;
    private final AiModelCallLogService callLogService;
    private final AiModelPricingRuleService pricingRuleService;
    private final AiUsageReadService usageReadService;
    private final ReportPreflightFindingOpsService preflightFindingOpsService;
    private final AiHarnessTraceEventService traceEventService;
    private final AiProviderConnectionTestService connectionTestService;
    private final AiObservationBufferService observationBufferService;

    public PlatformAiPolicyController(
            AiPolicyManagementService service,
            AiModelCallLogService callLogService,
            AiModelPricingRuleService pricingRuleService,
            AiUsageReadService usageReadService,
            ReportPreflightFindingOpsService preflightFindingOpsService,
            AiHarnessTraceEventService traceEventService,
            AiProviderConnectionTestService connectionTestService,
            AiObservationBufferService observationBufferService
    ) {
        this.service = service;
        this.callLogService = callLogService;
        this.pricingRuleService = pricingRuleService;
        this.usageReadService = usageReadService;
        this.preflightFindingOpsService = preflightFindingOpsService;
        this.traceEventService = traceEventService;
        this.connectionTestService = connectionTestService;
        this.observationBufferService = observationBufferService;
    }

    @GetMapping("/providers")
    public List<AiProviderCredentialResponse> providers(Authentication authentication) {
        return service.providers(principal(authentication));
    }

    @PostMapping("/providers")
    public AiProviderCredentialResponse createProvider(
            Authentication authentication,
            @RequestBody CreateAiProviderCredentialRequest request
    ) {
        return service.createProvider(principal(authentication), request);
    }

    @PutMapping("/providers/{providerId}")
    public AiProviderCredentialResponse updateProvider(
            Authentication authentication,
            @PathVariable Long providerId,
            @RequestBody UpdateAiProviderCredentialRequest request
    ) {
        return service.updateProvider(principal(authentication), providerId, request);
    }

    @PostMapping("/providers/{providerId}/publish")
    public AiProviderCredentialResponse publishProvider(
            Authentication authentication,
            @PathVariable Long providerId
    ) {
        return service.publishProvider(principal(authentication), providerId);
    }

    @PostMapping("/providers/{providerId}/test")
    public AiProviderConnectionTestResponse testProvider(
            Authentication authentication,
            @PathVariable Long providerId
    ) {
        return connectionTestService.testProvider(principal(authentication), providerId);
    }

    @GetMapping("/office-policies")
    public List<OfficeAiPolicyResponse> officePolicies(
            Authentication authentication,
            @RequestParam(required = false) Integer limit
    ) {
        return service.officePolicies(principal(authentication), limit);
    }

    @GetMapping("/call-logs")
    public List<AiModelCallLogResponse> callLogs(
            Authentication authentication,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String status
    ) {
        return callLogService.callLogs(principal(authentication), limit, status);
    }

    @GetMapping("/usage-summary")
    public AiUsageSummaryResponse usageSummary(Authentication authentication) {
        return usageReadService.monthlySummary(principal(authentication));
    }

    @GetMapping("/harness-traces")
    public List<AiHarnessTraceEventResponse> harnessTraces(
            Authentication authentication,
            @RequestParam(required = false) String harnessRunId,
            @RequestParam(required = false) String harnessId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) Integer limit
    ) {
        return traceEventService.traceEvents(
                principal(authentication),
                harnessRunId,
                harnessId,
                eventType,
                limit);
    }

    @GetMapping("/observation-mode")
    public AiObservationModeResponse observationMode(Authentication authentication) {
        return observationBufferService.mode(principal(authentication));
    }

    @PutMapping("/observation-mode")
    public AiObservationModeResponse updateObservationMode(
            Authentication authentication,
            @RequestBody UpdateAiObservationModeRequest request
    ) {
        return observationBufferService.updateMode(principal(authentication), request);
    }

    @DeleteMapping("/observations")
    public AiObservationModeResponse clearObservations(Authentication authentication) {
        return observationBufferService.clear(principal(authentication));
    }

    @GetMapping("/observations")
    public List<AiObservationResponse> observations(
            Authentication authentication,
            @RequestParam(required = false) Integer limit
    ) {
        return observationBufferService.observations(principal(authentication), limit);
    }

    @GetMapping("/preflight-findings")
    public List<PlatformReportPreflightFindingResponse> preflightFindings(
            Authentication authentication,
            @RequestParam(required = false) Long officeId,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String resolutionStatus,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) Integer limit
    ) {
        return preflightFindingOpsService.platformFindings(
                principal(authentication),
                officeId,
                severity,
                resolutionStatus,
                source,
                limit);
    }

    @GetMapping("/pricing-rules")
    public List<AiModelPricingRuleResponse> pricingRules(
            Authentication authentication,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String status
    ) {
        return pricingRuleService.pricingRules(principal(authentication), limit, status);
    }

    @PostMapping("/pricing-rules")
    public AiModelPricingRuleResponse createPricingRule(
            Authentication authentication,
            @RequestBody CreateAiModelPricingRuleRequest request
    ) {
        return pricingRuleService.createPricingRule(principal(authentication), request);
    }

    @PostMapping("/pricing-rules/{pricingRuleId}/disable")
    public AiModelPricingRuleResponse disablePricingRule(
            Authentication authentication,
            @PathVariable Long pricingRuleId
    ) {
        return pricingRuleService.disablePricingRule(principal(authentication), pricingRuleId);
    }

    @PutMapping("/office-policies/{officeId}")
    public OfficeAiPolicyResponse updateOfficePolicy(
            Authentication authentication,
            @PathVariable Long officeId,
            @RequestBody UpdateOfficeAiPolicyRequest request
    ) {
        return service.updateOfficePolicy(principal(authentication), officeId, request);
    }

    private UserPrincipal principal(Authentication authentication) {
        return (UserPrincipal) authentication.getPrincipal();
    }
}
