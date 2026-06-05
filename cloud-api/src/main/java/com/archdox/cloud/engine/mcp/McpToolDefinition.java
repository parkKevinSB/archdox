package com.archdox.cloud.engine.mcp;

import java.util.Locale;
import java.util.Map;
import java.util.function.ToIntFunction;

public record McpToolDefinition(
        String name,
        String title,
        String description,
        Map<String, Object> inputSchema,
        String capability,
        String requiredScope,
        String accessMode,
        boolean gatewayManagedUsage,
        ToIntFunction<Map<String, Object>> requestUnits,
        McpToolHandler handler
) {
    public McpToolDescriptor descriptor() {
        return new McpToolDescriptor(name, title, description, inputSchema);
    }

    public int requestUnits(Map<String, Object> arguments) {
        if (requestUnits == null) {
            return 1;
        }
        return Math.max(1, requestUnits.applyAsInt(arguments == null ? Map.of() : arguments));
    }

    public String operation() {
        return "MCP_" + name.toUpperCase(Locale.ROOT);
    }
}
