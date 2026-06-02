package com.archdox.cloud.agent.application;

import com.archdox.cloud.agent.domain.ArchDoxAgent;
import com.archdox.cloud.agent.domain.ArchDoxAgentDeploymentMode;
import com.archdox.cloud.agent.infra.ArchDoxAgentSessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArchDoxAgentSessionRegistryTest {
    @Test
    void send_to_same_agent_session_is_serialized_by_websocket_decorator() throws Exception {
        var repository = mock(ArchDoxAgentSessionRepository.class);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var properties = new ArchDoxAgentProperties();
        properties.setWebsocketSendTimeLimitMs(5_000);
        properties.setWebsocketSendBufferSizeBytes(1024 * 1024);
        var registry = new ArchDoxAgentSessionRegistry(new ObjectMapper(), repository, properties);

        var session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("ws-concurrent");
        when(session.isOpen()).thenReturn(true);

        var sending = new AtomicBoolean(false);
        var overlapped = new AtomicBoolean(false);
        doAnswer(invocation -> {
            if (!sending.compareAndSet(false, true)) {
                overlapped.set(true);
            }
            try {
                Thread.sleep(25);
            } finally {
                sending.set(false);
            }
            return null;
        }).when(session).sendMessage(any(TextMessage.class));

        var agent = new ArchDoxAgent(
                10L,
                "office-main",
                ArchDoxAgentDeploymentMode.LOCAL_OFFICE,
                "1.0.0",
                Map.of("documentGeneration", true),
                Map.of(),
                OffsetDateTime.now());
        ReflectionTestUtils.setField(agent, "id", 100L);

        registry.prepareOutboundSession(session);
        registry.register(agent, session);

        var sendCount = 12;
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(sendCount);
        try {
            var futures = java.util.stream.IntStream.range(0, sendCount)
                    .mapToObj(index -> executor.submit(() -> {
                        assertThat(start.await(2, TimeUnit.SECONDS)).isTrue();
                        return registry.send(100L, AgentOutboundMessage.error("message-" + index));
                    }))
                    .toList();
            start.countDown();
            for (var future : futures) {
                assertThat(future.get(2, TimeUnit.SECONDS)).isTrue();
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(overlapped).isFalse();
        verify(session, times(sendCount)).sendMessage(any(TextMessage.class));
    }
}
