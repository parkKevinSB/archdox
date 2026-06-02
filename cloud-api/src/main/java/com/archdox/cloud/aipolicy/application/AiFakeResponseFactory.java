package com.archdox.cloud.aipolicy.application;

import io.github.parkkevinsb.flower.ai.harness.model.AiModelRequest;
import io.github.parkkevinsb.flower.ai.harness.model.AiModelResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class AiFakeResponseFactory {
    private static final String DOCUMENT_QA_PROMPT = "archdox-document-qa";
    private static final String REPORT_PREFLIGHT_PROMPT = "archdox-report-preflight";
    private static final String OPS_DIAGNOSIS_PROMPT = "archdox-ops-diagnosis";
    private static final String CONVERSATION_PLANNER_PROMPT = "archdox-conversation-planner";
    private static final Pattern USER_MESSAGE_PATTERN = Pattern.compile("\\\"userMessage\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"");

    public AiModelResponse create(AiModelRequest request, long latencyMs) {
        var rawText = switch (request.prompt().version().id()) {
            case DOCUMENT_QA_PROMPT -> documentQaResponse();
            case REPORT_PREFLIGHT_PROMPT -> reportPreflightResponse();
            case OPS_DIAGNOSIS_PROMPT -> opsDiagnosisResponse();
            case CONVERSATION_PLANNER_PROMPT -> conversationPlannerResponse(request);
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

    private String conversationPlannerResponse(AiModelRequest request) {
        var prompt = promptText(request);
        var userMessage = userMessage(prompt);
        var lower = userMessage.toLowerCase(Locale.ROOT);
        if (prompt.contains("\"stage\" : \"AWAITING_SITE\"")
                && (userMessage.contains("현장") || lower.contains("site"))
                && (userMessage.contains("생성") || userMessage.contains("만들") || lower.contains("create"))) {
            var name = cleanName(userMessage, "현장");
            return """
                    {
                      "decision": "PROPOSE_ACTION",
                      "actionType": "CREATE_SITE",
                      "requiresConfirmation": true,
                      "confidence": 0.78,
                      "userMessage": "현장 생성을 제안합니다. 확인하면 기존 현장 생성 절차로 진행합니다.",
                      "payload": {
                        "name": "%s",
                        "siteType": "CONSTRUCTION_SITE"
                      },
                      "rationale": "Development fake AI detected a site creation request."
                    }
                    """.formatted(jsonText(name));
        }
        if (prompt.contains("\"stage\" : \"AWAITING_REPORT\"")
                && (userMessage.contains("리포트") || userMessage.contains("감리") || lower.contains("report"))
                && (userMessage.contains("생성") || userMessage.contains("작성") || userMessage.contains("시작")
                        || lower.contains("create") || lower.contains("start"))) {
            var title = cleanName(userMessage, "리포트");
            return """
                    {
                      "decision": "PROPOSE_ACTION",
                      "actionType": "CREATE_REPORT",
                      "requiresConfirmation": true,
                      "confidence": 0.76,
                      "userMessage": "리포트 생성을 제안합니다. 확인하면 기존 리포트 생성 절차로 진행합니다.",
                      "payload": {
                        "title": "%s",
                        "reportType": "CONSTRUCTION_DAILY_SUPERVISION_LOG"
                      },
                      "rationale": "Development fake AI detected a report creation request."
                    }
                    """.formatted(jsonText(title));
        }
        if (prompt.contains("\"stage\" : \"REPORT_WORKING\"")
                && (userMessage.contains("제출") || userMessage.contains("완료") || lower.contains("submit"))) {
            return """
                    {
                      "decision": "PROPOSE_ACTION",
                      "actionType": "SUBMIT_REPORT",
                      "requiresConfirmation": true,
                      "confidence": 0.72,
                      "userMessage": "리포트 제출을 제안합니다. 확인하면 기존 리포트 제출 검증 경로로 진행합니다.",
                      "payload": {},
                      "rationale": "Development fake AI detected a report submit request."
                    }
                    """;
        }
        if (prompt.contains("\"stage\" : \"REVIEWING\"")
                && (userMessage.contains("검토") || lower.contains("preflight") || lower.contains("review"))) {
            return """
                    {
                      "decision": "PROPOSE_ACTION",
                      "actionType": "RUN_PREFLIGHT_REVIEW",
                      "requiresConfirmation": true,
                      "confidence": 0.74,
                      "userMessage": "문서 생성 전 검토를 제안합니다. 확인하면 기존 사전검토 절차로 진행합니다.",
                      "payload": {},
                      "rationale": "Development fake AI detected a preflight review request."
                    }
                    """;
        }
        if (prompt.contains("\"stage\" : \"REVIEWING\"")
                && (userMessage.contains("문서 생성") || userMessage.contains("생성") || lower.contains("generate"))) {
            return """
                    {
                      "decision": "PROPOSE_ACTION",
                      "actionType": "REQUEST_DOCUMENT_GENERATION",
                      "requiresConfirmation": true,
                      "confidence": 0.72,
                      "userMessage": "문서 생성을 제안합니다. 확인하면 기존 문서 생성 job 절차로 진행합니다.",
                      "payload": {
                        "outputFormat": "DOCX"
                      },
                      "rationale": "Development fake AI detected a document generation request."
                    }
                    """;
        }
        if (prompt.contains("\"stage\" : \"REPORT_WORKING\"") && !userMessage.isBlank()) {
            return """
                    {
                      "decision": "PROPOSE_ACTION",
                      "actionType": "UPDATE_REPORT_STEP",
                      "requiresConfirmation": false,
                      "confidence": 0.68,
                      "userMessage": "리포트 단계 저장 후보를 찾았습니다. 선택한 단계에 내용을 저장할 수 있습니다.",
                      "payload": {
                        "payload": {
                          "workerNote": "%s",
                          "source": "WORKER_CHAT_PLANNER"
                        }
                      },
                      "rationale": "Development fake AI treated the message as report step content."
                    }
                    """.formatted(jsonText(userMessage));
        }
        return """
                {
                  "decision": "ASK_CLARIFICATION",
                  "actionType": "",
                  "requiresConfirmation": false,
                  "confidence": 0.5,
                  "userMessage": "어떤 작업을 진행할지 조금 더 구체적으로 알려주세요.",
                  "payload": {},
                  "rationale": "Development fake AI could not map the message to one allowed action."
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

    private String promptText(AiModelRequest request) {
        if (request == null || request.prompt() == null) {
            return "";
        }
        var builder = new StringBuilder();
        for (var message : request.prompt().messages()) {
            builder.append(message.content()).append('\n');
        }
        return builder.toString();
    }

    private String userMessage(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return "";
        }
        var matcher = USER_MESSAGE_PATTERN.matcher(prompt);
        String last = "";
        while (matcher.find()) {
            last = matcher.group(1);
        }
        return last
                .replace("\\\"", "\"")
                .replace("\\n", "\n")
                .trim();
    }

    private String cleanName(String userMessage, String fallback) {
        var value = userMessage == null ? "" : userMessage.trim();
        value = value.replace("만들어줘", "")
                .replace("만들어", "")
                .replace("생성해줘", "")
                .replace("생성", "")
                .replace("작성해줘", "")
                .replace("작성", "")
                .replace("시작해줘", "")
                .replace("시작", "")
                .trim();
        if (value.isBlank()) {
            return fallback;
        }
        return value.length() > 60 ? value.substring(0, 60) : value;
    }

    private String jsonText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", " ")
                .replace("\n", " ")
                .trim();
    }
}
