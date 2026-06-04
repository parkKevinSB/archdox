package com.archdox.cloud.office.application;

import com.archdox.cloud.account.domain.UserAccount;
import com.archdox.cloud.account.infra.UserAccountRepository;
import com.archdox.cloud.global.api.ConflictException;
import com.archdox.cloud.global.api.ForbiddenException;
import com.archdox.cloud.global.api.NotFoundException;
import com.archdox.cloud.global.api.UnauthorizedException;
import com.archdox.cloud.office.domain.OfficeInvitation;
import com.archdox.cloud.office.domain.OfficeInvitationStatus;
import com.archdox.cloud.office.domain.OfficeMembership;
import com.archdox.cloud.office.dto.CreateOfficeInvitationRequest;
import com.archdox.cloud.office.dto.OfficeInvitationPreviewResponse;
import com.archdox.cloud.office.dto.OfficeInvitationResponse;
import com.archdox.cloud.office.dto.OfficeMemberResponse;
import com.archdox.cloud.office.infra.OfficeInvitationRepository;
import com.archdox.cloud.office.infra.OfficeMembershipRepository;
import com.archdox.cloud.office.infra.OfficeRepository;
import com.archdox.cloud.operation.application.OperationEventService;
import com.archdox.cloud.operation.domain.OperationEventSeverity;
import com.archdox.cloud.platformadmin.application.PlatformAdminService;
import com.archdox.shared.MembershipRole;
import com.archdox.shared.MembershipStatus;
import com.archdox.shared.OfficeType;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OfficeInvitationService {
    private static final int DEFAULT_EXPIRES_IN_DAYS = 14;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserAccountRepository userRepository;
    private final OfficeRepository officeRepository;
    private final OfficeMembershipRepository membershipRepository;
    private final OfficeInvitationRepository invitationRepository;
    private final OperationEventService operationEventService;
    private final PlatformAdminService platformAdminService;

    public OfficeInvitationService(
            UserAccountRepository userRepository,
            OfficeRepository officeRepository,
            OfficeMembershipRepository membershipRepository,
            OfficeInvitationRepository invitationRepository,
            OperationEventService operationEventService,
            PlatformAdminService platformAdminService
    ) {
        this.userRepository = userRepository;
        this.officeRepository = officeRepository;
        this.membershipRepository = membershipRepository;
        this.invitationRepository = invitationRepository;
        this.operationEventService = operationEventService;
        this.platformAdminService = platformAdminService;
    }

    @Transactional(readOnly = true)
    public OfficeInvitationPreviewResponse previewInvitation(String token) {
        var now = OffsetDateTime.now();
        var invitation = invitationRepository.findByTokenHash(hash(token))
                .orElseThrow(() -> new NotFoundException("Office invitation not found"));
        return new OfficeInvitationPreviewResponse(
                invitation.email(),
                invitation.office().id(),
                invitation.office().officeCode(),
                invitation.office().displayName(),
                invitation.role(),
                invitation.effectiveStatus(now),
                invitation.expiresAt());
    }

    @Transactional(readOnly = true)
    public List<OfficeInvitationResponse> listInvitations(Long actorUserId, Long officeId) {
        requireOfficeManager(actorUserId, officeId);
        var now = OffsetDateTime.now();
        return invitationRepository.findByOfficeIdOrderByCreatedAtDesc(officeId).stream()
                .map(invitation -> toResponse(invitation, now, null))
                .toList();
    }

    @Transactional
    public OfficeInvitationResponse createInvitation(
            Long actorUserId,
            Long officeId,
            CreateOfficeInvitationRequest request
    ) {
        var actorMembership = requireOfficeManager(actorUserId, officeId);
        requireOfficeWorkspace(officeId);
        requireOwnerIfOwnerRole(actorMembership, request.role());
        var email = normalizeEmail(request.email());
        var now = OffsetDateTime.now();
        expireExistingPendingIfNeeded(officeId, email, now);
        invitationRepository.findFirstByOfficeIdAndEmailIgnoreCaseAndStatusOrderByCreatedAtDesc(
                        officeId,
                        email,
                        OfficeInvitationStatus.PENDING)
                .ifPresent(invitation -> {
                    throw new ConflictException("Office invitation is already pending for this email");
                });

        userRepository.findByEmailIgnoreCase(email)
                .filter(user -> membershipRepository.existsByUserIdAndOfficeIdAndStatus(
                        user.id(),
                        officeId,
                        MembershipStatus.ACTIVE))
                .ifPresent(user -> {
                    throw new ConflictException("User is already an active member of this office");
                });

        var rawToken = randomToken();
        var tokenPreview = rawToken.substring(0, 8);
        var office = officeRepository.getReferenceById(officeId);
        var actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new UnauthorizedException("User not found"));
        var expiresAt = now.plusDays(request.expiresInDays() == null ? DEFAULT_EXPIRES_IN_DAYS : request.expiresInDays());
        var invitation = invitationRepository.save(new OfficeInvitation(
                office,
                email,
                request.role(),
                hash(rawToken),
                tokenPreview,
                actor,
                now,
                expiresAt));

        recordInvitationEvent(
                officeId,
                actorUserId,
                "OFFICE_INVITATION_CREATED",
                invitation,
                Map.of(
                        "email", invitation.email(),
                        "role", invitation.role().name(),
                        "expiresAt", invitation.expiresAt().toString()));
        return toResponse(invitation, now, rawToken);
    }

    @Transactional
    public OfficeInvitationResponse cancelInvitation(Long actorUserId, Long officeId, Long invitationId) {
        requireOfficeManager(actorUserId, officeId);
        var now = OffsetDateTime.now();
        var invitation = invitationRepository.findByIdAndOfficeId(invitationId, officeId)
                .orElseThrow(() -> new NotFoundException("Office invitation not found"));
        if (invitation.isExpired(now)) {
            invitation.expire(now);
            throw new ConflictException("Expired invitation cannot be cancelled");
        }
        if (invitation.status() != OfficeInvitationStatus.PENDING) {
            throw new ConflictException("Only pending invitations can be cancelled");
        }
        invitation.cancel(now);
        recordInvitationEvent(
                officeId,
                actorUserId,
                "OFFICE_INVITATION_CANCELLED",
                invitation,
                Map.of(
                        "email", invitation.email(),
                        "role", invitation.role().name()));
        return toResponse(invitation, now, null);
    }

    @Transactional
    public OfficeMemberResponse acceptInvitation(Long userId, String token) {
        var now = OffsetDateTime.now();
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("User not found"));
        var invitation = invitationRepository.findByTokenHash(hash(token))
                .orElseThrow(() -> new NotFoundException("Office invitation not found"));
        if (invitation.isExpired(now)) {
            invitation.expire(now);
            recordInvitationEvent(
                    invitation.office().id(),
                    userId,
                    "OFFICE_INVITATION_EXPIRED",
                    invitation,
                    Map.of("email", invitation.email()));
            throw new ConflictException("Office invitation has expired");
        }
        if (invitation.status() != OfficeInvitationStatus.PENDING) {
            throw new ConflictException("Office invitation is not pending");
        }
        if (!normalizeEmail(user.email()).equals(invitation.email())) {
            throw new ForbiddenException("Invitation email does not match the signed-in user");
        }
        if (invitation.office().type() != OfficeType.OFFICE) {
            throw new ForbiddenException("Personal workspace cannot accept office invitations");
        }

        var officeId = invitation.office().id();
        var membership = membershipRepository.findByUserIdAndOfficeId(user.id(), officeId).orElse(null);
        if (membership != null && membership.status() == MembershipStatus.ACTIVE) {
            throw new ConflictException("User is already an active member of this office");
        }
        if (membership != null) {
            membership.reactivate(invitation.role(), now);
        } else {
            membership = membershipRepository.save(new OfficeMembership(
                    user,
                    invitation.office(),
                    invitation.role(),
                    now));
        }
        invitation.accept(user, now);
        recordInvitationEvent(
                officeId,
                userId,
                "OFFICE_INVITATION_ACCEPTED",
                invitation,
                Map.of(
                        "email", invitation.email(),
                        "role", invitation.role().name(),
                        "membershipId", membership.id()));
        return toMemberResponse(membership);
    }

    private void expireExistingPendingIfNeeded(Long officeId, String email, OffsetDateTime now) {
        invitationRepository.findFirstByOfficeIdAndEmailIgnoreCaseAndStatusOrderByCreatedAtDesc(
                        officeId,
                        email,
                        OfficeInvitationStatus.PENDING)
                .filter(invitation -> invitation.isExpired(now))
                .ifPresent(invitation -> {
                    invitation.expire(now);
                    invitationRepository.flush();
                });
    }

    private OfficeMembership requireOfficeManager(Long userId, Long officeId) {
        if (platformAdminService.isPlatformAdmin(userId)) {
            return null;
        }
        var membership = membershipRepository.findByUserIdAndOfficeIdAndStatus(
                        userId,
                        officeId,
                        MembershipStatus.ACTIVE)
                .orElseThrow(() -> new UnauthorizedException("Office membership required"));
        if (membership.role() != MembershipRole.OWNER && membership.role() != MembershipRole.ADMIN) {
            throw new ForbiddenException("Office admin role required");
        }
        return membership;
    }

    private void requireOfficeWorkspace(OfficeMembership membership) {
        if (membership.office().type() != OfficeType.OFFICE) {
            throw new ForbiddenException("Personal workspace cannot create office invitations");
        }
    }

    private void requireOfficeWorkspace(Long officeId) {
        var office = officeRepository.findById(officeId)
                .orElseThrow(() -> new NotFoundException("Office not found"));
        if (office.type() != OfficeType.OFFICE) {
            throw new ForbiddenException("Personal workspace cannot create office invitations");
        }
    }

    private void requireOwnerIfOwnerRole(OfficeMembership actorMembership, MembershipRole role) {
        if (actorMembership == null) {
            return;
        }
        if (role == MembershipRole.OWNER && actorMembership.role() != MembershipRole.OWNER) {
            throw new ForbiddenException("Only an owner can assign owner role");
        }
    }

    private OfficeInvitationResponse toResponse(OfficeInvitation invitation, OffsetDateTime now, String rawToken) {
        return new OfficeInvitationResponse(
                invitation.id(),
                invitation.office().id(),
                invitation.email(),
                invitation.role(),
                invitation.effectiveStatus(now),
                invitation.invitedBy().id(),
                invitation.acceptedBy() == null ? null : invitation.acceptedBy().id(),
                invitation.tokenPreview(),
                rawToken,
                rawToken == null ? null : "/api/v1/office-invitations/" + rawToken + "/accept",
                invitation.createdAt(),
                invitation.expiresAt(),
                invitation.acceptedAt(),
                invitation.cancelledAt(),
                invitation.updatedAt());
    }

    private OfficeMemberResponse toMemberResponse(OfficeMembership membership) {
        return new OfficeMemberResponse(
                membership.id(),
                membership.user().id(),
                membership.office().id(),
                membership.user().email(),
                membership.user().name(),
                membership.role(),
                membership.status(),
                membership.joinedAt());
    }

    private void recordInvitationEvent(
            Long officeId,
            Long actorUserId,
            String eventType,
            OfficeInvitation invitation,
            Map<String, Object> payload
    ) {
        operationEventService.record(
                officeId,
                OperationEventSeverity.INFO,
                eventType,
                null,
                null,
                "OFFICE_INVITATION",
                invitation.id(),
                actorUserId,
                null,
                eventType + " invitationId=" + invitation.id(),
                payload);
    }

    private String randomToken() {
        var bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String token) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes());
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to hash invitation token", ex);
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }
}
