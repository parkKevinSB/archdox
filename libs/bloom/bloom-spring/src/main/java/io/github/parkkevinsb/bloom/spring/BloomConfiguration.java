package io.github.parkkevinsb.bloom.spring;

import io.github.parkkevinsb.bloom.EventBus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Minimal Spring configuration for Bloom.
 */
@Configuration
public class BloomConfiguration {

    @Bean
    public static BloomEventBusRegistrar bloomEventBusRegistrar() {
        return new BloomEventBusRegistrar();
    }

    @Bean
    public static BloomBeanPostProcessor bloomBeanPostProcessor(EventBus eventBus) {
        return new BloomBeanPostProcessor(eventBus);
    }
}
