package com.archdox.document;

public record TemplateSpec(
        String templateCode,
        int version,
        String storageRef,
        String schemaJson,
        String composePolicyJson,
        String downloadUrl
) {
    public TemplateSpec(
            String templateCode,
            int version,
            String storageRef,
            String schemaJson,
            String composePolicyJson
    ) {
        this(templateCode, version, storageRef, schemaJson, composePolicyJson, null);
    }
}
