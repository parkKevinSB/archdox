package io.github.parkkevinsb.bloom;

import io.github.parkkevinsb.bloom.internal.DefaultSubscription;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubscriptionTest {

    @Test
    void close_runs_cleanup_once() {
        AtomicInteger cleanup = new AtomicInteger();
        Subscription subscription = new DefaultSubscription(cleanup::incrementAndGet);

        subscription.close();
        subscription.close();

        assertThat(cleanup.get()).isEqualTo(1);
        assertThat(subscription.isActive()).isFalse();
    }

    @Test
    void close_never_throws_even_when_cleanup_fails() {
        Subscription subscription = new DefaultSubscription(() -> {
            throw new IllegalStateException("cleanup failed");
        });

        assertThatCode(subscription::close).doesNotThrowAnyException();
        assertThat(subscription.isActive()).isFalse();
    }

    @Test
    void rejects_null_cleanup() {
        assertThatThrownBy(() -> new DefaultSubscription(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
