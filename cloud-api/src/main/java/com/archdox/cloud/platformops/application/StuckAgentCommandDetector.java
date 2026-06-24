package com.archdox.cloud.platformops.application;

import com.archdox.cloud.agent.domain.ArchDoxAgentCommandStatus;
import com.archdox.cloud.agent.infra.ArchDoxAgentCommandRepository;
import com.archdox.cloud.platformops.domain.PlatformOpsFindingSeverity;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class StuckAgentCommandDetector implements PlatformOpsDetector {
    private final ArchDoxAgentCommandRepository repository;
    private final PlatformOpsAutomationSettingsService automationSettingsService;

    public StuckAgentCommandDetector(ArchDoxAgentCommandRepository repository, PlatformOpsAutomationSettingsService automationSettingsService) {
        this.repository = repository;
        this.automationSettingsService = automationSettingsService;
    }

    @Override
    public String category() {
        return "AGENT_COMMAND_STUCK";
    }

    @Override
    public List<PlatformOpsDetectionFinding> detect(PlatformOpsDetectionContext context) {
        var settings = automationSettingsService.settings();
        var cutoff = context.now().minusMinutes(settings.agentCommandStuckMinutes());
        return repository.findByStatusInAndCreatedAtBeforeOrderByCreatedAtAsc(
                        List.of(ArchDoxAgentCommandStatus.PENDING, ArchDoxAgentCommandStatus.DELIVERED, ArchDoxAgentCommandStatus.ACKED),
                        cutoff,
                        context.page())
                .stream()
                .map(command -> {
                    var evidence = new LinkedHashMap<String, Object>();
                    evidence.put("agentId", command.agent().id());
                    evidence.put("commandType", command.commandType().name());
                    evidence.put("status", command.status().name());
                    evidence.put("attemptCount", command.attemptCount());
                    evidence.put("maxAttempts", command.maxAttempts());
                    evidence.put("createdAt", command.createdAt().toString());
                    evidence.put("thresholdMinutes", settings.agentCommandStuckMinutes());
                    return new PlatformOpsDetectionFinding(
                            command.agent().officeId(),
                            PlatformOpsFindingSeverity.WARN,
                            category(),
                            "AGENT_COMMAND_STUCK_DETECTED",
                            "Agent command appears stuck",
                            "An ArchDox Agent command has stayed in-flight longer than expected.",
                            "AGENT_COMMAND",
                            String.valueOf(command.id()),
                            "agent-command",
                            String.valueOf(command.id()),
                            evidence,
                            "Check whether the Agent is connected and whether the related workflow should retry or fail.");
                })
                .toList();
    }
}
