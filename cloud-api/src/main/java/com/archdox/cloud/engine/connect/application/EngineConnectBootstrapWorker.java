package com.archdox.cloud.engine.connect.application;

import com.archdox.cloud.engine.auth.application.EngineApiKeyManagementService;
import com.archdox.cloud.engine.connect.domain.EngineConnectClientType;
import com.archdox.cloud.engine.connect.dto.CreateEngineConnectBootstrapRequest;
import com.archdox.cloud.engine.connect.dto.EngineConnectBootstrapResponse;
import com.archdox.cloud.engine.connect.dto.EngineConnectClientResponse;
import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.global.api.ForbiddenException;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.office.infra.OfficeMembershipRepository;
import com.archdox.shared.MembershipStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EngineConnectBootstrapWorker {
    private final EngineApiKeyManagementService apiKeyManagementService;
    private final OfficeMembershipRepository membershipRepository;
    private final EngineConnectProperties properties;

    public EngineConnectBootstrapWorker(
            EngineApiKeyManagementService apiKeyManagementService,
            OfficeMembershipRepository membershipRepository,
            EngineConnectProperties properties
    ) {
        this.apiKeyManagementService = apiKeyManagementService;
        this.membershipRepository = membershipRepository;
        this.properties = properties;
    }

    public List<EngineConnectClientResponse> clients() {
        return List.of(
                client(EngineConnectClientType.CODEX, "Codex MCP client bootstrap package."),
                client(EngineConnectClientType.CLAUDE, "Claude MCP client bootstrap package."),
                client(EngineConnectClientType.CURSOR, "Cursor MCP client bootstrap package."),
                client(EngineConnectClientType.CHATGPT, "ChatGPT connector guidance package."),
                client(EngineConnectClientType.CUSTOM_AGENT, "Generic REST or MCP client bootstrap package."));
    }

    @Transactional
    public EngineConnectBootstrapResponse bootstrap(
            UserPrincipal principal,
            CreateEngineConnectBootstrapRequest request
    ) {
        if (request.clientType() == null) {
            throw new BadRequestException("clientType is required");
        }
        verifyOfficeAccess(principal, request.officeId());
        var displayName = displayName(request);
        var expiresAt = expiresAt(request.expiresAt());
        var issued = apiKeyManagementService.issue(
                displayName,
                principal.userId(),
                request.officeId(),
                principal.userId(),
                List.of(
                        EngineApiKeyManagementService.SCOPE_REVIEW_SESSION,
                        EngineApiKeyManagementService.SCOPE_LEGAL_UPDATES),
                expiresAt);
        var engineApiBaseUrl = properties.getEngineApiBaseUrl();
        var mcpServerUrl = properties.getMcpServerUrl();
        var headers = Map.of("X-ArchDox-Engine-Key", issued.apiKey());
        var connectionId = "eng_conn_" + issued.key().keyId();
        var createdAt = OffsetDateTime.now();
        return new EngineConnectBootstrapResponse(
                connectionId,
                request.clientType(),
                displayName,
                principal.userId(),
                request.officeId(),
                issued.key(),
                issued.apiKey(),
                engineApiBaseUrl,
                mcpServerUrl,
                headers,
                suggestedMcpConfig(mcpServerUrl, headers),
                curlExample(engineApiBaseUrl, issued.apiKey()),
                nextSteps(request.clientType()),
                createdAt);
    }

    private EngineConnectClientResponse client(EngineConnectClientType type, String description) {
        return new EngineConnectClientResponse(type, type.displayName(), description);
    }

    private void verifyOfficeAccess(UserPrincipal principal, Long officeId) {
        if (officeId == null) {
            return;
        }
        if (!membershipRepository.existsByUserIdAndOfficeIdAndStatus(
                principal.userId(),
                officeId,
                MembershipStatus.ACTIVE)) {
            throw new ForbiddenException(
                    "ENGINE_CONNECT_OFFICE_ACCESS_DENIED",
                    "errors.engineConnect.officeAccessDenied",
                    "User is not an active member of the requested office");
        }
    }

    private String displayName(CreateEngineConnectBootstrapRequest request) {
        if (request.displayName() != null && !request.displayName().isBlank()) {
            return request.displayName().trim();
        }
        return "ArchDox " + request.clientType().displayName() + " connection";
    }

    private OffsetDateTime expiresAt(OffsetDateTime requestedExpiresAt) {
        var now = OffsetDateTime.now();
        var maxExpiresAt = now.plusDays(properties.getMaxKeyTtlDays());
        if (requestedExpiresAt == null) {
            return now.plusDays(properties.getDefaultKeyTtlDays());
        }
        if (!requestedExpiresAt.isAfter(now)) {
            throw new BadRequestException("expiresAt must be in the future");
        }
        if (requestedExpiresAt.isAfter(maxExpiresAt)) {
            throw new BadRequestException("expiresAt exceeds the maximum Engine Connect key TTL");
        }
        return requestedExpiresAt;
    }

    private Map<String, Object> suggestedMcpConfig(String mcpServerUrl, Map<String, String> headers) {
        return Map.of(
                "mcpServers",
                Map.of(
                        "archdox",
                        Map.of(
                                "url", mcpServerUrl,
                                "headers", headers)));
    }

    private String curlExample(String engineApiBaseUrl, String apiKey) {
        return """
                curl -X POST "%s/api/v1/engine/external/review-sessions" \\
                  -H "Content-Type: application/json" \\
                  -H "X-ArchDox-Engine-Key: %s" \\
                  -d '{"reviewPurpose":"preflight"}'
                """.formatted(engineApiBaseUrl, apiKey).strip();
    }

    private List<String> nextSteps(EngineConnectClientType clientType) {
        return switch (clientType) {
            case CODEX -> List.of(
                    "Add the suggested MCP server config to Codex.",
                    "Keep the Engine API key secret. It is shown only once.",
                    "Ask Codex to validate an inspection report through ArchDox.");
            case CLAUDE -> List.of(
                    "Add the suggested MCP server config to Claude Desktop or the Claude connector surface.",
                    "Keep the Engine API key secret. It is shown only once.",
                    "Ask Claude to call ArchDox for report validation.");
            case CURSOR -> List.of(
                    "Add the suggested MCP server config to Cursor.",
                    "Keep the Engine API key secret. It is shown only once.",
                    "Use Cursor's agent mode to call ArchDox validation tools.");
            case CHATGPT -> List.of(
                    "Use the Engine API key with the future ArchDox connector or a custom action gateway.",
                    "Keep the Engine API key secret. It is shown only once.",
                    "For now, use the REST Engine API until MCP connector support is finalized.");
            case CUSTOM_AGENT -> List.of(
                    "Use the Engine API key in the X-ArchDox-Engine-Key header.",
                    "Keep the Engine API key secret. It is shown only once.",
                    "Call the REST Engine API directly or implement the suggested MCP config.");
        };
    }
}
