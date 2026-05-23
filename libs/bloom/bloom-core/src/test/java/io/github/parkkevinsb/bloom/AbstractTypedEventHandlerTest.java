package io.github.parkkevinsb.bloom;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AbstractTypedEventHandlerTest {

    static final class Ping { }

    static final class Pong { }

    static final class PingHandler extends AbstractTypedEventHandler<Ping> {
        private final AtomicInteger count = new AtomicInteger();

        PingHandler() {
            super(Ping.class);
        }

        @Override
        protected void onEvent(Ping event) {
            count.incrementAndGet();
        }

        int count() {
            return count.get();
        }
    }

    @Test
    void handler_is_bound_to_one_event_type() {
        PingHandler handler = new PingHandler();

        assertThat(handler.eventType()).isEqualTo(Ping.class);
    }

    @Test
    void subscribe_to_is_idempotent_for_active_subscription() {
        EventBus bus = LocalEventBus.create();
        PingHandler handler = new PingHandler();

        Subscription first = handler.subscribeTo(bus);
        Subscription second = handler.subscribeTo(bus);

        bus.publish(new Ping());

        assertThat(second).isSameAs(first);
        assertThat(handler.count()).isEqualTo(1);
        assertThat(first.isActive()).isTrue();
    }

    @Test
    void close_unsubscribes_and_allows_later_resubscribe() {
        EventBus bus = LocalEventBus.create();
        PingHandler handler = new PingHandler();

        Subscription first = handler.subscribeTo(bus);
        handler.close();
        bus.publish(new Ping());

        Subscription second = handler.subscribeTo(bus);
        bus.publish(new Ping());

        assertThat(first.isActive()).isFalse();
        assertThat(second).isNotSameAs(first);
        assertThat(second.isActive()).isTrue();
        assertThat(handler.count()).isEqualTo(1);
    }

    @Test
    void exact_type_matching_still_applies_to_typed_handler() {
        EventBus bus = LocalEventBus.create();
        PingHandler handler = new PingHandler();

        handler.subscribeTo(bus);
        bus.publish(new Pong());
        bus.publish(new Ping());

        assertThat(handler.count()).isEqualTo(1);
    }

    @Test
    void rejects_nulls() {
        assertThatThrownBy(() -> new AbstractTypedEventHandler<Ping>(null) {
            @Override protected void onEvent(Ping event) { }
        }).isInstanceOf(IllegalArgumentException.class);

        PingHandler handler = new PingHandler();
        assertThatThrownBy(() -> handler.subscribeTo(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
