package com.archdox.cloud.office.api;

import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.office.application.OfficeInvitationService;
import com.archdox.cloud.office.application.OfficeManagementService;
import com.archdox.cloud.office.dto.AddOfficeMemberRequest;
import com.archdox.cloud.office.dto.CreateOfficeInvitationRequest;
import com.archdox.cloud.office.dto.CreateOfficeRequest;
import com.archdox.cloud.office.dto.OfficeInvitationResponse;
import com.archdox.cloud.office.dto.OfficeMemberResponse;
import com.archdox.cloud.office.dto.OfficeResponse;
import com.archdox.cloud.office.dto.UpdateOfficeMemberRoleRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/offices")
public class OfficeController {
    private final OfficeManagementService officeManagementService;
    private final OfficeInvitationService officeInvitationService;

    public OfficeController(
            OfficeManagementService officeManagementService,
            OfficeInvitationService officeInvitationService
    ) {
        this.officeManagementService = officeManagementService;
        this.officeInvitationService = officeInvitationService;
    }

    @GetMapping
    public List<OfficeResponse> list(Authentication authentication) {
        return officeManagementService.listMyOffices(principal(authentication).userId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OfficeResponse create(@Valid @RequestBody CreateOfficeRequest request, Authentication authentication) {
        return officeManagementService.createOffice(principal(authentication).userId(), request);
    }

    @GetMapping("/{officeId}")
    public OfficeResponse get(@PathVariable Long officeId, Authentication authentication) {
        return officeManagementService.getOffice(principal(authentication).userId(), officeId);
    }

    @GetMapping("/{officeId}/members")
    public List<OfficeMemberResponse> members(@PathVariable Long officeId, Authentication authentication) {
        return officeManagementService.listMembers(principal(authentication).userId(), officeId);
    }

    @PostMapping("/{officeId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public OfficeMemberResponse addMember(
            @PathVariable Long officeId,
            @Valid @RequestBody AddOfficeMemberRequest request,
            Authentication authentication
    ) {
        return officeManagementService.addExistingUserMember(principal(authentication).userId(), officeId, request);
    }

    @PatchMapping("/{officeId}/members/{memberUserId}/role")
    public OfficeMemberResponse updateMemberRole(
            @PathVariable Long officeId,
            @PathVariable Long memberUserId,
            @Valid @RequestBody UpdateOfficeMemberRoleRequest request,
            Authentication authentication
    ) {
        return officeManagementService.updateMemberRole(
                principal(authentication).userId(),
                officeId,
                memberUserId,
                request);
    }

    @DeleteMapping("/{officeId}/members/{memberUserId}")
    public OfficeMemberResponse deactivateMember(
            @PathVariable Long officeId,
            @PathVariable Long memberUserId,
            Authentication authentication
    ) {
        return officeManagementService.deactivateMember(principal(authentication).userId(), officeId, memberUserId);
    }

    @GetMapping("/{officeId}/invitations")
    public List<OfficeInvitationResponse> invitations(@PathVariable Long officeId, Authentication authentication) {
        return officeInvitationService.listInvitations(principal(authentication).userId(), officeId);
    }

    @PostMapping("/{officeId}/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public OfficeInvitationResponse createInvitation(
            @PathVariable Long officeId,
            @Valid @RequestBody CreateOfficeInvitationRequest request,
            Authentication authentication
    ) {
        return officeInvitationService.createInvitation(principal(authentication).userId(), officeId, request);
    }

    @DeleteMapping("/{officeId}/invitations/{invitationId}")
    public OfficeInvitationResponse cancelInvitation(
            @PathVariable Long officeId,
            @PathVariable Long invitationId,
            Authentication authentication
    ) {
        return officeInvitationService.cancelInvitation(principal(authentication).userId(), officeId, invitationId);
    }

    private UserPrincipal principal(Authentication authentication) {
        return (UserPrincipal) authentication.getPrincipal();
    }
}
