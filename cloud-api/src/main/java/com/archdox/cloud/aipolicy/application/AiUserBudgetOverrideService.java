package com.archdox.cloud.aipolicy.application;

import com.archdox.cloud.account.infra.UserAccountRepository;
import com.archdox.cloud.aipolicy.domain.AiPolicyDefaults;
import com.archdox.cloud.aipolicy.domain.AiUserBudgetOverride;
import com.archdox.cloud.aipolicy.dto.AiUserBudgetOverrideResponse;
import com.archdox.cloud.aipolicy.dto.CreateAiUserBudgetOverrideRequest;
import com.archdox.cloud.aipolicy.dto.DisableAiUserBudgetOverrideRequest;
import com.archdox.cloud.aipolicy.infra.AiUserBudgetOverrideRepository;
import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.global.api.NotFoundException;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.office.infra.OfficeMembershipRepository;
import com.archdox.cloud.office.infra.OfficeRepository;
import com.archdox.cloud.operation.application.OperationEventService;
import com.archdox.cloud.operation.domain.OperationEventSeverity;
import com.archdox.cloud.platformadmin.application.PlatformAdminService;
import com.archdox.shared.MembershipStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiUserBudgetOverrideService {
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 500;

    private final AiUserBudgetOverrideRepository overrideRepository;
    private final OfficeRepository officeRepository;
    private final UserAccountRepository userRepository;
    private final OfficeMembershipRepository membershipRepository;
    private final PlatformAdminService platformAdminService;
    private final OperationEventService operationEventService;

    public AiUserBudgetOverrideService(
            AiUserBudgetOverrideRepository overrideRepository,
            OfficeRepository officeRepository,
            UserAccountRepository userRepository,
            OfficeMembershipRepository membershipRepository,
            PlatformAdminService platformAdminService,
            OperationEventService operationEventService
    ) {
        this.overrideRepository = overrideRepository;
        this.officeRepository = officeRepository;
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.platformAdminService = platformAdminService;
        this.operationEventService = operationEventService;
    }

    @Transactional(readOnly = true)
    public List<AiUserBudgetOverrideResponse> overrides(UserPrincipal principal, Integer limit) {
        platformAdminService.requirePlatformAdmin(principal);
        var now = OffsetDateTime.now();
        var overrides = overrideRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, boundedLimit(limit)));
        return responses(overrides, now);
    }

    @Transactional
    public AiUserBudgetOverrideResponse createOverride(
            UserPrincipal principal,
            CreateAiUserBudgetOverrideRequest request
    ) {
        platformAdminService.requirePlatformAdmin(principal);
        var officeId = required(request.officeId(), "officeId");
        var userId = required(request.userId(), "userId");
        var office = officeRepository.findById(officeId)
                .orElseThrow(() -> new NotFoundException("Office not found"));
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        if (!membershipRepository.existsByUserIdAndOfficeIdAndStatus(userId, officeId, MembershipStatus.ACTIVE)) {
            throw new BadRequestException(
                    "AI_USER_NOT_OFFICE_MEMBER",
                    "error.ai.userNotOfficeMember",
                    "User is not an active member of the selected office");
        }
        var dailyCallLimit = nonNegativeInteger(request.dailyCallLimit(), "dailyCallLimit");
        var monthlyTokenLimit = nonNegativeLong(request.monthlyTokenLimit(), "monthlyTokenLimit");
        var monthlyBudgetAmount = nonNegativeAmount(request.monthlyBudgetAmount(), "monthlyBudgetAmount");
        if (dailyCallLimit == null && monthlyTokenLimit == null && monthlyBudgetAmount == null) {
            throw new BadRequestException("At least one user AI limit override is required");
        }
        var now = OffsetDateTime.now();
        var expiresAt = request.expiresAt();
        if (expiresAt != null && !expiresAt.isAfter(now)) {
            throw new BadRequestException("expiresAt must be in the future");
        }
        var reason = requiredText(request.reason(), "reason");
        overrideRepository.findActiveByOfficeIdAndUserId(officeId, userId, now)
                .forEach(existing -> existing.disable(
                        principal.userId(),
                        "Replaced by a newer user AI budget override.",
                        now));
        var override = overrideRepository.save(new AiUserBudgetOverride(
                officeId,
                userId,
                dailyCallLimit,
                monthlyTokenLimit,
                monthlyBudgetAmount,
                currency(request.budgetCurrency()),
                reason,
                expiresAt,
                principal.userId(),
                now));
        recordEvent(
                principal,
                override,
                "AI_USER_BUDGET_OVERRIDE_CREATED",
                "User AI budget override created.",
                Map.of(
                        "officeId", office.id(),
                        "officeCode", office.officeCode(),
                        "userId", user.id(),
                        "userEmail", user.email(),
                        "dailyCallLimit", valueOrEmpty(dailyCallLimit),
                        "monthlyTokenLimit", valueOrEmpty(monthlyTokenLimit),
                        "monthlyBudgetAmount", monthlyBudgetAmount == null ? "" : monthlyBudgetAmount.toPlainString(),
                        "budgetCurrency", override.budgetCurrency(),
                        "expiresAt", expiresAt == null ? "" : expiresAt.toString(),
                        "reason", reason));
        return toResponse(override, office, user, now);
    }

    @Transactional
    public AiUserBudgetOverrideResponse disableOverride(
            UserPrincipal principal,
            Long overrideId,
            DisableAiUserBudgetOverrideRequest request
    ) {
        platformAdminService.requirePlatformAdmin(principal);
        var override = overrideRepository.findById(overrideId)
                .orElseThrow(() -> new NotFoundException("AI user budget override not found"));
        var now = OffsetDateTime.now();
        override.disable(principal.userId(), request == null ? null : request.reason(), now);
        recordEvent(
                principal,
                override,
                "AI_USER_BUDGET_OVERRIDE_DISABLED",
                "User AI budget override disabled.",
                Map.of(
                        "overrideId", override.id(),
                        "officeId", override.officeId(),
                        "userId", override.userId(),
                        "reason", request == null || request.reason() == null ? "" : request.reason()));
        return response(override, now);
    }

    private List<AiUserBudgetOverrideResponse> responses(List<AiUserBudgetOverride> overrides, OffsetDateTime now) {
        var officeIds = overrides.stream().map(AiUserBudgetOverride::officeId).collect(Collectors.toSet());
        var userIds = overrides.stream().map(AiUserBudgetOverride::userId).collect(Collectors.toSet());
        var officesById = officeRepository.findAllById(officeIds).stream()
                .collect(Collectors.toMap(com.archdox.cloud.office.domain.Office::id, Function.identity()));
        var usersById = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(com.archdox.cloud.account.domain.UserAccount::id, Function.identity()));
        return overrides.stream()
                .map(override -> toResponse(
                        override,
                        officesById.get(override.officeId()),
                        usersById.get(override.userId()),
                        now))
                .toList();
    }

    private AiUserBudgetOverrideResponse response(AiUserBudgetOverride override, OffsetDateTime now) {
        var office = officeRepository.findById(override.officeId()).orElse(null);
        var user = userRepository.findById(override.userId()).orElse(null);
        return toResponse(override, office, user, now);
    }

    private AiUserBudgetOverrideResponse toResponse(
            AiUserBudgetOverride override,
            com.archdox.cloud.office.domain.Office office,
            com.archdox.cloud.account.domain.UserAccount user,
            OffsetDateTime now
    ) {
        return new AiUserBudgetOverrideResponse(
                override.id(),
                override.officeId(),
                office == null ? null : office.officeCode(),
                office == null ? null : office.displayName(),
                override.userId(),
                user == null ? null : user.email(),
                user == null ? null : user.name(),
                override.dailyCallLimit(),
                override.monthlyTokenLimit(),
                override.monthlyBudgetAmount(),
                override.budgetCurrency(),
                override.reason(),
                override.activeAt(now),
                override.expiresAt(),
                override.createdByUserId(),
                override.createdAt(),
                override.disabledByUserId(),
                override.disableReason(),
                override.disabledAt());
    }

    private void recordEvent(
            UserPrincipal principal,
            AiUserBudgetOverride override,
            String eventType,
            String message,
            Map<String, Object> payload
    ) {
        operationEventService.record(
                override.officeId(),
                OperationEventSeverity.INFO,
                eventType,
                "ai-budget-management",
                "user-ai-budget-override:" + override.id(),
                "AI_USER_BUDGET_OVERRIDE",
                override.id(),
                principal.userId(),
                null,
                message,
                payload);
    }

    private Long required(Long value, String fieldName) {
        if (value == null) {
            throw new BadRequestException(fieldName + " is required");
        }
        return value;
    }

    private String requiredText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(fieldName + " is required");
        }
        var normalized = value.trim();
        if (normalized.length() > 1000) {
            throw new BadRequestException(fieldName + " is too long");
        }
        return normalized;
    }

    private Integer nonNegativeInteger(Integer value, String fieldName) {
        if (value == null) {
            return null;
        }
        if (value < 0) {
            throw new BadRequestException(fieldName + " must be greater than or equal to 0");
        }
        return value;
    }

    private Long nonNegativeLong(Long value, String fieldName) {
        if (value == null) {
            return null;
        }
        if (value < 0) {
            throw new BadRequestException(fieldName + " must be greater than or equal to 0");
        }
        return value;
    }

    private BigDecimal nonNegativeAmount(BigDecimal value, String fieldName) {
        if (value == null) {
            return null;
        }
        if (value.signum() < 0) {
            throw new BadRequestException(fieldName + " must be greater than or equal to 0");
        }
        return value;
    }

    private String currency(String value) {
        if (value == null || value.isBlank()) {
            return AiPolicyDefaults.DEFAULT_BUDGET_CURRENCY;
        }
        var normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() > 12) {
            throw new BadRequestException("budgetCurrency is too long");
        }
        return normalized;
    }

    private Object valueOrEmpty(Object value) {
        return value == null ? "" : value;
    }

    private int boundedLimit(Integer limit) {
        return Math.max(1, Math.min(limit == null ? DEFAULT_LIMIT : limit, MAX_LIMIT));
    }
}
