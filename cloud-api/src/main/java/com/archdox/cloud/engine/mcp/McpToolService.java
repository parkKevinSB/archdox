package com.archdox.cloud.engine.mcp;

import com.archdox.cloud.engine.application.EngineExternalReviewSessionService;
import com.archdox.cloud.engine.auth.application.EngineApiKeyManagementService;
import com.archdox.cloud.engine.auth.application.EngineApiPrincipal;
import com.archdox.cloud.engine.dto.CreateEngineReviewSessionRequest;
import com.archdox.cloud.engine.dto.EngineContextFactRequest;
import com.archdox.cloud.engine.dto.SubmitEngineReviewDocumentRequest;
import com.archdox.cloud.engine.dto.SubmitEngineReviewFactsRequest;
import com.archdox.cloud.engine.usage.application.EngineApiQuotaGuardService;
import com.archdox.cloud.engine.usage.application.EngineApiUsageService;
import com.archdox.cloud.global.api.ApiException;
import com.archdox.cloud.global.api.ForbiddenException;
import com.archdox.cloud.global.api.TooManyRequestsException;
import com.archdox.cloud.legal.application.LegalCorpusReadService;
import com.archdox.cloud.legal.application.LegalUpdateReadService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;
import org.springframework.stereotype.Service;

@Service
public class McpToolService {
    private static final String PROTOCOL_VERSION = "2025-06-18";
    private static final String ACCESS_READ = "READ";
    private static final String ACCESS_WRITE = "WRITE";

    private final EngineExternalReviewSessionService reviewSessionService;
    private final LegalUpdateReadService legalUpdateReadService;
    private final LegalCorpusReadService legalCorpusReadService;
    private final EngineApiQuotaGuardService quotaGuardService;
    private final EngineApiUsageService usageService;
    private final ObjectMapper objectMapper;
    private final Map<String, McpToolDefinition> toolDefinitions;

    public McpToolService(
            EngineExternalReviewSessionService reviewSessionService,
            LegalUpdateReadService legalUpdateReadService,
            LegalCorpusReadService legalCorpusReadService,
            EngineApiQuotaGuardService quotaGuardService,
            EngineApiUsageService usageService,
            ObjectMapper objectMapper
    ) {
        this.reviewSessionService = reviewSessionService;
        this.legalUpdateReadService = legalUpdateReadService;
        this.legalCorpusReadService = legalCorpusReadService;
        this.quotaGuardService = quotaGuardService;
        this.usageService = usageService;
        this.objectMapper = objectMapper;
        this.toolDefinitions = buildToolDefinitions();
    }

    public Map<String, Object> initialize() {
        return Map.of(
                "protocolVersion", PROTOCOL_VERSION,
                "capabilities", Map.of(
                        "tools", Map.of("listChanged", false)),
                "serverInfo", Map.of(
                        "name", "ArchDox MCP Gateway",
                        "version", "0.1.0"));
    }

    public Map<String, Object> listTools(EngineApiPrincipal principal) {
        return Map.of("tools", toolDefinitions.values().stream()
                .filter(tool -> principal.hasScope(tool.requiredScope()))
                .map(McpToolDefinition::descriptor)
                .map(tool -> Map.of(
                        "name", tool.name(),
                        "title", tool.title(),
                        "description", tool.description(),
                        "inputSchema", tool.inputSchema()))
                .toList());
    }

