package io.github.parkkevinsb.bloom;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalEventBusTest {

    static class ParentEvent { }

    static final class ChildEvent extends ParentEvent { }

    static final class OrderPlaced {
        private final String orderId;

        OrderPlaced(String orderId) {
            this.orderId = orderId;
        }

        String orderId() {
            return orderId;
        }
    }

    @Test
    void publishes_plain_domain_objects_to_matching_type() {
        EventBus bus = LocalEventBus.create();
        List<String> received = new ArrayList<>();

        bus.subscribe(OrderPlaced.class, event -> received.add(event.orderId()));

        bus.publish(new OrderPlaced("order-1"));
        bus.publish(new OrderPlaced("order-2"));

        assertThat(received).containsExactly("order-1", "order-2");
    }

    @Test
    void matching_uses_exact_runtime_type_only() {
        EventBus bus = LocalEventBus.create();
        AtomicInteger parentCount = new AtomicInteger();
        AtomicInteger childCount = new AtomicInteger();

        bus.subscribe(ParentEvent.class, event -> parentCount.incrementAndGet());
        bus.subscribe(ChildEvent.class, event -> childCount.incrementAndGet());

        bus.publish(new ChildEvent());

        assertThat(parentCount.get()).isZero();
        assertThat(childCount.get()).isEqualTo(1);
    }

    @Test
    void closing_subscription_removes_handler_and_is_idempotent() {
        EventBus bus = LocalEventBus.create();
        AtomicInteger count = new AtomicInteger();

        Subscription subscription = bus.subscribe(OrderPlaced.class, event -> count.incrementAndGet());

        bus.publish(new OrderPlaced("before"));
        subscription.close();
        subscription.close();
        bus.publish(new OrderPlaced("after"));

        assertThat(count.get()).isEqualTo(1);
        assertThat(subscription.isActive()).isFalse();
    }

    @Test
    void failing_handler_isolated_and_reported() {
        LocalEventBus bus = LocalEventBus.create();
        List<Throwable> errors = new ArrayList<>();
        AtomicInteger reached = new AtomicInteger();

        bus.onListenerError((event, handler, cause) -> errors.add(cause));
        bus.subscribe(OrderPlaced.class, event -> {
            throw new IllegalStateException("boom");
        });
        bus.subscribe(OrderPlaced.class, event -> reached.incrementAndGet());

        bus.publish(new OrderPlaced("order-1"));

        assertThat(reached.get()).isEqualTo(1);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void secondary_error_handler_failure_is_swallowed() {
        LocalEventBus bus = LocalEventBus.create();
        AtomicInteger reached = new AtomicInteger();

        bus.onListenerError((event, handler, cause) -> {
            throw new IllegalStateException("secondary");
        });
        bus.subscribe(OrderPlaced.class, event -> {
            throw new IllegalStateException("primary");
        });
        bus.subscribe(OrderPlaced.class, event -> reached.incrementAndGet());

        bus.publish(new OrderPlaced("order-1"));

        assertThat(reached.get()).isEqualTo(1);
    }

    @Test
    void rejects_null_inputs() {
        EventBus bus = LocalEventBus.create();

        assertThatThrownBy(() -> bus.subscribe(null, event -> { }))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> bus.subscribe(OrderPlaced.class, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> bus.publish(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
