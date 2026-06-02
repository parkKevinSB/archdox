package com.archdox.cloud.aipolicy.dto;

public record UpdateAiObservationModeRequest(
        Boolean enabled,
        Boolean clearExisting
) {
}
