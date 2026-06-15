package com.archdox.cloud.engine.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.archdox.cloud.engine.context.ArchDoxContextNormalizationService;
import com.archdox.cloud.engine.domain.EngineReviewSession;
import com.archdox.cloud.engine.dto.CreateEngineReviewSessionRequest;
import com.archdox.cloud.engine.dto.EngineContextFactRequest;
import com.archdox.cloud.engine.dto.SubmitEngineReviewDocumentRequest;
import com.archdox.cloud.engine.dto.SubmitEngineReviewFactsRequest;
import com.archdox.cloud.engine.infra.EngineReviewSessionRepository;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.legal.infra.LegalActRepository;
import com.archdox.cloud.legal.infra.LegalArticleRepository;
import com.archdox.cloud.legal.infra.LegalArticleVersionRepository;
import com.archdox.cloud.legal.infra.LegalDomainBindingRepository;
import com.archdox.cloud.legal.infra.LegalVersionRepository;
import com.archdox.cloud.supervisioncatalog.application.SupervisionDomainCatalogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class EngineReviewSessionServiceTest {
    private final EngineReviewSessionRepository repository = mock(EngineReviewSessionRepository.class);
    private final SupervisionDomainCatalogService catalogService =
            new SupervisionDomainCatalogService(new ObjectMapper());
    private final EngineReviewSessionService service = new EngineReviewSessionService(
            repository,
            new ArchDoxContextNormalizationService(),
            new EngineValidationService(
                    new EngineCatalogBindingReviewService(catalogService),
                    new EngineLegalReferenceBindingService(
                            mock(LegalDomainBindingRepository.class),
                            mock(LegalActRepository.class),
                            mock(LegalArticleRepository.class),
                            mock(LegalVersionRepository.class),
                            mock(LegalArticleVersionRepository.class)),
                    new EngineLegalRiskContextReviewService()),
            new ObjectMapper());
    private final UserPrincipal principal = new UserPrincipal(7L, "engine@test.co.kr");

    @Test
    void storesExternalReviewSessionInputsAndNormalizesContext() {
        var saved = new AtomicReference<EngineReviewSession>();
        when(repository.save(any(EngineReviewSession.class))).thenAnswer(invocation -> {
            var session = (EngineReviewSession) invocation.getArgument(0);
            saved.set(session);
            return session;
        });

        var created = service.create(
                new CreateEngineReviewSessionRequest("customer-project-1", "INSPECTION_REPORT_VALIDATION"),
                principal);
        when(repository.findByExternalSessionIdAndOwnerUserId(created.reviewSessionId(), principal.userId()))
                .thenAnswer(invocation -> Optional.of(saved.get()));

        service.submitDocument(
                created.reviewSessionId(),
                new SubmitEngineReviewDocumentRequest(
                        "daily supervision log",
                        "daily-log.txt",
                        "neighborhood living facility RC foundation concrete placement daily log"),
                principal);
        service.submitFacts(
                created.reviewSessionId(),
                new SubmitEngineReviewFactsRequest(List.of(
                        new EngineContextFactRequest(
                                "buildingUse",
                                null,
                                "neighborhood living facility",
                                "CUSTOMER_AGENT_EXTRACTED",
                                "document use line",
                                0.91d),
                        new EngineContextFactRequest(
                                "structureType",
                                null,
                                "RC",
                                "CUSTOMER_SYSTEM",
                                "customer PMS constMethod",
                                0.98d),
                        new EngineContextFactRequest(
                                "workType",
                                null,
                                "foundation concrete placement",
                                "USER_PROVIDED",
                                "user answer",
                                0.88d),
                        new EngineContextFactRequest(
                                "tradeCode",
                                null,
                                "REINFORCED_CONCRETE",
                                "CUSTOMER_SYSTEM",
                                "customer checklist row",
                                0.95d),
                        new EngineContextFactRequest(
                                "processCode",
                                null,
                                "REBAR_ASSEMBLY",
                                "CUSTOMER_SYSTEM",
                                "customer checklist row",
                                0.95d),
                        new EngineContextFactRequest(
                                "inspectionItemCode",
                                null,
                                "RC_REBAR_COUNT_DIAMETER_PITCH",
                                "CUSTOMER_SYSTEM",
                                "customer checklist row",
                                0.88d))),
                principal);

        var normalized = service.normalize(created.reviewSessionId(), principal);
        var validation = service.runValidation(created.reviewSessionId(), principal);
        var result = service.getResult(created.reviewSessionId(), principal);

        assertThat(normalized.normalizedContext()).containsKey("values");
        assertThat(normalized.normalizedContext().toString()).contains(
                "NEIGHBORHOOD_LIVING_FACILITY",
                "REINFORCED_CONCRETE",
                "FOUNDATION_CONCRETE_PLACEMENT");
        assertThat(validation.validationResult().status()).isEqualTo(ArchDoxEngineResultStatus.PASS);
        assertThat(validation.validationResult().executedActions()).containsExactly(
                "CATALOG_BINDING_REVIEW",
                "LEGAL_REFERENCE_BINDING",
                "LEGAL_RISK_CONTEXT_REVIEW",
                "RETURN_TYPED_RESULT");
        assertThat(validation.validationResult().metadata().toString())
                .contains(
                        "catalogBindings",
                        "RC_REBAR_COUNT_DIAMETER_PITCH",
                        "ARCHDOX_WORKER_SERVICE",
                        "LEGAL_RISK_CONTEXT_REVIEW");
        assertThat(result.resultReady()).isTrue();
        assertThat(result.reviewSessionId()).isEqualTo(created.reviewSessionId());
        assertThat(result.validationResult().status()).isEqualTo(ArchDoxEngineResultStatus.PASS);
        assertThat(result.validationResult().metadata().toString())
                .contains("catalogBindings", "ARCHDOX_WORKER_SERVICE");
    }

    @Test
    void resultEndpointReturnsPendingShapeBeforeValidation() {
        var saved = new AtomicReference<EngineReviewSession>();
        when(repository.save(any(EngineReviewSession.class))).thenAnswer(invocation -> {
            var session = (EngineReviewSession) invocation.getArgument(0);
            saved.set(session);
            return session;
        });

        var created = service.create(
                new CreateEngineReviewSessionRequest("customer-project-pending", "INSPECTION_REPORT_VALIDATION"),
                principal);
        when(repository.findByExternalSessionIdAndOwnerUserId(created.reviewSessionId(), principal.userId()))
                .thenAnswer(invocation -> Optional.of(saved.get()));

        var result = service.getResult(created.reviewSessionId(), principal);

        assertThat(result.resultReady()).isFalse();
        assertThat(result.status()).isEqualTo("CREATED");
        assertThat(result.validationResult().status()).isEqualTo(ArchDoxEngineResultStatus.PENDING);
        assertThat(result.validationResult().findings()).isEmpty();
    }

    @Test
    void returnsTypedFindingWhenExternalCatalogSelectionIsInvalid() {
        var saved = new AtomicReference<EngineReviewSession>();
        when(repository.save(any(EngineReviewSession.class))).thenAnswer(invocation -> {
            var session = (EngineReviewSession) invocation.getArgument(0);
            saved.set(session);
            return session;
        });

        var created = service.create(
                new CreateEngineReviewSessionRequest("customer-project-2", "INSPECTION_REPORT_VALIDATION"),
                principal);
        when(repository.findByExternalSessionIdAndOwnerUserId(created.reviewSessionId(), principal.userId()))
                .thenAnswer(invocation -> Optional.of(saved.get()));

        service.submitFacts(
                created.reviewSessionId(),
                new SubmitEngineReviewFactsRequest(List.of(
                        new EngineContextFactRequest(
                                "buildingUse",
                                null,
                                "neighborhood living facility",
                                "CUSTOMER_AGENT_EXTRACTED",
                                "document use line",
                                0.91d),
                        new EngineContextFactRequest(
                                "structureType",
                                null,
                                "RC",
                                "CUSTOMER_SYSTEM",
                                "customer PMS constMethod",
                                0.98d),
                        new EngineContextFactRequest(
                                "workType",
                                null,
                                "foundation concrete placement",
                                "USER_PROVIDED",
                                "user answer",
                                0.88d),
                        new EngineContextFactRequest(
                                "tradeCode",
                                null,
                                "REINFORCED_CONCRETE",
                                "CUSTOMER_SYSTEM",
                                "customer checklist row",
                                0.95d),
                        new EngineContextFactRequest(
                                "processCode",
                                null,
                                "REBAR_ASSEMBLY",
                                "CUSTOMER_SYSTEM",
                                "customer checklist row",
                                0.95d),
                        new EngineContextFactRequest(
                                "inspectionItemCode",
                                null,
                                "REBAR_SPACING",
                                "CUSTOMER_SYSTEM",
                                "legacy customer row",
                                0.95d))),
                principal);

        var validation = service.runValidation(created.reviewSessionId(), principal);

        assertThat(validation.validationResult().status()).isEqualTo(ArchDoxEngineResultStatus.FAIL);
        assertThat(validation.validationResult().generationAllowed()).isFalse();
        assertThat(validation.validationResult().findings())
                .extracting(finding -> finding.code())
                .contains("CATALOG_SELECTION_INVALID");
        assertThat(validation.validationResult().nextActions())
                .extracting(action -> action.code())
                .contains("FIX_CATALOG_SELECTION", "RUN_VALIDATION_AGAIN");
    }

    @Test
    void normalizesCatalogSelectionsFactForMultiItemDocumentReview() {
        var saved = new AtomicReference<EngineReviewSession>();
        when(repository.save(any(EngineReviewSession.class))).thenAnswer(invocation -> {
            var session = (EngineReviewSession) invocation.getArgument(0);
            saved.set(session);
            return session;
        });

        var created = service.create(
                new CreateEngineReviewSessionRequest("customer-project-multi", "INSPECTION_DOCUMENT_REVIEW"),
                principal);
        when(repository.findByExternalSessionIdAndOwnerUserId(created.reviewSessionId(), principal.userId()))
                .thenAnswer(invocation -> Optional.of(saved.get()));

        service.submitDocument(
                created.reviewSessionId(),
                new SubmitEngineReviewDocumentRequest(
                        "CONSTRUCTION_DAILY_SUPERVISION_LOG",
                        "daily-log.txt",
                        "2021년 1월 7일 기초 층 토공사 터파기 깊이 확인"),
                principal);
        service.submitFacts(
                created.reviewSessionId(),
                new SubmitEngineReviewFactsRequest(List.of(
                        new EngineContextFactRequest("buildingUse", null, "residential", "CUSTOMER_SYSTEM", "test", 0.9d),
                        new EngineContextFactRequest("structureType", null, "RC", "CUSTOMER_SYSTEM", "test", 0.9d),
                        new EngineContextFactRequest("workType", null, "CONSTRUCTION_SUPERVISION", "CUSTOMER_SYSTEM", "test", 0.9d),
                        new EngineContextFactRequest(
                                "catalogSelections",
                                null,
                                """
                                        [
                                          {"tradeCode":"TEMPORARY_WORKS","processCode":"GENERAL","inspectionItemCode":"TEMP_SITE_CONDITION","location":"extracted[0]"},
                                          {"tradeCode":"EARTH_WORKS","processCode":"GENERAL","inspectionItemCode":"EARTH_EXCAVATION_DEPTH","location":"extracted[1]"}
                                        ]
                                        """,
                                "CUSTOMER_AGENT_EXTRACTED",
                                "daily log extraction",
                                0.9d))),
                principal);

        var normalized = service.normalize(created.reviewSessionId(), principal);
        var validation = service.runValidation(created.reviewSessionId(), principal);

        assertThat(normalized.normalizedContext().get("catalogSelections").toString())
                .contains("TEMP_SITE_CONDITION", "EARTH_EXCAVATION_DEPTH");
        assertThat(validation.validationResult().status()).isEqualTo(ArchDoxEngineResultStatus.PASS);
        assertThat(validation.validationResult().metadata().toString())
                .contains("catalogSelectionCount=2", "catalogBindingCount=2");
    }
}
