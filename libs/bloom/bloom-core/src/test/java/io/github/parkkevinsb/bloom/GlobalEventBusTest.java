package io.github.parkkevinsb.bloom;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalEventBusTest {

    @Test
    void get_instance_returns_shared_default_bus() {
        EventBus first = GlobalEventBus.getInstance();
        EventBus second = GlobalEventBus.getInstance();

        assertThat(first).isSameAs(second);
    }
}