    public Map<String, Object> callTool(
            EngineApiPrincipal principal,
            Map<String, Object> params,
            McpExecutionContext context
    ) {
        var callParams = params == null ? Map.<String, Object>of() : params;
        var name = requiredText(callParams.get("name"), "MCP tool name is required");
        var arguments = objectMap(callParams.get("arguments"));
        var definition = toolDefinitions.get(name);
        if (definition == null) {
            throw new McpToolNotFoundException(name);
        }
        var requestUnits = definition.requestUnits(arguments);
        try {
            requireScope(principal, definition);
            if (definition.gatewayManagedUsage()) {
                quotaGuardService.requireQuota(principal, definition.capability(), definition.operation(), requestUnits);
            }
            var result = definition.handler().handle(principal, arguments);
            if (definition.gatewayManagedUsage()) {
                recordToolUsage(
                        principal,
                        definition,
                        context,
                        EngineApiUsageService.STATUS_SUCCEEDED,
                        requestUnits,
                        null);
            }
            return toolResult(result);
        } catch (RuntimeException ex) {
            recordToolUsage(
                    principal,
                    definition,
                    context,
                    usageStatus(ex),
                    0,
                    ex);
            throw ex;
        }
    }

    private Object createReviewSession(EngineApiPrincipal principal, Map<String, Object> arguments) {
        return reviewSessionService.create(new CreateEngineReviewSessionRequest(
                optionalText(arguments.get("customerProjectRef")),
                defaultText(arguments.get("reviewPurpose"), "preflight")), principal);
    }

    private Object submitDocument(EngineApiPrincipal principal, Map<String, Object> arguments) {
        var reviewSessionId = requiredText(arguments.get("reviewSessionId"), "reviewSessionId is required");
        return reviewSessionService.submitDocument(reviewSessionId, new SubmitEngineReviewDocumentRequest(
                optionalText(arguments.get("documentTypeHint")),
                optionalText(arguments.get("fileName")),
                requiredText(arguments.get("contentText"), "contentText is required")), principal);
    }

    private Object submitContextFacts(EngineApiPrincipal principal, Map<String, Object> arguments) {
        var reviewSessionId = requiredText(arguments.get("reviewSessionId"), "reviewSessionId is required");
        return reviewSessionService.submitFacts(reviewSessionId, factsRequest(arguments.get("facts")), principal);
    }

    private Object normalizeContext(EngineApiPrincipal principal, Map<String, Object> arguments) {
        return reviewSessionService.normalize(
                requiredText(arguments.get("reviewSessionId"), "reviewSessionId is required"),
                principal);
    }

    private Object runValidation(EngineApiPrincipal principal, Map<String, Object> arguments) {
        return reviewSessionService.runValidation(
                requiredText(arguments.get("reviewSessionId"), "reviewSessionId is required"),
                principal);
    }

    private Object getReviewResult(EngineApiPrincipal principal, Map<String, Object> arguments) {
        return reviewSessionService.getResult(
                requiredText(arguments.get("reviewSessionId"), "reviewSessionId is required"),
                principal);
    }

    private Object validateInspectionReport(EngineApiPrincipal principal, Map<String, Object> arguments) {
        reviewSessionService.requireQuota(principal, "VALIDATE_INSPECTION_REPORT", validateInspectionReportUnits(arguments));
        var session = reviewSessionService.create(new CreateEngineReviewSessionRequest(
                optionalText(arguments.get("customerProjectRef")),
                defaultText(arguments.get("reviewPurpose"), "preflight")), principal);
        var reviewSessionId = session.reviewSessionId();
        var contentText = optionalText(arguments.get("contentText"));
        if (contentText != null && !contentText.isBlank()) {
            reviewSessionService.submitDocument(reviewSessionId, new SubmitEngineReviewDocumentRequest(
                    optionalText(arguments.get("documentTypeHint")),
                    optionalText(arguments.get("fileName")),
                    contentText), principal);
        }
        var facts = factsRequest(arguments.get("facts"));
        if (!facts.facts().isEmpty()) {
            reviewSessionService.submitFacts(reviewSessionId, facts, principal);
        }
        return reviewSessionService.runValidation(reviewSessionId, principal);
    }

    private int validateInspectionReportUnits(Map<String, Object> arguments) {
        var units = 2;
        var contentText = optionalText(arguments.get("contentText"));
        if (contentText != null && !contentText.isBlank()) {
            units += 1;
        }
        if (!factsRequest(arguments.get("facts")).facts().isEmpty()) {
            units += 1;
        }
        return units;
    }

