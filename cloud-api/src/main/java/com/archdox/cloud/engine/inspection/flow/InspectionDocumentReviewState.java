package com.archdox.cloud.engine.inspection.flow;

import com.archdox.cloud.engine.dto.EngineReviewSessionResponse;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public final class InspectionDocumentReviewState {
    private final AtomicReference<String> reviewSessionId = new AtomicReference<>("");
    private final AtomicReference<EngineReviewSessionResponse> normalizedResponse = new AtomicReference<>();
    private final AtomicReference<EngineReviewSessionResponse> validationResponse = new AtomicReference<>();
    private final AtomicReference<Map<String, Object>> extractionMetadata = new AtomicReference<>(Map.of());
    private final AtomicReference<Map<String, Object>> output = new AtomicReference<>(Map.of());

    public String reviewSessionId() {
        return reviewSessionId.get();
    }

    public void reviewSessionId(String value) {
        reviewSessionId.set(value == null ? "" : value.trim());
    }

    public EngineReviewSessionResponse normalizedResponse() {
        return normalizedResponse.get();
    }

    public void normalizedResponse(EngineReviewSessionResponse value) {
        normalizedResponse.set(value);
    }

    public EngineReviewSessionResponse validationResponse() {
        return validationResponse.get();
    }

    public void validationResponse(EngineReviewSessionResponse value) {
        validationResponse.set(value);
    }

    public Map<String, Object> extractionMetadata() {
        return extractionMetadata.get();
    }

    public void extractionMetadata(Map<String, Object> value) {
        extractionMetadata.set(value == null ? Map.of() : Map.copyOf(value));
    }

    public Map<String, Object> output() {
        return output.get();
    }

    public void output(Map<String, Object> value) {
        output.set(value == null ? Map.of() : Map.copyOf(value));
    }
}
