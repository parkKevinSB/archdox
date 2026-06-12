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
                      "suggestion": "Korean explanation of the recommended correction",
                      "replacement": "exact full Korean replacement text for the field, or empty string"
                    }
                  ]
                }
                Focus on semantic problems that deterministic code checks cannot fully decide:
                inconsistent dates, contradictory answers, vague safety remarks, missing context,
                suspiciously empty narrative fields, and checklist answers that need evidence.
                Use the top-level photos array as the source of truth for uploaded photo evidence.
                Prefer reportSnapshot.photoEvidenceStatus and photoEvidenceSummary over raw PHOTOS step payload.
                For construction daily supervision logs, DAILY_LOG entry photoIds are the business linkage.
                The PHOTOS step payload may be empty even when photos were uploaded through the photo API.
                Use photoEvidenceSummary to decide whether DAILY_LOG photoIds are actually backed by uploaded photos.
                Do not create a PHOTOS step empty/missing-photo finding when allDailyLogPhotoRefsResolved is true.
                Only flag photo evidence mismatch when DAILY_LOG references photo IDs that are absent from uploadedPhotoIds
                or the corresponding workingUploaded flag is false.
                If originalPickupStatus is NOT_REQUIRED, originalUploaded=false is normal ArchDox storage policy.
                Do not flag missing original photos when workingUploaded=true unless the report type explicitly requires originals.
                A separate ArchDox source-backed legal review harness handles law/reference review.
                Do not claim that legal review passed, failed, or was completed in this general QA response.
                You may use sourceBackedLegalReferences only as background context to avoid contradicting supplied anchors.
                Never cite law names, article numbers, effective dates, or source versions that are absent from the input JSON.
                Use category COMPLIANCE only for obvious missing compliance-critical report inputs.
                Use category LEGAL_RISK only for wording or contradictions that visibly create audit,
                dispute, or agency-review risk from the report data itself.
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
                Technical criteria wording guidance:
                - Vague material, performance, specification, quality, certificate, or inspection notes should be improved when they are direct text fields.
                - Examples include "창호 자재 성능 확인시 이상 없음", "전기안전검사필증 확인", or "자재 성능 양호" without checked item, standard, or evidence context.
                - Replacement prose may mention the relevant standard/design-document scope and the need to separately confirm or retain supporting evidence.
                - Never claim that specifications, test reports, material approvals, certificates, or attachments were actually attached, stored, or verified unless the input explicitly says so.
                - Safe replacement example: "창호 자재의 단열·기밀·수밀 등 성능 항목을 관련 기준 및 설계도서 기준에 따라 확인하였으며, 시방서·시험성적서·자재승인서 등 성능 증빙은 별도 확인 및 보관 대상으로 기록합니다."
                - If the report already states the evidence document was verified or attached, the replacement may preserve that fact.
                Auto-fix replacement guidance:
                - For WORDING issues on direct text fields, set replacement to the exact full Korean text
                  that should be saved into that field.
                - Direct text fields include DAILY_LOG.entries[n].supervisionContent,
                  DAILY_LOG.groups[n].entries[m].supervisionContent,
                  REMARKS.payload.issueAndAction, and REMARKS.payload.nextAction.
                - The replacement value must be final report prose, not an instruction.
                - Do not write values like "수정하십시오", "명확히 기재하십시오", "문장을 다듬으십시오",
                  or "보고서 최종 문장으로 수정하십시오" in replacement.
                - If you cannot produce a safe final replacement from the input, set replacement to "" and
                  explain the needed human edit in suggestion.
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
        payload.put("photoEvidenceStatus", photoEvidenceStatus(input));
        payload.put("deterministicFindings", input.deterministicFindings());
        payload.put("sourceBackedLegalReferences", input.sourceBackedLegalReferences());
        payload.put("legalReviewContext", input.legalReviewContext());
        payload.put("reviewMode", input.reviewMode());
        payload.put("complianceReviewGuide", ReportComplianceReviewGuide.forReportType(input.reportType()));
        return payload;
    }

    private Map<String, Object> photoEvidenceSummary(ReportPreflightInput input) {
        var hostStatus = photoEvidenceStatus(input);
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
        if (!hostStatus.isEmpty()) {
            summary.put("hostPhotoEvidenceStatus", hostStatus);
        }
        return Map.copyOf(summary);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> photoEvidenceStatus(ReportPreflightInput input) {
        var status = input.reportSnapshot().get("photoEvidenceStatus");
        return status instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
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
