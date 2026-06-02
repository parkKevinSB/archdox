package com.archdox.cloud.supervisionledger.api;

import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.supervisionledger.application.SiteSupervisionLedgerService;
import com.archdox.cloud.supervisionledger.dto.SiteSupervisionEntryResponse;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/sites/{siteId}/supervision-ledger")
public class SiteSupervisionLedgerController {
    private final SiteSupervisionLedgerService ledgerService;

    public SiteSupervisionLedgerController(SiteSupervisionLedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @GetMapping("/entries")
    public List<SiteSupervisionEntryResponse> listEntries(
            @PathVariable Long projectId,
            @PathVariable Long siteId,
            Authentication authentication
    ) {
        return ledgerService.listEntries(projectId, siteId, (UserPrincipal) authentication.getPrincipal());
    }
}
