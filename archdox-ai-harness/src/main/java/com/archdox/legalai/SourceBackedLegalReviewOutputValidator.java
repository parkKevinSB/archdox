package com.archdox.legalai;

import io.github.parkkevinsb.flower.ai.harness.model.AiModelResponse;
import io.github.parkkevinsb.flower.ai.harness.validate.AiSchemaValidator;
import io.github.parkkevinsb.flower.ai.harness.validate.ValidationError;
import io.github.parkkevinsb.flower.ai.harness.validate.ValidationResult;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

final class SourceBackedLegalReviewOutputValidator implements AiSchemaValidator<SourceBackedLegalReviewResult> {
    private static final Pattern DAILY_ROW_TEXT_FIELD = Pattern.compile(
            ".*DAILY_LOG\\.groups\\[\\d+]\\.entries\\[\\d+]\\.checklistRows\\[\\d+]\\.(referenceNote|actionNote)$");

    private final AiSchemaValidator<SourceBackedLegalReviewResult> delegate;

    SourceBackedLegalReviewOutputValidator(AiSchemaValidator<SourceBackedLegalReviewResult> delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    }

    @Override
    public ValidationResult<SourceBackedLegalReviewResult> validate(AiModelResponse response) {
        var parsed = delegate.validate(response);
        if (parsed instanceof ValidationResult.Invalid<SourceBackedLegalReviewResult>) {
            return parsed;
        }
        var value = value(parsed);
        var errors = validateOutput(value);
        if (!errors.isEmpty()) {
            return new ValidationResult.Invalid<>(errors);
        }
        return parsed;
    }

    private static SourceBackedLegalReviewResult value(ValidationResult<SourceBackedLegalReviewResult> result) {
        if (result instanceof ValidationResult.Valid<?> valid) {
            return (SourceBackedLegalReviewResult) valid.value();
        }
        throw new IllegalStateException("Expected valid SourceBackedLegalReviewResult");
    }

    private static List<ValidationError> validateOutput(SourceBackedLegalReviewResult result) {
        var errors = new ArrayList<ValidationError>();
        if (result.status() == SourceBackedLegalReviewStatus.PASS && !result.issues().isEmpty()) {
            errors.add(new ValidationError(
                    "issues",
                    "PASS_WITH_ISSUES",
                    "PASS legal review must not contain issues."));
        }
        var reviewedIds = new HashSet<>(result.reviewedReferenceIds());
        for (int index = 0; index < result.issues().size(); index++) {
            var issue = result.issues().get(index);
            var issuePath = "issues[" + index + "]";
            if (isAggregateRemarksPayload(issue.relatedFieldPath())) {
                errors.add(new ValidationError(
                        issuePath + ".relatedFieldPath",
                        "AGGREGATE_REMARKS_RELATED_FIELD",
                        "REMARKS.payload is an aggregate object. Use REMARKS.payload.specialNotes, "
                                + "REMARKS.payload.issueAndAction, or REMARKS.payload.nextAction."));
            }
            if (isGeneralDocumentQaIssue(issue)) {
                errors.add(new ValidationError(
                        issuePath,
                        "GENERAL_QA_ISSUE_IN_LEGAL_REVIEW",
                        "Source-backed legal review must not emit ordinary date, typo, or wording QA issues. "
                                + "Leave those to the general report preflight QA harness."));
            }
            if (!issue.replacement().isBlank() && issue.relatedFieldPath().isBlank()) {
                errors.add(new ValidationError(
                        issuePath + ".relatedFieldPath",
                        "MISSING_REPLACEMENT_TARGET",
                        "replacement requires relatedFieldPath."));
            }
            if (!issue.replacement().isBlank() && containsInstructionWording(issue.replacement())) {
                errors.add(new ValidationError(
                        issuePath + ".replacement",
                        "INSTRUCTION_REPLACEMENT",
                        "replacement must be final report prose, not an instruction."));
            }
            if (!issue.replacement().isBlank() && !isDirectEditableTextField(issue.relatedFieldPath())) {
                errors.add(new ValidationError(
                        issuePath + ".relatedFieldPath",
                        "NON_EDITABLE_REPLACEMENT_TARGET",
                        "replacement requires a direct editable report field path."));
            }
            if (!reviewedIds.isEmpty() && !reviewedIds.containsAll(issue.legalReferenceIds())) {
                errors.add(new ValidationError(
                        issuePath + ".legalReferenceIds",
                        "UNREVIEWED_ISSUE_REFERENCE",
                        "issue legalReferenceIds must be included in reviewedReferenceIds."));
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

    private static boolean isGeneralDocumentQaIssue(SourceBackedLegalReviewIssue issue) {
        var code = normalizePath(issue.code()).toUpperCase(java.util.Locale.ROOT);
        var category = issue.category();
        var path = normalizePath(firstNonBlank(issue.relatedFieldPath(), issue.location()));
        if (code.startsWith("DATE_") || code.contains("TYPO") || code.contains("VAGUE_REMARKS")) {
            return true;
        }
        if (path.endsWith("BASIC_INFO.payload.inspectionDate")) {
            return true;
        }
        if ((path.endsWith("REMARKS.payload.specialNotes") || path.endsWith("REMARKS.payload.nextAction"))
                && category == SourceBackedLegalReviewIssueCategory.CONSISTENCY) {
            return true;
        }
        return false;
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

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second == null ? "" : second;
    }
}
