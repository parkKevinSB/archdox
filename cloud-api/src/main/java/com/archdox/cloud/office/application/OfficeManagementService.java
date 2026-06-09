package com.archdox.cloud.office.application;

import com.archdox.cloud.account.infra.UserAccountRepository;
import com.archdox.cloud.aipolicy.domain.OfficeAiPolicy;
import com.archdox.cloud.aipolicy.infra.OfficeAiPolicyRepository;
import com.archdox.cloud.global.api.ConflictException;
import com.archdox.cloud.global.api.ForbiddenException;
import com.archdox.cloud.global.api.NotFoundException;
import com.archdox.cloud.global.api.UnauthorizedException;
import com.archdox.cloud.office.domain.Office;
import com.archdox.cloud.office.domain.OfficeMembership;
import com.archdox.cloud.office.dto.AddOfficeMemberRequest;
import com.archdox.cloud.office.dto.CreateOfficeRequest;
import com.archdox.cloud.office.dto.OfficeMemberResponse;
import com.archdox.cloud.office.dto.OfficeResponse;
import com.archdox.cloud.office.dto.UpdateOfficeMemberRoleRequest;
import com.archdox.cloud.office.infra.OfficeMembershipRepository;
import com.archdox.cloud.office.infra.OfficeRepository;
import com.archdox.cloud.operation.application.OperationEventService;
import com.archdox.cloud.operation.domain.OperationEventSeverity;
import com.archdox.cloud.platformadmin.application.PlatformAdminService;
import com.archdox.shared.MembershipRole;
import com.archdox.shared.MembershipStatus;
import com.archdox.shared.OfficeType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OfficeManagementService {
    private final UserAccountRepository userRepository;
    private final OfficeRepository officeRepository;
    private final OfficeMembershipRepository membershipRepository;
    private final OfficeAiPolicyRepository officeAiPolicyRepository;
    private final OperationEventService operationEventService;
    private final PlatformAdminService platformAdminService;

    public OfficeManagementService(
            UserAccountRepository userRepository,
            OfficeRepository officeRepository,
            OfficeMembershipRepository membershipRepository,
            OfficeAiPolicyRepository officeAiPolicyRepository,
            OperationEventService operationEventService,
            PlatformAdminService platformAdminService
    ) {
        this.userRepository = userRepository;
        this.officeRepository = officeRepository;
        this.membershipRepository = membershipRepository;
        this.officeAiPolicyRepository = officeAiPolicyRepository;
        this.operationEventService = operationEventService;
        this.platformAdminService = platformAdminService;
    }

    @Transactional(readOnly = true)
    public List<OfficeResponse> listMyOffices(Long userId) {
        return membershipRepository.findByUserIdAndStatus(userId, MembershipStatus.ACTIVE).stream()
                .map(this::toOfficeResponse)
                .toList();
    }

    @Transactional
    public OfficeResponse createOffice(Long userId, CreateOfficeRequest request) {
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("User not found"));
        var now = OffsetDateTime.now();
        var office = officeRepository.save(new Office(
                "office-" + UUID.randomUUID().toString().substring(0, 8),
                request.displayName().trim(),
                OfficeType.OFFICE,
                "FREE",
                now));
        officeAiPolicyRepository.save(new OfficeAiPolicy(office.id(), userId, now));
        var membership = membershipRepository.save(new OfficeMembership(user, office, MembershipRole.OWNER, now));
        return toOfficeResponse(membership);
    }

    @Transactional(readOnly = true)
    public OfficeResponse getOffice(Long userId, Long officeId) {
        if (platformAdminService.isPlatformAdmin(userId)) {
            var office = officeRepository.findById(officeId)
                    .orElseThrow(() -> new NotFoundException("Office not found"));
            return toOfficeResponse(office, MembershipRole.OWNER);
        }
        var membership = requireMembership(userId, officeId);
        return toOfficeResponse(membership);
    }

    @Transactional(readOnly = true)
    public List<OfficeMemberResponse> listMembers(Long userId, Long officeId) {
        requireOfficeManager(userId, officeId);
        return membershipRepository.findByOfficeId(officeId).stream()
                .map(this::toMemberResponse)
                .toList();
    }

    @Transactional
    public OfficeMemberResponse addExistingUserMember(Long actorUserId, Long officeId, AddOfficeMemberRequest request) {
        var actorMembership = requireOfficeManager(actorUserId, officeId);
        requireOfficeWorkspace(officeId);
        requireOwnerIfOwnerRole(actorMembership, request.role());
        var email = normalizeEmail(request.email());
        var user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ConflictException("Invitee must sign up before being added in MVP"));
        var existing = membershipRepository.findByUserIdAndOfficeId(user.id(), officeId).orElse(null);
        if (existing != null && existing.status() == MembershipStatus.ACTIVE) {
            throw new ConflictException("User is already an active member of this office");
        }
        if (existing != null) {
            var previousStatus = existing.status();
            existing.reactivate(request.role(), OffsetDateTime.now());
            recordMemberEvent(
                    officeId,
                    actorUserId,
                    "OFFICE_MEMBER_REACTIVATED",
                    existing,
                    Map.of(
                            "previousStatus", previousStatus.name(),
                            "newStatus", existing.status().name(),
                            "role", existing.role().name()));
            return toMemberResponse(existing);
        }
        var office = officeRepository.getReferenceById(officeId);
        var membership = membershipRepository.save(new OfficeMembership(
                user,
                office,
                request.role(),
                OffsetDateTime.now()));
        recordMemberEvent(
                officeId,
                actorUserId,
                "OFFICE_MEMBER_ADDED",
                membership,
                Map.of("role", membership.role().name()));
        return toMemberResponse(membership);
    }

    @Transactional
    public OfficeMemberResponse updateMemberRole(
            Long actorUserId,
            Long officeId,
            Long memberUserId,
            UpdateOfficeMemberRoleRequest request
    ) {
        var actorMembership = requireOfficeManager(actorUserId, officeId);
        var target = requireActiveOfficeMember(memberUserId, officeId);
        if (actorUserId.equals(memberUserId)) {
            throw new ConflictException("Use another owner account to change your own office role");
        }
        requireOwnerIfOwnerRole(actorMembership, request.role());
        requireOwnerToModifyOwner(actorMembership, target);
        if (target.role() == MembershipRole.OWNER && request.role() != MembershipRole.OWNER) {
            requireAnotherActiveOwner(officeId);
        }
        var previousRole = target.role();
        target.changeRole(request.role());
        recordMemberEvent(
                officeId,
                actorUserId,
                "OFFICE_MEMBER_ROLE_CHANGED",
                target,
                Map.of(
                        "previousRole", previousRole.name(),
                        "newRole", target.role().name()));
        return toMemberResponse(target);
    }

    @Transactional
    public OfficeMemberResponse deactivateMember(Long actorUserId, Long officeId, Long memberUserId) {
        var actorMembership = requireOfficeManager(actorUserId, officeId);
        var target = requireActiveOfficeMember(memberUserId, officeId);
        if (actorUserId.equals(memberUserId)) {
            throw new ConflictException("Use another owner account to deactivate your own membership");
        }
        requireOwnerToModifyOwner(actorMembership, target);
        if (target.role() == MembershipRole.OWNER) {
            requireAnotherActiveOwner(officeId);
        }
        var previousStatus = target.status();
        target.suspend();
        recordMemberEvent(
                officeId,
                actorUserId,
                "OFFICE_MEMBER_DEACTIVATED",
                target,
                Map.of(
                        "previousStatus", previousStatus.name(),
                        "newStatus", target.status().name(),
                        "role", target.role().name()));
        return toMemberResponse(target);
    }

    private OfficeMembership requireMembership(Long userId, Long officeId) {
        return membershipRepository.findByUserIdAndOfficeIdAndStatus(userId, officeId, MembershipStatus.ACTIVE)
                .orElseThrow(() -> new UnauthorizedException("Office membership required"));
    }

    private OfficeMembership requireOfficeManager(Long userId, Long officeId) {
        if (platformAdminService.isPlatformAdmin(userId)) {
            return null;
        }
        var membership = requireMembership(userId, officeId);
        if (membership.role() != MembershipRole.OWNER && membership.role() != MembershipRole.ADMIN) {
            throw new ForbiddenException("Office admin role required");
        }
        return membership;
    }

    private void requireOfficeWorkspace(OfficeMembership membership) {
        if (membership.office().type() != OfficeType.OFFICE) {
            throw new ForbiddenException("Personal workspace cannot manage office members");
        }
    }

    private void requireOfficeWorkspace(Long officeId) {
        var office = officeRepository.findById(officeId)
                .orElseThrow(() -> new NotFoundException("Office not found"));
        if (office.type() != OfficeType.OFFICE) {
            throw new ForbiddenException("Personal workspace cannot manage office members");
        }
    }

    private OfficeMembership requireActiveOfficeMember(Long userId, Long officeId) {
        return membershipRepository.findByUserIdAndOfficeIdAndStatus(userId, officeId, MembershipStatus.ACTIVE)
                .orElseThrow(() -> new NotFoundException("Office member not found"));
    }

    private void requireOwnerIfOwnerRole(OfficeMembership actorMembership, MembershipRole role) {
        if (actorMembership == null) {
            return;
        }
        if (role == MembershipRole.OWNER && actorMembership.role() != MembershipRole.OWNER) {
            throw new ForbiddenException("Only an owner can assign owner role");
        }
    }

    private void requireOwnerToModifyOwner(OfficeMembership actorMembership, OfficeMembership target) {
        if (actorMembership == null) {
            return;
        }
        if (target.role() == MembershipRole.OWNER && actorMembership.role() != MembershipRole.OWNER) {
            throw new ForbiddenException("Only an owner can modify another owner");
        }
    }

    private void requireAnotherActiveOwner(Long officeId) {
        var activeOwnerCount = membershipRepository.countByOfficeIdAndRoleAndStatus(
                officeId,
                MembershipRole.OWNER,
                MembershipStatus.ACTIVE);
        if (activeOwnerCount <= 1) {
            throw new ConflictException("Office must keep at least one active owner");
        }
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

    private void recordMemberEvent(
            Long officeId,
            Long actorUserId,
            String eventType,
            OfficeMembership target,
            Map<String, Object> payload
    ) {
        operationEventService.record(
                officeId,
                OperationEventSeverity.INFO,
                eventType,
                null,
                null,
                "OFFICE_MEMBER",
                target.id(),
                actorUserId,
                null,
                eventType + " userId=" + target.user().id(),
                Map.of(
                        "targetUserId", target.user().id(),
                        "targetEmail", target.user().email(),
                        "details", payload));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private OfficeResponse toOfficeResponse(OfficeMembership membership) {
        var office = membership.office();
        return toOfficeResponse(office, membership.role());
    }

    private OfficeResponse toOfficeResponse(Office office, MembershipRole role) {
        return new OfficeResponse(
                office.id(),
                office.officeCode(),
                office.displayName(),
                office.type(),
                office.planCode(),
                role);
    }
}
