package com.archdox.cloud.workerchat.dto;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Map;

public record SendArchDoxWorkerChatMessageRequest(
        @Size(max = 2000)
        String content,
        Long siteId,
        Long reportId,
        CreateSiteAction createSite,
        CreateReportAction createReport,
        UpdateReportStepAction updateReportStep,
        SubmitReportAction submitReport,
        RunPreflightReviewAction runPreflightReview,
        RequestDocumentGenerationAction requestDocumentGeneration
) {
    public record CreateSiteAction(
            @Size(max = 80)
            String siteCode,
            @Size(max = 200)
            String name,
            @Size(max = 500)
            String address,
            @Size(max = 100)
            String siteType,
            LocalDate startDate,
            LocalDate endDate
    ) {
    }

    public record CreateReportAction(
            Long siteId,
            @Size(max = 100)
            String reportType,
            @Size(max = 200)
            String title,
            Long templateId
    ) {
    }

    public record UpdateReportStepAction(
            Long reportId,
            @Size(max = 100)
            String stepCode,
            Map<String, Object> payload
    ) {
    }

    public record SubmitReportAction(
            Long reportId
    ) {
    }

    public record RunPreflightReviewAction(
            Long reportId
    ) {
    }

    public record RequestDocumentGenerationAction(
            Long reportId,
            @Size(max = 40)
            String outputFormat,
            @Size(max = 80)
            String workerType
    ) {
    }
}
