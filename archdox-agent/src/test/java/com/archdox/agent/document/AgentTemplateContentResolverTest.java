package com.archdox.agent.document;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.archdox.agent.cloud.ArchDoxAgentProperties;
import com.archdox.document.TemplateSpec;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgentTemplateContentResolverTest {
    @Test
    void downloadsTemplateWithAgentCredentialsAndCachesByStorageRef(@TempDir Path tempDir) throws Exception {
        var content = "template-content".getBytes();
        var server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/agent/api/v1/document-jobs/700/template/content", exchange -> {
            assertEquals("7", exchange.getRequestHeaders().getFirst("X-Agent-Id"));
            assertEquals("device-secret", exchange.getRequestHeaders().getFirst("X-Agent-Device-Secret"));
            assertEquals("10", exchange.getRequestHeaders().getFirst("X-Agent-Office-Id"));
            exchange.sendResponseHeaders(200, content.length);
            try (var output = exchange.getResponseBody()) {
                output.write(content);
            }
        });
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();
        try {
            var properties = new ArchDoxAgentProperties();
            properties.setCloudHttpBaseUrl("http://localhost:" + server.getAddress().getPort());
            properties.setLocalStorageRoot(tempDir.toString());
            properties.setAgentId(7L);
            properties.setOfficeId(10L);
            properties.setDeviceSecret("device-secret");
            var resolver = new AgentTemplateContentResolver(properties, new AgentTemplateStore(properties));

            var resolved = resolver.resolve(new TemplateSpec(
                    "INSPECTION_REPORT",
                    1,
                    "templates/offices/10/revisions/700/template.docx",
                    "{}",
                    "{}",
                    "/agent/api/v1/document-jobs/700/template/content"));

            assertArrayEquals(content, resolved.orElseThrow());
            assertArrayEquals(content, Files.readAllBytes(tempDir.resolve("templates/offices/10/revisions/700/template.docx")));
        } finally {
            server.stop(0);
        }
    }
}
