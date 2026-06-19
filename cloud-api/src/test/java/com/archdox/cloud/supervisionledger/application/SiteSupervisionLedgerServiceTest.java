package com.archdox.cloud.supervisionledger.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.archdox.cloud.inspection.domain.InspectionReport;
import com.archdox.cloud.inspection.domain.InspectionReportStep;
import com.archdox.cloud.inspection.domain.PayloadStorageMode;
import com.archdox.cloud.inspection.infra.InspectionReportStepRepository;
import com.archdox.cloud.office.application.OfficePermissionService;
import com.archdox.cloud.site.application.SiteService;
import com.archdox.cloud.supervisioncatalog.application.SupervisionDomainCatalogService;
import com.archdox.cloud.supervisionledger.domain.SiteSupervisionEntry;
import com.archdox.cloud.supervisionledger.infra.SiteSupervisionEntryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class SiteSupervisionLedgerServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SiteSupervisionEntryRepository entryRepository = mock(SiteSupervisionEntryRepository.class);
    private final InspectionReportStepRepository stepRepository = mock(InspectionReportStepRepository.class);
    private final SiteService siteService = mock(SiteService.class);
    private final OfficePermissionService permissionService = mock(OfficePermissionService.class);
    private final SupervisionDomainCatalogService catalogService = new SupervisionDomainCatalogService(objectMapper);
    private final SiteSupervisionLedgerService service = new SiteSupervisionLedgerService(
            entryRepository,
            stepRepository,
            siteService,
            permissionService,
            catalogService,
            objectMapper);

    @Test
    void syncReportDailyLogSavesPhaseChecklistEntriesWithoutTradeCode() {
        var now = OffsetDateTime.parse("2026-06-19T10:00:00+09:00");
        var report = new InspectionReport(
                1L,
                2L,
                3L,
                "R-001",
                "CONSTRUCTION_DAILY_SUPERVISION_LOG",
                "공사감리일지",
                4L,
                5L,
                now);
        ReflectionTestUtils.setField(report, "id", 100L);
        var phaseChecklistRow = Map.of(
                "code", "PHASE_NON_RESIDENT_PRE_CONSTRUCTION_BASIC_IT_3C8CC2930A",
                "label", "당해 공사 관련 설계도서 인수 확인서 작성",
                "result", "COMPLIANT",
                "photoIds", List.of());
        var phaseEntry = Map.of(
                "id", "entry-phase-1",
                "inspectionItemCode", "PHASE_NON_RESIDENT_PRE_CONSTRUCTION_BASIC_IT_3C8CC2930A",
                "inspectionItemName", "당해 공사 관련 설계도서 인수 확인서 작성",
                "checklistRows", List.of(phaseChecklistRow));
        var phaseGroup = Map.of(
                "id", "group-phase-1",
                "groupType", "PHASE",
                "floor", "-",
                "phaseCode", "PRE_CONSTRUCTION",
                "phaseName", "공사전 단계",
                "processCode", "PHASE_NON_RESIDENT_PRE_CONSTRUCTION_BASIC_PG_FC0767BB28",
                "processName", "감리업무 착수준비",
                "entries", List.of(phaseEntry));
        when(stepRepository.findByReportIdAndStepCode(100L, "BASIC_INFO")).thenReturn(Optional.of(step(report, "BASIC_INFO", Map.of(
                "inspectionDate", "2026-06-19"
        ))));
        when(stepRepository.findByReportIdAndStepCode(100L, "DAILY_LOG")).thenReturn(Optional.of(step(report, "DAILY_LOG", Map.of(
                "dailyItems", Map.of("groups", List.of(phaseGroup))
        ))));

        service.syncReportDailyLog(report, 5L, now);

        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<Iterable<SiteSupervisionEntry>> captor = ArgumentCaptor.forClass((Class) Iterable.class);
        verify(entryRepository).saveAll(captor.capture());
        var saved = new ArrayList<SiteSupervisionEntry>();
        captor.getValue().forEach(saved::add);
        assertThat(saved).hasSize(1);
        var entry = saved.getFirst();
        assertThat(entry.groupType()).isEqualTo("PHASE");
        assertThat(entry.tradeCode()).isNull();
        assertThat(entry.phaseChecklistGroupCode()).isEqualTo("PHASE_SUPERVISION");
        assertThat(entry.phaseCode()).isEqualTo("PRE_CONSTRUCTION");
        assertThat(entry.processCode()).isEqualTo("PHASE_NON_RESIDENT_PRE_CONSTRUCTION_BASIC_PG_FC0767BB28");
        assertThat(entry.inspectionItemCode()).isEqualTo("PHASE_NON_RESIDENT_PRE_CONSTRUCTION_BASIC_IT_3C8CC2930A");
    }

    private InspectionReportStep step(InspectionReport report, String stepCode, Map<String, Object> payload) {
        return new InspectionReportStep(
                report,
                stepCode,
                PayloadStorageMode.CLOUD_ENCRYPTED,
                payload,
                5L,
                OffsetDateTime.parse("2026-06-19T10:00:00+09:00"));
    }
}
