package com.archdox.agent.storage;

public record AgentStoredObject(String logicalRef, String storageLocation, long bytes) {
}
