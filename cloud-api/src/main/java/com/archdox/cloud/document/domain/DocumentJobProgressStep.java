package com.archdox.cloud.document.domain;

public enum DocumentJobProgressStep {
    QUEUED,
    VALIDATING,
    DISPATCHING,
    WAITING_FOR_AGENT,
    RENDERING,
    STORING_ARTIFACTS,
    GENERATED,
    FAILED
}
