package com.archdox.cloud.agent.api;

import com.archdox.cloud.agent.application.ArchDoxAgentAuthenticationService;
import com.archdox.cloud.agent.dto.CreateArchDoxAgentInstallTokenRequest;
import com.archdox.cloud.agent.dto.ArchDoxAgentInstallTokenResponse;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.office.application.OfficeContext;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/archdox-agents/install-tokens")
public class ArchDoxAgentInstallTokenController {
    private final ArchDoxAgentAuthenticationService authenticationService;

    public ArchDoxAgentInstallTokenController(ArchDoxAgentAuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ArchDoxAgentInstallTokenResponse create(
            @RequestBody(required = false) CreateArchDoxAgentInstallTokenRequest request,
            Authentication authentication
    ) {
        var principal = (UserPrincipal) authentication.getPrincipal();
        var issued = authenticationService.issueInstallToken(
                OfficeContext.requireCurrentOfficeId(),
                principal.userId(),
                request == null ? null : request.expiresInMinutes());
        var token = issued.installToken();
        return new ArchDoxAgentInstallTokenResponse(
                token.id(),
                token.officeId(),
                token.status(),
                issued.rawToken(),
                token.expiresAt());
    }
}
