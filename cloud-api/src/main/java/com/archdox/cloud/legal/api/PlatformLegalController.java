package com.archdox.cloud.legal.api;

import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.legal.application.LegalDomainBindingAdminService;
import com.archdox.cloud.legal.application.LegalPlatformAdminService;
import com.archdox.cloud.legal.dto.CreateLegalDomainBindingRequest;
import com.archdox.cloud.legal.dto.LegalChangeDigestResponse;
import com.archdox.cloud.legal.dto.LegalChangeSetResponse;
import com.archdox.cloud.legal.dto.LegalDomainBindingAutoGenerateResponse;
import com.archdox.cloud.legal.dto.LegalDomainBindingCoverageResponse;
import com.archdox.cloud.legal.dto.LegalDomainBindingResponse;
import com.archdox.cloud.legal.dto.LegalDigestAiDraftResponse;
import com.archdox.cloud.legal.dto.LegalDigestRefreshResponse;
import com.archdox.cloud.legal.dto.LegalLawArticleResponse;
import com.archdox.cloud.legal.dto.LegalLawSearchResponse;
import com.archdox.cloud.legal.dto.LegalOpenApiStatusResponse;
import com.archdox.cloud.legal.dto.LegalSyncRunResponse;
import com.archdox.cloud.legal.dto.UpdateLegalDomainBindingRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform-admin/legal")
public class PlatformLegalController {
    private final LegalPlatformAdminService service;
    private final LegalDomainBindingAdminService bindingAdminService;

    public PlatformLegalController(
            LegalPlatformAdminService service,
            LegalDomainBindingAdminService bindingAdminService
    ) {
        this.service = service;
        this.bindingAdminService = bindingAdminService;
    }

    @PostMapping("/sync/fake")
    public LegalSyncRunResponse startFakeSync(Authentication authentication) {
        return service.startFakeSync(principal(authentication));
    }

    @PostMapping("/sync/open-data")
    public LegalSyncRunResponse startOpenDataSync(Authentication authentication) {
        return service.startOpenDataSync(principal(authentication));
    }

    @PostMapping("/change-digests/refresh-deterministic")
    public LegalDigestRefreshResponse refreshDeterministicDigests(
            Authentication authentication,
            @RequestParam(required = false) Integer limit
    ) {
        return service.refreshDeterministicDigests(principal(authentication), limit);
    }

    @PostMapping("/change-digests/{digestId}/ai-draft")
    public CompletableFuture<LegalDigestAiDraftResponse> generateDigestAiDraft(
            Authentication authentication,
            @PathVariable Long digestId
    ) {
        return service.generateDigestAiDraft(principal(authentication), digestId);
    }

    @GetMapping("/change-digests/{digestId}/ai-drafts")
    public List<LegalDigestAiDraftResponse> digestAiDrafts(
            Authentication authentication,
            @PathVariable Long digestId
    ) {
        return service.digestAiDrafts(principal(authentication), digestId);
    }

    @PostMapping("/change-digests/{digestId}/ai-drafts/{draftId}/apply")
    public LegalDigestAiDraftResponse applyDigestAiDraft(
            Authentication authentication,
            @PathVariable Long digestId,
            @PathVariable Long draftId
    ) {
        return service.applyDigestAiDraft(principal(authentication), digestId, draftId);
    }

    @PostMapping("/change-digests/{digestId}/ai-drafts/{draftId}/approve")
    public LegalDigestAiDraftResponse approveDigestAiDraft(
            Authentication authentication,
            @PathVariable Long digestId,
            @PathVariable Long draftId
    ) {
        return service.approveDigestAiDraft(principal(authentication), digestId, draftId);
    }

    @PostMapping("/change-digests/{digestId}/ai-drafts/{draftId}/reject")
    public LegalDigestAiDraftResponse rejectDigestAiDraft(
            Authentication authentication,
            @PathVariable Long digestId,
            @PathVariable Long draftId
    ) {
        return service.rejectDigestAiDraft(principal(authentication), digestId, draftId);
    }

    @GetMapping("/open-api/status")
    public LegalOpenApiStatusResponse openApiStatus(Authentication authentication) {
        return service.openApiStatus(principal(authentication));
    }

