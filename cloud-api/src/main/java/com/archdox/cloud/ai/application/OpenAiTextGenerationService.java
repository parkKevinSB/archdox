package com.archdox.cloud.ai.application;

import com.archdox.cloud.global.api.BadRequestException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class OpenAiTextGenerationService implements AiTextGenerationService {
    private final AiGenerationProperties properties;

    public OpenAiTextGenerationService(AiGenerationProperties properties) {
        this.properties = properties;
    }

    @Override
    public AiProvider provider() {
        return AiProvider.OPENAI;
    }

    @Override
    public AiTextGenerationResponse generate(AiTextGenerationRequest request) {
        var openai = properties.getOpenai();
        if (!hasText(openai.getApiKey())) {
            throw new BadRequestException("OpenAI API key is not configured");
        }
        var messages = new ArrayList<Map<String, String>>();
        if (hasText(request.systemPrompt())) {
            messages.add(Map.of("role", "system", "content", request.systemPrompt()));
        }
        messages.add(Map.of("role", "user", "content", request.prompt()));

        var body = new LinkedHashMap<String, Object>();
        body.put("model", openai.getModel());
        body.put("messages", messages);

        var response = RestClient.create(openai.getBaseUrl())
                .post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + openai.getApiKey())
                .body(body)
                .retrieve()
                .body(Map.class);
        return new AiTextGenerationResponse(extractOpenAiText(response), openai.getModel(), AiProvider.OPENAI);
    }

    private String extractOpenAiText(Map<?, ?> response) {
        var choices = valueAsList(response, "choices");
        if (choices.isEmpty() || !(choices.get(0) instanceof Map<?, ?> firstChoice)) {
            return "";
        }
        var message = firstChoice.get("message");
        if (message instanceof Map<?, ?> messageMap && messageMap.get("content") instanceof String content) {
            return content;
        }
        return "";
    }

    private List<?> valueAsList(Map<?, ?> response, String key) {
        if (response != null && response.get(key) instanceof List<?> values) {
            return values;
        }
        return List.of();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
