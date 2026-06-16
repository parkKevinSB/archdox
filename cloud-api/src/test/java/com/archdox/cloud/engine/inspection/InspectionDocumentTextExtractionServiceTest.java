package com.archdox.cloud.engine.inspection;

import static org.assertj.core.api.Assertions.assertThat;

import com.archdox.cloud.supervisioncatalog.application.SupervisionDomainCatalogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class InspectionDocumentTextExtractionServiceTest {
    private final InspectionDocumentTextExtractionService service = new InspectionDocumentTextExtractionService(
            new SupervisionDomainCatalogService(new ObjectMapper()),
            new ObjectMapper());

    @Test
    void extractsDailyLogCatalogSelectionsForTargetDate() {
        var result = service.extract("""
                ■ 건축공사 감리세부기준〔별지 제2호서식〕
                공 사 감 리 일 지
                공사명
                초읍동 커뮤니티케어 안심주택 신축공사 2021년 1월 7일(목요일) 날씨: 맑음
                공종 및 세부공정
                감리 항목 감리내용
                ( 기초 층 )
                가설공사 부지 상황 확인 대지의 고저차 설계도서 확인
                줄쳐보기 대지경계 확인
                벤치마크(BM) 기준점의 확인
                BM위치에 대한 변화 확인
                규준틀 먹매김 확인
                토공사 터파기 터파기 깊이 확인
                바닥면의 토질상태 확인
                지정 및 기초공사 자갈 쇄석 지정 바닥면의 레벨 확인
                지정공사의 확인
                특기사항
                지적사항 및 처리결과
                """,
                "2021-01-07",
                "CONSTRUCTION_DAILY_SUPERVISION_LOG");

        assertThat(result.metadata())
                .containsEntry("targetDate", "2021-01-07")
                .containsEntry("targetDateMatched", true);
        assertThat(result.metadata().get("availableDates"))
                .asList()
                .contains("2021-01-07");
        assertThat(result.catalogSelections())
                .extracting(selection -> selection.get("inspectionItemCode"))
                .containsExactlyInAnyOrder(
                        "TEMP_SITE_CONDITION",
                        "TEMP_SETTING_OUT",
                        "TEMP_BENCHMARK",
                        "TEMP_BATTER_BOARD",
                        "EARTH_EXCAVATION_DEPTH",
                        "EARTH_SOIL_CONDITION",
                        "FOUNDATION_LEVEL",
                        "FOUNDATION_GRAVEL");
        assertThat(result.facts())
                .extracting(fact -> fact.resolvedFieldName())
                .contains("catalogSelections", "inspectionDate", "projectName", "workArea", "supervisionContent");
    }

    @Test
    void reportsAvailableDatesWhenTargetDateIsMissing() {
        var result = service.extract("""
                ■ 건축공사 감리세부기준〔별지 제2호서식〕
                공 사 감 리 일 지
                공사명
                초읍동 커뮤니티케어 안심주택 신축공사 2021년 1월 26일(화요일) 날씨: 맑음
                공종 및 세부공정
                감리 항목 감리내용
                지정 및 기초공사 자갈 쇄석 지정 바닥면의 레벨 확인
                ■ 건축공사 감리세부기준〔별지 제2호서식〕
                공 사 감 리 일 지
                공사명
                초읍동 커뮤니티케어 안심주택 신축공사 2021년 1월 29일(금요일) 날씨: 맑음
                공종 및 세부공정
                감리 항목 감리내용
                철근 콘크리트 공사 철근 조립 배근 철근배근의 확인
                """,
                "2021-01-28",
                "CONSTRUCTION_DAILY_SUPERVISION_LOG");

        assertThat(result.metadata())
                .containsEntry("targetDate", "2021-01-28")
                .containsEntry("targetDateMatched", false);
        assertThat(result.metadata().get("availableDates"))
                .asList()
                .containsExactly("2021-01-26", "2021-01-29");
    }

    @Test
    void extractsNumericDatesFromPdfTextOrder() {
        var result = service.extract("""
                공사명 초읍동 커뮤니티케어 안심주택 신축공사 년 월 일 화요일2021 1 26 ( )날씨 맑음:
                지정 및 기초공사 자갈 쇄석 지정 바닥면의 레벨 확인
                공사명 초읍동 커뮤니티케어 안심주택 신축공사 년 월 일 금요일2021 1 29 ( )날씨 맑음:
                철근 콘크리트 공사 철근 조립 배근 철근배근의 확인
                """,
                "2021-01-29",
                "CONSTRUCTION_DAILY_SUPERVISION_LOG");

        assertThat(result.metadata())
                .containsEntry("targetDate", "2021-01-29")
                .containsEntry("targetDateMatched", true);
        assertThat(result.metadata().get("availableDates"))
                .asList()
                .contains("2021-01-26", "2021-01-29");
    }
}
