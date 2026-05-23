package com.archdox.cloud.inspection.application;

import com.archdox.cloud.checklist.infra.InspectionChecklistAnswerRepository;
import com.archdox.cloud.inspection.domain.InspectionReport;
import com.archdox.cloud.inspection.dto.ReportSubmitValidationIssueResponse;
import com.archdox.cloud.inspection.dto.ReportSubmitValidationResponse;
import com.archdox.cloud.inspection.infra.InspectionReportStepRepository;
import com.archdox.cloud.photo.domain.PhotoAssetStatus;
import com.archdox.cloud.photo.domain.PhotoAssetType;
import com.archdox.cloud.photo.infra.PhotoAssetRepository;
import java.util.ArrayList;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportSubmitValidationService {
    private static final String BASIC_INFO_STEP = "BASIC_INFO";
    private static final String CHECKLIST_STEP = "CHECKLIST";

    private final InspectionReportStepRepository stepRepository;
    private final InspectionChecklistAnswerRepository checklistAnswerRepository;
    private final PhotoAssetRepository photoAssetRepository;

    public ReportSubmitValidationService(
            InspectionReportStepRepository stepRepository,
            InspectionChecklistAnswerRepository checklistAnswerRepository,
            PhotoAssetRepository photoAssetRepository
    ) {
        this.stepRepository = stepRepository;
        this.checklistAnswerRepository = checklistAnswerRepository;
        this.photoAssetRepository = photoAssetRepository;
    }

    @Transactional(readOnly = true)
    public ReportSubmitValidationResponse validate(InspectionReport report) {
        var blockingIssues = new ArrayList<ReportSubmitValidationIssueResponse>();
        var warnings = new ArrayList<ReportSubmitValidationIssueResponse>();

        requireSavedStep(report, BASIC_INFO_STEP, "Basic information step must be saved before submit.", blockingIssues);
        requireChecklistSaved(report, blockingIssues);
        requireWorkingPhoto(report, blockingIssues);

        if (blockingIssues.isEmpty()) {
            return ReportSubmitValidationResponse.valid(warnings);
        }
        return ReportSubmitValidationResponse.invalid(blockingIssues, warnings);
    }

    private void requireChecklistSaved(
            InspectionReport report,
            ArrayList<ReportSubmitValidationIssueResponse> blockingIssues
    ) {
        var hasStep = stepRepository.existsByReportIdAndStepCode(report.id(), CHECKLIST_STEP);
        var hasAnswer = checklistAnswerRepository.existsByOfficeIdAndReportId(report.officeId(), report.id());
        if (!hasStep && !hasAnswer) {
            blockingIssues.add(new ReportSubmitValidationIssueResponse(
                    "MISSING_STEP_CHECKLIST",
                    "Checklist step must be saved before submit.",
                    "INSPECTION_REPORT_STEP",
                    CHECKLIST_STEP));
        }
    }

    @Transactional(readOnly = true)
    public void requireValid(InspectionReport report) {
        var validation = validate(report);
        if (!validation.valid()) {
            throw new ReportSubmitValidationException(validation);
        }
    }

    private void requireSavedStep(
            InspectionReport report,
            String stepCode,
            String message,
            ArrayList<ReportSubmitValidationIssueResponse> blockingIssues
    ) {
        if (!stepRepository.existsByReportIdAndStepCode(report.id(), stepCode)) {
            blockingIssues.add(new ReportSubmitValidationIssueResponse(
                    "MISSING_STEP_" + stepCode,
                    message,
                    "INSPECTION_REPORT_STEP",
                    stepCode));
        }
    }

    private void requireWorkingPhoto(
            InspectionReport report,
            ArrayList<ReportSubmitValidationIssueResponse> blockingIssues
    ) {
        var hasWorkingPhoto = photoAssetRepository.existsUploadedAsset(
                report.id(),
                report.officeId(),
                PhotoAssetType.WORKING,
                PhotoAssetStatus.UPLOADED);
        if (!hasWorkingPhoto) {
            blockingIssues.add(new ReportSubmitValidationIssueResponse(
                    "MISSING_WORKING_PHOTO",
                    "At least one uploaded working photo is required before submit.",
                    "PHOTO",
                    "WORKING"));
        }
    }
}
