package com.archdox.cloud.legal.api;

import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.legal.application.LegalPlatformAdminService;
import com.archdox.cloud.legal.dto.LegalChangeDigestResponse;
import com.archdox.cloud.legal.dto.LegalChangeSetResponse;
import com.archdox.cloud.legal.dto.LegalDigestAiDraftResponse;
import com.archdox.cloud.legal.dto.LegalDigestRefreshResponse;
import com.archdox.cloud.legal.dto.LegalOpenApiStatusResponse;
import com.archdox.cloud.legal.dto.LegalSyncRunResponse;
import java.util.List;
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

    public PlatformLegalController(LegalPlatformAdminService service) {
        this.service = service;
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
    public LegalDigestAiDraftResponse generateDigestAiDraft(
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

    private UserPrincipal principal(Authentication authentication) {
        return (UserPrincipal) authentication.getPrincipal();
    }
}
