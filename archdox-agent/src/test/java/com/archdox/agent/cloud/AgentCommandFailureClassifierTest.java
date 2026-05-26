package com.archdox.agent.cloud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import org.junit.jupiter.api.Test;

class AgentCommandFailureClassifierTest {
    @Test
    void classifiesRemoteServerFailureAsRetryable() {
        var failure = AgentCommandFailureClassifier.classify(
                new IOException("Render package download failed with HTTP 503"),
                "DOCUMENT_RENDER_FAILED");

        assertEquals("AGENT_REMOTE_SERVICE_UNAVAILABLE", failure.errorCode());
        assertTrue(failure.retryable());
    }

    @Test
    void classifiesInvalidDocxTemplateAsNonRetryable() {
        var failure = AgentCommandFailureClassifier.classify(
                new IllegalStateException("Document template content could not be read: ZipFile invalid LOC header"),
                "DOCUMENT_RENDER_FAILED");

        assertEquals("TEMPLATE_INVALID_DOCX", failure.errorCode());
        assertFalse(failure.retryable());
    }
}
