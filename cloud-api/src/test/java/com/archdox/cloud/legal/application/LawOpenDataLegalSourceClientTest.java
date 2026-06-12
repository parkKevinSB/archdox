package com.archdox.cloud.legal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpHeaders;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import org.junit.jupiter.api.Test;

class LawOpenDataLegalSourceClientTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LawOpenDataLegalSourceClient client = new LawOpenDataLegalSourceClient(
            objectMapper,
            new LegalSyncProperties(),
            HttpClient.newHttpClient());

    @Test
    void fetchRetriesRetryableHttpStatusAndBuildsSourceSnapshot() {
        var properties = openApiProperties(List.of(new LegalSyncProperties.Target(
                "law",
                "\uAC74\uCD95\uBC95",
                "\uAC74\uCD95\uBC95",
                "BUILDING_ACT",
                "LAW")));
        properties.getOpenApi().setMaxAttempts(2);
        var httpClient = new RecordingHttpClient(List.of(
                response(500, "{}"),
                response(200, """
                        {
                          "LawSearch": {
                            "law": {
                              "\uBC95\uB839ID": "001823",
                              "\uBC95\uB839\uBA85\uD55C\uAE00": "\uAC74\uCD95\uBC95"
                            }
                          }
                        }
                        """),
                response(200, lawDetailJson())));
        var retryingClient = new LawOpenDataLegalSourceClient(objectMapper, properties, httpClient);

        var snapshot = retryingClient.fetch(LawOpenDataLegalSourceClient.SOURCE_CODE);

        assertThat(snapshot.sourceCode()).isEqualTo(LawOpenDataLegalSourceClient.SOURCE_CODE);
        assertThat(snapshot.acts()).singleElement()
                .satisfies(act -> {
                    assertThat(act.actCode()).isEqualTo("BUILDING_ACT");
                    assertThat(act.articles()).singleElement()
                            .satisfies(article -> assertThat(article.articleNo()).isEqualTo("1"));
                });
        assertThat(httpClient.requestUris()).hasSize(3);
        assertThat(httpClient.requestUris().get(0).toString()).contains("lawSearch.do");
        assertThat(httpClient.requestUris().get(1).toString()).contains("lawSearch.do");
        assertThat(httpClient.requestUris().get(2).toString()).contains("lawService.do");
        assertThat(httpClient.asyncRequestCount()).isEqualTo(3);
    }

    @Test
    void fetchRejectsOpenApiErrorPayload() {
        var properties = openApiProperties(List.of(new LegalSyncProperties.Target(
                "law",
                "\uAC74\uCD95\uBC95",
                "\uAC74\uCD95\uBC95",
                "BUILDING_ACT",
                "LAW")));
        var httpClient = new RecordingHttpClient(List.of(response(200, """
                {
                  "RESULT": {
                    "CODE": "ERROR-001",
                    "MESSAGE": "OC is invalid"
                  }
                }
                """)));
        var guardedClient = new LawOpenDataLegalSourceClient(objectMapper, properties, httpClient);

        assertThatThrownBy(() -> guardedClient.fetch(LawOpenDataLegalSourceClient.SOURCE_CODE))
                .isInstanceOf(LawOpenDataException.class)
                .hasMessageContaining("ERROR-001")
                .extracting(ex -> ((LawOpenDataException) ex).code())
                .isEqualTo("LAW_OPEN_DATA_RESPONSE_ERROR");
    }

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

    @Test
    void parsesOrdinanceDetailArticlesAndLocalGovernmentMetadata() throws Exception {
        var target = new LegalSyncProperties.Target(
                "ordin",
                "서울특별시 건축 조례",
                "서울특별시 건축 조례",
                "LOCAL_SEOUL_BUILDING_ORDINANCE",
                "LOCAL_ORDINANCE");
        var detail = objectMapper.readTree("""
                {
                  "LawService": {
                    "자치법규기본정보": {
                      "자치법규명": "서울특별시 건축 조례",
                      "자치법규ID": "2026666",
                      "자치법규일련번호": "1316146",
                      "공포번호": "9000",
                      "공포일자": "20250101",
                      "시행일자": "20250115",
                      "자치법규종류": "조례",
                      "지자체기관명": "서울특별시",
                      "담당부서명": "건축기획과",
                      "제개정정보": "일부개정"
                    },
                    "조문": {
                      "조": [
                        {
                          "조문번호": "1",
                          "조문여부": "Y",
                          "조제목": "목적",
                          "조내용": "제1조(목적) 이 조례는 ..."
                        },
                        {
                          "조문번호": "2",
                          "조문여부": "Y",
                          "조제목": "적용범위",
                          "조내용": "제2조(적용범위) ..."
                        }
                      ]
                    },
                    "부칙": {
                      "부칙내용": "부칙 이 조례는 공포한 날부터 시행한다."
                    }
                  }
                }
                """);

        var snapshot = client.parseOrdinanceDetail(target, detail, "https://www.law.go.kr/DRF/lawService.do");

        assertThat(snapshot.actCode()).isEqualTo("LOCAL_SEOUL_BUILDING_ORDINANCE");
        assertThat(snapshot.actName()).isEqualTo("서울특별시 건축 조례");
        assertThat(snapshot.actType()).isEqualTo("LOCAL_ORDINANCE");
        assertThat(snapshot.sourceLawId()).isEqualTo("1316146");
        assertThat(snapshot.sourceVersionKey()).isEqualTo("1316146:9000:20250101:20250115");
        assertThat(snapshot.metadata()).containsEntry("localGovernment", "서울특별시");
        assertThat(snapshot.articles()).hasSize(3);
        assertThat(snapshot.articles().get(0).articleTitle()).isEqualTo("목적");
        assertThat(snapshot.articles().get(2).articleKey()).isEqualTo("SUPPLEMENT");
    }

    @Test
    void fetchOrdinanceUsesOrdinTargetAndConfiguredLocalGovernmentFilters() {
        var target = new LegalSyncProperties.Target(
                "ordin",
                "건축 조례",
                "서울특별시 건축 조례",
                "LOCAL_SEOUL_BUILDING_ORDINANCE",
                "LOCAL_ORDINANCE");
        target.setOrg("6110000");
        target.setKnd("30001");
        var properties = openApiProperties(List.of(target));
        var httpClient = new RecordingHttpClient(List.of(
                response(200, """
                        {
                          "OrdinSearch": {
                            "law": {
                              "자치법규ID": "2026666",
                              "자치법규일련번호": "1316146",
                              "자치법규명": "서울특별시 건축 조례"
                            }
                          }
                        }
                        """),
                response(200, """
                        {
                          "LawService": {
                            "자치법규기본정보": {
                              "자치법규명": "서울특별시 건축 조례",
                              "자치법규ID": "2026666",
                              "자치법규일련번호": "1316146",
                              "공포일자": "20250101",
                              "시행일자": "20250115",
                              "자치법규종류": "조례",
                              "지자체기관명": "서울특별시"
                            },
                            "조문": {
                              "조": {
                                "조문번호": "1",
                                "조문여부": "Y",
                                "조제목": "목적",
                                "조내용": "제1조(목적) ..."
                              }
                            }
                          }
                        }
                        """)));
        var ordinanceClient = new LawOpenDataLegalSourceClient(objectMapper, properties, httpClient);

        var snapshot = ordinanceClient.fetch(LawOpenDataLegalSourceClient.SOURCE_CODE);

        assertThat(snapshot.acts()).singleElement()
                .satisfies(act -> assertThat(act.actCode()).isEqualTo("LOCAL_SEOUL_BUILDING_ORDINANCE"));
        assertThat(httpClient.requestUris().get(0).toString())
                .contains("target=ordin")
                .contains("org=6110000")
                .contains("knd=30001");
        assertThat(httpClient.requestUris().get(1).toString())
                .contains("lawService.do")
                .contains("target=ordin")
                .contains("ID=2026666");
    }

    private LegalSyncProperties openApiProperties(List<LegalSyncProperties.Target> targets) {
        var properties = new LegalSyncProperties();
        properties.getOpenApi().setEnabled(true);
        properties.getOpenApi().setOc("archdox_api_test");
        properties.getOpenApi().setRequestIntervalMs(0);
        properties.getOpenApi().setRequestTimeoutMs(1000);
        properties.getOpenApi().setTargets(targets);
        return properties;
    }

    private static String lawDetailJson() {
        return """
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
                      "\uC870\uBB38\uB2E8\uC704": {
                        "\uC870\uBB38\uC5EC\uBD80": "\uC870\uBB38",
                        "\uC870\uBB38\uD0A4": "0001001",
                        "\uC870\uBB38\uBC88\uD638": "1",
                        "\uC870\uBB38\uC81C\uBAA9": "\uBAA9\uC801",
                        "\uC870\uBB38\uB0B4\uC6A9": "\uC81C1\uC870(\uBAA9\uC801) \uC774 \uBC95\uC740 ...",
                        "\uC870\uBB38\uC2DC\uD589\uC77C\uC790": "20260227"
                      }
                    }
                  }
                }
                """;
    }

    private static QueuedResponse response(int statusCode, String body) {
        return new QueuedResponse(statusCode, body);
    }

    private static final class RecordingHttpClient extends HttpClient {
        private final ArrayDeque<QueuedResponse> responses;
        private final List<URI> requestUris = new java.util.ArrayList<>();
        private int asyncRequestCount = 0;

        private RecordingHttpClient(List<QueuedResponse> responses) {
            this.responses = new ArrayDeque<>(responses);
        }

        private List<URI> requestUris() {
            return List.copyOf(requestUris);
        }

        private int asyncRequestCount() {
            return asyncRequestCount;
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            return null;
        }

        @Override
        public SSLParameters sslParameters() {
            return null;
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
                throws IOException, InterruptedException {
            requestUris.add(request.uri());
            if (responses.isEmpty()) {
                throw new IOException("No queued response");
            }
            var response = responses.removeFirst();
            return (HttpResponse<T>) new ByteArrayResponse(
                    request,
                    response.statusCode(),
                    response.body().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler
        ) {
            asyncRequestCount++;
            try {
                return CompletableFuture.completedFuture(send(request, responseBodyHandler));
            } catch (IOException ex) {
                return CompletableFuture.failedFuture(ex);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return CompletableFuture.failedFuture(ex);
            }
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler
        ) {
            return sendAsync(request, responseBodyHandler);
        }
    }

    private record QueuedResponse(int statusCode, String body) {
    }

    private record ByteArrayResponse(
            HttpRequest request,
            int statusCode,
            byte[] body
    ) implements HttpResponse<byte[]> {
        @Override
        public Optional<HttpResponse<byte[]>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(Map.of("Content-Type", List.of("application/json; charset=UTF-8")), (left, right) -> true);
        }

        @Override
        public Optional<javax.net.ssl.SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return request.uri();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }
    }
}
