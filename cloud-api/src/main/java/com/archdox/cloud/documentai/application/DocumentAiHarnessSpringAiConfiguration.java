package com.archdox.cloud.documentai.application;

import io.github.parkkevinsb.flower.ai.harness.gateway.AiModelGateway;
import io.github.parkkevinsb.flower.ai.harness.model.AiModelCall;
import io.github.parkkevinsb.flower.ai.harness.model.AiModelCallStatus;
import io.github.parkkevinsb.flower.ai.harness.model.AiModelRequest;
import io.github.parkkevinsb.flower.ai.harness.model.AiModelResponse;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;

@Configuration
class DocumentAiHarnessSpringAiConfiguration {
    private static final Set<String> ENABLED_CHAT_MODELS = Set.of("openai", "ollama");

    @Bean
    @Conditional(AiChatModelEnabledCondition.class)
    ChatClient archDoxAiHarnessChatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    @Bean
    @Conditional(AiChatModelDisabledCondition.class)
    AiModelGateway disabledAiModelGateway() {
        return request -> new DisabledAiModelCall("ai-disabled-" + UUID.randomUUID());
    }

    static final class AiChatModelEnabledCondition implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return isEnabled(context);
        }
    }

    static final class AiChatModelDisabledCondition implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return !isEnabled(context);
        }
    }

    private static boolean isEnabled(ConditionContext context) {
        String chatModel = context.getEnvironment().getProperty("spring.ai.model.chat", "none");
        return ENABLED_CHAT_MODELS.contains(chatModel.trim().toLowerCase(Locale.ROOT));
    }

    private static final class DisabledAiModelCall implements AiModelCall {
        private final String callId;
        private final IllegalStateException error = new IllegalStateException(
                "Document AI review is disabled. Set spring.ai.model.chat to openai or ollama.");

        private DisabledAiModelCall(String callId) {
            this.callId = callId;
        }

        @Override
        public String callId() {
            return callId;
        }

        @Override
        public AiModelCallStatus poll() {
            return AiModelCallStatus.FAILED;
        }

        @Override
        public AiModelResponse result() {
            throw error;
        }

        @Override
        public Throwable error() {
            return error;
        }

        @Override
        public void cancel() {
            // Nothing to cancel because no provider call was started.
        }
    }
}
