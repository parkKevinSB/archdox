package com.archdox.documentai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.parkkevinsb.flower.ai.harness.prompt.PromptBuilder;
import io.github.parkkevinsb.flower.ai.harness.prompt.RenderedPrompt;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunContext;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ReportPreflightPromptBuilder implements PromptBuilder<ReportPreflightInput> {
    private final ObjectMapper objectMapper;

    public ReportPreflightPromptBuilder(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public RenderedPrompt build(ReportPreflightInput input, AiHarnessRunContext ctx) {
        Objects.requireNonNull(input, "input must not be null");
        var system = """
                You are ArchDox report preflight QA.
                Review inspection or construction supervision report input before document generation.
                Return JSON only. Do not include markdown.
                Write summary, message, evidence, and suggestion in Korean for Korean office users.
                Keep code, category, severity, status, confidence, and location as stable English enum-like values.
                The JSON must match:
                {
                  "status": "PASS|WARN|FAIL",
                  "summary": "short Korean review summary",
                  "confidence": "LOW|MEDIUM|HIGH",
                  "issues": [
                    {
                      "code": "stable uppercase code",
                      "category": "GENERAL|COMPLETENESS|CONSISTENCY|EVIDENCE|COMPLIANCE|LEGAL_RISK|WORDING",
                      "severity": "INFO|LOW|MEDIUM|HIGH|CRITICAL",
                      "location": "field, step, checklist item, or section",
                      "message": "human readable Korean issue",
                      "evidence": "Korean explanation of input evidence",
                      "suggestion": "Korean recommended correction"
                    }
                  ]
                }
                Focus on semantic problems that deterministic code checks cannot fully decide:
                inconsistent dates, contradictory answers, vague safety remarks, missing context,
                suspiciously empty narrative fields, and checklist answers that need evidence.
                Use the top-level photos array as the source of truth for uploaded photo evidence.
                The PHOTOS step payload may be empty even when photos were uploaded through the photo API.
                Use photoEvidenceSummary to decide whether DAILY_LOG photoIds are actually backed by uploaded photos.
                Do not create a PHOTOS step empty/missing-photo finding when allDailyLogPhotoRefsResolved is true.
                Only flag photo evidence mismatch when DAILY_LOG references photo IDs that are absent from uploadedPhotoIds
                or the corresponding workingUploaded flag is false.
                If originalPickupStatus is NOT_REQUIRED, originalUploaded=false is normal ArchDox storage policy.
                Do not flag missing original photos when workingUploaded=true unless the report type explicitly requires originals.
                Also perform a lightweight legal/compliance risk review for the report type.
                This is not legal advice and must not invent laws, article numbers, or facts.
                When sourceBackedLegalReferences or legalReviewContext are present, use only those
                supplied legal/source anchors for legal-risk review.
                Never cite law names, article numbers, effective dates, or source versions that are
                absent from the input JSON.
                If reviewMode is SOURCE_BACKED_LEGAL_DRY_RUN, treat legal-risk issues as review
                draft findings only. Do not claim final noncompliance, do not request direct document
                generation, and recommend human review or field-context correction.
                If legal references are absent or fragmented, say that source-backed review is
                insufficient instead of inventing a legal basis.
                Use category COMPLIANCE for missing compliance-critical inputs.
                Use category LEGAL_RISK for wording or contradictions that could create audit,
                dispute, or agency-review risk.
                Do not repeat deterministic findings unless you can add a concrete extra reason.
                Use FAIL only for issues that should block document generation.
                Use WARN for issues that should be reviewed by a person before generation.
                Severity guidance:
                - savedAt is the data-entry/save timestamp, not necessarily the real inspection time.
                - Do not mark inspectionDate vs savedAt differences as HIGH or CRITICAL unless the input clearly proves an impossible or legally blocking chronology.
                - If inspectionDate is after savedAt, normally use LOW or MEDIUM and ask the user to verify the date.
                - Use HIGH only when the contradiction directly affects required report facts, approval, or document validity.
                - Use LOW for wording cleanup, typos, or unclear follow-up text that does not block generation.
                - Use MEDIUM for missing evidence, vague safety/compliance remarks, or contradictions that need human review.
                Korean wording guidance:
                - Be concise and professional.
                - Avoid overly legalistic language unless the input includes a concrete compliance risk.
                - Do not translate stable code/category/severity enum values.
                """;
        var user = """
                Review this ArchDox report input before document generation.

                Input JSON:
                %s
                """.formatted(toJson(inputPayload(input)));
        return new RenderedPrompt(List.of(
                new RenderedPrompt.Message(RenderedPrompt.Role.SYSTEM, system),
                new RenderedPrompt.Message(RenderedPrompt.Role.USER, user)), ctx.promptVersion());
    }

    private Map<String, Object> inputPayload(ReportPreflightInput input) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("officeId", input.officeId());
        payload.put("reportId", input.reportId());
        payload.put("reportType", input.reportType());
        payload.put("title", input.title());
        payload.put("status", input.status());
        payload.put("contentRevision", input.contentRevision());
        payload.put("reportSnapshot", input.reportSnapshot());
        payload.put("steps", input.steps());
        payload.put("photos", input.photos());
        payload.put("photoEvidenceSummary", photoEvidenceSummary(input));
        payload.put("deterministicFindings", input.deterministicFindings());
        payload.put("sourceBackedLegalReferences", input.sourceBackedLegalReferences());
        payload.put("legalReviewContext", input.legalReviewContext());
        payload.put("reviewMode", input.reviewMode());
        payload.put("complianceReviewGuide", ReportComplianceReviewGuide.forReportType(input.reportType()));
        return payload;
    }

    private Map<String, Object> photoEvidenceSummary(ReportPreflightInput input) {
        var uploadedPhotoIds = new LinkedHashSet<String>();
        var workingUploadedPhotoIds = new LinkedHashSet<String>();
        for (var photo : input.photos()) {
            var photoId = text(photo.get("photoId"));
            if (photoId.isBlank()) {
                continue;
            }
            uploadedPhotoIds.add(photoId);
            if (Boolean.TRUE.equals(photo.get("workingUploaded"))) {
                workingUploadedPhotoIds.add(photoId);
            }
        }

        var referencedPhotoIds = new LinkedHashSet<String>();
        collectDailyLogPhotoIds(input.steps(), referencedPhotoIds);

        var missingPhotoIds = new LinkedHashSet<>(referencedPhotoIds);
        missingPhotoIds.removeAll(uploadedPhotoIds);

        var notWorkingUploadedPhotoIds = new LinkedHashSet<>(referencedPhotoIds);
        notWorkingUploadedPhotoIds.retainAll(uploadedPhotoIds);
        notWorkingUploadedPhotoIds.removeAll(workingUploadedPhotoIds);

        var summary = new LinkedHashMap<String, Object>();
        summary.put("photosStepPayloadEmpty", photosStepPayloadEmpty(input.steps()));
        summary.put("uploadedPhotoCount", uploadedPhotoIds.size());
        summary.put("dailyLogReferencedPhotoCount", referencedPhotoIds.size());
        summary.put("uploadedPhotoIds", List.copyOf(uploadedPhotoIds));
        summary.put("workingUploadedPhotoIds", List.copyOf(workingUploadedPhotoIds));
        summary.put("dailyLogReferencedPhotoIds", List.copyOf(referencedPhotoIds));
        summary.put("missingDailyLogPhotoIds", List.copyOf(missingPhotoIds));
        summary.put("notWorkingUploadedDailyLogPhotoIds", List.copyOf(notWorkingUploadedPhotoIds));
        summary.put("allDailyLogPhotoRefsResolved", missingPhotoIds.isEmpty() && notWorkingUploadedPhotoIds.isEmpty());
        summary.put("photoSourceOfTruth", "top-level photos array from ArchDox photo API");
        return Map.copyOf(summary);
    }

    private boolean photosStepPayloadEmpty(Map<String, Object> steps) {
        var photosStep = mapValue(steps.get("PHOTOS"));
        var payload = mapValue(photosStep.get("payload"));
        return payload.isEmpty();
    }

    private void collectDailyLogPhotoIds(Map<String, Object> steps, Set<String> result) {
        var dailyLog = mapValue(steps.get("DAILY_LOG"));
        var payload = mapValue(dailyLog.get("payload"));
        var dailyItems = mapValue(payload.get("dailyItems"));
        for (Object groupValue : listValue(dailyItems.get("groups"))) {
            var group = mapValue(groupValue);
            for (Object entryValue : listValue(group.get("entries"))) {
                var entry = mapValue(entryValue);
                for (Object rawPhotoId : listValue(entry.get("photoIds"))) {
                    var photoId = text(rawPhotoId);
                    if (!photoId.isBlank()) {
                        result.add(photoId);
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private List<?> listValue(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to render report preflight prompt input", ex);
        }
    }
}
