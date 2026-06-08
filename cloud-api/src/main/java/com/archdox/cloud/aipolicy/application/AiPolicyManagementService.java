package com.archdox.cloud.aipolicy.application;

import com.archdox.cloud.agent.application.AgentOutboundMessage;
import com.archdox.cloud.agent.application.ArchDoxAgentSessionRegistry;
import com.archdox.cloud.agent.infra.ArchDoxAgentRepository;
import com.archdox.cloud.aipolicy.domain.AiCredentialDeliveryMode;
import com.archdox.cloud.aipolicy.domain.AiHarnessPolicy;
import com.archdox.cloud.aipolicy.domain.AiHarnessPolicyKey;
import com.archdox.cloud.aipolicy.domain.AiProviderCredential;
import com.archdox.cloud.aipolicy.domain.AiProviderCredentialStatus;
import com.archdox.cloud.aipolicy.domain.AiProviderType;
import com.archdox.cloud.aipolicy.domain.OfficeAiPolicy;
import com.archdox.cloud.aipolicy.dto.AiHarnessPolicyResponse;
import com.archdox.cloud.aipolicy.dto.AiProviderCredentialResponse;
import com.archdox.cloud.aipolicy.dto.CreateAiProviderCredentialRequest;
import com.archdox.cloud.aipolicy.dto.OfficeAiPolicyResponse;
import com.archdox.cloud.aipolicy.dto.UpdateAiHarnessPolicyRequest;
import com.archdox.cloud.aipolicy.dto.UpdateAiProviderCredentialRequest;
import com.archdox.cloud.aipolicy.dto.UpdateOfficeAiPolicyRequest;
import com.archdox.cloud.aipolicy.infra.AiHarnessPolicyRepository;
import com.archdox.cloud.aipolicy.infra.AiProviderCredentialRepository;
import com.archdox.cloud.aipolicy.infra.OfficeAiPolicyRepository;
import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.global.api.ConflictException;
import com.archdox.cloud.global.api.NotFoundException;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.office.domain.Office;
import com.archdox.cloud.office.infra.OfficeRepository;
import com.archdox.cloud.operation.application.OperationEventService;
import com.archdox.cloud.operation.domain.OperationEventSeverity;
import com.archdox.cloud.platformadmin.application.PlatformAdminService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class AiPolicyManagementService {
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 500;

    private final PlatformAdminService platformAdminService;
    private final AiProviderCredentialRepository providerRepository;
    private final AiHarnessPolicyRepository harnessPolicyRepository;
    private final OfficeAiPolicyRepository officePolicyRepository;
    private final OfficeRepository officeRepository;
    private final ArchDoxAgentRepository agentRepository;
    private final ArchDoxAgentSessionRegistry sessionRegistry;
    private final OperationEventService operationEventService;
    private final AiCredentialCipher credentialCipher;

    public AiPolicyManagementService(
            PlatformAdminService platformAdminService,
            AiProviderCredentialRepository providerRepository,
            AiHarnessPolicyRepository harnessPolicyRepository,
            OfficeAiPolicyRepository officePolicyRepository,
            OfficeRepository officeRepository,
            ArchDoxAgentRepository agentRepository,
            ArchDoxAgentSessionRegistry sessionRegistry,
            OperationEventService operationEventService,
            AiCredentialCipher credentialCipher
    ) {
        this.platformAdminService = platformAdminService;
        this.providerRepository = providerRepository;
        this.harnessPolicyRepository = harnessPolicyRepository;
        this.officePolicyRepository = officePolicyRepository;
        this.officeRepository = officeRepository;
        this.agentRepository = agentRepository;
        this.sessionRegistry = sessionRegistry;
        this.operationEventService = operationEventService;
        this.credentialCipher = credentialCipher;
    }

    @Transactional(readOnly = true)
    public List<AiProviderCredentialResponse> providers(UserPrincipal principal) {
        platformAdminService.requirePlatformAdmin(principal);
        return providerRepository.findAllByOrderByProviderCodeAsc()
                .stream()
                .map(this::toProviderResponse)
                .toList();
    }

    @Transactional
    public AiProviderCredentialResponse createProvider(
            UserPrincipal principal,
            CreateAiProviderCredentialRequest request
    ) {
        platformAdminService.requirePlatformAdmin(principal);
        var providerCode = providerCode(request.providerCode());
        providerRepository.findByProviderCode(providerCode)
                .ifPresent(existing -> {
                    throw new ConflictException("AI provider code already exists");
                });
        var type = providerType(request.providerType());
        var apiKey = validateApiKey(type, blankToNull(request.apiKey()), true);
        var provider = providerRepository.save(new AiProviderCredential(
                providerCode,
                required(request.displayName(), "displayName"),
                type,
                blankToNull(request.baseUrl()),
                blankToNull(request.defaultModel()),
                credentialCipher.encrypt(apiKey),
                credentialCipher.fingerprint(apiKey),
                principal.userId(),
                OffsetDateTime.now()));
        recordProviderEvent(principal, provider, "AI_PROVIDER_CREDENTIAL_CREATED", "AI provider credential created.");
        return toProviderResponse(provider);
    }

    @Transactional
    public AiProviderCredentialResponse updateProvider(
            UserPrincipal principal,
            Long providerId,
            UpdateAiProviderCredentialRequest request
    ) {
        platformAdminService.requirePlatformAdmin(principal);
        var provider = requireProvider(providerId);
        var type = providerType(request.providerType());
        var apiKey = validateApiKey(type, blankToNull(request.apiKey()), false);
        provider.update(
                required(request.displayName(), "displayName"),
                type,
                blankToNull(request.baseUrl()),
                blankToNull(request.defaultModel()),
                apiKey != null,
                credentialCipher.encrypt(apiKey),
                credentialCipher.fingerprint(apiKey),
                OffsetDateTime.now());
        recordProviderEvent(principal, provider, "AI_PROVIDER_CREDENTIAL_UPDATED", "AI provider credential updated.");
        notifyOfficesUsingProviderAfterCommit(provider.id());
        return toProviderResponse(provider);
    }

    @Transactional
    public AiProviderCredentialResponse publishProvider(UserPrincipal principal, Long providerId) {
        platformAdminService.requirePlatformAdmin(principal);
        var provider = requireProvider(providerId);
        provider.publish(OffsetDateTime.now());
        recordProviderEvent(principal, provider, "AI_PROVIDER_CREDENTIAL_PUBLISHED", "AI provider credential published.");
        notifyOfficesUsingProviderAfterCommit(provider.id());
        return toProviderResponse(provider);
    }

    @Transactional(readOnly = true)
    public List<OfficeAiPolicyResponse> officePolicies(UserPrincipal principal, Integer limit) {
        platformAdminService.requirePlatformAdmin(principal);
        var size = Math.max(1, Math.min(limit == null ? DEFAULT_LIMIT : limit, MAX_LIMIT));
        var offices = officeRepository.findAll(PageRequest.of(0, size, Sort.by("id").ascending())).getContent();
        var policiesByOfficeId = officePolicyRepository.findAll()
                .stream()
                .collect(Collectors.toMap(OfficeAiPolicy::officeId, Function.identity()));
        var providersById = providerRepository.findAll()
                .stream()
                .collect(Collectors.toMap(AiProviderCredential::id, Function.identity()));
        return offices.stream()
                .map(office -> toOfficePolicyResponse(office, policiesByOfficeId.get(office.id()), providersById))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AiHarnessPolicyResponse> harnessPolicies(UserPrincipal principal) {
        platformAdminService.requirePlatformAdmin(principal);
        var policiesByKey = harnessPolicyRepository.findAllByOrderByPolicyKeyAsc()
                .stream()
                .collect(Collectors.toMap(AiHarnessPolicy::policyKey, Function.identity()));
        var providersById = providerRepository.findAll()
                .stream()
                .collect(Collectors.toMap(AiProviderCredential::id, Function.identity()));
        return java.util.Arrays.stream(AiHarnessPolicyKey.values())
                .map(key -> toHarnessPolicyResponse(policiesByKey.get(key), key, providersById))
                .toList();
    }

    @Transactional
    public AiHarnessPolicyResponse updateHarnessPolicy(
            UserPrincipal principal,
            String policyKey,
            UpdateAiHarnessPolicyRequest request
    ) {
        platformAdminService.requirePlatformAdmin(principal);
        var key = harnessPolicyKey(policyKey);
        var providerId = request.providerCredentialId();
        var provider = providerId == null ? null : requireProvider(providerId);
        if (provider != null && provider.status() == AiProviderCredentialStatus.DISABLED) {
            throw new BadRequestException("Disabled AI provider credential cannot be assigned");
        }
        var now = OffsetDateTime.now();
        var policy = harnessPolicyRepository.findByPolicyKey(key)
                .orElseGet(() -> harnessPolicyRepository.save(new AiHarnessPolicy(key, principal.userId(), now)));
        policy.update(
                request.enabled(),
                provider == null ? null : provider.id(),
                request.modelName(),
                request.maxAttempts(),
                request.timeoutSeconds(),
                principal.userId(),
                now);
        recordHarnessPolicyEvent(principal, policy, provider);
        var providersById = providerRepository.findAll()
                .stream()
                .collect(Collectors.toMap(AiProviderCredential::id, Function.identity()));
        return toHarnessPolicyResponse(policy, key, providersById);
    }

    @Transactional
    public OfficeAiPolicyResponse updateOfficePolicy(
            UserPrincipal principal,
            Long officeId,
            UpdateOfficeAiPolicyRequest request
    ) {
        platformAdminService.requirePlatformAdmin(principal);
        var office = officeRepository.findById(officeId)
                .orElseThrow(() -> new NotFoundException("Office not found"));
        var deliveryMode = credentialDeliveryMode(request.credentialDeliveryMode());
        if (deliveryMode != AiCredentialDeliveryMode.PROXY_ONLY) {
            throw new BadRequestException(
                    "AI_CREDENTIAL_DELIVERY_UNSUPPORTED",
                    "error.aiPolicy.credentialDeliveryUnsupported",
                    "Only PROXY_ONLY AI credential delivery is supported in MVP");
        }
        var providerId = request.preferredProviderCredentialId();
        var provider = providerId == null ? null : requireProvider(providerId);
        if (provider != null && provider.status() == AiProviderCredentialStatus.DISABLED) {
            throw new BadRequestException("Disabled AI provider credential cannot be assigned");
        }
        if (Boolean.TRUE.equals(request.aiEnabled())
                && (Boolean.TRUE.equals(request.documentReviewAiEnabled())
                || Boolean.TRUE.equals(request.documentGenerationAiEnabled()))
                && provider == null) {
            throw new BadRequestException("AI provider credential is required when AI features are enabled");
        }
        var now = OffsetDateTime.now();
        var policy = officePolicyRepository.findByOfficeId(officeId)
                .orElseGet(() -> officePolicyRepository.save(new OfficeAiPolicy(officeId, principal.userId(), now)));
        var nextBudgetCurrency = request.budgetCurrency() == null
                ? policy.budgetCurrency()
                : budgetCurrency(request.budgetCurrency());
        policy.update(
                request.aiEnabled(),
                request.documentReviewAiEnabled(),
                request.documentGenerationAiEnabled(),
                provider == null ? null : provider.id(),
                deliveryMode,
                request.budgetEnforcementEnabled(),
                nonNegativeAmount(request.monthlyBudgetAmount(), "monthlyBudgetAmount"),
                nextBudgetCurrency,
                nonNegativeInteger(request.dailyCallLimit(), "dailyCallLimit"),
                nonNegativeLong(request.monthlyTokenLimit(), "monthlyTokenLimit"),
                principal.userId(),
                now);
        recordOfficePolicyEvent(principal, policy, "OFFICE_AI_POLICY_UPDATED", "Office AI policy updated.");
        notifyOfficeAgentsAfterCommit(officeId);
        return toOfficePolicyResponse(
                office,
                policy,
                providerRepository.findAll().stream().collect(Collectors.toMap(AiProviderCredential::id, Function.identity())));
    }

    @Transactional(readOnly = true)
    public EffectiveAgentAiPolicy effectiveAgentPolicy(Long officeId) {
        var policy = officePolicyRepository.findByOfficeId(officeId).orElse(null);
        if (policy == null || !policy.aiEnabled()) {
            return EffectiveAgentAiPolicy.disabled("AI is disabled for this office.");
        }
        var provider = policy.preferredProviderCredentialId() == null
                ? null
                : providerRepository.findById(policy.preferredProviderCredentialId()).orElse(null);
        if (provider == null) {
            return EffectiveAgentAiPolicy.from(policy, null, "AI provider is not assigned.");
        }
        if (provider.status() != AiProviderCredentialStatus.ACTIVE) {
            return EffectiveAgentAiPolicy.from(policy, provider, "Assigned AI provider is not active.");
        }
        return EffectiveAgentAiPolicy.from(policy, provider, "AI calls must use Cloud API proxy. Provider API keys are not delivered to Agents.");
    }

    private void notifyOfficesUsingProviderAfterCommit(Long providerId) {
        var officeIds = officePolicyRepository.findByPreferredProviderCredentialIdAndAiEnabledTrue(providerId)
                .stream()
                .map(OfficeAiPolicy::officeId)
                .distinct()
                .toList();
        officeIds.forEach(this::notifyOfficeAgentsAfterCommit);
    }

    private void notifyOfficeAgentsAfterCommit(Long officeId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    notifyOfficeAgents(officeId);
                }
            });
            return;
        }
        notifyOfficeAgents(officeId);
    }

    private void notifyOfficeAgents(Long officeId) {
        var policy = effectiveAgentPolicy(officeId).toMap();
        var agents = agentRepository.findByOfficeIdOrderByLastSeenAtDesc(officeId, PageRequest.of(0, MAX_LIMIT));
        for (var agent : agents) {
            sessionRegistry.send(agent.id(), AgentOutboundMessage.aiPolicyChanged(agent.id(), policy));
        }
    }

    private AiProviderCredential requireProvider(Long providerId) {
        if (providerId == null) {
            throw new BadRequestException("providerId is required");
        }
        return providerRepository.findById(providerId)
                .orElseThrow(() -> new NotFoundException("AI provider credential not found"));
    }

    private AiProviderCredentialResponse toProviderResponse(AiProviderCredential provider) {
        var fingerprint = provider.apiKeyFingerprint();
        return new AiProviderCredentialResponse(
                provider.id(),
                provider.providerCode(),
                provider.displayName(),
                provider.providerType(),
                provider.status(),
                provider.baseUrl(),
                provider.defaultModel(),
                provider.credentialVersion(),
                fingerprint,
                credentialCipher.maskFingerprint(fingerprint),
                provider.encryptedApiKey() != null && !provider.encryptedApiKey().isBlank(),
                provider.createdAt(),
                provider.updatedAt(),
                provider.publishedAt());
    }

    private OfficeAiPolicyResponse toOfficePolicyResponse(
            Office office,
            OfficeAiPolicy policy,
            Map<Long, AiProviderCredential> providersById
    ) {
        var provider = policy == null || policy.preferredProviderCredentialId() == null
                ? null
                : providersById.get(policy.preferredProviderCredentialId());
        var effective = policy == null
                ? EffectiveAgentAiPolicy.disabled("AI policy is not configured.")
                : effectivePolicyFromLoaded(policy, provider);
        return new OfficeAiPolicyResponse(
                office.id(),
                office.officeCode(),
                office.displayName(),
                policy != null && policy.aiEnabled(),
                policy != null && policy.documentReviewAiEnabled(),
                policy != null && policy.documentGenerationAiEnabled(),
                policy == null ? null : policy.preferredProviderCredentialId(),
                provider == null ? null : provider.providerCode(),
                provider == null ? null : provider.providerType().name(),
                policy == null ? AiCredentialDeliveryMode.PROXY_ONLY : policy.credentialDeliveryMode(),
                policy != null && policy.budgetEnforcementEnabled(),
                policy == null ? null : policy.monthlyBudgetAmount(),
                policy == null ? "USD" : policy.budgetCurrency(),
                policy == null ? null : policy.dailyCallLimit(),
                policy == null ? null : policy.monthlyTokenLimit(),
                policy == null ? 0 : policy.policyVersion(),
                effective.enabled(),
                effective.message(),
                policy == null ? null : policy.updatedAt());
    }

    private AiHarnessPolicyResponse toHarnessPolicyResponse(
            AiHarnessPolicy policy,
            AiHarnessPolicyKey key,
            Map<Long, AiProviderCredential> providersById
    ) {
        var provider = policy == null || policy.providerCredentialId() == null
                ? null
                : providersById.get(policy.providerCredentialId());
        var modelName = policy == null ? null : blankToNull(policy.modelName());
        var effectiveModel = modelName != null
                ? modelName
                : provider == null ? null : blankToNull(provider.defaultModel());
        var effective = effectiveHarnessPolicy(policy, provider, effectiveModel);
        return new AiHarnessPolicyResponse(
                policy == null ? null : policy.id(),
                key.name(),
                key.displayName(),
                key.description(),
                policy != null && policy.enabled(),
                policy == null ? null : policy.providerCredentialId(),
                provider == null ? null : provider.providerCode(),
                provider == null ? null : provider.displayName(),
                provider == null ? null : provider.providerType().name(),
                modelName,
                effectiveModel,
                policy == null ? 2 : policy.maxAttempts(),
                policy == null ? 90 : policy.timeoutSeconds(),
                policy == null ? 0 : policy.policyVersion(),
                effective.enabled(),
                effective.message(),
                policy == null ? null : policy.updatedAt());
    }

    private EffectiveHarnessPolicy effectiveHarnessPolicy(
            AiHarnessPolicy policy,
            AiProviderCredential provider,
            String effectiveModel
    ) {
        if (policy == null) {
            return new EffectiveHarnessPolicy(false, "AI 작업 정책이 아직 생성되지 않았습니다.");
        }
        if (!policy.enabled()) {
            return new EffectiveHarnessPolicy(false, "AI 작업 정책이 꺼져 있습니다.");
        }
        if (provider == null) {
            return new EffectiveHarnessPolicy(false, "AI 제공자가 지정되지 않았습니다.");
        }
        if (provider.status() != AiProviderCredentialStatus.ACTIVE) {
            return new EffectiveHarnessPolicy(false, "지정된 AI 제공자가 활성 상태가 아닙니다.");
        }
        if (effectiveModel == null || effectiveModel.isBlank()) {
            return new EffectiveHarnessPolicy(false, "모델명이 설정되지 않았습니다.");
        }
        return new EffectiveHarnessPolicy(true, "이 AI 작업은 지정된 provider/model로 실행됩니다.");
    }

    private EffectiveAgentAiPolicy effectivePolicyFromLoaded(OfficeAiPolicy policy, AiProviderCredential provider) {
        if (!policy.aiEnabled()) {
            return EffectiveAgentAiPolicy.disabled("AI is disabled for this office.");
        }
        if (provider == null) {
            return EffectiveAgentAiPolicy.from(policy, null, "AI provider is not assigned.");
        }
        if (provider.status() != AiProviderCredentialStatus.ACTIVE) {
            return EffectiveAgentAiPolicy.from(policy, provider, "Assigned AI provider is not active.");
        }
        return EffectiveAgentAiPolicy.from(policy, provider, "AI calls must use Cloud API proxy. Provider API keys are not delivered to Agents.");
    }

    private void recordProviderEvent(
            UserPrincipal principal,
            AiProviderCredential provider,
            String eventType,
            String message
    ) {
        operationEventService.record(
                null,
                OperationEventSeverity.INFO,
                eventType,
                "ai-provider-management",
                "ai-provider:" + provider.id(),
                "AI_PROVIDER_CREDENTIAL",
                provider.id(),
                principal.userId(),
                null,
                message,
                Map.of(
                        "providerId", provider.id(),
                        "providerCode", provider.providerCode(),
                        "providerType", provider.providerType().name(),
                        "status", provider.status().name(),
                        "credentialVersion", provider.credentialVersion()));
    }

    private void recordOfficePolicyEvent(
            UserPrincipal principal,
            OfficeAiPolicy policy,
            String eventType,
            String message
    ) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("officeId", policy.officeId());
        payload.put("aiEnabled", policy.aiEnabled());
        payload.put("documentReviewAiEnabled", policy.documentReviewAiEnabled());
        payload.put("documentGenerationAiEnabled", policy.documentGenerationAiEnabled());
        payload.put("preferredProviderCredentialId", policy.preferredProviderCredentialId());
        payload.put("credentialDeliveryMode", policy.credentialDeliveryMode().name());
        payload.put("budgetEnforcementEnabled", policy.budgetEnforcementEnabled());
        payload.put("monthlyBudgetAmount", policy.monthlyBudgetAmount());
        payload.put("budgetCurrency", policy.budgetCurrency());
        payload.put("dailyCallLimit", policy.dailyCallLimit());
        payload.put("monthlyTokenLimit", policy.monthlyTokenLimit());
        payload.put("policyVersion", policy.policyVersion());
        operationEventService.record(
                policy.officeId(),
                OperationEventSeverity.INFO,
                eventType,
                "ai-policy-management",
                "office:" + policy.officeId(),
                "OFFICE_AI_POLICY",
                policy.officeId(),
                principal.userId(),
                null,
                message,
                payload);
    }

    private void recordHarnessPolicyEvent(
            UserPrincipal principal,
            AiHarnessPolicy policy,
            AiProviderCredential provider
    ) {
        operationEventService.record(
                null,
                OperationEventSeverity.INFO,
                "AI_HARNESS_POLICY_UPDATED",
                "ai-policy-management",
                "ai-harness-policy:" + policy.policyKey().name(),
                "AI_HARNESS_POLICY",
                policy.id(),
                principal.userId(),
                null,
                "AI harness policy updated.",
                Map.of(
                        "policyKey", policy.policyKey().name(),
                        "enabled", policy.enabled(),
                        "providerCredentialId", policy.providerCredentialId() == null ? "" : policy.providerCredentialId(),
                        "providerCode", provider == null ? "" : provider.providerCode(),
                        "modelName", policy.modelName() == null ? "" : policy.modelName(),
                        "maxAttempts", policy.maxAttempts(),
                        "timeoutSeconds", policy.timeoutSeconds(),
                        "policyVersion", policy.policyVersion()));
    }

    private AiProviderType providerType(String providerType) {
        var normalized = required(providerType, "providerType").trim().toUpperCase(Locale.ROOT);
        return AiProviderType.valueOf(normalized);
    }

    private AiHarnessPolicyKey harnessPolicyKey(String policyKey) {
        try {
            return AiHarnessPolicyKey.valueOf(required(policyKey, "policyKey").trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException(
                    "AI_HARNESS_POLICY_UNKNOWN",
                    "error.aiHarnessPolicy.unknown",
                    "Unknown AI harness policy key.");
        }
    }

    private AiCredentialDeliveryMode credentialDeliveryMode(String credentialDeliveryMode) {
        if (credentialDeliveryMode == null || credentialDeliveryMode.isBlank()) {
            return AiCredentialDeliveryMode.PROXY_ONLY;
        }
        return AiCredentialDeliveryMode.valueOf(credentialDeliveryMode.trim().toUpperCase(Locale.ROOT));
    }

    private String budgetCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            return "USD";
        }
        var normalized = currency.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() > 12) {
            throw new BadRequestException("budgetCurrency is too long");
        }
        return normalized;
    }

    private BigDecimal nonNegativeAmount(BigDecimal value, String fieldName) {
        if (value != null && value.signum() < 0) {
            throw new BadRequestException(fieldName + " must be greater than or equal to 0");
        }
        return value;
    }

    private Integer nonNegativeInteger(Integer value, String fieldName) {
        if (value != null && value < 0) {
            throw new BadRequestException(fieldName + " must be greater than or equal to 0");
        }
        return value;
    }

    private Long nonNegativeLong(Long value, String fieldName) {
        if (value != null && value < 0) {
            throw new BadRequestException(fieldName + " must be greater than or equal to 0");
        }
        return value;
    }

    private String providerCode(String providerCode) {
        return required(providerCode, "providerCode").trim().toLowerCase(Locale.ROOT);
    }

    private String required(String value, String fieldName) {
        var normalized = blankToNull(value);
        if (normalized == null) {
            throw new BadRequestException(fieldName + " is required");
        }
        return normalized;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private record EffectiveHarnessPolicy(boolean enabled, String message) {
    }

    private String validateApiKey(AiProviderType providerType, String apiKey, boolean create) {
        if (providerType != AiProviderType.OPENAI || apiKey == null) {
            return apiKey;
        }
        if (apiKey.contains(" ") || apiKey.contains("\n") || apiKey.contains("\r")
                || !(apiKey.startsWith("sk-") || apiKey.startsWith("sk-proj-"))) {
            throw new BadRequestException(
                    "AI_PROVIDER_API_KEY_INVALID",
                    "error.aiProvider.apiKeyInvalid",
                    create
                            ? "OpenAI API key must start with sk- or sk-proj-."
                            : "OpenAI API key is invalid. Leave it empty to keep the existing key, or paste a key starting with sk- or sk-proj-.");
        }
        return apiKey;
    }
}
