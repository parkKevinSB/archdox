package com.archdox.cloud.aipolicy.application;

import io.github.parkkevinsb.flower.ai.harness.model.AiModelRequest;
import io.github.parkkevinsb.flower.ai.harness.model.AiModelResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class AiFakeResponseFactory {
    private static final String DOCUMENT_QA_PROMPT = "archdox-document-qa";
    private static final String REPORT_PREFLIGHT_PROMPT = "archdox-report-preflight";
    private static final String OPS_DIAGNOSIS_PROMPT = "archdox-ops-diagnosis";

    public AiModelResponse create(AiModelRequest request, long latencyMs) {
        var rawText = switch (request.prompt().version().id()) {
            case DOCUMENT_QA_PROMPT -> documentQaResponse();
            case REPORT_PREFLIGHT_PROMPT -> reportPreflightResponse();
            case OPS_DIAGNOSIS_PROMPT -> opsDiagnosisResponse();
            default -> defaultPassResponse();
        };
        return new AiModelResponse(
                rawText,
                request.modelId(),
                new AiModelResponse.ResponseMetadata(
                        Optional.of(80),
                        Optional.of(60),
                        Optional.of(Duration.ofMillis(Math.max(0, latencyMs))),
                        Optional.of("fake-stop"),
                        Map.of(
                                "providerCode", request.modelId().provider(),
                                "providerResponseId", "fake-ai-response",
                                "fake", "true")));
    }

    private String documentQaResponse() {
        return """
                {
                  "status": "WARN",
                  "summary": "Development fake AI document QA result. No external model API was called.",
                  "confidence": "MEDIUM",
                  "issues": [
                    {
                      "code": "FAKE_DOCUMENT_QA_REVIEW",
                      "severity": "MEDIUM",
                      "location": "document.preview",
                      "message": "This is a development-only document QA warning.",
                      "evidence": "Fake provider response for local flow verification.",
                      "suggestion": "Run a separate smoke test with a real OpenAI/Ollama provider before production."
                    }
                  ]
                }
                """;
    }

    private String reportPreflightResponse() {
        return """
                {
                  "status": "WARN",
                  "summary": "Development fake AI report preflight result. No external model API was called.",
                  "confidence": "MEDIUM",
                  "issues": [
                    {
                      "code": "FAKE_PREFLIGHT_REVIEW",
                      "category": "WORDING",
                      "severity": "MEDIUM",
                      "location": "report.summary",
                      "message": "This is a development-only wording/completeness warning.",
                      "evidence": "Fake provider response for local preflight verification.",
                      "suggestion": "Confirm required fields and photo evidence against the real office workflow."
                    }
                  ]
                }
                """;
    }

    private String opsDiagnosisResponse() {
        return """
                {
                  "status": "NEEDS_ATTENTION",
                  "summary": "Development fake AI platform ops diagnosis result. No external model API was called.",
                  "confidence": "HIGH",
                  "issues": [
                    {
                      "code": "FAKE_OPS_DIAGNOSIS",
                      "category": "OPS_AI_DIAGNOSIS",
                      "severity": "HIGH",
                      "title": "Development ops diagnosis",
                      "message": "The platform ops diagnosis harness processed a fake provider response successfully.",
                      "evidence": "Fake provider response for platform ops diagnosis flow verification.",
                      "likelyCause": "Local fake AI provider is enabled.",
                      "recommendation": "Before production, run a separate smoke test with a real provider credential.",
                      "suggestedAction": "MANUAL_INVESTIGATION"
                    }
                  ]
                }
                """;
    }

    private String defaultPassResponse() {
        return """
                {
                  "status": "PASS",
                  "summary": "Fake AI provider default response.",
                  "confidence": "LOW",
                  "issues": []
                }
                """;
    }
}
