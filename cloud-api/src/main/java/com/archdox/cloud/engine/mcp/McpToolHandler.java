package com.archdox.cloud.engine.mcp;

import com.archdox.cloud.engine.auth.application.EngineApiPrincipal;
import java.util.Map;

@FunctionalInterface
public interface McpToolHandler {
    Object handle(EngineApiPrincipal principal, Map<String, Object> arguments);
}
