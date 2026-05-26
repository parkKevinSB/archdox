package com.archdox.cloud.agent.api;

import com.archdox.cloud.agent.application.ArchDoxAgentProperties;
import jakarta.websocket.server.ServerContainer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocket
public class AgentWebSocketConfig implements WebSocketConfigurer {
    private final AgentWebSocketHandler agentWebSocketHandler;
    private final ArchDoxAgentProperties properties;

    public AgentWebSocketConfig(
            AgentWebSocketHandler agentWebSocketHandler,
            ArchDoxAgentProperties properties
    ) {
        this.agentWebSocketHandler = agentWebSocketHandler;
        this.properties = properties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(agentWebSocketHandler, "/agent/ws")
                .setAllowedOrigins("*");
    }

    @Bean
    public ServletServerContainerFactoryBean agentWebSocketContainer() {
        var container = new LenientServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(properties.safeWebsocketMaxTextMessageBufferBytes());
        container.setMaxBinaryMessageBufferSize(properties.safeWebsocketMaxBinaryMessageBufferBytes());
        return container;
    }

    private static class LenientServletServerContainerFactoryBean extends ServletServerContainerFactoryBean {
        @Override
        public void afterPropertiesSet() {
            try {
                super.afterPropertiesSet();
            } catch (IllegalStateException ex) {
                if (ex.getMessage() == null || !ex.getMessage().contains(ServerContainer.class.getName())) {
                    throw ex;
                }
            }
        }
    }
}
