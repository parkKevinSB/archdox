package com.archdox.cloud.aipolicy.application;

import io.github.parkkevinsb.flower.ai.harness.model.ProviderOptions;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AiModelCallMetadata {
    public static final String OFFICE_ID = "archdox.officeId";
    public static final String FEATURE = "archdox.feature";
    public static final String WORKFLOW_TYPE = "archdox.workflowType";
    public static final String WORKFLOW_KEY = "archdox.workflowKey";
    public static final String RESOURCE_TYPE = "archdox.resourceType";
    public static final String RESOURCE_ID = "archdox.resourceId";
    public static final String PROVIDER_CONNECTION_TEST = "archdox.providerConnectionTest";

    private AiModelCallMetadata() {
    }

    public static ProviderOptions options(
            Long officeId,
            String feature,
            String workflowType,
            String workflowKey,
            String resourceType,
            Object resourceId,
            Map<String, Object> extras
    ) {
        var values = new LinkedHashMap<String, Object>();
        put(values, OFFICE_ID, officeId);
        put(values, FEATURE, feature);
        put(values, WORKFLOW_TYPE, workflowType);
        put(values, WORKFLOW_KEY, workflowKey);
        put(values, RESOURCE_TYPE, resourceType);
        put(values, RESOURCE_ID, resourceId == null ? null : String.valueOf(resourceId));
        if (extras != null) {
            extras.forEach((key, value) -> put(values, key, value));
        }
        return values.isEmpty() ? ProviderOptions.empty() : ProviderOptions.of(values);
    }

    private static void put(Map<String, Object> values, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String stringValue && stringValue.isBlank()) {
            return;
        }
        values.put(key, value);
    }
}
