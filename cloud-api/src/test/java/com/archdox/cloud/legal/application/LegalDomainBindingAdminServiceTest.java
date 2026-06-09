package com.archdox.cloud.legal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.legal.domain.LegalAct;
import com.archdox.cloud.legal.domain.LegalArticle;
import com.archdox.cloud.legal.domain.LegalDomainBinding;
import com.archdox.cloud.legal.dto.CreateLegalDomainBindingRequest;
import com.archdox.cloud.legal.dto.UpdateLegalDomainBindingRequest;
import com.archdox.cloud.legal.infra.LegalActRepository;
import com.archdox.cloud.legal.infra.LegalArticleRepository;
import com.archdox.cloud.legal.infra.LegalDomainBindingRepository;
import com.archdox.cloud.operation.application.OperationEventService;
import com.archdox.cloud.operation.domain.OperationEventSeverity;
import com.archdox.cloud.platformadmin.application.PlatformAdminService;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class LegalDomainBindingAdminServiceTest {
    private final PlatformAdminService platformAdminService = mock(PlatformAdminService.class);
    private final LegalDomainBindingRepository bindingRepository = mock(LegalDomainBindingRepository.class);
    private final LegalActRepository actRepository = mock(LegalActRepository.class);
    private final LegalArticleRepository articleRepository = mock(LegalArticleRepository.class);
    private final OperationEventService operationEventService = mock(OperationEventService.class);
    private final LegalCorpusReadService legalCorpusReadService = mock(LegalCorpusReadService.class);
    private final LegalDomainBindingAdminService service = new LegalDomainBindingAdminService(
            platformAdminService,
            bindingRepository,
            actRepository,
            articleRepository,
            operationEventService,
            legalCorpusReadService);

    @Test
    void createBindingGeneratesCatalogBindingKeyAndRecordsAudit() {
        var principal = new UserPrincipal(7L, "platform@test.co.kr");
        var now = OffsetDateTime.parse("2026-06-09T10:00:00+09:00");
        var act = act(10L, now);
        var article = article(20L, 10L, now);
        var request = new CreateLegalDomainBindingRequest(
                "catalog_item",
                null,
                10L,
                20L,
                "CONSTRUCTION_DAILY_SUPERVISION_LOG",
                "CONSTRUCTION_SUPERVISION_LEGAL_CONTEXT",
                1,
                "FOUNDATION_REBAR",
                "primary",
                null,
                null,
                null,
                "Foundation rebar evidence context",
                Map.of("tradeCode", "RC"));

        when(actRepository.findById(10L)).thenReturn(Optional.of(act));
        when(articleRepository.findById(20L)).thenReturn(Optional.of(article));
        when(bindingRepository.save(any(LegalDomainBinding.class))).thenAnswer(invocation -> {
            var binding = invocation.getArgument(0, LegalDomainBinding.class);
            ReflectionTestUtils.setField(binding, "id", 30L);
            return binding;
        });

        var response = service.createBinding(principal, request);

        assertThat(response.id()).isEqualTo(30L);
        assertThat(response.bindingScope()).isEqualTo("CATALOG_ITEM");
        assertThat(response.bindingKey()).isEqualTo("CONSTRUCTION_SUPERVISION_LEGAL_CONTEXT:v1:FOUNDATION_REBAR");
        assertThat(response.relevance()).isEqualTo("PRIMARY");
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.actCode()).isEqualTo("BUILDING_ACT");
        assertThat(response.articleNo()).isEqualTo("25");
        verify(platformAdminService).requirePlatformAdmin(principal);
        verify(operationEventService).record(
                eq(null),
                eq(OperationEventSeverity.INFO),
                eq("LEGAL_DOMAIN_BINDING_CREATED"),
                eq("legal-domain-binding"),
                eq("binding:30"),
                eq("LEGAL_DOMAIN_BINDING"),
                eq(30L),
                eq(7L),
                eq(null),
                eq("Legal domain binding was created."),
                any());
    }

    @Test
    void createBindingRejectsArticleFromDifferentAct() {
        var principal = new UserPrincipal(7L, "platform@test.co.kr");
        var now = OffsetDateTime.parse("2026-06-09T10:00:00+09:00");
        var request = new CreateLegalDomainBindingRequest(
                "CATALOG_ITEM",
                "binding-key",
                10L,
                20L,
                null,
                null,
                null,
                null,
                "REFERENCE",
                "ACTIVE",
                null,
                null,
                null,
                Map.of());

        when(actRepository.findById(10L)).thenReturn(Optional.of(act(10L, now)));
        when(articleRepository.findById(20L)).thenReturn(Optional.of(article(20L, 99L, now)));

        assertThatThrownBy(() -> service.createBinding(principal, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Legal article does not belong");
        verify(platformAdminService).requirePlatformAdmin(principal);
    }

    @Test
    void updateBindingChangesDomainMappingAndRecordsAudit() {
        var principal = new UserPrincipal(7L, "platform@test.co.kr");
        var now = OffsetDateTime.parse("2026-06-09T10:00:00+09:00");
        var binding = new LegalDomainBinding(
                "REPORT_TYPE",
                "CONSTRUCTION_DAILY_SUPERVISION_LOG",
                10L,
                20L,
                "CONSTRUCTION_DAILY_SUPERVISION_LOG",
                null,
                null,
                null,
                "REFERENCE",
                "ACTIVE",
                null,
                null,
                "Initial mapping",
                Map.of(),
                now);
        ReflectionTestUtils.setField(binding, "id", 30L);
        var request = new UpdateLegalDomainBindingRequest(
                "CATALOG_ITEM",
                null,
                10L,
                20L,
                "CONSTRUCTION_DAILY_SUPERVISION_LOG",
                "CONSTRUCTION_SUPERVISION_CHECKLIST_2020_12_24",
                2,
                "RC_REBAR_COUNT_DIAMETER_PITCH",
                "PRIMARY",
                "ACTIVE",
                null,
                null,
                "Use this as the primary preflight legal basis.",
                Map.of("source", "admin"));

        when(bindingRepository.findById(30L)).thenReturn(Optional.of(binding));
        when(actRepository.findById(10L)).thenReturn(Optional.of(act(10L, now)));
        when(articleRepository.findById(20L)).thenReturn(Optional.of(article(20L, 10L, now)));

        var response = service.updateBinding(principal, 30L, request);

        assertThat(response.bindingScope()).isEqualTo("CATALOG_ITEM");
        assertThat(response.bindingKey()).isEqualTo("CONSTRUCTION_SUPERVISION_CHECKLIST_2020_12_24:v2:RC_REBAR_COUNT_DIAMETER_PITCH");
        assertThat(response.catalogCode()).isEqualTo("CONSTRUCTION_SUPERVISION_CHECKLIST_2020_12_24");
        assertThat(response.catalogVersion()).isEqualTo(2);
        assertThat(response.checklistItemCode()).isEqualTo("RC_REBAR_COUNT_DIAMETER_PITCH");
        assertThat(response.relevance()).isEqualTo("PRIMARY");
        assertThat(response.notes()).isEqualTo("Use this as the primary preflight legal basis.");
        verify(platformAdminService).requirePlatformAdmin(principal);
        verify(operationEventService).record(
                eq(null),
                eq(OperationEventSeverity.INFO),
                eq("LEGAL_DOMAIN_BINDING_UPDATED"),
                eq("legal-domain-binding"),
                eq("binding:30"),
                eq("LEGAL_DOMAIN_BINDING"),
                eq(30L),
                eq(7L),
                eq(null),
                eq("Legal domain binding was updated."),
                any());
    }

    private LegalAct act(Long id, OffsetDateTime now) {
        var act = new LegalAct(1L, "BUILDING_ACT", "Building Act", "LAW", "KR", "001823", now);
        ReflectionTestUtils.setField(act, "id", id);
        return act;
    }

    private LegalArticle article(Long id, Long actId, OffsetDateTime now) {
        var article = new LegalArticle(actId, "0025001", "25", "Construction supervision", null, 25, now);
        ReflectionTestUtils.setField(article, "id", id);
        return article;
    }
}
