package com.archdox.cloud.legal.application;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class FakeLegalSourceClient implements LegalSourceClient {
    public static final String DEFAULT_SOURCE_CODE = "NATIONAL_LAW_FAKE";

    @Override
    public boolean supports(String sourceCode) {
        return DEFAULT_SOURCE_CODE.equalsIgnoreCase(sourceCode == null ? "" : sourceCode.trim());
    }

    @Override
    public LegalSourceSnapshot fetch(String sourceCode) {
        if (!supports(sourceCode)) {
            throw new IllegalArgumentException("Unsupported legal source code: " + sourceCode);
        }
        return new LegalSourceSnapshot(
                DEFAULT_SOURCE_CODE,
                "FAKE",
                "Development Fake National Law Source",
                "https://open.law.go.kr",
                Map.of("purpose", "legal sync flow smoke test"),
                List.of(buildingAct()));
    }

    private LegalActSnapshot buildingAct() {
        return new LegalActSnapshot(
                "BUILDING_ACT_FAKE",
                "Building Act Fake Seed",
                "LAW",
                "KR",
                "FAKE-BUILDING-ACT",
                "2026-06-04-fake-v1",
                LocalDate.of(2026, 6, 4),
                LocalDate.of(2026, 6, 4),
                "https://open.law.go.kr/LSO/openApi/guideList.do",
                Map.of("officialConnectorAttached", false),
                List.of(
                        new LegalArticleSnapshot(
                                "ARTICLE_001",
                                "1",
                                "Purpose",
                                null,
                                10,
                                "This fake article defines the purpose of construction supervision compliance review.",
                                Map.of("fake", true)),
                        new LegalArticleSnapshot(
                                "ARTICLE_002",
                                "2",
                                "Supervision Evidence",
                                null,
                                20,
                                "This fake article requires supervision records to include date, responsible person, work area, and evidence when relevant.",
                                Map.of("fake", true))));
    }
}
