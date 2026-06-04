package com.archdox.cloud.engine.dto;

import jakarta.validation.Valid;
import java.util.List;

public record SubmitEngineReviewFactsRequest(
        @Valid List<EngineContextFactRequest> facts
) {
    public SubmitEngineReviewFactsRequest {
        facts = facts == null ? List.of() : List.copyOf(facts);
    }
}
