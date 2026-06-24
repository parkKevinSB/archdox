package com.archdox.documentai;

import io.github.parkkevinsb.flower.ai.harness.model.AiModelResponse;
import io.github.parkkevinsb.flower.ai.harness.validate.AiSchemaValidator;
import io.github.parkkevinsb.flower.ai.harness.validate.ValidationError;
import io.github.parkkevinsb.flower.ai.harness.validate.ValidationResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

final class ReportPreflightOutputValidator implements AiSchemaValidator<ReportPreflightResult> {
    private static final Pattern DAILY_ROW_TEXT_FIELD = Pattern.compile(
            ".*DAILY_LOG\\.groups\\[\\d+]\\.entries\\[\\d+]\\.checklistRows\\[\\d+]\\.(referenceNote|actionNote)$");

    private final AiSchemaValidator<ReportPreflightResult> delegate;

    ReportPreflightOutputValidator(AiSchemaValidator<ReportPreflightResult> delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    }

    @Override
    public ValidationResult<ReportPreflightResult> validate(AiModelResponse response) {
        var parsed = delegate.validate(response);
        if (parsed instanceof ValidationResult.Invalid<ReportPreflightResult>) {
            return parsed;
        }
        var value = value(parsed);
        var errors = validateOutput(value);
        if (!errors.isEmpty()) {
            return new ValidationResult.Invalid<>(errors);
        }
        return parsed;
    }

    private static ReportPreflightResult value(ValidationResult<ReportPreflightResult> result) {
        if (result instanceof ValidationResult.Valid<?> valid) {
            return (ReportPreflightResult) valid.value();
        }
        throw new IllegalStateException("Expected valid ReportPreflightResult");
    }

    private static List<ValidationError> validateOutput(ReportPreflightResult result) {
        var errors = new ArrayList<ValidationError>();
        for (int index = 0; index < result.issues().size(); index++) {
            var issue = result.issues().get(index);
            var issuePath = "issues[" + index + "]";
            if (isAggregateRemarksPayload(issue.location())) {
                errors.add(new ValidationError(
                        issuePath + ".location",
                        "AGGREGATE_REMARKS_LOCATION",
                        "REMARKS.payload is an aggregate object. Use REMARKS.payload.specialNotes, "
                                + "REMARKS.payload.issueAndAction, or REMARKS.payload.nextAction."));
            }
            if (!issue.replacement().isBlank() && containsInstructionWording(issue.replacement())) {
                errors.add(new ValidationError(
                        issuePath + ".replacement",
                        "INSTRUCTION_REPLACEMENT",
                        "replacement must be final report prose, not an instruction."));
            }
            if (!issue.replacement().isBlank()
                    && !isDirectEditableTextField(issue.location())) {
                errors.add(new ValidationError(
                        issuePath + ".location",
                        "NON_EDITABLE_REPLACEMENT_TARGET",
                        "WORDING replacement requires a direct editable field path."));
            }
        }
        return List.copyOf(errors);
    }

    private static boolean isAggregateRemarksPayload(String value) {
        var path = normalizePath(value);
        return "REMARKS.payload".equals(path) || "steps.REMARKS.payload".equals(path);
    }

    private static boolean isDirectEditableTextField(String value) {
        var path = normalizePath(value);
        return path.endsWith("REMARKS.payload.specialNotes")
                || path.endsWith("REMARKS.payload.issueAndAction")
                || path.endsWith("REMARKS.payload.nextAction")
                || DAILY_ROW_TEXT_FIELD.matcher(path).matches();
    }

    private static boolean containsInstructionWording(String value) {
        var text = value == null ? "" : value.trim();
        if (text.isBlank()) {
            return false;
        }
        return List.of(
                "하십시오",
                "하세요",
                "바랍니다",
                "수정하십시오",
                "수정 바랍니다",
                "수정하고",
                "다듬으십시오",
                "기재하십시오",
                "기재하여",
                "권고합니다",
                "첨부합니다",
                "확인 후",
                "명확히 기재",
                "문장을 완성",
                "문장으로 수정",
                "최종 문장으로").stream().anyMatch(text::contains);
    }

    private static String normalizePath(String value) {
        return value == null ? "" : value.trim();
    }
}
