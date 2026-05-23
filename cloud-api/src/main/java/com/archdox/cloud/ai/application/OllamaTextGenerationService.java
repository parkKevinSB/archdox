package com.archdox.cloud.ai.application;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class OllamaTextGenerationService implements AiTextGenerationService {
    private final AiGenerationProperties properties;

    public OllamaTextGenerationService(AiGenerationProperties properties) {
        this.properties = properties;
    }

    @Override
    public AiProvider provider() {
        return AiProvider.OLLAMA;
    }

    @Override
    public AiTextGenerationResponse generate(AiTextGenerationRequest request) {
        var ollama = properties.getOllama();
        var body = new LinkedHashMap<String, Object>();
        body.put("model", ollama.getModel());
        body.put("prompt", request.prompt());
        body.put("stream", false);
        if (hasText(request.systemPrompt())) {
            body.put("system", request.systemPrompt());
        }
        var response = RestClient.create(ollama.getBaseUrl())
                .post()
                .uri("/api/generate")
                .body(body)
                .retrieve()
                .body(Map.class);
        var text = response != null && response.get("response") instanceof String value ? value : "";
        return new AiTextGenerationResponse(text, ollama.getModel(), AiProvider.OLLAMA);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
