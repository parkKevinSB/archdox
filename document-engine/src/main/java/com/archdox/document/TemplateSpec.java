package com.archdox.document;

public record TemplateSpec(
        String templateCode,
        int version,
        String storageRef,
        String schemaJson,
        String composePolicyJson,
        String downloadUrl,
        boolean contentRequired
) {
    public TemplateSpec(
            String templateCode,
            int version,
            String storageRef,
            String schemaJson,
            String composePolicyJson
    ) {
        this(templateCode, version, storageRef, schemaJson, composePolicyJson, null, false);
    }

    public TemplateSpec(
            String templateCode,
            int version,
            String storageRef,
            String schemaJson,
            String composePolicyJson,
            String downloadUrl
    ) {
        this(templateCode, version, storageRef, schemaJson, composePolicyJson, downloadUrl, false);
    }
}
