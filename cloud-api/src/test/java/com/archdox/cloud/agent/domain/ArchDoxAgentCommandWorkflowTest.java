package com.archdox.cloud.agent.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ArchDoxAgentCommandWorkflowTest {
    @Test
    void commandMovesThroughDeliveryAckAndCompletion() {
        var now = OffsetDateTime.now();
        var agent = new ArchDoxAgent(10L, "agent-a", "0.0.1", Map.of("nas", true), now);
        var command = new ArchDoxAgentCommand(
                agent,
                ArchDoxAgentCommandType.PHOTO_PICKUP,
                Map.of("photoId", 100L),
                now,
                now.plusMinutes(30));

        command.markDelivered(now.plusSeconds(1));
        assertEquals(1, command.attemptCount());
        command.ack(now.plusSeconds(2));
        command.complete(Map.of("agentOriginalStorageRef", "reports/100/original.jpg"), now.plusSeconds(3));

        assertEquals(ArchDoxAgentCommandStatus.COMPLETED, command.status());
    }

    @Test
    void commandCanFailWithErrorMessage() {
        var now = OffsetDateTime.now();
        var agent = new ArchDoxAgent(10L, "agent-a", "0.0.1", Map.of("nas", true), now);
        var command = new ArchDoxAgentCommand(
                agent,
                ArchDoxAgentCommandType.PHOTO_PICKUP,
                Map.of("photoId", 100L),
                now,
                now.plusMinutes(30));

        command.fail("NAS unavailable", Map.of(), now.plusSeconds(1));

        assertEquals(ArchDoxAgentCommandStatus.FAILED, command.status());
    }

    @Test
    void commandRetryBudgetIsConfiguredForTransportTraceOnly() {
        var now = OffsetDateTime.now();
        var agent = new ArchDoxAgent(10L, "agent-a", "0.0.1", Map.of("nas", true), now);
        var command = new ArchDoxAgentCommand(
                agent,
                ArchDoxAgentCommandType.PHOTO_PICKUP,
                Map.of("photoId", 100L),
                now,
                now.plusMinutes(30));
        command.configureRetry(3, now);

        command.markDelivered(now.plusSeconds(1));

        assertEquals(ArchDoxAgentCommandStatus.DELIVERED, command.status());
        assertEquals(1, command.attemptCount());
        assertEquals(3, command.maxAttempts());
        assertNull(command.nextAttemptAt());
    }

    @Test
    void commandExpiresAfterRetryBudgetIsConsumed() {
        var now = OffsetDateTime.now();
        var agent = new ArchDoxAgent(10L, "agent-a", "0.0.1", Map.of("nas", true), now);
        var command = new ArchDoxAgentCommand(
                agent,
                ArchDoxAgentCommandType.PHOTO_PICKUP,
                Map.of("photoId", 100L),
                now,
                now.plusMinutes(30));
        command.configureRetry(1, now);

        command.markDelivered(now.plusSeconds(1));
        command.expire("Agent command attempt timed out", now.plusMinutes(30));

        assertEquals(ArchDoxAgentCommandStatus.EXPIRED, command.status());
        assertEquals(1, command.attemptCount());
    }
}
