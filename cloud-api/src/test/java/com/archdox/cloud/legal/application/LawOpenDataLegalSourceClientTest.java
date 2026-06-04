package com.archdox.cloud.legal.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import org.junit.jupiter.api.Test;

class LawOpenDataLegalSourceClientTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LawOpenDataLegalSourceClient client = new LawOpenDataLegalSourceClient(
            objectMapper,
            new LegalSyncProperties(),
            HttpClient.newHttpClient());

    @Test
    void parsesLawDetailArticles() throws Exception {
        var target = new LegalSyncProperties.Target(
                "law",
                "\uAC74\uCD95\uBC95",
                "\uAC74\uCD95\uBC95",
                "BUILDING_ACT",
                "LAW");
        var detail = objectMapper.readTree("""
                {
                  "\uBC95\uB839": {
                    "\uBC95\uB839\uD0A4": "0018232025082621035",
                    "\uAE30\uBCF8\uC815\uBCF4": {
                      "\uBC95\uB839ID": "001823",
                      "\uBC95\uB839\uBA85_\uD55C\uAE00": "\uAC74\uCD95\uBC95",
                      "\uBC95\uC885\uAD6C\uBD84": { "content": "\uBC95\uB960" },
                      "\uACF5\uD3EC\uBC88\uD638": "21035",
                      "\uACF5\uD3EC\uC77C\uC790": "20250826",
                      "\uC2DC\uD589\uC77C\uC790": "20260227",
                      "\uC18C\uAD00\uBD80\uCC98": { "content": "\uAD6D\uD1A0\uAD50\uD1B5\uBD80" },
                      "\uC81C\uAC1C\uC815\uAD6C\uBD84": "\uC77C\uBD80\uAC1C\uC815"
                    },
                    "\uC870\uBB38": {
                      "\uC870\uBB38\uB2E8\uC704": [
                        {
                          "\uC870\uBB38\uC5EC\uBD80": "\uC804\uBB38",
                          "\uC870\uBB38\uB0B4\uC6A9": "\uC81C1\uC7A5 \uCD1D\uCE59"
                        },
                        {
                          "\uC870\uBB38\uC5EC\uBD80": "\uC870\uBB38",
                          "\uC870\uBB38\uD0A4": "0001001",
                          "\uC870\uBB38\uBC88\uD638": "1",
                          "\uC870\uBB38\uC81C\uBAA9": "\uBAA9\uC801",
                          "\uC870\uBB38\uB0B4\uC6A9": "\uC81C1\uC870(\uBAA9\uC801) \uC774 \uBC95\uC740 ...",
                          "\uC870\uBB38\uC2DC\uD589\uC77C\uC790": "20260227"
                        },
                        {
                          "\uC870\uBB38\uC5EC\uBD80": "\uC870\uBB38",
                          "\uC870\uBB38\uD0A4": "0001001",
                          "\uC870\uBB38\uBC88\uD638": "1",
                          "\uC870\uBB38\uC81C\uBAA9": "\uBAA9\uC801",
                          "\uC870\uBB38\uB0B4\uC6A9": "\uC81C1\uC870 \uC911\uBCF5 \uC6D0\uBCF8\uD0A4 \uC0D8\uD50C",
                          "\uC870\uBB38\uC2DC\uD589\uC77C\uC790": "20260227"
                        }
                      ]
                    }
                  }
                }
                """);

        var snapshot = client.parseLawDetail(target, detail, "https://www.law.go.kr/DRF/lawService.do");

        assertThat(snapshot.actCode()).isEqualTo("BUILDING_ACT");
        assertThat(snapshot.actName()).isEqualTo("\uAC74\uCD95\uBC95");
        assertThat(snapshot.sourceLawId()).isEqualTo("001823");
        assertThat(snapshot.sourceVersionKey()).isEqualTo("0018232025082621035");
        assertThat(snapshot.promulgationDate()).hasToString("2025-08-26");
        assertThat(snapshot.effectiveDate()).hasToString("2026-02-27");
        assertThat(snapshot.articles()).hasSize(2);
        assertThat(snapshot.articles().get(0).articleKey()).isEqualTo("0001001");
        assertThat(snapshot.articles().get(0).articleTitle()).isEqualTo("\uBAA9\uC801");
        assertThat(snapshot.articles().get(1).articleKey()).isEqualTo("0001001_2");
    }

    @Test
    void parsesAdminRuleBodyAndAnnexes() throws Exception {
        var target = new LegalSyncProperties.Target(
                "admrul",
                "\uAC74\uCD95\uACF5\uC0AC \uAC10\uB9AC\uC138\uBD80\uAE30\uC900",
                "\uAC74\uCD95\uACF5\uC0AC \uAC10\uB9AC\uC138\uBD80\uAE30\uC900",
                "CONSTRUCTION_SUPERVISION_DETAILED_STANDARD",
                "ADMINISTRATIVE_RULE");
        var detail = objectMapper.readTree("""
                {
                  "AdmRulService": {
                    "\uD589\uC815\uADDC\uCE59\uAE30\uBCF8\uC815\uBCF4": {
                      "\uD589\uC815\uADDC\uCE59\uBA85": "\uAC74\uCD95\uACF5\uC0AC \uAC10\uB9AC\uC138\uBD80\uAE30\uC900",
                      "\uD589\uC815\uADDC\uCE59\uC77C\uB828\uBC88\uD638": "2100000244148",
                      "\uBC1C\uB839\uBC88\uD638": "2024-377",
                      "\uBC1C\uB839\uC77C\uC790": "20240710",
                      "\uC2DC\uD589\uC77C\uC790": "20240710",
                      "\uC18C\uAD00\uBD80\uCC98\uBA85": "\uAD6D\uD1A0\uAD50\uD1B5\uBD80",
                      "\uC81C\uAC1C\uC815\uAD6C\uBD84\uBA85": "\uC77C\uBD80\uAC1C\uC815"
                    },
                    "\uC870\uBB38\uB0B4\uC6A9": "\uC81C1\uC7A5 \uC77C\uBC18 \uC0AC\uD56D\\n1.1 \uBAA9\uC801",
                    "\uBCC4\uD45C": {
                      "\uBCC4\uD45C\uB2E8\uC704": {
                        "\uBCC4\uD45C\uC81C\uBAA9": "\uB2E8\uACC4\uBCC4 \uAC10\uB9AC \uCCB4\uD06C\uB9AC\uC2A4\uD2B8 \uB300\uC7A5",
                        "\uBCC4\uD45C\uBC88\uD638": "0001",
                        "\uBCC4\uD45C\uD0A4": "000100",
                        "\uBCC4\uD45C\uB0B4\uC6A9": [
                          ["[\uBCC4\uD45C 1]", "\uB2E8\uACC4\uBCC4 \uAC10\uB9AC \uCCB4\uD06C\uB9AC\uC2A4\uD2B8 \uB300\uC7A5"]
                        ]
                      }
                    }
                  }
                }
                """);

        var snapshot = client.parseAdminRuleDetail(target, detail, "https://www.law.go.kr/DRF/lawService.do");

        assertThat(snapshot.actCode()).isEqualTo("CONSTRUCTION_SUPERVISION_DETAILED_STANDARD");
        assertThat(snapshot.actName()).isEqualTo("\uAC74\uCD95\uACF5\uC0AC \uAC10\uB9AC\uC138\uBD80\uAE30\uC900");
        assertThat(snapshot.sourceLawId()).isEqualTo("2100000244148");
        assertThat(snapshot.sourceVersionKey()).isEqualTo("2100000244148:2024-377:20240710:20240710");
        assertThat(snapshot.articles()).hasSize(2);
        assertThat(snapshot.articles().get(0).articleKey()).isEqualTo("BODY");
        assertThat(snapshot.articles().get(1).articleKey()).isEqualTo("000100");
        assertThat(snapshot.articles().get(1).articleText()).contains("\uB2E8\uACC4\uBCC4 \uAC10\uB9AC");
    }
}
