package com.archdox.cloud.ai.application;

import com.archdox.cloud.global.api.BadRequestException;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AiTextGenerationServiceResolver {
    private final AiGenerationProperties properties;
    private final List<AiTextGenerationService> services;

    public AiTextGenerationServiceResolver(AiGenerationProperties properties, List<AiTextGenerationService> services) {
        this.properties = properties;
        this.services = services;
    }

    public AiTextGenerationService active() {
        if (properties.getProvider() == AiProvider.DISABLED) {
            throw new BadRequestException("AI text generation is disabled");
        }
        return services.stream()
                .filter(service -> service.supports(properties.getProvider()))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("AI provider is not supported: " + properties.getProvider()));
    }
}
