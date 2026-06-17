package com.archdox.cloud.documentai.application;

import com.archdox.cloud.aipolicy.application.AiHarnessPolicyExecutionService;
import com.archdox.cloud.aipolicy.application.AiModelCallMetadata;
import com.archdox.cloud.aipolicy.domain.AiHarnessPolicyKey;
import com.archdox.cloud.documentai.dto.DocumentNarrativeApplyResponse;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
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
    private static final Pattern DAILY_LOG_REMARK_PATH = Pattern.compile(
            "^steps\\.DAILY_LOG\\.payload\\.(specialNotes|issueAndAction|issueAndActionResult|nextAction)$");
    private static final Pattern REMARKS_PATH = Pattern.compile(
            "^steps\\.REMARKS\\.payload\\.(specialNotes|remarks|issueAndAction|nextAction)$");
    private static final Pattern DAILY_LOG_ENTRY_DOCUMENT_NARRATIVE_PATH = Pattern.compile(
            "^steps\\.DAILY_LOG\\.payload\\.dailyItems\\.groups\\[(\\d+)]\\.entries\\[(\\d+)]\\.documentNarrativeText$");

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
    public CompletableFuture<DocumentNarrativePolishResponse> polish(
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
        return aiWorker.submitAndTrackAsync(flow, plan.timeout().plus(WAIT_GRACE))
                .thenApply(awaited -> {
                    if (!awaited) {
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
                });
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
                    var containsForeignText = DocumentNarrativePolishLanguageGuard.containsJapaneseText(polishedText);
                    var applicable = suggestion.applicable()
                            && !containsForeignText
                            && !polishedText.isBlank()
                            && !polishedText.equals(field.originalText());
                    return new DocumentNarrativePolishResponse.SuggestionResponse(
                            field.path(),
                            field.label(),
                            field.originalText(),
                            containsForeignText ? field.originalText() : polishedText,
                            containsForeignText
                                    ? DocumentNarrativePolishLanguageGuard.unsafeAiTextReason()
                                    : DocumentNarrativePolishLanguageGuard.sanitizeAiReason(suggestion.reason()),
                            "AI_HARNESS",
                            suggestion.confidence().name(),
                            applicable);
                })
                .toList();
        suggestions = DocumentNarrativePolishRuleBasedPolishPolicy.supplement(fields, suggestions);
        var status = result.status();
        if (suggestions.stream().anyMatch(DocumentNarrativePolishResponse.SuggestionResponse::applicable)) {
            status = NarrativePolishStatus.DRAFTED;
        } else if (status == NarrativePolishStatus.DRAFTED) {
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

    @Transactional
    public DocumentNarrativeApplyResponse applyToReport(
            Long reportId,
            DocumentNarrativePolishRequest request,
            UserPrincipal principal
    ) {
        var report = reportService.requireReport(reportId);
        permissionService.requireReportWriter(principal.userId(), report.officeId(), report.projectId(), report.id());
        var fields = normalizeFields(request);
        var payloadsByStep = new LinkedHashMap<String, Map<String, Object>>();
        var appliedPaths = new ArrayList<String>();
        for (var field : fields) {
            var payload = payloadsByStep.computeIfAbsent(fieldStepCode(field.path()), stepCode -> currentPayload(report.id(), stepCode));
            if (applyField(payload, field.path(), field.originalText())) {
                appliedPaths.add(field.path());
            }
        }
        if (appliedPaths.isEmpty()) {
            return new DocumentNarrativeApplyResponse(0, List.of());
        }
        ensureReportEditableForNarrativeApply(report, principal);
        payloadsByStep.forEach((stepCode, payload) ->
                reportService.applyPreflightFixStep(report.id(), stepCode, new com.archdox.cloud.inspection.dto.SaveInspectionStepRequest(payload), principal));
        return new DocumentNarrativeApplyResponse(appliedPaths.size(), List.copyOf(appliedPaths));
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
        return DAILY_LOG_REMARK_PATH.matcher(path).matches()
                || REMARKS_PATH.matcher(path).matches()
                || DAILY_LOG_ENTRY_DOCUMENT_NARRATIVE_PATH.matcher(path).matches();
    }

    private void ensureReportEditableForNarrativeApply(InspectionReport report, UserPrincipal principal) {
        if (report.canSaveStep() || report.canApplyPreflightFixToSubmittedRevision()) {
            return;
        }
        if (!report.canReopenForEdit()) {
            throw new BadRequestException(
                    "DOCUMENT_NARRATIVE_APPLY_NOT_EDITABLE",
                    "errors.documentNarrativePolish.applyNotEditable",
                    "Report cannot be edited while applying narrative polish.",
                    Map.of("reportId", report.id(), "status", report.status().name()));
        }
        reportService.reopenForEdit(report.id(), principal);
    }

    private Map<String, Object> currentPayload(Long reportId, String stepCode) {
        return reportService.listSteps(reportId).stream()
                .filter(step -> stepCode.equals(step.stepCode()))
                .findFirst()
                .map(step -> mutableMap(step.payload()))
                .orElseGet(LinkedHashMap::new);
    }

    private String fieldStepCode(String path) {
        if (path.startsWith("steps.DAILY_LOG.")) {
            return "DAILY_LOG";
        }
        if (path.startsWith("steps.REMARKS.")) {
            return "REMARKS";
        }
        throw new BadRequestException(
                "DOCUMENT_NARRATIVE_POLISH_PATH_UNSUPPORTED",
                "errors.documentNarrativePolish.pathUnsupported",
                "Unsupported narrative field path.",
                Map.of("path", path));
    }

    private boolean applyField(Map<String, Object> payload, String path, String value) {
        var dailyLogMatcher = DAILY_LOG_REMARK_PATH.matcher(path);
        if (dailyLogMatcher.matches()) {
            payload.put(dailyLogMatcher.group(1), value);
            return true;
        }
        var dailyEntryMatcher = DAILY_LOG_ENTRY_DOCUMENT_NARRATIVE_PATH.matcher(path);
        if (dailyEntryMatcher.matches()) {
            return applyDailyLogEntryDocumentNarrative(
                    payload,
                    Integer.parseInt(dailyEntryMatcher.group(1)),
                    Integer.parseInt(dailyEntryMatcher.group(2)),
                    value);
        }
        var remarksMatcher = REMARKS_PATH.matcher(path);
        if (remarksMatcher.matches()) {
            payload.put(remarksMatcher.group(1), value);
            return true;
        }
        return false;
    }

    private boolean applyDailyLogEntryDocumentNarrative(
            Map<String, Object> payload,
            int groupIndex,
            int entryIndex,
            String value
    ) {
        var dailyItems = mutableMap(payload.get("dailyItems"));
        if (dailyItems.isEmpty()) {
            return false;
        }
        payload.put("dailyItems", dailyItems);
        var groups = mutableList(dailyItems.get("groups"));
        dailyItems.put("groups", groups);
        if (groupIndex < 0 || groupIndex >= groups.size()) {
            return false;
        }
        var group = mutableMap(groups.get(groupIndex));
        groups.set(groupIndex, group);
        var entries = mutableList(group.get("entries"));
        group.put("entries", entries);
        if (entryIndex < 0 || entryIndex >= entries.size()) {
            return false;
        }
        var entry = mutableMap(entries.get(entryIndex));
        entry.put("documentNarrativeText", value);
        entries.set(entryIndex, entry);
        return true;
    }

    private Map<String, Object> mutableMap(Map<String, Object> source) {
        var result = new LinkedHashMap<String, Object>();
        if (source == null) {
            return result;
        }
        source.forEach((key, value) -> result.put(key, mutableValue(value)));
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mutableMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            var result = new LinkedHashMap<String, Object>();
            map.forEach((key, item) -> result.put(String.valueOf(key), mutableValue(item)));
            return result;
        }
        return new LinkedHashMap<>();
    }

    private Object mutableValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            var result = new LinkedHashMap<String, Object>();
            map.forEach((key, item) -> result.put(String.valueOf(key), mutableValue(item)));
            return result;
        }
        if (value instanceof List<?> list) {
            return new ArrayList<>(list.stream().map(this::mutableValue).toList());
        }
        return value;
    }

    private List<Object> mutableList(Object value) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list.stream().map(this::mutableValue).toList());
        }
        return new ArrayList<>();
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
