package com.archdox.cloud.ai.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "archdox.ai")
public class AiGenerationProperties {
    private AiProvider provider = AiProvider.DISABLED;
    private final OpenAi openai = new OpenAi();
    private final Ollama ollama = new Ollama();

    public AiProvider getProvider() {
        return provider;
    }

    public void setProvider(AiProvider provider) {
        this.provider = provider == null ? AiProvider.DISABLED : provider;
    }

    public OpenAi getOpenai() {
        return openai;
    }

    public Ollama getOllama() {
        return ollama;
    }

    public static class OpenAi {
        private String baseUrl = "https://api.openai.com/v1";
        private String apiKey;
        private String model = "gpt-4.1-mini";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }
    }

    public static class Ollama {
        private String baseUrl = "http://localhost:11434";
        private String model = "llama3.1";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }
    }
}
