package com.archdox.cloud.office.api;

import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.office.application.OfficeInvitationService;
import com.archdox.cloud.office.dto.OfficeInvitationPreviewResponse;
import com.archdox.cloud.office.dto.OfficeMemberResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/office-invitations")
public class OfficeInvitationController {
    private final OfficeInvitationService officeInvitationService;

    public OfficeInvitationController(OfficeInvitationService officeInvitationService) {
        this.officeInvitationService = officeInvitationService;
    }

    @GetMapping("/{token}")
    public OfficeInvitationPreviewResponse preview(@PathVariable String token) {
        return officeInvitationService.previewInvitation(token);
    }

    @PostMapping("/{token}/accept")
    public OfficeMemberResponse accept(@PathVariable String token, Authentication authentication) {
        return officeInvitationService.acceptInvitation(principal(authentication).userId(), token);
    }

    private UserPrincipal principal(Authentication authentication) {
        return (UserPrincipal) authentication.getPrincipal();
    }
}
