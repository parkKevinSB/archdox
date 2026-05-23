package com.archdox.document;

public record TemplateSpec(
        String templateCode,
        int version,
        String storageRef,
        String schemaJson,
        String composePolicyJson
) {
}
