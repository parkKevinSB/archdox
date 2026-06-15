package com.archdox.cloud.engine.mcp;

import com.archdox.cloud.engine.auth.application.EngineApiKeyAuthenticationService;
import com.archdox.cloud.engine.connect.application.EngineConnectProperties;
import com.archdox.cloud.engine.mcp.dto.McpLiveSmokeResponse;
import com.archdox.cloud.engine.mcp.dto.McpLiveSmokeStepResponse;
import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.global.api.ForbiddenException;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.platformadmin.application.PlatformAdminService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class McpLiveSmokeService {
    private final PlatformAdminService platformAdminService;
    private final EngineApiKeyAuthenticationService apiKeyAuthenticationService;
    private final EngineConnectProperties connectProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public McpLiveSmokeService(
            PlatformAdminService platformAdminService,
            EngineApiKeyAuthenticationService apiKeyAuthenticationService,
            EngineConnectProperties connectProperties,
            ObjectMapper objectMapper
    ) {
        this.platformAdminService = platformAdminService;
        this.apiKeyAuthenticationService = apiKeyAuthenticationService;
        this.connectProperties = connectProperties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build();
    }

    public McpLiveSmokeResponse run(UserPrincipal principal, String apiKey) {
        platformAdminService.requirePlatformAdmin(principal);
        return runVerified(
                apiKey,
                "admin-mcp-live-smoke",
                "ArchDox Admin MCP Live Smoke",
                "archdox-admin-live-smoke");
    }

    public McpLiveSmokeResponse runOwn(UserPrincipal principal, String apiKey) {
        var normalizedKey = normalizeApiKey(apiKey);
        var apiPrincipal = apiKeyAuthenticationService.authenticate(normalizedKey);
        if (!principal.userId().equals(apiPrincipal.ownerUserId())) {
            throw new ForbiddenException(
                    "ENGINE_MCP_SMOKE_KEY_OWNER_REQUIRED",
                    "errors.engineMcpSmoke.keyOwnerRequired",
                    "Only Engine API keys owned by the current user can be tested");
        }
        return runVerified(
                normalizedKey,
                "user-mcp-live-smoke",
                "ArchDox User MCP Live Smoke",
                "archdox-user-live-smoke");
    }

    private McpLiveSmokeResponse runVerified(
            String apiKey,
            String correlationPrefix,
            String userAgent,
            String clientName
    ) {
        var normalizedKey = normalizeApiKey(apiKey);
        var endpoint = connectProperties.getMcpServerUrl();
        if (endpoint.isBlank()) {
            throw new BadRequestException("MCP server URL is not configured");
        }

        var startedAt = System.nanoTime();
        var steps = new ArrayList<McpLiveSmokeStepResponse>();
        steps.add(call(
                endpoint,
                normalizedKey,
                "initialize",
                null,
                "initialize",
                initializeParams(clientName),
                1,
                correlationPrefix,
                userAgent));
        steps.add(call(endpoint, normalizedKey, "tools/list", null, "tools/list", Map.of(), 2, correlationPrefix, userAgent));
        steps.add(call(
                endpoint,
                normalizedKey,
                "get_legal_updates",
                "get_legal_updates",
                "tools/call",
                Map.of(
                        "name", "get_legal_updates",
                        "arguments", Map.of("days", 30, "limit", 1)),
                3,
                correlationPrefix,
                userAgent));
        steps.add(call(
                endpoint,
                normalizedKey,
                "search_law",
                "search_law",
                "tools/call",
                Map.of(
                        "name", "search_law",
                        "arguments", Map.of("query", "건축법", "limit", 1)),
                4,
                correlationPrefix,
                userAgent));

        var succeeded = (int) steps.stream().filter(McpLiveSmokeStepResponse::success).count();
        var failed = steps.size() - succeeded;
        return new McpLiveSmokeResponse(
                endpoint,
                failed == 0 ? "PASS" : "WARN",
                failed == 0,
                steps.size(),
                succeeded,
                failed,
                elapsedMs(startedAt),
                List.copyOf(steps),
                OffsetDateTime.now());
    }

    private String normalizeApiKey(String apiKey) {
        var normalizedKey = apiKey == null ? "" : apiKey.trim();
        if (normalizedKey.isBlank()) {
            throw new BadRequestException("apiKey is required");
        }
        return normalizedKey;
    }

    private Map<String, Object> initializeParams(String clientName) {
        return Map.of(
                "protocolVersion", "2025-06-18",
                "capabilities", Map.of(),
                "clientInfo", Map.of(
                        "name", clientName,
                        "version", "1"));
    }

    private McpLiveSmokeStepResponse call(
            String endpoint,
            String apiKey,
            String step,
            String toolName,
            String method,
            Map<String, Object> params,
            int id,
            String correlationPrefix,
            String userAgent
    ) {
        var startedAt = System.nanoTime();
        try {
            var requestBody = objectMapper.writeValueAsString(jsonRpc(id, method, params));
            var request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("X-ArchDox-Engine-Key", apiKey)
                    .header("X-Correlation-Id", correlationPrefix + "-" + id)
                    .header("User-Agent", userAgent)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return parseResponse(step, method, toolName, response.statusCode(), response.body(), elapsedMs(startedAt));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return failed(step, method, toolName, 0, elapsedMs(startedAt), "INTERRUPTED", "MCP live smoke was interrupted.", null);
        } catch (IOException | RuntimeException ex) {
            return failed(step, method, toolName, 0, elapsedMs(startedAt), "REQUEST_FAILED", ex.getMessage(), null);
        }
    }

    private Map<String, Object> jsonRpc(int id, String method, Map<String, Object> params) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("jsonrpc", "2.0");
        payload.put("id", id);
        payload.put("method", method);
        payload.put("params", params == null ? Map.of() : params);
        return payload;
    }

    private McpLiveSmokeStepResponse parseResponse(
            String step,
            String method,
            String toolName,
            int httpStatus,
            String body,
            long elapsedMs
    ) {
        try {
            var json = objectMapper.readTree(body == null ? "" : body);
            if (!isHttpSuccess(httpStatus)) {
                return failed(
                        step,
                        method,
                        toolName,
                        httpStatus,
                        elapsedMs,
                        text(json, "code", "HTTP_" + httpStatus),
                        text(json, "message", "MCP endpoint returned HTTP " + httpStatus),
                        json);
            }
            if (json.hasNonNull("error")) {
                var error = json.get("error");
                var data = error.path("data");
                return new McpLiveSmokeStepResponse(
                        step,
                        method,
                        toolName,
                        httpStatus,
                        "FAILED",
                        false,
                        elapsedMs,
                        text(error, "message", "MCP JSON-RPC error"),
                        text(data, "code", "MCP_ERROR"),
                        text(data, "category", null),
                        data.has("retryable") ? data.get("retryable").asBoolean() : null,
                        preview(json));
            }
            return new McpLiveSmokeStepResponse(
                    step,
                    method,
                    toolName,
                    httpStatus,
                    "SUCCEEDED",
                    true,
                    elapsedMs,
                    successSummary(step, json),
                    null,
                    null,
                    null,
                    preview(json));
        } catch (IOException ex) {
            return failed(step, method, toolName, httpStatus, elapsedMs, "INVALID_JSON", "MCP endpoint returned a non-JSON response.", null);
        }
    }

    private McpLiveSmokeStepResponse failed(
            String step,
            String method,
            String toolName,
            int httpStatus,
            long elapsedMs,
            String errorCode,
            String summary,
            JsonNode response
    ) {
        return new McpLiveSmokeStepResponse(
                step,
                method,
                toolName,
                httpStatus,
                "FAILED",
                false,
                elapsedMs,
                summary == null || summary.isBlank() ? "MCP live smoke step failed." : summary,
                errorCode,
                null,
                false,
                response == null ? "" : preview(response));
    }

    private boolean isHttpSuccess(int httpStatus) {
        return httpStatus >= 200 && httpStatus < 300;
    }

    private String successSummary(String step, JsonNode json) {
        var result = json.path("result");
        return switch (step) {
            case "initialize" -> "MCP initialize succeeded. server="
                    + text(result.path("serverInfo"), "name", "unknown")
                    + " / version="
                    + text(result.path("serverInfo"), "version", "unknown");
            case "tools/list" -> "MCP tools/list succeeded. tools=" + result.path("tools").size();
            case "get_legal_updates" -> "get_legal_updates succeeded. result keys=" + firstFieldName(result);
            case "search_law" -> "search_law succeeded. result keys=" + firstFieldName(result);
            default -> step + " succeeded.";
        };
    }

    private String firstFieldName(JsonNode node) {
        var fieldNames = node == null ? null : node.fieldNames();
        if (fieldNames != null && fieldNames.hasNext()) {
            return fieldNames.next();
        }
        return "-";
    }

    private String text(JsonNode node, String field, String fallback) {
        if (node != null && node.hasNonNull(field)) {
            return node.get(field).asText();
        }
        return fallback;
    }

    private String preview(JsonNode json) {
        try {
            var text = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
            return text.length() > 1600 ? text.substring(0, 1600) + "\n..." : text;
        } catch (IOException ex) {
            return "";
        }
    }

    private long elapsedMs(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }
}
