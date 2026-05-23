package com.archdox.cloud.configuration.dto;

import java.util.Map;

public record CreateJsonConfigRevisionRequest(
        Map<String, Object> payload
) {
}
