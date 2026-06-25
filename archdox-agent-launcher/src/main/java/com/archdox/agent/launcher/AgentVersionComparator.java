package com.archdox.agent.launcher;

public final class AgentVersionComparator {
    private AgentVersionComparator() {
    }

    public static int compareVersion(String left, String right) {
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

    public static int compareProtocol(String left, String right) {
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

    private static int[] numericVersionParts(String version) {
        var normalized = text(version);
        if (normalized == null || "embedded".equalsIgnoreCase(normalized)) {
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

    private static int parseInt(String value) {
        try {
            var numeric = value.replaceAll("[^0-9]", "");
            return numeric.isBlank() ? 0 : Integer.parseInt(numeric);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
