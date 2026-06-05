package com.archdox.cloud.engine.mcp;

import com.archdox.cloud.engine.auth.application.EngineApiPrincipal;
import com.archdox.cloud.global.api.ApiException;
import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.global.api.ForbiddenException;
import com.archdox.cloud.global.api.NotFoundException;
import com.archdox.cloud.global.api.TooManyRequestsException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/mcp")
public class McpGatewayController {
    private static final int METHOD_NOT_FOUND = -32601;
    private static final int INVALID_PARAMS = -32602;
    private static final int SERVER_ERROR = -32000;
    private static final int FORBIDDEN = -32003;
    private static final int NOT_FOUND = -32004;
    private static final int TOO_MANY_REQUESTS = -32029;
    private static final int INTERNAL_ERROR = -32603;

    private final McpToolService toolService;

    public McpGatewayController(McpToolService toolService) {
        this.toolService = toolService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> handle(
            @RequestBody McpJsonRpcRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        try {
            var principal = (EngineApiPrincipal) authentication.getPrincipal();
            var context = McpExecutionContext.from(request, httpRequest);
            return switch (request.method()) {
                case "initialize" -> McpJsonRpcResponse.result(request.id(), toolService.initialize());
                case "ping" -> McpJsonRpcResponse.result(request.id(), Map.of());
                case "tools/list" -> McpJsonRpcResponse.result(request.id(), toolService.listTools(principal));
                case "tools/call" -> McpJsonRpcResponse.result(
                        request.id(),
                        toolService.callTool(principal, request.params(), context));
                default -> McpJsonRpcResponse.error(
                        request.id(),
                        METHOD_NOT_FOUND,
                        "Unsupported MCP method: " + request.method(),
                        errorData(
                                "MCP_METHOD_NOT_FOUND",
                                McpJsonRpcErrorCategory.METHOD_NOT_FOUND,
                                false,
                                null,
                                Map.of("method", request.method())));
            };
        } catch (RuntimeException ex) {
            if (ex instanceof McpToolNotFoundException apiException) {
                return apiError(request, INVALID_PARAMS, apiException, McpJsonRpcErrorCategory.TOOL_NOT_FOUND, false);
            }
            if (ex instanceof McpInvalidParamsException apiException) {
                return apiError(request, INVALID_PARAMS, apiException, McpJsonRpcErrorCategory.INVALID_PARAMS, false);
            }
            if (ex instanceof BadRequestException apiException) {
                return apiError(request, INVALID_PARAMS, apiException, McpJsonRpcErrorCategory.INVALID_PARAMS, false);
            }
            if (ex instanceof TooManyRequestsException apiException) {
                return apiError(request, TOO_MANY_REQUESTS, apiException, McpJsonRpcErrorCategory.QUOTA_EXCEEDED, true);
            }
            if (ex instanceof ForbiddenException apiException) {
                return apiError(request, FORBIDDEN, apiException, forbiddenCategory(apiException), false);
            }
            if (ex instanceof NotFoundException apiException) {
                return apiError(request, NOT_FOUND, apiException, McpJsonRpcErrorCategory.NOT_FOUND, false);
            }
            if (ex instanceof IllegalArgumentException) {
                return McpJsonRpcResponse.error(
                        request.id(),
                        INVALID_PARAMS,
                        ex.getMessage(),
                        errorData(
                                "MCP_INVALID_PARAMS",
                                McpJsonRpcErrorCategory.INVALID_PARAMS,
                                false,
                                "errors.mcp.invalidParams",
                                Map.of()));
            }
            if (ex instanceof ApiException apiException) {
                return apiError(request, SERVER_ERROR, apiException, McpJsonRpcErrorCategory.ARCHDOX_API_ERROR, false);
            }
            return McpJsonRpcResponse.error(
                    request.id(),
                    INTERNAL_ERROR,
                    ex.getMessage(),
                    errorData(
                            "MCP_INTERNAL_ERROR",
                            McpJsonRpcErrorCategory.INTERNAL_ERROR,
                            true,
                            "errors.mcp.internal",
                            Map.of()));
        }
    }

    private Map<String, Object> apiError(
            McpJsonRpcRequest request,
            int jsonRpcCode,
            ApiException apiException,
            McpJsonRpcErrorCategory category,
            boolean retryable
    ) {
        var message = apiException instanceof RuntimeException runtimeException
                ? runtimeException.getMessage()
                : "ArchDox MCP API error";
        return McpJsonRpcResponse.error(
                request.id(),
                jsonRpcCode,
                message,
                errorData(
                        apiException.code(),
                        category,
                        retryable,
                        apiException.messageKey(),
                        apiException.params()));
    }

    private McpJsonRpcErrorCategory forbiddenCategory(ForbiddenException apiException) {
        if ("ENGINE_API_SCOPE_REQUIRED".equals(apiException.code())) {
            return McpJsonRpcErrorCategory.SCOPE_REQUIRED;
        }
        return McpJsonRpcErrorCategory.FORBIDDEN;
    }

    private Map<String, Object> errorData(
            String code,
            McpJsonRpcErrorCategory category,
            boolean retryable,
            String messageKey,
            Map<String, Object> params
    ) {
        return new McpJsonRpcErrorData(code, category, retryable, messageKey, params).toMap();
    }
}