    @GetMapping("/sync-runs")
    public List<LegalSyncRunResponse> syncRuns(
            Authentication authentication,
            @RequestParam(required = false) String sourceCode,
            @RequestParam(required = false) Integer limit
    ) {
        return service.syncRuns(principal(authentication), sourceCode, limit);
    }

    @GetMapping("/change-sets")
    public List<LegalChangeSetResponse> changeSets(
            Authentication authentication,
            @RequestParam(required = false) Integer limit
    ) {
        return service.changeSets(principal(authentication), limit);
    }

    @GetMapping("/change-digests")
    public List<LegalChangeDigestResponse> changeDigests(
            Authentication authentication,
            @RequestParam(required = false) Integer limit
    ) {
        return service.changeDigests(principal(authentication), limit);
    }

    @GetMapping("/domain-bindings")
    public List<LegalDomainBindingResponse> domainBindings(
            Authentication authentication,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String bindingScope,
            @RequestParam(required = false) String bindingKey,
            @RequestParam(required = false) String reportType,
            @RequestParam(required = false) String catalogCode,
            @RequestParam(required = false) Integer catalogVersion,
            @RequestParam(required = false) String checklistItemCode,
            @RequestParam(required = false) Integer limit
    ) {
        return bindingAdminService.bindings(
                principal(authentication),
                status,
                bindingScope,
                bindingKey,
                reportType,
                catalogCode,
                catalogVersion,
                checklistItemCode,
                limit);
    }

    @GetMapping("/domain-bindings/coverage/construction-supervision")
    public LegalDomainBindingCoverageResponse constructionSupervisionDomainBindingCoverage(Authentication authentication) {
        return bindingAdminService.constructionSupervisionCoverage(principal(authentication));
    }

    @GetMapping("/law-search")
    public LegalLawSearchResponse lawSearch(
            Authentication authentication,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String actCode,
            @RequestParam(required = false) String actName,
            @RequestParam(required = false) String articleNo,
            @RequestParam(required = false) LocalDate effectiveDate,
            @RequestParam(required = false) Integer limit
    ) {
        return bindingAdminService.searchLaw(
                principal(authentication),
                query,
                actCode,
                actName,
                articleNo,
                effectiveDate,
                limit);
    }

    @GetMapping("/law-article")
    public LegalLawArticleResponse lawArticle(
            Authentication authentication,
            @RequestParam(required = false) Long articleVersionId,
            @RequestParam(required = false) Long articleId,
            @RequestParam(required = false) String actCode,
            @RequestParam(required = false) String articleNo,
            @RequestParam(required = false) LocalDate effectiveDate
    ) {
        return bindingAdminService.getLawArticle(
                principal(authentication),
                articleVersionId,
                articleId,
                actCode,
                articleNo,
                effectiveDate);
    }

    @PostMapping("/domain-bindings")
    public LegalDomainBindingResponse createDomainBinding(
            Authentication authentication,
            @RequestBody CreateLegalDomainBindingRequest request
    ) {
        return bindingAdminService.createBinding(principal(authentication), request);
    }

    @PostMapping("/domain-bindings/auto-generate/construction-supervision")
    public LegalDomainBindingAutoGenerateResponse autoGenerateConstructionSupervisionDomainBindings(
            Authentication authentication
    ) {
        return bindingAdminService.autoGenerateConstructionSupervisionBindings(principal(authentication));
    }

    @PostMapping("/domain-bindings/{bindingId}")
    public LegalDomainBindingResponse updateDomainBinding(
            Authentication authentication,
            @PathVariable Long bindingId,
            @RequestBody UpdateLegalDomainBindingRequest request
    ) {
        return bindingAdminService.updateBinding(principal(authentication), bindingId, request);
    }

    @PostMapping("/domain-bindings/{bindingId}/deactivate")
    public LegalDomainBindingResponse deactivateDomainBinding(
            Authentication authentication,
            @PathVariable Long bindingId
    ) {
        return bindingAdminService.deactivateBinding(principal(authentication), bindingId);
    }

    private UserPrincipal principal(Authentication authentication) {
        return (UserPrincipal) authentication.getPrincipal();
    }
}
