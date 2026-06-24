package com.archdox.cloud.inspection.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.archdox.cloud.inspection.infra.InspectionReportRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class InspectionReportNumberGeneratorTest {
    private final InspectionReportRepository reportRepository = org.mockito.Mockito.mock(InspectionReportRepository.class);
    private final InspectionReportNumberGenerator generator = new InspectionReportNumberGenerator(reportRepository);

    @Test
    void createsSimpleDailySupervisionReportNoWithoutTimezoneOrRandomSuffix() {
        when(reportRepository.findReportNosByPrefix(
                eq(1L),
                eq(10L),
                eq("CSDL-20260624-")))
                .thenReturn(List.of());

        var reportNo = generator.nextReportNo(
                1L,
                10L,
                "CONSTRUCTION_DAILY_SUPERVISION_LOG",
                OffsetDateTime.parse("2026-06-24T09:30:00+09:00"));

        assertThat(reportNo).isEqualTo("CSDL-20260624-001");
        assertThat(reportNo).doesNotContain("+");
    }

    @Test
    void incrementsSequenceWithinProjectAndDate() {
        when(reportRepository.findReportNosByPrefix(
                eq(1L),
                eq(10L),
                eq("CSDL-20260624-")))
                .thenReturn(List.of(
                        "CSDL-20260624-002",
                        "CSDL-20260624-001",
                        "CSDL-20260624-BAD"));

        var reportNo = generator.nextReportNo(
                1L,
                10L,
                "CONSTRUCTION_DAILY_SUPERVISION_LOG",
                OffsetDateTime.parse("2026-06-24T18:00:00+09:00"));

        assertThat(reportNo).isEqualTo("CSDL-20260624-003");
    }

    @Test
    void usesChecklistPrefixForChecklistReports() {
        when(reportRepository.findReportNosByPrefix(
                eq(1L),
                eq(10L),
                eq("CSCL-20260624-")))
                .thenReturn(List.of("CSCL-20260624-009"));

        var reportNo = generator.nextReportNo(
                1L,
                10L,
                "CONSTRUCTION_SUPERVISION_CHECKLIST",
                OffsetDateTime.parse("2026-06-24T18:00:00+09:00"));

        assertThat(reportNo).isEqualTo("CSCL-20260624-010");
    }
}
