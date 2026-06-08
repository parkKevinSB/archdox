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
import com.archdox.cloud.legal.domain.LegalDigestAiDraft;
import com.archdox.cloud.legal.domain.LegalDigestAiDraftStatus;
import com.archdox.cloud.legal.dto.LegalChangeDigestResponse;
import com.archdox.cloud.legal.flow.LegalSyncFlowFactory;
import com.archdox.cloud.legal.flow.LegalSyncWorker;
import com.archdox.cloud.legal.infra.LegalActRepository;
import com.archdox.cloud.legal.infra.LegalArticleDiffRepository;
import com.archdox.cloud.legal.infra.LegalChangeDigestRepository;
import com.archdox.cloud.legal.infra.LegalChangeSetRepository;
import com.archdox.cloud.legal.infra.LegalDigestAiDraftRepository;
import com.archdox.cloud.legal.infra.LegalSyncRunRepository;
import com.archdox.cloud.operation.application.OperationEventService;
import com.archdox.cloud.platformadmin.application.PlatformAdminService;
import com.archdox.cloud.worker.ArchDoxWorkerServiceWorker;
import com.archdox.worker.application.ArchDoxWorkerActionExecutor;
import com.archdox.worker.application.ArchDoxWorkerActionRegistry;
import com.archdox.worker.application.ArchDoxWorkerExecutionContext;
import com.archdox.worker.application.ArchDoxWorkerPolicyGate;
import com.archdox.worker.application.ArchDoxWorkerTraceSink;
import com.archdox.worker.domain.ArchDoxWorkerActionResult;
import com.archdox.worker.domain.ArchDoxWorkerActionType;
import com.archdox.worker.flow.ArchDoxWorkerExecutionFlowFactory;
import io.github.parkkevinsb.flower.core.engine.Engine;
import io.github.parkkevinsb.flower.core.flow.Flow;
import io.github.parkkevinsb.flower.core.worker.Worker;
import java.lang.reflect.Field;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
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
    private final ArchDoxWorkerExecutionFlowFactory workerFlowFactory = mock(ArchDoxWorkerExecutionFlowFactory.class);
    private final ArchDoxWorkerServiceWorker workerServiceWorker = mock(ArchDoxWorkerServiceWorker.class);
    private final LegalDigestAiProperties legalDigestAiProperties = new LegalDigestAiProperties();
    private final LegalDigestAiDraftRepository aiDraftRepository = mock(LegalDigestAiDraftRepository.class);
    private final OperationEventService operationEventService = mock(OperationEventService.class);
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
            changeDigestService,
            workerFlowFactory,
            workerServiceWorker,
            legalDigestAiProperties,
            aiDraftRepository,
            operationEventService);

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

    @Test
    void generateDigestAiDraftRunsArchDoxWorkerDryRunAndReturnsDraft() throws Exception {
        var now = OffsetDateTime.parse("2026-06-05T09:00:00+09:00");
        var principal = new UserPrincipal(3L, "vvzerg@test.co.kr");
        var digest = digest(10L, LegalChangeDigestSource.DETERMINISTIC, now);
        setId(digest, 1L);
        when(changeDigestRepository.findById(1L)).thenReturn(Optional.of(digest));
        var properties = new LegalDigestAiProperties();
        properties.setTimeoutSeconds(10);
        when(aiDraftRepository.save(any(LegalDigestAiDraft.class))).thenAnswer(invocation -> {
            var draft = invocation.<LegalDigestAiDraft>getArgument(0);
            setId(draft, 99L);
            return draft;
        });
        var draftService = new LegalPlatformAdminService(
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
                changeDigestService,
                new ArchDoxWorkerExecutionFlowFactory(
                        new ArchDoxWorkerActionRegistry(List.of(aiDraftExecutor())),
                        ArchDoxWorkerPolicyGate.allowAll(),
                        ArchDoxWorkerTraceSink.noop()),
                new DirectWorker(),
                properties,
                aiDraftRepository,
                operationEventService);

        var result = draftService.generateDigestAiDraft(principal, 1L);

        assertThat(result.id()).isEqualTo(99L);
        assertThat(result.status()).isEqualTo(LegalDigestAiDraftStatus.GENERATED);
        assertThat(result.digestId()).isEqualTo(1L);
        assertThat(result.changeSetId()).isEqualTo(10L);
        assertThat(result.dryRun()).isTrue();
        assertThat(result.publicationApplied()).isFalse();
        assertThat(result.corpusMutated()).isFalse();
        assertThat(result.digestMutated()).isFalse();
        assertThat(result.title()).isEqualTo("AI draft title");
        assertThat(result.keyArticles()).containsExactly("0025001");
        verify(platformAdminService).requirePlatformAdmin(principal);
        verify(aiDraftRepository).save(any(LegalDigestAiDraft.class));
    }

    @Test
    void applyDigestAiDraftUpdatesPublishedDigestOnlyAfterAdminApproval() throws Exception {
        var now = OffsetDateTime.parse("2026-06-05T09:00:00+09:00");
        var principal = new UserPrincipal(3L, "vvzerg@test.co.kr");
        var digest = digest(10L, LegalChangeDigestSource.DETERMINISTIC, now);
        setId(digest, 1L);
        var draft = aiDraft(1L, 10L, 3L, now);
        setId(draft, 7L);
        when(changeDigestRepository.findById(1L)).thenReturn(Optional.of(digest));
        when(aiDraftRepository.findById(7L)).thenReturn(Optional.of(draft));

        var result = service.applyDigestAiDraft(principal, 1L, 7L);

        assertThat(result.id()).isEqualTo(7L);
        assertThat(result.status()).isEqualTo(LegalDigestAiDraftStatus.APPLIED);
        assertThat(result.appliedByUserId()).isEqualTo(3L);
        assertThat(digest.source()).isEqualTo(LegalChangeDigestSource.AI);
        assertThat(digest.title()).isEqualTo("AI draft title");
        assertThat(digest.summary()).isEqualTo("AI draft summary");
        assertThat(digest.aiHarnessRunId()).isEqualTo("ai-run-1");
        verify(platformAdminService).requirePlatformAdmin(principal);
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

    private LegalDigestAiDraft aiDraft(Long digestId, Long changeSetId, Long generatedByUserId, OffsetDateTime now) {
        return new LegalDigestAiDraft(
                digestId,
                changeSetId,
                java.util.UUID.randomUUID(),
                "SUCCEEDED",
                null,
                "ai-run-1",
                "NEEDS_HUMAN_REVIEW",
                "AI draft title",
                "AI draft summary",
                "AI impact",
                "MEDIUM",
                List.of("CONSTRUCTION_DAILY_SUPERVISION_LOG"),
                List.of("CONSTRUCTION_SUPERVISION_LEGAL_CONTEXT"),
                List.of("0025001"),
                "Admin review required",
                false,
                false,
                false,
                generatedByUserId,
                now);
    }

    private void setId(Object target, Long id) throws Exception {
        Field field = target.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(target, id);
    }

    private ArchDoxWorkerActionExecutor aiDraftExecutor() {
        return new ArchDoxWorkerActionExecutor() {
            @Override
            public ArchDoxWorkerActionType actionType() {
                return ArchDoxWorkerActionType.ENRICH_LEGAL_CHANGE_DIGEST;
            }

            @Override
            public ArchDoxWorkerActionResult execute(ArchDoxWorkerExecutionContext context) {
                assertThat(context.action().payload())
                        .containsEntry("digestId", 1L)
                        .containsEntry("changeSetId", 10L)
                        .containsEntry("dryRun", true);
                var output = new LinkedHashMap<String, Object>();
                output.put("dryRun", true);
                output.put("publicationApplied", false);
                output.put("corpusMutated", false);
                output.put("digestMutated", false);
                output.put("workerRequestId", context.request().requestId().toString());
                output.put("digestId", 1L);
                output.put("changeSetId", 10L);
                output.put("aiHarnessRunId", "ai-run-1");
                output.put("digestDraftStatus", "NEEDS_HUMAN_REVIEW");
                output.put("title", "AI draft title");
                output.put("summary", "AI draft summary");
                output.put("impactSummary", "AI impact");
                output.put("confidence", "MEDIUM");
                output.put("affectedReportTypes", List.of("CONSTRUCTION_DAILY_SUPERVISION_LOG"));
                output.put("affectedCatalogItems", List.of("CONSTRUCTION_SUPERVISION_LEGAL_CONTEXT"));
                output.put("keyArticles", List.of("0025001"));
                output.put("reviewNotes", "Admin review required");
                return ArchDoxWorkerActionResult.succeeded(output);
            }
        };
    }

    private static final class DirectWorker extends ArchDoxWorkerServiceWorker {
        private DirectWorker() {
            super(null);
        }

        @Override
        public boolean submitAndAwait(Flow flow, Duration timeout) {
            var worker = Worker.builder("archdox-worker-test").build();
            Engine.builder()
                    .worker(worker)
                    .build()
                    .attach();
            worker.submit(flow);
            for (var i = 0; i < 10; i++) {
                worker.tickOnce();
                if (flow.state().isTerminal()) {
                    return true;
                }
            }
            return false;
        }
    }
}