    private Object getLegalUpdates(Map<String, Object> arguments) {
        return legalUpdateReadService.recent(intValue(arguments.get("days")), intValue(arguments.get("limit")));
    }

    private Object searchLaw(Map<String, Object> arguments) {
        return legalCorpusReadService.search(
                optionalText(arguments.get("query")),
                optionalText(arguments.get("actCode")),
                optionalText(arguments.get("actName")),
                optionalText(arguments.get("articleNo")),
                localDateValue(arguments.get("effectiveDate"), "effectiveDate"),
                intValue(arguments.get("limit")));
    }

    private Object getLawArticle(Map<String, Object> arguments) {
        return legalCorpusReadService.getArticle(
                longValue(arguments.get("articleVersionId"), "articleVersionId"),
                longValue(arguments.get("articleId"), "articleId"),
                optionalText(arguments.get("actCode")),
                optionalText(arguments.get("articleNo")),
                localDateValue(arguments.get("effectiveDate"), "effectiveDate"));
    }

    private void requireScope(EngineApiPrincipal principal, McpToolDefinition definition) {
        if (principal.hasScope(definition.requiredScope())) {
            return;
        }
        throw new ForbiddenException(
                "ENGINE_API_SCOPE_REQUIRED",
                "errors.engineApi.scopeRequired",
                "Engine API key does not have the required MCP tool scope",
                Map.of(
                        "toolName", definition.name(),
                        "requiredScope", definition.requiredScope(),
                        "capability", definition.capability()));
    }

    private void recordToolUsage(
            EngineApiPrincipal principal,
            McpToolDefinition definition,
            McpExecutionContext context,
            String status,
            int requestUnits,
            RuntimeException error
    ) {
        var metadata = new LinkedHashMap<String, Object>(context.metadata(definition));
        if (error != null) {
            metadata.put("errorType", error.getClass().getSimpleName());
            metadata.put("errorMessage", error.getMessage() == null ? "" : error.getMessage());
            if (error instanceof ApiException apiException) {
                metadata.put("errorCode", apiException.code());
                metadata.put("messageKey", apiException.messageKey());
                metadata.put("errorParams", apiException.params());
            }
        }
        usageService.recordUsage(
                principal,
                definition.capability(),
                definition.operation(),
                null,
                status,
                requestUnits,
                metadata);
    }

    private String usageStatus(RuntimeException ex) {
        if (ex instanceof ForbiddenException || ex instanceof TooManyRequestsException) {
            return EngineApiUsageService.STATUS_DENIED;
        }
        return EngineApiUsageService.STATUS_FAILED;
    }

    private SubmitEngineReviewFactsRequest factsRequest(Object value) {
        if (!(value instanceof List<?> list)) {
            return new SubmitEngineReviewFactsRequest(List.of());
        }
        var facts = new ArrayList<EngineContextFactRequest>();
        for (var item : list) {
            if (item instanceof Map<?, ?> map) {
                facts.add(new EngineContextFactRequest(
                        optionalText(map.get("name")),
                        optionalText(map.get("fieldName")),
                        optionalText(map.get("rawValue")),
                        optionalText(map.get("source")),
                        optionalText(map.get("evidence")),
                        doubleValue(map.get("confidence"))));
            }
        }
        return new SubmitEngineReviewFactsRequest(facts);
    }

