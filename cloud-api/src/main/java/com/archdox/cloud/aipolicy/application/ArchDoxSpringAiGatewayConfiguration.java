package com.archdox.cloud.aipolicy.application;

import io.github.parkkevinsb.flower.ai.harness.springai.SpringAiModelGateway;
import io.github.parkkevinsb.flower.ai.harness.springai.SpringAiModelResolver;
import io.github.parkkevinsb.flower.ai.harness.springboot.FlowerAiHarnessSpringAiAutoConfiguration;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ArchDoxSpringAiGatewayConfiguration {
    @Bean
    @ConditionalOnBean(SpringAiModelResolver.class)
    @ConditionalOnMissingBean(SpringAiModelGateway.class)
    SpringAiModelGateway archDoxSpringAiModelGateway(
            SpringAiModelResolver resolver,
            @Qualifier(FlowerAiHarnessSpringAiAutoConfiguration.MODEL_EXECUTOR_BEAN_NAME) Executor executor
    ) {
        return new SpringAiModelGateway(resolver, executor);
    }
}
