package com.archdox.cloud.engine.connect.domain;

public enum EngineConnectClientType {
    CODEX("Codex"),
    CLAUDE("Claude"),
    CURSOR("Cursor"),
    CHATGPT("ChatGPT"),
    CUSTOM_AGENT("Custom Agent");

    private final String displayName;

    EngineConnectClientType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