    private Map<String, Object> toolResult(Object result) {
        var json = structuredContent(result);
        return Map.of(
                "content", List.of(Map.of(
                        "type", "text",
                        "text", writeJson(json))),
                "structuredContent", json,
                "isError", false);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> structuredContent(Object value) {
        if (value instanceof Map<?, ?> map) {
            return Map.copyOf((Map<String, Object>) map);
        }
        if (value instanceof List<?> list) {
            return Map.of(
                    "items", objectMapper.convertValue(list, List.class),
                    "count", list.size());
        }
        return objectMapper.convertValue(value, Map.class);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (Exception ex) {
            return String.valueOf(value);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> objectMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return Map.copyOf((Map<String, Object>) map);
        }
        return Map.of();
    }

    private String requiredText(Object value, String message) {
        var text = optionalText(value);
        if (text == null || text.isBlank()) {
            throw new McpInvalidParamsException(message);
        }
        return text;
    }

    private String defaultText(Object value, String defaultValue) {
        var text = optionalText(value);
        return text == null || text.isBlank() ? defaultValue : text;
    }

    private String optionalText(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private Integer intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Long longValue(Object value, String fieldName) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                throw new McpInvalidParamsException(fieldName + " must be an integer");
            }
        }
        return null;
    }

    private LocalDate localDateValue(Object value, String fieldName) {
        var text = optionalText(value);
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException ignored) {
            throw new McpInvalidParamsException(fieldName + " must be a date in yyyy-MM-dd format");
        }
    }

    private Double doubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Map<String, McpToolDefinition> buildToolDefinitions() {
        var definitions = toolDefinitions();
        var map = new LinkedHashMap<String, McpToolDefinition>();
        for (var definition : definitions) {
            map.put(definition.name(), definition);
        }
        return Map.copyOf(map);
    }

    private List<McpToolDefinition> toolDefinitions() {
        return List.of(
                tool("create_review_session", "Create review session",
                        "Create an ArchDox Engine review session for an external agent.",
                        schema(required("reviewPurpose"), property("customerProjectRef", "string"), property("reviewPurpose", "string")),
                        EngineApiUsageService.CAPABILITY_REVIEW_SESSION,
                        EngineApiKeyManagementService.SCOPE_REVIEW_SESSION,
                        ACCESS_WRITE,
                        false,
                        arguments -> 1,
                        this::createReviewSession),
                tool("submit_document", "Submit document",
                        "Submit document text to a review session.",
                        schema(required("reviewSessionId", "contentText"),
                                property("reviewSessionId", "string"),
                                property("contentText", "string"),
                                property("documentTypeHint", "string"),
                                property("fileName", "string")),
                        EngineApiUsageService.CAPABILITY_REVIEW_SESSION,
                        EngineApiKeyManagementService.SCOPE_REVIEW_SESSION,
                        ACCESS_WRITE,
                        false,
                        arguments -> 1,
                        this::submitDocument),
                tool("submit_context_facts", "Submit context facts",
                        "Submit extracted context facts to a review session.",
                        schema(required("reviewSessionId", "facts"),
                                property("reviewSessionId", "string"),
                                arrayProperty("facts")),
                        EngineApiUsageService.CAPABILITY_REVIEW_SESSION,
                        EngineApiKeyManagementService.SCOPE_REVIEW_SESSION,
                        ACCESS_WRITE,
                        false,
                        arguments -> 1,
                        this::submitContextFacts),
                tool("normalize_context", "Normalize context",
                        "Normalize submitted context facts and detect missing or ambiguous fields.",
                        schema(required("reviewSessionId"), property("reviewSessionId", "string")),
                        EngineApiUsageService.CAPABILITY_REVIEW_SESSION,
                        EngineApiKeyManagementService.SCOPE_REVIEW_SESSION,
                        ACCESS_WRITE,
                        false,
                        arguments -> 1,
                        this::normalizeContext),
                tool("run_validation", "Run validation",
                        "Run ArchDox source-backed deterministic validation for a review session.",
                        schema(required("reviewSessionId"), property("reviewSessionId", "string")),
                        EngineApiUsageService.CAPABILITY_REVIEW_SESSION,
                        EngineApiKeyManagementService.SCOPE_REVIEW_SESSION,
                        ACCESS_WRITE,
                        false,
                        arguments -> 1,
                        this::runValidation),
                tool("get_review_result", "Get review result",
                        "Read the latest ArchDox Engine review result.",
                        schema(required("reviewSessionId"), property("reviewSessionId", "string")),
                        EngineApiUsageService.CAPABILITY_REVIEW_SESSION,
                        EngineApiKeyManagementService.SCOPE_REVIEW_SESSION,
                        ACCESS_READ,
                        false,
                        arguments -> 1,
                        this::getReviewResult),
                tool("validate_inspection_report", "Validate inspection report",
                        "Create a session, submit provided document/facts, and run validation in one tool call.",
                        schema(required(),
                                property("customerProjectRef", "string"),
                                property("reviewPurpose", "string"),
                                property("contentText", "string"),
                                property("documentTypeHint", "string"),
                                property("fileName", "string"),
                                arrayProperty("facts")),
                        EngineApiUsageService.CAPABILITY_REVIEW_SESSION,
                        EngineApiKeyManagementService.SCOPE_REVIEW_SESSION,
                        ACCESS_WRITE,
                        false,
                        this::validateInspectionReportUnits,
                        this::validateInspectionReport),
                tool("get_legal_updates", "Get legal updates",
                        "Read recent published ArchDox legal-change digests.",
                        schema(required(), property("days", "integer"), property("limit", "integer")),
                        EngineApiUsageService.CAPABILITY_LEGAL_UPDATES,
                        EngineApiKeyManagementService.SCOPE_LEGAL_UPDATES,
                        ACCESS_READ,
                        true,
                        arguments -> 1,
                        (principal, arguments) -> getLegalUpdates(arguments)),
                tool("search_law", "Search law",
                        "Search source-backed ArchDox legal corpus articles.",
                        schema(required(),
                                property("query", "string"),
                                property("actCode", "string"),
                                property("actName", "string"),
                                property("articleNo", "string"),
                                property("effectiveDate", "string"),
                                property("limit", "integer")),
                        EngineApiUsageService.CAPABILITY_LEGAL_SEARCH,
                        EngineApiKeyManagementService.SCOPE_LEGAL_SEARCH,
                        ACCESS_READ,
                        true,
                        arguments -> 1,
                        (principal, arguments) -> searchLaw(arguments)),
                tool("get_law_article", "Get law article",
                        "Read one source-backed legal article by articleVersionId, articleId, or actCode plus articleNo.",
                        schema(required(),
                                property("articleVersionId", "integer"),
                                property("articleId", "integer"),
                                property("actCode", "string"),
                                property("articleNo", "string"),
                                property("effectiveDate", "string")),
                        EngineApiUsageService.CAPABILITY_LEGAL_SEARCH,
                        EngineApiKeyManagementService.SCOPE_LEGAL_SEARCH,
                        ACCESS_READ,
                        true,
                        arguments -> 1,
                        (principal, arguments) -> getLawArticle(arguments)));
    }

    private McpToolDefinition tool(
            String name,
            String title,
            String description,
            Map<String, Object> schema,
            String capability,
            String requiredScope,
            String accessMode,
            boolean gatewayManagedUsage,
            ToIntFunction<Map<String, Object>> requestUnits,
            McpToolHandler handler
    ) {
        return new McpToolDefinition(
                name,
                title,
                description,
                schema,
                capability,
                requiredScope,
                accessMode,
                gatewayManagedUsage,
                requestUnits,
                handler);
    }

    private Map<String, Object> schema(List<String> required, Map<String, Object>... properties) {
        var propertyMap = new LinkedHashMap<String, Object>();
        for (var property : properties) {
            propertyMap.putAll(property);
        }
        return Map.of(
                "type", "object",
                "properties", Map.copyOf(propertyMap),
                "required", required);
    }

    private List<String> required(String... names) {
        return names == null ? List.of() : List.of(names);
    }

    private Map<String, Object> property(String name, String type) {
        return Map.of(name, Map.of("type", type));
    }

    private Map<String, Object> arrayProperty(String name) {
        return Map.of(name, Map.of(
                "type", "array",
                "items", Map.of("type", "object")));
    }
}
