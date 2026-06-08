package com.archdox.cloud.engine.application;

import com.archdox.cloud.engine.auth.application.EngineApiKeyManagementService;
import com.archdox.cloud.engine.auth.application.EngineApiPrincipal;
import com.archdox.cloud.engine.usage.application.EngineApiQuotaGuardService;
import com.archdox.cloud.engine.usage.application.EngineApiUsageService;
import com.archdox.cloud.legal.application.LegalCorpusReadService;
import com.archdox.cloud.legal.dto.LegalLawArticleResponse;
import com.archdox.cloud.legal.dto.LegalLawSearchResponse;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class EngineExternalLegalEvidenceService {
    private static final String OPERATION_SEARCH_LAW = "REST_SEARCH_LAW";
    private static final String OPERATION_GET_LAW_ARTICLE = "REST_GET_LAW_ARTICLE";

    private final LegalCorpusReadService legalCorpusReadService;
    private final EngineApiQuotaGuardService quotaGuardService;
    private final EngineApiUsageService usageService;

    public EngineExternalLegalEvidenceService(
            LegalCorpusReadService legalCorpusReadService,
            EngineApiQuotaGuardService quotaGuardService,
            EngineApiUsageService usageService
    ) {
        this.legalCorpusReadService = legalCorpusReadService;
        this.quotaGuardService = quotaGuardService;
        this.usageService = usageService;
    }

    public LegalLawSearchResponse search(
            EngineApiPrincipal principal,
            String query,
            String actCode,
            String actName,
            String articleNo,
            LocalDate effectiveDate,
            Integer limit
    ) {
        authorize(principal, OPERATION_SEARCH_LAW);
        var response = legalCorpusReadService.search(query, actCode, actName, articleNo, effectiveDate, limit);
        recordUsage(principal, OPERATION_SEARCH_LAW, null, metadata(
                "queryPresent", query != null && !query.isBlank(),
                "actCode", response.actCode(),
                "actName", response.actName(),
                "articleNo", response.articleNo(),
                "effectiveDate", response.effectiveDate(),
                "resultCount", response.count()));
        return response;
    }

    public LegalLawArticleResponse getArticle(
            EngineApiPrincipal principal,
            Long articleVersionId,
            Long articleId,
            String actCode,
            String articleNo,
            LocalDate effectiveDate
    ) {
        authorize(principal, OPERATION_GET_LAW_ARTICLE);
        var response = legalCorpusReadService.getArticle(articleVersionId, articleId, actCode, articleNo, effectiveDate);
        recordUsage(principal, OPERATION_GET_LAW_ARTICLE, response.referenceId(), metadata(
                "referenceId", response.referenceId(),
                "actCode", response.actCode(),
                "articleNo", response.articleNo(),
                "articleVersionId", response.articleVersionId(),
                "sourceCode", response.sourceCode()));
        return response;
    }

    private void authorize(EngineApiPrincipal principal, String operation) {
        principal.requireScope(EngineApiKeyManagementService.SCOPE_LEGAL_SEARCH);
        quotaGuardService.requireQuota(
                principal,
                EngineApiUsageService.CAPABILITY_LEGAL_SEARCH,
                operation,
                1);
    }

    private void recordUsage(
            EngineApiPrincipal principal,
            String operation,
            String resourceId,
            Map<String, Object> metadata
    ) {
        usageService.recordUsage(
                principal,
                EngineApiUsageService.CAPABILITY_LEGAL_SEARCH,
                operation,
                resourceId,
                EngineApiUsageService.STATUS_SUCCEEDED,
                1,
                metadata);
    }

    private Map<String, Object> metadata(Object... values) {
        var metadata = new LinkedHashMap<String, Object>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            var key = String.valueOf(values[i]);
            var value = values[i + 1];
            if (value != null) {
                metadata.put(key, value);
            }
        }
        return Map.copyOf(metadata);
    }
}
