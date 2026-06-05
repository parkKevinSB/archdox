package com.archdox.cloud.legal.api;

import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.legal.application.LegalPlatformAdminService;
import com.archdox.cloud.legal.dto.LegalChangeDigestResponse;
import com.archdox.cloud.legal.dto.LegalChangeSetResponse;
import com.archdox.cloud.legal.dto.LegalOpenApiStatusResponse;
import com.archdox.cloud.legal.dto.LegalSyncRunResponse;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
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
