package io.github.parkkevinsb.bloom;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AbstractEventSubscriberTest {

    static final class FirstEvent { }

    static final class SecondEvent { }

    static final class Owner extends AbstractEventSubscriber {
        Subscription onFirst(EventBus bus, AtomicInteger count) {
            return on(bus, FirstEvent.class, event -> count.incrementAndGet());
        }

        Subscription onSecond(EventBus bus, AtomicInteger count) {
            return on(bus, SecondEvent.class, event -> count.incrementAndGet());
        }

        void invalid(EventBus bus) {
            on(bus, FirstEvent.class, null);
        }
    }

    @Test
    void closes_all_tracked_subscriptions() {
        EventBus bus = LocalEventBus.create();
        Owner owner = new Owner();
        AtomicInteger first = new AtomicInteger();
        AtomicInteger second = new AtomicInteger();

        Subscription firstSub = owner.onFirst(bus, first);
        Subscription secondSub = owner.onSecond(bus, second);

        bus.publish(new FirstEvent());
        bus.publish(new SecondEvent());
        owner.close();
        owner.close();
        bus.publish(new FirstEvent());
        bus.publish(new SecondEvent());

        assertThat(first.get()).isEqualTo(1);
        assertThat(second.get()).isEqualTo(1);
        assertThat(firstSub.isActive()).isFalse();
        assertThat(secondSub.isActive()).isFalse();
    }

    @Test
    void validates_inputs_before_subscribing() {
        Owner owner = new Owner();
        EventBus bus = LocalEventBus.create();

        assertThatThrownBy(() -> owner.onFirst(null, new AtomicInteger()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> owner.invalid(bus))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
