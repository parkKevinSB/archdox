package com.archdox.cloud.legal.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LawOpenDataLegalSourceClient implements LegalSourceClient {
    public static final String SOURCE_CODE = "NATIONAL_LAW_OPEN_DATA";

    private static final String TARGET_LAW = "law";
    private static final String TARGET_ADMIN_RULE = "admrul";
    private static final Charset LAW_OPEN_DATA_DEFAULT_CHARSET = StandardCharsets.UTF_8;
    private static final Charset LAW_OPEN_DATA_LEGACY_CHARSET = Charset.forName("MS949");

    private static final String K_CONTENT = "content";
    private static final String K_LAW_ROOT = "\uBC95\uB839";
    private static final String K_LAW_BASE = "\uAE30\uBCF8\uC815\uBCF4";
    private static final String K_LAW_ID = "\uBC95\uB839ID";
    private static final String K_LAW_SERIAL_NO = "\uBC95\uB839\uC77C\uB828\uBC88\uD638";
    private static final String K_LAW_NAME = "\uBC95\uB839\uBA85_\uD55C\uAE00";
    private static final String K_LAW_SEARCH_NAME = "\uBC95\uB839\uBA85\uD55C\uAE00";
    private static final String K_LAW_TYPE = "\uBC95\uC885\uAD6C\uBD84";
    private static final String K_PROMULGATION_DATE = "\uACF5\uD3EC\uC77C\uC790";
    private static final String K_EFFECTIVE_DATE = "\uC2DC\uD589\uC77C\uC790";
    private static final String K_LAW_VERSION_KEY = "\uBC95\uB839\uD0A4";
    private static final String K_PROMULGATION_NO = "\uACF5\uD3EC\uBC88\uD638";
    private static final String K_MINISTRY = "\uC18C\uAD00\uBD80\uCC98";
    private static final String K_REVISION_TYPE = "\uC81C\uAC1C\uC815\uAD6C\uBD84";
    private static final String K_ARTICLES = "\uC870\uBB38";
    private static final String K_ARTICLE_UNIT = "\uC870\uBB38\uB2E8\uC704";
    private static final String K_ARTICLE_FLAG = "\uC870\uBB38\uC5EC\uBD80";
    private static final String K_ARTICLE_FLAG_VALUE = "\uC870\uBB38";
    private static final String K_ARTICLE_KEY = "\uC870\uBB38\uD0A4";
    private static final String K_ARTICLE_NO = "\uC870\uBB38\uBC88\uD638";
    private static final String K_ARTICLE_TITLE = "\uC870\uBB38\uC81C\uBAA9";
    private static final String K_ARTICLE_TEXT = "\uC870\uBB38\uB0B4\uC6A9";
    private static final String K_ARTICLE_EFFECTIVE_DATE = "\uC870\uBB38\uC2DC\uD589\uC77C\uC790";

    private static final String K_ADMIN_ROOT = "AdmRulService";
    private static final String K_ADMIN_BASE = "\uD589\uC815\uADDC\uCE59\uAE30\uBCF8\uC815\uBCF4";
    private static final String K_ADMIN_ID = "\uD589\uC815\uADDC\uCE59ID";
    private static final String K_ADMIN_SERIAL_NO = "\uD589\uC815\uADDC\uCE59\uC77C\uB828\uBC88\uD638";
    private static final String K_ADMIN_NAME = "\uD589\uC815\uADDC\uCE59\uBA85";
    private static final String K_ADMIN_ISSUE_NO = "\uBC1C\uB839\uBC88\uD638";
    private static final String K_ADMIN_ISSUE_DATE = "\uBC1C\uB839\uC77C\uC790";
    private static final String K_ADMIN_MINISTRY = "\uC18C\uAD00\uBD80\uCC98\uBA85";
    private static final String K_ADMIN_REVISION_TYPE = "\uC81C\uAC1C\uC815\uAD6C\uBD84\uBA85";
    private static final String K_ANNEXES = "\uBCC4\uD45C";
    private static final String K_ANNEX_UNIT = "\uBCC4\uD45C\uB2E8\uC704";
    private static final String K_ANNEX_TITLE = "\uBCC4\uD45C\uC81C\uBAA9";
    private static final String K_ANNEX_NO = "\uBCC4\uD45C\uBC88\uD638";
    private static final String K_ANNEX_KEY = "\uBCC4\uD45C\uD0A4";
    private static final String K_ANNEX_TEXT = "\uBCC4\uD45C\uB0B4\uC6A9";
    private static final String K_ANNEX_PDF_LINK = "\uBCC4\uD45C\uC11C\uC2DDPDF\uD30C\uC77C\uB9C1\uD06C";

    private final ObjectMapper objectMapper;
    private final LegalSyncProperties properties;
    private final HttpClient httpClient;
    private final LawOpenDataRequestThrottle requestThrottle;

    @Autowired
    public LawOpenDataLegalSourceClient(
            ObjectMapper objectMapper,
            LegalSyncProperties properties,
            LawOpenDataRequestThrottle requestThrottle
    ) {
        this(objectMapper, properties, HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(1000, properties.getOpenApi().getRequestTimeoutMs())))
                .build(), requestThrottle);
    }

    LawOpenDataLegalSourceClient(
            ObjectMapper objectMapper,
            LegalSyncProperties properties,
            HttpClient httpClient
    ) {
        this(objectMapper, properties, httpClient, new LawOpenDataRequestThrottle());
    }

    LawOpenDataLegalSourceClient(
            ObjectMapper objectMapper,
            LegalSyncProperties properties,
            HttpClient httpClient,
            LawOpenDataRequestThrottle requestThrottle
    ) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.httpClient = httpClient;
        this.requestThrottle = requestThrottle;
    }

    @Override
    public boolean supports(String sourceCode) {
        var requested = normalize(sourceCode);
        var configured = normalize(properties.getOpenApi().getSourceCode());
        return SOURCE_CODE.equals(requested) || configured.equals(requested);
    }

    @Override
    public LegalSourceSnapshot fetch(String sourceCode) {
        try {
            return fetchAsync(sourceCode).join();
        } catch (CompletionException ex) {
            throw runtimeFailure(ex.getCause() == null ? ex : ex.getCause());
        }
    }

    @Override
    public boolean nativeAsyncFetchSupported() {
        return true;
    }

    @Override
    public CompletableFuture<LegalSourceSnapshot> fetchAsync(String sourceCode) {
        if (!supports(sourceCode)) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    "Unsupported legal source code: " + sourceCode));
        }
        var openApi = properties.getOpenApi();
        if (!openApi.isEnabled()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Law Open Data API sync is disabled"));
        }
        if (openApi.getOc() == null || openApi.getOc().isBlank()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Law Open Data API OC is required"));
        }

        var targets = openApi.getTargets().stream()
                .filter(target -> target.getQuery() != null && !target.getQuery().isBlank())
                .toList();
        if (targets.isEmpty()) {
            return CompletableFuture.failedFuture(new LawOpenDataException(
                    "LAW_OPEN_DATA_TARGETS_EMPTY",
                    "fetch",
                    "",
                    0,
                    false,
                    "Law Open Data API sync target list is empty"));
        }

        CompletableFuture<List<LegalActSnapshot>> actsFuture = CompletableFuture.completedFuture(new ArrayList<>());
        for (var target : targets) {
            actsFuture = actsFuture.thenCompose(acts -> fetchTargetAsync(target)
                    .thenApply(snapshot -> {
                        acts.add(snapshot);
                        return acts;
                    }));
        }
        return actsFuture.thenApply(acts -> new LegalSourceSnapshot(
                openApi.getSourceCode(),
                "LAW_OPEN_DATA",
                "\uAD6D\uAC00\uBC95\uB839\uC815\uBCF4 \uACF5\uB3D9\uD65C\uC6A9",
                baseUrl(),
                Map.of(
                        "provider", "law.go.kr",
                        "targets", acts.size()),
                acts.stream()
                        .sorted(Comparator.comparing(LegalActSnapshot::actCode))
                        .toList()));
    }

    private CompletableFuture<LegalActSnapshot> fetchTargetAsync(LegalSyncProperties.Target target) {
        var normalizedTarget = normalizeTarget(target.getTarget());
        return fetchJsonAsync(
                "lawSearch.do",
                normalizedTarget,
                Map.of(
                        "target", normalizedTarget,
                        "type", "JSON",
                        "query", target.getQuery(),
                        "display", "10"))
                .thenCompose(search -> {
            var item = selectSearchResult(target, normalizedTarget, search);
            var detailId = detailId(normalizedTarget, item);
            if (detailId.isBlank()) {
                return CompletableFuture.failedFuture(new LawOpenDataException(
                        "LAW_OPEN_DATA_DETAIL_ID_MISSING",
                        "lawSearch.do",
                        normalizedTarget,
                        0,
                        false,
                        "Law Open Data search result has no detail ID for query: " + target.getQuery()));
            }
            return fetchJsonAsync(
                            "lawService.do",
                            normalizedTarget,
                            Map.of(
                                    "target", normalizedTarget,
                                    "type", "JSON",
                                    "ID", detailId))
                    .thenApply(detail -> {
                        var sourceUrl = serviceUrl(normalizedTarget, detailId);
                        var snapshot = TARGET_LAW.equals(normalizedTarget)
                                ? parseLawDetail(target, detail, sourceUrl)
                                : parseAdminRuleDetail(target, detail, sourceUrl);
                        if (snapshot.articles().isEmpty()) {
                            throw new LawOpenDataException(
                                    "LAW_OPEN_DATA_ARTICLES_EMPTY",
                                    "lawService.do",
                                    normalizedTarget,
                                    0,
                                    false,
                                    "Law Open Data detail response has no articles or annex body for query: "
                                            + target.getQuery());
                        }
                        return snapshot;
                    });
        })
                .handle((snapshot, error) -> {
                    if (error == null) {
                        return CompletableFuture.completedFuture(snapshot);
                    }
                    var failure = unwrapCompletion(error);
                    if (failure instanceof LawOpenDataException openDataException) {
                        return CompletableFuture.<LegalActSnapshot>failedFuture(openDataException);
                    }
                    return CompletableFuture.<LegalActSnapshot>failedFuture(new LawOpenDataException(
                            "LAW_OPEN_DATA_TARGET_FAILED",
                            "fetchTarget",
                            normalizedTarget,
                            0,
                            false,
                            "Law Open Data target failed for query '" + target.getQuery() + "': "
                                    + failure.getMessage(),
                            failure));
                })
                .thenCompose(Function.identity());
    }

    private CompletableFuture<JsonNode> fetchJsonAsync(String path, String target, Map<String, String> parameters) {
        var uri = URI.create(baseUrl() + "/" + path + "?" + query(parameters));
        var request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(Math.max(1000, properties.getOpenApi().getRequestTimeoutMs())))
                .header("User-Agent", properties.getOpenApi().getUserAgent())
                .GET()
                .build();
        return fetchJsonAttemptAsync(
                request,
                path,
                target,
                1,
                Math.max(1, properties.getOpenApi().getMaxAttempts()),
                null);
    }

    private CompletableFuture<JsonNode> fetchJsonAttemptAsync(
            HttpRequest request,
            String path,
            String target,
            int attempt,
            int maxAttempts,
            IOException lastIOException
    ) {
        return requestThrottle.requestSlotAsync(properties.getOpenApi().getRequestIntervalMs())
                .thenCompose(ignored -> httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()))
                .thenApply(response -> responseJson(response, path, target))
                .handle((json, error) -> {
                    if (error == null) {
                        return CompletableFuture.completedFuture(json);
                    }
                    var failure = unwrapCompletion(error);
                    if (failure instanceof LawOpenDataException openDataException) {
                        if (openDataException.retryable() && attempt < maxAttempts) {
                            return retryFetchJson(request, path, target, attempt, maxAttempts, lastIOException);
                        }
                        return CompletableFuture.<JsonNode>failedFuture(openDataException);
                    }
                    if (failure instanceof IOException ioException) {
                        if (attempt < maxAttempts) {
                            return retryFetchJson(request, path, target, attempt, maxAttempts, ioException);
                        }
                        return CompletableFuture.<JsonNode>failedFuture(ioFailure(path, target, ioException));
                    }
                    return CompletableFuture.<JsonNode>failedFuture(runtimeFailure(failure));
                })
                .thenCompose(Function.identity());
    }

    private CompletableFuture<JsonNode> retryFetchJson(
            HttpRequest request,
            String path,
            String target,
            int attempt,
            int maxAttempts,
            IOException lastIOException
    ) {
        return requestThrottle.retryDelayAsync(attempt)
                .thenCompose(ignored -> fetchJsonAttemptAsync(
                        request,
                        path,
                        target,
                        attempt + 1,
                        maxAttempts,
                        lastIOException));
    }

    private JsonNode responseJson(HttpResponse<byte[]> response, String path, String target) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new LawOpenDataException(
                    "LAW_OPEN_DATA_HTTP_" + response.statusCode(),
                    path,
                    target,
                    response.statusCode(),
                    retryableHttpStatus(response.statusCode()),
                    "Law Open Data API returned HTTP " + response.statusCode() + " for " + path);
        }
        try {
            var json = readJsonBody(response, path);
            validateOpenDataResponse(json, path, target);
            return json;
        } catch (IOException ex) {
            throw new CompletionException(ex);
        }
    }

    private LawOpenDataException ioFailure(String path, String target, IOException lastIOException) {
        return new LawOpenDataException(
                "LAW_OPEN_DATA_IO_FAILURE",
                path,
                target,
                0,
                true,
                "Failed to read Law Open Data API response for " + path
                        + ": " + (lastIOException == null ? "unknown IO failure" : lastIOException.getMessage()),
                lastIOException);
    }

    private RuntimeException runtimeFailure(Throwable failure) {
        var unwrapped = unwrapCompletion(failure);
        if (unwrapped instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new IllegalStateException(unwrapped.getMessage(), unwrapped);
    }

    private Throwable unwrapCompletion(Throwable failure) {
        if (failure instanceof CompletionException completionException && completionException.getCause() != null) {
            return unwrapCompletion(completionException.getCause());
        }
        return failure;
    }

    private static boolean retryableHttpStatus(int statusCode) {
        return statusCode == 429 || statusCode >= 500;
    }

    private JsonNode readJsonBody(HttpResponse<byte[]> response, String path) throws IOException {
        var body = response.body();
        var charset = response.headers()
                .firstValue("Content-Type")
                .flatMap(LawOpenDataLegalSourceClient::charsetFromContentType)
                .orElse(LAW_OPEN_DATA_DEFAULT_CHARSET);
        try {
            return objectMapper.readTree(new String(body, charset));
        } catch (JsonProcessingException ex) {
            if (charset.equals(LAW_OPEN_DATA_LEGACY_CHARSET)) {
                throw ex;
            }
            try {
                return objectMapper.readTree(new String(body, LAW_OPEN_DATA_LEGACY_CHARSET));
            } catch (JsonProcessingException legacyEx) {
                ex.addSuppressed(legacyEx);
                throw ex;
            }
        }
    }

    LegalActSnapshot parseLawDetail(
            LegalSyncProperties.Target target,
            JsonNode detail,
            String sourceUrl
    ) {
        var root = requiredObject(detail, K_LAW_ROOT);
        var base = requiredObject(root, K_LAW_BASE);
        var lawId = text(base, K_LAW_ID, target.getQuery());
        var name = text(base, K_LAW_NAME, target.getExpectedName(), target.getQuery());
        var actType = textObjectContent(base, K_LAW_TYPE, target.getActType(), "LAW");
        var promulgationDate = date(text(base, K_PROMULGATION_DATE, null));
        var effectiveDate = date(text(base, K_EFFECTIVE_DATE, null));
        var versionKey = firstNonBlank(
                text(root, K_LAW_VERSION_KEY, null),
                lawId + ":" + text(base, K_PROMULGATION_NO, "") + ":"
                        + text(base, K_PROMULGATION_DATE, "") + ":"
                        + text(base, K_EFFECTIVE_DATE, ""));
        var articles = new ArrayList<LegalArticleSnapshot>();
        var seenArticleKeys = new LinkedHashMap<String, Integer>();
        for (var node : asList(path(root, K_ARTICLES, K_ARTICLE_UNIT))) {
            if (!K_ARTICLE_FLAG_VALUE.equals(text(node, K_ARTICLE_FLAG, ""))) {
                continue;
            }
            var articleText = text(node, K_ARTICLE_TEXT, "");
            if (articleText.isBlank()) {
                continue;
            }
            var rawKey = firstNonBlank(text(node, K_ARTICLE_KEY, null), "ARTICLE_" + text(node, K_ARTICLE_NO, ""));
            articles.add(new LegalArticleSnapshot(
                    uniqueArticleKey(rawKey, seenArticleKeys),
                    text(node, K_ARTICLE_NO, ""),
                    text(node, K_ARTICLE_TITLE, null),
                    null,
                    intValue(text(node, K_ARTICLE_NO, null), 0),
                    articleText,
                    Map.of(
                            "target", TARGET_LAW,
                            "sourceArticleKey", rawKey,
                            "effectiveDate", text(node, K_ARTICLE_EFFECTIVE_DATE, ""))));
        }

        return new LegalActSnapshot(
                firstNonBlank(target.getActCode(), "LAW_" + lawId),
                name,
                firstNonBlank(target.getActType(), actType, "LAW"),
                "KR",
                lawId,
                versionKey,
                promulgationDate,
                effectiveDate,
                sourceUrl,
                Map.of(
                        "target", TARGET_LAW,
                        "ministry", textObjectContent(base, K_MINISTRY, "", ""),
                        "promulgationNo", text(base, K_PROMULGATION_NO, ""),
                        "revisionType", text(base, K_REVISION_TYPE, "")),
                articles);
    }

    LegalActSnapshot parseAdminRuleDetail(
            LegalSyncProperties.Target target,
            JsonNode detail,
            String sourceUrl
    ) {
        var root = requiredObject(detail, K_ADMIN_ROOT);
        var base = requiredObject(root, K_ADMIN_BASE);
        var ruleId = text(base, K_ADMIN_SERIAL_NO, text(base, K_ADMIN_ID, target.getQuery()));
        var name = text(base, K_ADMIN_NAME, target.getExpectedName(), target.getQuery());
        var promulgationDate = date(text(base, K_ADMIN_ISSUE_DATE, null));
        var effectiveDate = date(text(base, K_EFFECTIVE_DATE, null));
        var versionKey = ruleId + ":" + text(base, K_ADMIN_ISSUE_NO, "") + ":"
                + text(base, K_ADMIN_ISSUE_DATE, "") + ":" + text(base, K_EFFECTIVE_DATE, "");

        var articles = new ArrayList<LegalArticleSnapshot>();
        var body = text(root, K_ARTICLE_TEXT, "");
        if (!body.isBlank()) {
            articles.add(new LegalArticleSnapshot(
                    "BODY",
                    "\uBCF8\uBB38",
                    "\uBCF8\uBB38",
                    null,
                    10,
                    body,
                    Map.of("target", TARGET_ADMIN_RULE)));
        }
        var annexOrder = 1000;
        var seenArticleKeys = new LinkedHashMap<String, Integer>();
        seenArticleKeys.put("BODY", 1);
        for (var annex : asList(path(root, K_ANNEXES, K_ANNEX_UNIT))) {
            var title = text(annex, K_ANNEX_TITLE, "\uBCC4\uD45C");
            var annexNo = text(annex, K_ANNEX_NO, String.valueOf(annexOrder));
            var annexText = flattenText(annex.get(K_ANNEX_TEXT));
            if (!annexText.isBlank()) {
                var rawKey = firstNonBlank(text(annex, K_ANNEX_KEY, null), "ANNEX_" + annexNo);
                articles.add(new LegalArticleSnapshot(
                        uniqueArticleKey(rawKey, seenArticleKeys),
                        "\uBCC4\uD45C " + annexNo,
                        title,
                        null,
                        annexOrder++,
                        annexText,
                        Map.of(
                                "target", TARGET_ADMIN_RULE,
                                "sourceArticleKey", rawKey,
                                "pdfLink", text(annex, K_ANNEX_PDF_LINK, ""))));
            }
        }

        return new LegalActSnapshot(
                firstNonBlank(target.getActCode(), "ADMRUL_" + ruleId),
                name,
                firstNonBlank(target.getActType(), "ADMINISTRATIVE_RULE"),
                "KR",
                ruleId,
                versionKey,
                promulgationDate,
                effectiveDate,
                sourceUrl,
                Map.of(
                        "target", TARGET_ADMIN_RULE,
                        "ministry", text(base, K_ADMIN_MINISTRY, ""),
                        "issueNo", text(base, K_ADMIN_ISSUE_NO, ""),
                        "revisionType", text(base, K_ADMIN_REVISION_TYPE, "")),
                articles);
    }

    private static Optional<Charset> charsetFromContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return Optional.empty();
        }
        for (var segment : contentType.split(";")) {
            var trimmed = segment.trim();
            if (trimmed.regionMatches(true, 0, "charset=", 0, "charset=".length())) {
                var value = trimmed.substring("charset=".length()).replace("\"", "").trim();
                if (!value.isBlank()) {
                    try {
                        return Optional.of(Charset.forName(value));
                    } catch (RuntimeException ignored) {
                        return Optional.empty();
                    }
                }
            }
        }
        return Optional.empty();
    }

    private JsonNode selectSearchResult(LegalSyncProperties.Target target, String normalizedTarget, JsonNode search) {
        var rootName = TARGET_LAW.equals(normalizedTarget) ? "LawSearch" : "AdmRulSearch";
        var itemName = TARGET_LAW.equals(normalizedTarget) ? "law" : "admrul";
        var nameField = TARGET_LAW.equals(normalizedTarget) ? K_LAW_SEARCH_NAME : K_ADMIN_NAME;
        var root = requiredObject(search, rootName);
        var results = asList(root.get(itemName));
        if (results.isEmpty()) {
            throw new IllegalStateException("No Law Open Data search result for query: " + target.getQuery());
        }
        var expectedName = normalizeDisplayName(firstNonBlank(target.getExpectedName(), target.getQuery()));
        return results.stream()
                .filter(node -> normalizeDisplayName(text(node, nameField, "")).equals(expectedName))
                .findFirst()
                .orElse(results.get(0));
    }

    private void validateOpenDataResponse(JsonNode json, String path, String target) {
        var error = openDataError(json);
        if (error.isEmpty()) {
            return;
        }
        var errorPayload = error.get();
        var providerCode = text(errorPayload, "code", "UNKNOWN");
        throw new LawOpenDataException(
                "LAW_OPEN_DATA_RESPONSE_ERROR",
                path,
                target,
                0,
                false,
                "Law Open Data API returned an error payload for " + path + ": "
                        + providerCode + " " + text(errorPayload, "message", "unknown error"));
    }

    private Optional<JsonNode> openDataError(JsonNode json) {
        if (json == null || !json.isObject()) {
            return Optional.empty();
        }
        var result = json.get("RESULT");
        if (result == null) {
            result = json.get("result");
        }
        if (result != null && result.isObject()) {
            var code = firstNonBlank(
                    text(result, "CODE", null),
                    text(result, "code", null),
                    text(result, "resultCode", null));
            if (!code.isBlank() && !successCode(code)) {
                var error = new LinkedHashMap<String, String>();
                error.put("code", code);
                error.put("message", firstNonBlank(
                        text(result, "MESSAGE", null),
                        text(result, "message", null),
                        text(result, "resultMsg", null),
                        text(result, "resultMessage", null)));
                return Optional.of(objectMapper.valueToTree(error));
            }
        }
        return Optional.empty();
    }

    private static boolean successCode(String code) {
        var normalized = normalize(code);
        return normalized.isBlank()
                || "0".equals(normalized)
                || "00".equals(normalized)
                || "success".equals(normalized)
                || "info-000".equals(normalized);
    }

    private String detailId(String normalizedTarget, JsonNode item) {
        if (TARGET_LAW.equals(normalizedTarget)) {
            return firstNonBlank(text(item, K_LAW_ID, null), text(item, K_LAW_SERIAL_NO, null));
        }
        return firstNonBlank(text(item, K_ADMIN_SERIAL_NO, null), text(item, K_ADMIN_ID, null));
    }

    private String query(Map<String, String> parameters) {
        var merged = new LinkedHashMap<String, String>();
        merged.put("OC", properties.getOpenApi().getOc());
        merged.putAll(parameters);
        return merged.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
    }

    private String serviceUrl(String target, String id) {
        return baseUrl() + "/lawService.do?target=" + encode(target) + "&type=JSON&ID=" + encode(id);
    }

    private String baseUrl() {
        return properties.getOpenApi().getBaseUrl().replaceAll("/+$", "");
    }

    private String normalizeTarget(String target) {
        var normalized = normalize(target);
        if (normalized.isBlank()) {
            return TARGET_LAW;
        }
        if (!TARGET_LAW.equals(normalized) && !TARGET_ADMIN_RULE.equals(normalized)) {
            throw new IllegalArgumentException("Unsupported Law Open Data target: " + target);
        }
        return normalized;
    }

    private static JsonNode requiredObject(JsonNode node, String fieldName) {
        var value = node == null ? null : node.get(fieldName);
        if (value == null || !value.isObject()) {
            throw new IllegalStateException("Law Open Data response has no object field: " + fieldName);
        }
        return value;
    }

    private static JsonNode path(JsonNode node, String first, String second) {
        var firstNode = node == null ? null : node.get(first);
        return firstNode == null ? null : firstNode.get(second);
    }

    private static List<JsonNode> asList(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        if (node.isArray()) {
            var values = new ArrayList<JsonNode>();
            node.forEach(values::add);
            return values;
        }
        return List.of(node);
    }

    private static String text(JsonNode node, String fieldName, String defaultValue) {
        var value = node == null ? null : node.get(fieldName);
        if (value == null || value.isNull()) {
            return defaultValue;
        }
        if (value.isTextual() || value.isNumber() || value.isBoolean()) {
            var text = value.asText();
            return text == null || text.isBlank() ? defaultValue : text.trim();
        }
        if (value.isObject() && value.has(K_CONTENT)) {
            return text(value, K_CONTENT, defaultValue);
        }
        var flattened = flattenText(value);
        return flattened.isBlank() ? defaultValue : flattened;
    }

    private static String text(JsonNode node, String fieldName, String firstDefault, String secondDefault) {
        return firstNonBlank(text(node, fieldName, null), firstDefault, secondDefault);
    }

    private static String textObjectContent(JsonNode node, String fieldName, String defaultValue, String fallback) {
        var value = node == null ? null : node.get(fieldName);
        if (value == null || value.isNull()) {
            return firstNonBlank(defaultValue, fallback);
        }
        return firstNonBlank(text(value, K_CONTENT, null), defaultValue, fallback);
    }

    private static String flattenText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        if (node.isTextual() || node.isNumber() || node.isBoolean()) {
            return node.asText();
        }
        if (node.isArray()) {
            var lines = new ArrayList<String>();
            node.forEach(child -> {
                var text = flattenText(child);
                if (!text.isBlank()) {
                    lines.add(text);
                }
            });
            return String.join("\n", lines);
        }
        if (node.isObject()) {
            var lines = new ArrayList<String>();
            node.fields().forEachRemaining(entry -> {
                var text = flattenText(entry.getValue());
                if (!text.isBlank()) {
                    lines.add(text);
                }
            });
            return String.join("\n", lines);
        }
        return "";
    }

    private static LocalDate date(String yyyyMMdd) {
        if (yyyyMMdd == null || yyyyMMdd.isBlank() || yyyyMMdd.length() != 8) {
            return null;
        }
        return LocalDate.of(
                Integer.parseInt(yyyyMMdd.substring(0, 4)),
                Integer.parseInt(yyyyMMdd.substring(4, 6)),
                Integer.parseInt(yyyyMMdd.substring(6, 8)));
    }

    private static int intValue(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private static String firstNonBlank(String... values) {
        for (var value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String uniqueArticleKey(String rawKey, Map<String, Integer> seenKeys) {
        var baseKey = firstNonBlank(rawKey, "ARTICLE");
        var count = seenKeys.getOrDefault(baseKey, 0) + 1;
        seenKeys.put(baseKey, count);
        return count == 1 ? baseKey : baseKey + "_" + count;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeDisplayName(String value) {
        return Objects.toString(value, "").replaceAll("\\s+", "").trim();
    }

    private static String encode(String value) {
        return URLEncoder.encode(Objects.toString(value, ""), StandardCharsets.UTF_8);
    }
}
