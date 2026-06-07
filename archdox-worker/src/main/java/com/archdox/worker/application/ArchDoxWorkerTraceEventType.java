package com.archdox.worker.application;

public enum ArchDoxWorkerTraceEventType {
    REQUEST_RECEIVED,
    ACTION_UNKNOWN,
    POLICY_ALLOWED,
    POLICY_DENIED,
    APPROVAL_REQUIRED,
    ACTION_STARTED,
    ACTION_SUCCEEDED,
    ACTION_CANCELLED,
    ACTION_FAILED,
    ACTION_REJECTED
}
