package io.github.parkkevinsb.bloom;

import org.junit.jupiter.api.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AsyncEventBusTest {

    static final class Ping { }

    @Test
    void delegates_subscriptions_and_schedules_publish() {
        EventBus delegate = LocalEventBus.create();
        AtomicInteger scheduled = new AtomicInteger();
        Executor executor = command -> {
            scheduled.incrementAndGet();
            command.run();
        };
        EventBus bus = new AsyncEventBus(delegate, executor);
        AtomicInteger received = new AtomicInteger();

        bus.subscribe(Ping.class, event -> received.incrementAndGet());
        bus.publish(new Ping());

        assertThat(scheduled.get()).isEqualTo(1);
        assertThat(received.get()).isEqualTo(1);
    }

    @Test
    void validates_inputs() {
        EventBus delegate = LocalEventBus.create();
        Executor executor = Runnable::run;

        assertThatThrownBy(() -> new AsyncEventBus(null, executor))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AsyncEventBus(delegate, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AsyncEventBus(delegate, executor).publish(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
