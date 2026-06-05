package com.archdox.cloud.legal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.legal.domain.LegalAct;
import com.archdox.cloud.legal.domain.LegalChangeDigest;
import com.archdox.cloud.legal.domain.LegalChangeDigestSource;
import com.archdox.cloud.legal.domain.LegalChangeDigestStatus;
import com.archdox.cloud.legal.domain.LegalChangeSet;
import com.archdox.cloud.legal.dto.LegalChangeDigestResponse;
import com.archdox.cloud.legal.flow.LegalSyncFlowFactory;
import com.archdox.cloud.legal.flow.LegalSyncWorker;
import com.archdox.cloud.legal.infra.LegalActRepository;
import com.archdox.cloud.legal.infra.LegalArticleDiffRepository;
import com.archdox.cloud.legal.infra.LegalChangeDigestRepository;
import com.archdox.cloud.legal.infra.LegalChangeSetRepository;
import com.archdox.cloud.legal.infra.LegalSyncRunRepository;
import com.archdox.cloud.platformadmin.application.PlatformAdminService;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

class LegalPlatformAdminServiceTest {
    private final PlatformAdminService platformAdminService = mock(PlatformAdminService.class);
    private final LegalCorpusSyncService syncService = mock(LegalCorpusSyncService.class);
    private final LegalSyncFlowFactory flowFactory = mock(LegalSyncFlowFactory.class);
    private final LegalSyncWorker worker = mock(LegalSyncWorker.class);
    private final LegalSyncRunRepository syncRunRepository = mock(LegalSyncRunRepository.class);
    private final LegalActRepository actRepository = mock(LegalActRepository.class);
    private final LegalChangeSetRepository changeSetRepository = mock(LegalChangeSetRepository.class);
    private final LegalArticleDiffRepository articleDiffRepository = mock(LegalArticleDiffRepository.class);
    private final LegalChangeDigestRepository changeDigestRepository = mock(LegalChangeDigestRepository.class);
    private final LegalUpdateReadService updateReadService = mock(LegalUpdateReadService.class);
    private final LegalSyncProperties legalSyncProperties = new LegalSyncProperties();
    private final LegalChangeDigestService changeDigestService = mock(LegalChangeDigestService.class);
    private final LegalPlatformAdminService service = new LegalPlatformAdminService(
            platformAdminService,
            syncService,
            flowFactory,
            worker,
            syncRunRepository,
            actRepository,
            changeSetRepository,
            articleDiffRepository,
            changeDigestRepository,
            updateReadService,
            legalSyncProperties,
            changeDigestService);

    @Test
    void changeDigestsExcludeFakeSource() {
        var now = OffsetDateTime.parse("2026-06-05T09:00:00+09:00");
        var principal = new UserPrincipal(3L, "vvzerg@test.co.kr");
        var digest = digest(10L, LegalChangeDigestSource.DETERMINISTIC, now);
        var response = new LegalChangeDigestResponse(
                1L,
                10L,
                LegalChangeDigestStatus.PUBLISHED,
                LegalChangeDigestSource.DETERMINISTIC,
                "건축법 조문 변경: 신설 1건",
                "summary",
                "impact",
                List.of(),
                List.of(),
                null,
                LocalDate.of(2026, 7, 1),
                now,
                now,
                now,
                now,
                List.of());
        when(changeDigestRepository.findAllExcludingSourceCode(
                eq(FakeLegalSourceClient.DEFAULT_SOURCE_CODE),
                any(Pageable.class)))
                .thenReturn(List.of(digest));
        when(updateReadService.toResponse(digest)).thenReturn(response);

        var result = service.changeDigests(principal, 50);

        assertThat(result).containsExactly(response);
        verify(platformAdminService).requirePlatformAdmin(principal);
        verify(changeDigestRepository).findAllExcludingSourceCode(
                eq(FakeLegalSourceClient.DEFAULT_SOURCE_CODE),
                any(Pageable.class));
    }

    @Test
    void refreshDeterministicDigestsSkipsAiAndMissingActs() throws Exception {
        var now = OffsetDateTime.parse("2026-06-05T09:00:00+09:00");
        var principal = new UserPrincipal(3L, "vvzerg@test.co.kr");
        var deterministicChangeSet = changeSet(10L, 1L, now);
        var aiChangeSet = changeSet(11L, 1L, now);
        var missingActChangeSet = changeSet(12L, 99L, now);
        var act = new LegalAct(1L, "BUILDING_ACT", "건축법", "LAW", "KR", "001823", now);
        var deterministicDigest = digest(10L, LegalChangeDigestSource.DETERMINISTIC, now);
        var aiDigest = digest(11L, LegalChangeDigestSource.AI, now);

        when(changeSetRepository.findAllByOrderByDetectedAtDescIdDesc(any(Pageable.class)))
                .thenReturn(List.of(deterministicChangeSet, aiChangeSet, missingActChangeSet));
        when(actRepository.findById(1L)).thenReturn(Optional.of(act));
        when(actRepository.findById(99L)).thenReturn(Optional.empty());
        when(changeDigestRepository.findByChangeSetId(10L)).thenReturn(Optional.of(deterministicDigest));
        when(changeDigestRepository.findByChangeSetId(11L)).thenReturn(Optional.of(aiDigest));
        when(articleDiffRepository.findByChangeSetIdOrderByIdAsc(10L)).thenReturn(List.of());
        when(changeDigestService.ensureDeterministicDigest(eq(deterministicChangeSet), eq(act), eq(List.of()), any()))
                .thenReturn(deterministicDigest);

        var result = service.refreshDeterministicDigests(principal, 50);

        assertThat(result.inspectedChangeSets()).isEqualTo(3);
        assertThat(result.createdDigests()).isZero();
        assertThat(result.refreshedDigests()).isEqualTo(1);
        assertThat(result.skippedAiDigests()).isEqualTo(1);
        assertThat(result.skippedMissingActs()).isEqualTo(1);
        verify(platformAdminService).requirePlatformAdmin(principal);
        verify(changeDigestService).ensureDeterministicDigest(eq(deterministicChangeSet), eq(act), eq(List.of()), any());
        verifyNoInteractions(syncService, flowFactory, worker, syncRunRepository, updateReadService);
    }

    private LegalChangeSet changeSet(Long id, Long actId, OffsetDateTime now) throws Exception {
        var changeSet = new LegalChangeSet(
                actId,
                1L,
                null,
                2L,
                LocalDate.of(2026, 7, 1),
                "source summary",
                Map.of(),
                now);
        setId(changeSet, id);
        return changeSet;
    }

    private LegalChangeDigest digest(Long changeSetId, LegalChangeDigestSource source, OffsetDateTime now) {
        return new LegalChangeDigest(
                changeSetId,
                LegalChangeDigestStatus.PUBLISHED,
                source,
                "title",
                "summary",
                "impact",
                List.of(),
                List.of(),
                source == LegalChangeDigestSource.AI ? "ai-run" : null,
                LocalDate.of(2026, 7, 1),
                now,
                now,
                now);
    }

    private void setId(Object target, Long id) throws Exception {
        Field field = target.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(target, id);
    }
}
