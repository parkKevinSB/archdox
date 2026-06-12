package com.archdox.cloud.inspection.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InspectionReportWorkflowTest {
    @Test
    void draftCanSaveStepThenSubmit() {
        var now = OffsetDateTime.now();
        var report = new InspectionReport(10L, 100L, null, "RPT-1", "DAILY", "Daily", null, 1L, now);

        assertTrue(report.canSaveStep());
        assertTrue(report.canSubmit());

        report.markStepSaved("BASIC_INFO", now.plusMinutes(1));

        assertEquals(InspectionReportStatus.STEP_SAVED, report.status());
        assertTrue(report.canSaveStep());
        assertTrue(report.canSubmit());

        report.submit(now.plusMinutes(2));

        assertEquals(InspectionReportStatus.READY_TO_GENERATE, report.status());
        assertFalse(report.canSaveStep());
        assertFalse(report.canSubmit());
    }

    @Test
    void submittedPreflightFixKeepsReadyRevision() {
        var now = OffsetDateTime.now();
        var report = new InspectionReport(10L, 100L, null, "RPT-1", "DAILY", "Daily", null, 1L, now);

        report.markStepSaved("DAILY_LOG", now.plusMinutes(1));
        report.submit(now.plusMinutes(2));
        report.markSubmittedPreflightFixApplied("REMARKS", now.plusMinutes(3));

        assertEquals(InspectionReportStatus.READY_TO_GENERATE, report.status());
        assertEquals(1, report.contentRevision());
        assertEquals(1, report.submittedRevision());
        assertEquals(1, report.generationRevision());
        assertTrue(report.canRequestGeneration());
    }

    @Test
    void approvedTextCorrectionAfterGenerationReturnsToReadyWithoutReopenRevision() {
        var now = OffsetDateTime.now();
        var report = new InspectionReport(10L, 100L, null, "RPT-1", "DAILY", "Daily", null, 1L, now);

        report.markStepSaved("DAILY_LOG", now.plusMinutes(1));
        report.submit(now.plusMinutes(2));
        report.requestGeneration(500L, report.generationRevision(), now.plusMinutes(3));
        report.markGenerated(1, now.plusMinutes(4));
        report.markSubmittedPreflightFixApplied("REMARKS", now.plusMinutes(5));

        assertEquals(InspectionReportStatus.READY_TO_GENERATE, report.status());
        assertEquals(1, report.contentRevision());
        assertEquals(1, report.submittedRevision());
        assertEquals(1, report.generatedRevision());
        assertEquals(1, report.generationRevision());
        assertTrue(report.canRequestGeneration());
    }

    @Test
    void cancelledReportCannotBeEditedOrSubmittedAgain() {
        var now = OffsetDateTime.now();
        var report = new InspectionReport(10L, 100L, null, "RPT-1", "DAILY", "Daily", null, 1L, now);

        report.cancel(now.plusMinutes(1));

        assertEquals(InspectionReportStatus.CANCELLED, report.status());
        assertFalse(report.canSaveStep());
        assertFalse(report.canSubmit());
        assertFalse(report.canCancel());
    }

    @Test
    void newStepStartsAtRevisionOne() {
        var now = OffsetDateTime.now();
        var report = new InspectionReport(10L, 100L, null, "RPT-1", "DAILY", "Daily", null, 1L, now);
        var step = new InspectionReportStep(
                report,
                "BASIC_INFO",
                PayloadStorageMode.CLOUD_ENCRYPTED,
                Map.of("weather", "SUNNY"),
                1L,
                now);

        assertEquals(1, step.clientRevision());

        step.update(Map.of("weather", "RAIN"), 1L, now.plusMinutes(1));

        assertEquals(2, step.clientRevision());
    }
}
