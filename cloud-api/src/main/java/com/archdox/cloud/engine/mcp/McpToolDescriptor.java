package com.archdox.cloud.engine.mcp;

import java.util.Map;

public record McpToolDescriptor(
        String name,
        String title,
        String description,
        Map<String, Object> inputSchema
) {
    public McpToolDescriptor {
        name = name == null ? "" : name.trim();
        title = title == null ? "" : title.trim();
        description = description == null ? "" : description.trim();
        inputSchema = inputSchema == null ? Map.of() : Map.copyOf(inputSchema);
    }
}
