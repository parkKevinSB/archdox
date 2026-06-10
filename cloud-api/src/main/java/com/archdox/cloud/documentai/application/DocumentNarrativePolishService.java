package com.archdox.cloud.documentai.application;

import com.archdox.cloud.aipolicy.application.AiHarnessPolicyExecutionService;
import com.archdox.cloud.aipolicy.application.AiModelCallMetadata;
import com.archdox.cloud.aipolicy.domain.AiHarnessPolicyKey;
import com.archdox.cloud.documentai.dto.DocumentNarrativePolishRequest;
import com.archdox.cloud.documentai.dto.DocumentNarrativePolishResponse;
import com.archdox.cloud.documentai.flow.DocumentNarrativePolishAiWorker;
import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.inspection.application.InspectionReportService;
import com.archdox.cloud.inspection.domain.InspectionReport;
import com.archdox.cloud.office.application.OfficePermissionService;
import com.archdox.documentai.NarrativePolishField;
import com.archdox.documentai.NarrativePolishHarnessFactory;
import com.archdox.documentai.NarrativePolishInput;
import com.archdox.documentai.NarrativePolishResult;
import com.archdox.documentai.NarrativePolishStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.parkkevinsb.flower.ai.harness.flow.AiHarnessFlowFactory;
import io.github.parkkevinsb.flower.ai.harness.gateway.AiModelGateway;
import io.github.parkkevinsb.flower.ai.harness.refine.MaxAttemptsRefinePolicy;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunStatus;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunStore;
import io.github.parkkevinsb.flower.ai.harness.spi.TraceListener;
import io.github.parkkevinsb.flower.ai.harness.validate.ValidationResult;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentNarrativePolishService {
    private static final Duration WAIT_GRACE = Duration.ofSeconds(3);
    private static final int MAX_FIELD_COUNT = 30;
    private static final int MAX_FIELD_TEXT_LENGTH = 2000;
    private static final Pattern DAILY_LOG_ENTRY_PATH = Pattern.compile(
            "^steps\\.DAILY_LOG\\.payload\\.dailyItems\\.groups\\[\\d+]\\.entries\\[\\d+]\\.supervisionContent$");
    private static final Pattern DAILY_LOG_REMARK_PATH = Pattern.compile(
            "^steps\\.DAILY_LOG\\.payload\\.(issueAndAction|issueAndActionResult|nextAction)$");
    private static final Pattern REMARKS_PATH = Pattern.compile(
            "^steps\\.REMARKS\\.payload\\.(issueAndAction|nextAction)$");

    private final InspectionReportService reportService;
    private final OfficePermissionService permissionService;
    private final AiHarnessPolicyExecutionService policyExecutionService;
    private final DocumentNarrativePolishAiWorker aiWorker;
    private final AiModelGateway aiModelGateway;
    private final ObjectMapper objectMapper;
    private final TraceListener aiHarnessTraceListener;

    public DocumentNarrativePolishService(
            InspectionReportService reportService,
            OfficePermissionService permissionService,
            AiHarnessPolicyExecutionService policyExecutionService,
            DocumentNarrativePolishAiWorker aiWorker,
            AiModelGateway aiModelGateway,
            ObjectMapper objectMapper,
            TraceListener aiHarnessTraceListener
    ) {
        this.reportService = reportService;
        this.permissionService = permissionService;
        this.policyExecutionService = policyExecutionService;
        this.aiWorker = aiWorker;
        this.aiModelGateway = aiModelGateway;
        this.objectMapper = objectMapper;
        this.aiHarnessTraceListener = aiHarnessTraceListener;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public DocumentNarrativePolishResponse polish(
            Long reportId,
            DocumentNarrativePolishRequest request,
            UserPrincipal principal
    ) {
        var report = reportService.requireReport(reportId);
        permissionService.requireReportWriter(principal.userId(), report.officeId(), report.projectId(), report.id());
        var fields = normalizeFields(request);
        var policy = policyExecutionService.resolve(AiHarnessPolicyKey.DOCUMENT_NARRATIVE_POLISH);
        if (!policy.runnable()) {
            throw new BadRequestException(
                    "DOCUMENT_NARRATIVE_POLISH_NOT_CONFIGURED",
                    "errors.aiHarness.notConfigured",
                    "Document narrative polish AI is not runnable: " + policy.unavailableReason(),
                    Map.of("reason", policy.unavailableReason()));
        }
        var plan = policy.plan();
        policyExecutionService.requireWithinBudget(plan);

        var spec = new NarrativePolishHarnessFactory(objectMapper).spec(
                (findings, ctx) -> {
                },
                AiHarnessRunStore.noop(),
                new MaxAttemptsRefinePolicy(plan.maxAttempts()),
                aiHarnessTraceListener);
        var flow = new AiHarnessFlowFactory<>(aiModelGateway, spec, Instant::now)
                .createFlow(input(report, fields), AiHarnessFlowFactory.RunOverrides.builder()
                        .modelId(plan.modelId())
                        .timeout(plan.timeout())
                        .providerOptions(AiModelCallMetadata.options(
                                report.officeId(),
                                principal.userId(),
                                AiHarnessPolicyKey.DOCUMENT_NARRATIVE_POLISH.name(),
                                "document-narrative-polish",
                                "report:" + report.id() + ":document-narrative-polish",
                                "INSPECTION_REPORT",
                                report.id(),
                                Map.of(
                                        "archdox.reportId", report.id(),
                                        "archdox.policyKey", AiHarnessPolicyKey.DOCUMENT_NARRATIVE_POLISH.name()),
                                plan.maxOutputTokens()))
                        .build());
        if (!aiWorker.submitAndAwait(flow, plan.timeout().plus(WAIT_GRACE))) {
            throw new BadRequestException(
                    "DOCUMENT_NARRATIVE_POLISH_TIMEOUT",
                    "errors.aiHarness.timeout",
                    "Document narrative polish AI timed out.");
        }
        if (flow.context().status() != AiHarnessRunStatus.SUCCEEDED) {
            throw new BadRequestException(
                    "DOCUMENT_NARRATIVE_POLISH_HARNESS_FAILED",
                    "errors.aiHarness.failed",
                    flow.context().terminalReason().orElse("Document narrative polish AI did not succeed."));
        }
        var result = result(flow.context().latestValidation())
                .orElseThrow(() -> new BadRequestException(
                        "DOCUMENT_NARRATIVE_POLISH_RESULT_INVALID",
                        "errors.aiHarness.invalidResult",
                        "Document narrative polish AI did not return a valid result."));
        return response(
                result,
                fields,
                plan.provider().providerCode(),
                plan.modelId().asString(),
                flow.context().runId().value());
    }

    private NarrativePolishInput input(InspectionReport report, List<NarrativePolishField> fields) {
        return new NarrativePolishInput(
                String.valueOf(report.officeId()),
                String.valueOf(report.id()),
                report.reportType(),
                report.title(),
                "DOCUMENT_RENDER_DRAFT",
                fields,
                reportContext(report));
    }

    private Map<String, Object> reportContext(InspectionReport report) {
        var context = new LinkedHashMap<String, Object>();
        context.put("reportNo", report.reportNo() == null ? "" : report.reportNo());
        context.put("status", report.status().name());
        context.put("contentRevision", report.contentRevision());
        context.put("submittedRevision", report.submittedRevision() == null ? "" : report.submittedRevision());
        context.put("projectId", report.projectId());
        context.put("siteId", report.siteId() == null ? "" : report.siteId());
        return Map.copyOf(context);
    }

    private DocumentNarrativePolishResponse response(
            NarrativePolishResult result,
            List<NarrativePolishField> fields,
            String providerCode,
            String modelId,
            String runId
    ) {
        var fieldsByPath = fields.stream()
                .collect(Collectors.toMap(
                        NarrativePolishField::path,
                        field -> field,
                        (left, right) -> left,
                        LinkedHashMap::new));
        var suggestions = result.suggestions().stream()
                .filter(suggestion -> fieldsByPath.containsKey(suggestion.path()))
                .map(suggestion -> {
                    var field = fieldsByPath.get(suggestion.path());
                    var polishedText = suggestion.polishedText();
                    var applicable = suggestion.applicable()
                            && !polishedText.isBlank()
                            && !polishedText.equals(field.originalText());
                    return new DocumentNarrativePolishResponse.SuggestionResponse(
                            field.path(),
                            field.label(),
                            field.originalText(),
                            polishedText,
                            suggestion.reason(),
                            suggestion.confidence().name(),
                            applicable);
                })
                .toList();
        var status = result.status();
        if (status == NarrativePolishStatus.DRAFTED && suggestions.stream().noneMatch(DocumentNarrativePolishResponse.SuggestionResponse::applicable)) {
            status = NarrativePolishStatus.NO_CHANGES;
        }
        return new DocumentNarrativePolishResponse(
                status.name(),
                result.summary(),
                providerCode,
                modelId,
                runId,
                suggestions);
    }

    private List<NarrativePolishField> normalizeFields(DocumentNarrativePolishRequest request) {
        if (request == null || request.fields() == null || request.fields().isEmpty()) {
            throw new BadRequestException(
                    "DOCUMENT_NARRATIVE_POLISH_FIELDS_REQUIRED",
                    "errors.documentNarrativePolish.fieldsRequired",
                    "At least one narrative field is required.");
        }
        var fieldsByPath = new LinkedHashMap<String, NarrativePolishField>();
        for (var field : request.fields()) {
            if (field == null) {
                continue;
            }
            var path = text(field.path());
            var value = text(field.value());
            if (path.isBlank() || value.isBlank()) {
                continue;
            }
            if (!isSupportedPath(path)) {
                throw new BadRequestException(
                        "DOCUMENT_NARRATIVE_POLISH_PATH_UNSUPPORTED",
                        "errors.documentNarrativePolish.pathUnsupported",
                        "Unsupported narrative field path.",
                        Map.of("path", path));
            }
            if (value.length() > MAX_FIELD_TEXT_LENGTH) {
                throw new BadRequestException(
                        "DOCUMENT_NARRATIVE_POLISH_FIELD_TOO_LONG",
                        "errors.documentNarrativePolish.fieldTooLong",
                        "Narrative field is too long.",
                        Map.of("path", path, "maxLength", MAX_FIELD_TEXT_LENGTH));
            }
            fieldsByPath.putIfAbsent(path, new NarrativePolishField(path, text(field.label()), value));
        }
        if (fieldsByPath.isEmpty()) {
            throw new BadRequestException(
                    "DOCUMENT_NARRATIVE_POLISH_FIELDS_REQUIRED",
                    "errors.documentNarrativePolish.fieldsRequired",
                    "At least one non-empty narrative field is required.");
        }
        if (fieldsByPath.size() > MAX_FIELD_COUNT) {
            throw new BadRequestException(
                    "DOCUMENT_NARRATIVE_POLISH_FIELD_LIMIT_EXCEEDED",
                    "errors.documentNarrativePolish.fieldLimitExceeded",
                    "Too many narrative fields.",
                    Map.of("maxCount", MAX_FIELD_COUNT));
        }
        return List.copyOf(fieldsByPath.values());
    }

    private boolean isSupportedPath(String path) {
        return DAILY_LOG_ENTRY_PATH.matcher(path).matches()
                || DAILY_LOG_REMARK_PATH.matcher(path).matches()
                || REMARKS_PATH.matcher(path).matches();
    }

    private Optional<NarrativePolishResult> result(Optional<ValidationResult<?>> validation) {
        return validation
                .filter(ValidationResult::isValid)
                .flatMap(value -> {
                    if (value instanceof ValidationResult.Valid<?> valid
                            && valid.value() instanceof NarrativePolishResult result) {
                        return Optional.of(result);
                    }
                    return Optional.empty();
                });
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}
