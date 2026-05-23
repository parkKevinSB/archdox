package io.github.parkkevinsb.bloom.spring;

import io.github.parkkevinsb.bloom.EventBus;
import io.github.parkkevinsb.bloom.LocalEventBus;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class BloomSpringIntegrationTest {

    @Test
    void subscribeMethodReceivesEventsAndIsUnsubscribedOnContextClose() {
        AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(DefaultBloomConfig.class);
        EventBus bus = context.getBean(EventBus.class);
        OrderSubscriber subscriber = context.getBean(OrderSubscriber.class);

        bus.publish(new OrderPlaced("A"));
        assertThat(subscriber.count()).isEqualTo(1);

        context.close();
        bus.publish(new OrderPlaced("B"));

        assertThat(subscriber.count()).isEqualTo(1);
    }

    @Test
    void userProvidedEventBusBacksOffDefaultBus() {
        AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(CustomBusConfig.class);

        Map<String, EventBus> buses = context.getBeansOfType(EventBus.class);
        assertThat(buses).containsOnlyKeys("customEventBus");

        EventBus bus = context.getBean(EventBus.class);
        OrderSubscriber subscriber = context.getBean(OrderSubscriber.class);
        bus.publish(new OrderPlaced("A"));

        assertThat(subscriber.count()).isEqualTo(1);
        context.close();
    }

    @Configuration
    @EnableBloom
    static class DefaultBloomConfig {
        @Bean
        OrderSubscriber orderSubscriber() {
            return new OrderSubscriber();
        }
    }

    @Configuration
    @EnableBloom
    static class CustomBusConfig {
        @Bean
        EventBus customEventBus() {
            return LocalEventBus.create();
        }

        @Bean
        OrderSubscriber orderSubscriber() {
            return new OrderSubscriber();
        }
    }

    static final class OrderPlaced {
        final String orderId;

        OrderPlaced(String orderId) {
            this.orderId = orderId;
        }
    }

    static final class OrderSubscriber {
        private final AtomicInteger count = new AtomicInteger();

        @Subscribe
        void on(OrderPlaced event) {
            count.incrementAndGet();
        }

        int count() {
            return count.get();
        }
    }
}
