package com.archdox.cloud.agent.application;

import com.archdox.cloud.agent.domain.ArchDoxAgent;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ArchDoxAgentRuntimeCompatibilityService {
    private static final String OK = "OK";
    private static final String UPDATE_RECOMMENDED = "UPDATE_RECOMMENDED";
    private static final String UPDATE_REQUIRED = "UPDATE_REQUIRED";

    private final ArchDoxAgentProperties properties;

    public ArchDoxAgentRuntimeCompatibilityService(ArchDoxAgentProperties properties) {
        this.properties = properties;
    }

    public ArchDoxAgentRuntimeCompatibility evaluate(AgentHello hello) {
        var agentVersion = text(hello.version());
        var protocolVersion = text(hello.protocolVersion());
        var launcherVersion = text(hello.launcherVersion());
        var updateChannel = text(hello.updateChannel());
        if (protocolVersion == null) {
            return compatibility(
                    UPDATE_REQUIRED,
                    false,
                    true,
                    "Agent protocolVersion is missing; update the ArchDox Agent runtime.",
                    agentVersion,
                    protocolVersion,
                    launcherVersion,
                    updateChannel);
        }
        if (compareProtocol(protocolVersion, properties.safeMinimumProtocolVersion()) < 0) {
            return compatibility(
                    UPDATE_REQUIRED,
                    false,
                    true,
                    "Agent protocolVersion is older than the minimum supported Cloud API protocol.",
                    agentVersion,
                    protocolVersion,
                    launcherVersion,
                    updateChannel);
        }
        if (compareVersion(agentVersion, properties.safeMinimumAgentVersion()) < 0) {
            return compatibility(
                    UPDATE_REQUIRED,
                    false,
                    true,
                    "Agent runtime version is older than the minimum supported version.",
                    agentVersion,
                    protocolVersion,
                    launcherVersion,
                    updateChannel);
        }
        if (compareVersion(agentVersion, properties.safeRecommendedAgentVersion()) < 0) {
            return compatibility(
                    UPDATE_RECOMMENDED,
                    true,
                    false,
                    "A newer ArchDox Agent runtime is recommended.",
                    agentVersion,
                    protocolVersion,
                    launcherVersion,
                    updateChannel);
        }
        return compatibility(
                OK,
                true,
                false,
                "Agent runtime is compatible.",
                agentVersion,
                protocolVersion,
                launcherVersion,
                updateChannel);
    }

    public Map<String, Object> capabilitiesWithCompatibility(
            AgentHello hello,
            ArchDoxAgentRuntimeCompatibility compatibility
    ) {
        var capabilities = new LinkedHashMap<String, Object>(hello.capabilities() == null ? Map.of() : hello.capabilities());
        capabilities.put("agentVersion", text(hello.version()));
        capabilities.put("protocolVersion", text(hello.protocolVersion()));
        capabilities.put("launcherVersion", text(hello.launcherVersion()));
        capabilities.put("updateChannel", text(hello.updateChannel()));
        capabilities.put("compatibility", compatibility.toMap());
        return capabilities;
    }

    public boolean commandAllowed(ArchDoxAgent agent) {
        if (agent == null) {
            return false;
        }
        var compatibility = mapValue(agent.capabilitiesJson(), "compatibility");
        if (compatibility == null || compatibility.isEmpty()) {
            return false;
        }
        return Boolean.TRUE.equals(compatibility.get("commandAllowed"));
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> compatibilityMap(ArchDoxAgent agent) {
        if (agent == null || agent.capabilitiesJson() == null) {
            return Map.of();
        }
        var value = agent.capabilitiesJson().get("compatibility");
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Map<String, Object> source, String key) {
        if (source == null) {
            return Map.of();
        }
        var value = source.get(key);
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private ArchDoxAgentRuntimeCompatibility compatibility(
            String status,
            boolean commandAllowed,
            boolean updateRequired,
            String reason,
            String agentVersion,
            String protocolVersion,
            String launcherVersion,
            String updateChannel
    ) {
        return new ArchDoxAgentRuntimeCompatibility(
                status,
                commandAllowed,
                updateRequired,
                reason,
                agentVersion,
                protocolVersion,
                launcherVersion,
                updateChannel,
                properties.safeCurrentProtocolVersion(),
                properties.safeMinimumProtocolVersion(),
                properties.safeMinimumAgentVersion(),
                properties.safeRecommendedAgentVersion());
    }

    private int compareProtocol(String left, String right) {
        var safeLeft = text(left);
        var safeRight = text(right);
        if (safeLeft == null && safeRight == null) {
            return 0;
        }
        if (safeLeft == null) {
            return -1;
        }
        if (safeRight == null) {
            return 1;
        }
        return safeLeft.compareTo(safeRight);
    }

    private int compareVersion(String left, String right) {
        var leftParts = numericVersionParts(left);
        var rightParts = numericVersionParts(right);
        for (int i = 0; i < Math.max(leftParts.length, rightParts.length); i++) {
            var leftPart = i < leftParts.length ? leftParts[i] : 0;
            var rightPart = i < rightParts.length ? rightParts[i] : 0;
            if (leftPart != rightPart) {
                return Integer.compare(leftPart, rightPart);
            }
        }
        return 0;
    }

    private int[] numericVersionParts(String version) {
        var normalized = text(version);
        if (normalized == null) {
            return new int[] {0};
        }
        var dash = normalized.indexOf('-');
        if (dash >= 0) {
            normalized = normalized.substring(0, dash);
        }
        var parts = normalized.split("\\.");
        var numbers = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            numbers[i] = parseInt(parts[i]);
        }
        return numbers;
    }

    private int parseInt(String value) {
        try {
            return Integer.parseInt(value.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private String text(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
