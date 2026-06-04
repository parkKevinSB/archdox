package com.archdox.cloud.workerchat.application;

import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.document.application.DocumentGenerationRequestService;
import com.archdox.cloud.document.domain.DocumentWorkerType;
import com.archdox.cloud.document.dto.CreateDocumentJobRequest;
import com.archdox.cloud.document.infra.DocumentJobRepository;
import com.archdox.cloud.global.api.NotFoundException;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.inspection.application.InspectionReportService;
import com.archdox.cloud.inspection.application.ReportWorkflowDefinitionService;
import com.archdox.cloud.inspection.domain.InspectionReport;
import com.archdox.cloud.inspection.dto.CreateInspectionReportRequest;
import com.archdox.cloud.inspection.dto.ReportWorkflowFieldResponse;
import com.archdox.cloud.inspection.dto.ReportWorkflowStepResponse;
import com.archdox.cloud.inspection.dto.SaveInspectionStepRequest;
import com.archdox.cloud.inspection.infra.InspectionReportRepository;
import com.archdox.cloud.inspection.infra.InspectionReportStepRepository;
import com.archdox.cloud.office.application.OfficeContext;
import com.archdox.cloud.office.application.OfficePermissionService;
import com.archdox.cloud.operation.application.OperationEventService;
import com.archdox.cloud.operation.domain.OperationEventSeverity;
import com.archdox.cloud.project.infra.ProjectRepository;
import com.archdox.cloud.reportai.application.ReportPreflightReviewService;
import com.archdox.cloud.reportai.domain.ReportPreflightFindingResolutionStatus;
import com.archdox.cloud.reportai.domain.ReportPreflightReviewStatus;
import com.archdox.cloud.reportai.flow.ReportPreflightReviewWorker;
import com.archdox.cloud.reportai.infra.ReportPreflightReviewFindingRepository;
import com.archdox.cloud.reportai.infra.ReportPreflightReviewRunRepository;
import com.archdox.cloud.site.application.SiteService;
import com.archdox.cloud.site.domain.Site;
import com.archdox.cloud.site.dto.CreateSiteRequest;
import com.archdox.cloud.site.infra.SiteRepository;
import com.archdox.cloud.worker.ArchDoxWorkerServiceWorker;
import com.archdox.cloud.workerchat.domain.ArchDoxWorkerChatMessage;
import com.archdox.cloud.workerchat.domain.ArchDoxWorkerChatMessageRole;
import com.archdox.cloud.workerchat.domain.ArchDoxWorkerChatMessageStatus;
import com.archdox.cloud.workerchat.domain.ArchDoxWorkerChatSession;
import com.archdox.cloud.workerchat.domain.ArchDoxWorkerChatSessionStatus;
import com.archdox.cloud.workerchat.domain.ArchDoxWorkerChatStage;
import com.archdox.cloud.workerchat.dto.ArchDoxWorkerChatMessageResponse;
import com.archdox.cloud.workerchat.dto.ArchDoxWorkerChatSessionResponse;
import com.archdox.cloud.workerchat.dto.SendArchDoxWorkerChatMessageRequest;
import com.archdox.cloud.workerchat.infra.ArchDoxWorkerChatMessageRepository;
import com.archdox.cloud.workerchat.infra.ArchDoxWorkerChatSessionRepository;
import com.archdox.worker.domain.ArchDoxWorkerAction;
import com.archdox.worker.domain.ArchDoxWorkerActionOrigin;
import com.archdox.worker.domain.ArchDoxWorkerActionType;
import com.archdox.worker.domain.ArchDoxWorkerRequest;
import com.archdox.worker.domain.ArchDoxWorkerRequestContext;
import com.archdox.worker.domain.ArchDoxWorkerRequestSource;
import com.archdox.worker.flow.ArchDoxWorkerExecutionFlowFactory;
import com.archdox.workerai.ConversationPlannerActionOption;
import com.archdox.workerai.ConversationPlannerDecision;
import com.archdox.workerai.ConversationPlannerEntityOption;
import com.archdox.workerai.ConversationPlannerFieldOption;
import com.archdox.workerai.ConversationPlannerInput;
import com.archdox.workerai.ConversationPlannerResult;
import com.archdox.workerai.ConversationPlannerWorkflowStepOption;
import com.archdox.document.OutputFormat;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class ArchDoxWorkerChatService {
    private static final int MAX_MESSAGE_LENGTH = 2000;
    private static final String DEFAULT_REPORT_TYPE = "CONSTRUCTION_DAILY_SUPERVISION_LOG";

    private final ArchDoxWorkerChatSessionRepository sessionRepository;
    private final ArchDoxWorkerChatMessageRepository messageRepository;
    private final ProjectRepository projectRepository;
    private final SiteRepository siteRepository;
    private final SiteService siteService;
    private final InspectionReportRepository reportRepository;
    private final InspectionReportStepRepository stepRepository;
    private final InspectionReportService inspectionReportService;
    private final ReportWorkflowDefinitionService workflowDefinitionService;
    private final ReportPreflightReviewService preflightReviewService;
    private final ReportPreflightReviewWorker preflightReviewWorker;
    private final ReportPreflightReviewRunRepository preflightRunRepository;
    private final ReportPreflightReviewFindingRepository preflightFindingRepository;
    private final DocumentGenerationRequestService documentGenerationRequestService;
    private final DocumentJobRepository documentJobRepository;
    private final OfficePermissionService permissionService;
    private final ObjectProvider<ArchDoxWorkerExecutionFlowFactory> flowFactoryProvider;
    private final ObjectProvider<ArchDoxWorkerServiceWorker> workerProvider;
    private final OperationEventService operationEventService;
    private final WorkerConversationPlannerService conversationPlannerService;

    public ArchDoxWorkerChatService(
            ArchDoxWorkerChatSessionRepository sessionRepository,
            ArchDoxWorkerChatMessageRepository messageRepository,
            ProjectRepository projectRepository,
            SiteRepository siteRepository,
            SiteService siteService,
            InspectionReportRepository reportRepository,
            InspectionReportStepRepository stepRepository,
            InspectionReportService inspectionReportService,
            ReportWorkflowDefinitionService workflowDefinitionService,
            ReportPreflightReviewService preflightReviewService,
            ReportPreflightReviewWorker preflightReviewWorker,
            ReportPreflightReviewRunRepository preflightRunRepository,
            ReportPreflightReviewFindingRepository preflightFindingRepository,
            DocumentGenerationRequestService documentGenerationRequestService,
            DocumentJobRepository documentJobRepository,
            OfficePermissionService permissionService,
            ObjectProvider<ArchDoxWorkerExecutionFlowFactory> flowFactoryProvider,
            ObjectProvider<ArchDoxWorkerServiceWorker> workerProvider,
            OperationEventService operationEventService,
            WorkerConversationPlannerService conversationPlannerService
    ) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.projectRepository = projectRepository;
        this.siteRepository = siteRepository;
        this.siteService = siteService;
        this.reportRepository = reportRepository;
        this.stepRepository = stepRepository;
        this.inspectionReportService = inspectionReportService;
        this.workflowDefinitionService = workflowDefinitionService;
        this.preflightReviewService = preflightReviewService;
        this.preflightReviewWorker = preflightReviewWorker;
        this.preflightRunRepository = preflightRunRepository;
        this.preflightFindingRepository = preflightFindingRepository;
        this.documentGenerationRequestService = documentGenerationRequestService;
        this.documentJobRepository = documentJobRepository;
        this.permissionService = permissionService;
        this.flowFactoryProvider = flowFactoryProvider;
        this.workerProvider = workerProvider;
        this.operationEventService = operationEventService;
        this.conversationPlannerService = conversationPlannerService;
    }

    @Transactional
    public ArchDoxWorkerChatSessionResponse open(Long projectId, UserPrincipal principal) {
        var officeId = OfficeContext.requireCurrentOfficeId();
        validateProjectAccess(officeId, projectId, principal.userId());
        var session = activeSession(officeId, projectId, principal.userId());
        ensureInitialAssistantMessage(session);
        return toResponse(session);
    }

    @Transactional
    public ArchDoxWorkerChatSessionResponse send(
            Long projectId,
            SendArchDoxWorkerChatMessageRequest request,
            UserPrincipal principal
    ) {
        var officeId = OfficeContext.requireCurrentOfficeId();
        validateProjectAccess(officeId, projectId, principal.userId());
        var now = OffsetDateTime.now();
        var session = activeSession(officeId, projectId, principal.userId());
        var interaction = resolveInteraction(officeId, projectId, session, request, now);
        var requestId = UUID.randomUUID();
        messageRepository.save(new ArchDoxWorkerChatMessage(
                officeId,
                session.id(),
                principal.userId(),
                ArchDoxWorkerChatMessageRole.USER,
                ArchDoxWorkerChatMessageStatus.COMPLETED,
                interaction.userMessage(),
                requestId,
                null,
                interaction.userMetadata(),
                now));
        var assistantMessage = messageRepository.save(new ArchDoxWorkerChatMessage(
                officeId,
                session.id(),
                null,
                ArchDoxWorkerChatMessageRole.ASSISTANT,
                ArchDoxWorkerChatMessageStatus.PENDING,
                pendingMessage(interaction.actionType()),
                requestId,
                interaction.actionType().name(),
                Map.of("projectId", projectId, "stage", session.stage().name()),
                now));
        session.touch(now);
        submitWorkerActionFlowAfterCommit(
                projectId,
                principal.userId(),
                officeId,
                session.siteId(),
                session.reportId(),
                interaction.userMessage(),
                requestId,
                session.id(),
                assistantMessage.id(),
                interaction.actionType(),
                interaction.actionPayload());
        operationEventService.record(
                officeId,
                OperationEventSeverity.INFO,
                "ARCHDOX_WORKER_CHAT_ACTION_SUBMITTED",
                "archdox-worker-chat",
                workflowKey(session.id()),
                "ARCHDOX_WORKER_CHAT_SESSION",
                session.id(),
                principal.userId(),
                null,
                "ArchDox Worker chat action was submitted.",
                Map.of(
                        "projectId", projectId,
                        "sessionId", session.id(),
                        "stage", session.stage().name(),
                        "actionType", interaction.actionType().name(),
                        "assistantMessageId", assistantMessage.id()));
        return toResponse(session);
    }

    @Transactional
    public ArchDoxWorkerChatSessionResponse cancelActiveAction(Long projectId, UserPrincipal principal) {
        var officeId = OfficeContext.requireCurrentOfficeId();
        validateProjectAccess(officeId, projectId, principal.userId());
        var session = sessionRepository.findFirstByOfficeIdAndProjectIdAndUserIdAndStatusOrderByUpdatedAtDesc(
                        officeId,
                        projectId,
                        principal.userId(),
                        ArchDoxWorkerChatSessionStatus.ACTIVE)
                .orElseThrow(() -> new BadRequestException("Active worker chat session not found"));
        var message = messageRepository
                .findFirstByOfficeIdAndSessionIdAndRoleAndStatusOrderByCreatedAtDescIdDesc(
                        officeId,
                        session.id(),
                        ArchDoxWorkerChatMessageRole.ASSISTANT,
                        ArchDoxWorkerChatMessageStatus.PENDING)
                .orElseThrow(() -> new BadRequestException(
                        "WORKER_CHAT_NO_PENDING_ACTION",
                        "errors.workerChat.noPendingAction",
                        "No pending worker chat action can be cancelled",
                        Map.of("sessionId", session.id())));
        var now = OffsetDateTime.now();
        var metadata = new LinkedHashMap<String, Object>(message.metadataJson());
        metadata.put("cancelled", true);
        metadata.put("cancelledAt", now.toString());
        putIfNotNull(metadata, "actionType", message.workerActionType());
        message.cancel("작업을 취소했습니다. 필요한 내용을 다시 입력해주세요.", metadata, now);
        session.touch(now);
        operationEventService.record(
                officeId,
                OperationEventSeverity.INFO,
                "ARCHDOX_WORKER_CHAT_ACTION_CANCELLED",
                "archdox-worker-chat",
                workflowKey(session.id()),
                "ARCHDOX_WORKER_CHAT_MESSAGE",
                message.id(),
                principal.userId(),
                null,
                "ArchDox Worker chat action was cancelled by the user.",
                Map.of(
                        "projectId", projectId,
                        "sessionId", session.id(),
                        "assistantMessageId", message.id(),
                        "actionType", message.workerActionType() == null ? "" : message.workerActionType()));
        return toResponse(session);
    }

    @Transactional
    public void completeAssistantReply(
            Long officeId,
            Long sessionId,
            Long assistantMessageId,
            String userMessage,
            boolean plannerEligible
    ) {
        if (!assistantMessagePending(officeId, sessionId, assistantMessageId)) {
            return;
        }
        var session = requireSession(officeId, sessionId);
        var now = OffsetDateTime.now();
        var reply = buildFlowReply(session);
        if (plannerEligible) {
            reply = withPlannerProposal(session, reply, userMessage);
        }
        completeAssistantMessage(
                session,
                assistantMessageId,
                reply,
                now,
                ArchDoxWorkerActionType.WORKER_CHAT_ADVANCE);
    }

    @Transactional
    public void failAssistantReply(Long officeId, Long sessionId, Long assistantMessageId, String reason) {
        var session = requireSession(officeId, sessionId);
        var message = requireAssistantMessage(officeId, sessionId, assistantMessageId);
        if (message.status() != ArchDoxWorkerChatMessageStatus.PENDING) {
            return;
        }
        var now = OffsetDateTime.now();
        message.fail("작업 중 문제가 발생했습니다. 잠시 후 다시 요청해주세요.", Map.of("reason", reasonOf(reason)), now);
        session.touch(now);
    }

    @Transactional
    public void createSiteFromWorker(
            Long officeId,
            Long userId,
            Long projectId,
            Long sessionId,
            Long assistantMessageId,
            Map<String, Object> payload
    ) {
        if (!assistantMessagePending(officeId, sessionId, assistantMessageId)) {
            return;
        }
        var session = requireSession(officeId, sessionId);
        validateProjectAccess(officeId, projectId, userId);
        var name = requiredString(payload, "name", "Site name is required");
        var site = withOfficeContext(officeId, () -> siteService.create(
                projectId,
                new CreateSiteRequest(
                        stringValue(payload.get("siteCode")),
                        name,
                        stringValue(payload.get("address")),
                        defaultString(payload.get("siteType"), "CONSTRUCTION_SITE"),
                        localDateValue(payload.get("startDate")),
                        localDateValue(payload.get("endDate"))),
                workerPrincipal(userId)));
        var now = OffsetDateTime.now();
        session.selectSite(site.id(), now);
        var next = buildFlowReply(session);
        completeAssistantMessage(
                session,
                assistantMessageId,
                withActionPrefix(next, "현장을 생성했습니다: " + site.name(), Map.of("createdSiteId", site.id())),
                now,
                ArchDoxWorkerActionType.CREATE_SITE);
    }

    @Transactional
    public void createReportFromWorker(
            Long officeId,
            Long userId,
            Long projectId,
            Long sessionId,
            Long assistantMessageId,
            Map<String, Object> payload
    ) {
        if (!assistantMessagePending(officeId, sessionId, assistantMessageId)) {
            return;
        }
        var session = requireSession(officeId, sessionId);
        validateProjectAccess(officeId, projectId, userId);
        var siteId = longValue(payload.get("siteId"));
        if (siteId == null) {
            siteId = session.siteId();
        }
        if (siteId == null) {
            throw new BadRequestException("Site must be selected before creating a report");
        }
        var resolvedSiteId = siteId;
        requireSite(officeId, projectId, resolvedSiteId);
        var title = defaultString(payload.get("title"), "감리일지 작성");
        var reportType = defaultString(payload.get("reportType"), DEFAULT_REPORT_TYPE);
        var report = withOfficeContext(officeId, () -> inspectionReportService.create(
                new CreateInspectionReportRequest(
                        projectId,
                        resolvedSiteId,
                        reportType,
                        title,
                        longValue(payload.get("templateId"))),
                workerPrincipal(userId)));
        var now = OffsetDateTime.now();
        session.selectReport(report.id(), now);
        var next = buildFlowReply(session);
        completeAssistantMessage(
                session,
                assistantMessageId,
                withActionPrefix(next, "리포트를 생성했습니다: " + report.title(), Map.of("createdReportId", report.id())),
                now,
                ArchDoxWorkerActionType.CREATE_REPORT);
    }

    @Transactional
    public void updateReportStepFromWorker(
            Long officeId,
            Long userId,
            Long projectId,
            Long sessionId,
            Long assistantMessageId,
            Map<String, Object> payload
    ) {
        if (!assistantMessagePending(officeId, sessionId, assistantMessageId)) {
            return;
        }
        var session = requireSession(officeId, sessionId);
        validateProjectAccess(officeId, projectId, userId);
        var reportId = longValue(payload.get("reportId"));
        if (reportId == null) {
            reportId = session.reportId();
        }
        if (reportId == null) {
            throw new BadRequestException("Report must be selected before updating a report step");
        }
        var report = requireReport(officeId, projectId, reportId);
        var workflowStep = resolveWritableWorkflowStep(report, stringValue(payload.get("stepCode")));
        var stepCode = workflowStep.code();
        var stepPayload = mapValue(payload.get("payload"));
        if (stepPayload.isEmpty()) {
            throw new BadRequestException("Report step payload is required");
        }
        withOfficeContext(officeId, () -> inspectionReportService.saveStep(
                report.id(),
                stepCode,
                new SaveInspectionStepRequest(stepPayload),
                workerPrincipal(userId)));
        var now = OffsetDateTime.now();
        if (session.reportId() == null) {
            session.selectReport(report.id(), now);
        } else {
            session.touch(now);
        }
        completeAssistantMessage(
                session,
                assistantMessageId,
                new FlowReply(
                        "리포트 단계 내용을 저장했습니다: " + stepCode,
                        reportWorkingMetadata(session, report, Map.of(
                                "actionType", ArchDoxWorkerActionType.UPDATE_REPORT_STEP.name(),
                                "savedStepCode", stepCode,
                                "savedStepTitle", workflowStep.title()))),
                now,
                ArchDoxWorkerActionType.UPDATE_REPORT_STEP);
    }

    @Transactional
    public void submitReportFromWorker(
            Long officeId,
            Long userId,
            Long projectId,
            Long sessionId,
            Long assistantMessageId,
            Map<String, Object> payload
    ) {
        if (!assistantMessagePending(officeId, sessionId, assistantMessageId)) {
            return;
        }
        var session = requireSession(officeId, sessionId);
        validateProjectAccess(officeId, projectId, userId);
        var reportId = longValue(payload.get("reportId"));
        if (reportId == null) {
            reportId = session.reportId();
        }
        if (reportId == null) {
            throw new BadRequestException("Report must be selected before submitting a report");
        }
        var report = requireReport(officeId, projectId, reportId);
        var submitted = withOfficeContext(officeId, () -> inspectionReportService.submit(report.id(), workerPrincipal(userId)));
        var now = OffsetDateTime.now();
        if (session.reportId() == null) {
            session.selectReport(report.id(), now);
        }
        session.moveTo(ArchDoxWorkerChatStage.REVIEWING, now);
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("stage", session.stage().name());
        putIfNotNull(metadata, "siteId", session.siteId());
        metadata.put("reportId", report.id());
        metadata.put("submittedReportId", report.id());
        metadata.put("reportStatus", submitted.status().name());
        putIfNotNull(metadata, "submittedRevision", submitted.submittedRevision());
        metadata.put("documentTabAvailable", true);
        metadata.put("nextAction", ArchDoxWorkerActionType.RUN_PREFLIGHT_REVIEW.name());
        completeAssistantMessage(
                session,
                assistantMessageId,
                new FlowReply(
                        "리포트를 제출했습니다. 이제 문서 탭에서 생성 전 검토와 문서 생성을 진행할 수 있습니다.",
                        metadata),
                now,
                ArchDoxWorkerActionType.SUBMIT_REPORT);
    }

    @Transactional
    public void runPreflightReviewFromWorker(
            Long officeId,
            Long userId,
            Long projectId,
            Long sessionId,
            Long assistantMessageId,
            Map<String, Object> payload
    ) {
        if (!assistantMessagePending(officeId, sessionId, assistantMessageId)) {
            return;
        }
        var session = requireSession(officeId, sessionId);
        validateProjectAccess(officeId, projectId, userId);
        var reportId = longValue(payload.get("reportId"));
        if (reportId == null) {
            reportId = session.reportId();
        }
        if (reportId == null) {
            throw new BadRequestException("Report must be selected before running preflight review");
        }
        var report = requireReport(officeId, projectId, reportId);
        var submission = withOfficeContext(
                officeId,
                () -> preflightReviewService.requestReview(report.id(), workerPrincipal(userId)));
        registerAfterCommit(() -> preflightReviewWorker.submit(submission.flow()));
        var now = OffsetDateTime.now();
        if (session.reportId() == null) {
            session.selectReport(report.id(), now);
        }
        session.moveTo(ArchDoxWorkerChatStage.REVIEWING, now);
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("stage", session.stage().name());
        putIfNotNull(metadata, "siteId", session.siteId());
        metadata.put("reportId", report.id());
        metadata.put("preflightRunId", submission.response().id());
        metadata.put("preflightStatus", submission.response().status());
        metadata.put("reportRevision", submission.response().reportRevision());
        metadata.put("documentTabAvailable", true);
        metadata.put("nextAction", ArchDoxWorkerActionType.REQUEST_DOCUMENT_GENERATION.name());
        completeAssistantMessage(
                session,
                assistantMessageId,
                new FlowReply(
                        "문서 생성 전 검토를 시작했습니다. 검토 결과는 문서 탭과 같은 기록으로 남습니다.",
                        metadata),
                now,
                ArchDoxWorkerActionType.RUN_PREFLIGHT_REVIEW);
    }

    @Transactional
    public void requestDocumentGenerationFromWorker(
            Long officeId,
            Long userId,
            Long projectId,
            Long sessionId,
            Long assistantMessageId,
            Map<String, Object> payload
    ) {
        if (!assistantMessagePending(officeId, sessionId, assistantMessageId)) {
            return;
        }
        var session = requireSession(officeId, sessionId);
        validateProjectAccess(officeId, projectId, userId);
        var reportId = longValue(payload.get("reportId"));
        if (reportId == null) {
            reportId = session.reportId();
        }
        if (reportId == null) {
            throw new BadRequestException("Report must be selected before requesting document generation");
        }
        var report = requireReport(officeId, projectId, reportId);
        var outputFormat = outputFormatValue(payload.get("outputFormat"));
        var workerType = documentWorkerTypeValue(payload.get("workerType"));
        var job = withOfficeContext(
                officeId,
                () -> documentGenerationRequestService.request(
                        report.id(),
                        new CreateDocumentJobRequest(outputFormat, workerType, null),
                        workerPrincipal(userId)));
        var now = OffsetDateTime.now();
        if (session.reportId() == null) {
            session.selectReport(report.id(), now);
        }
        session.moveTo(ArchDoxWorkerChatStage.GENERATING_DOCUMENT, now);
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("stage", session.stage().name());
        putIfNotNull(metadata, "siteId", session.siteId());
        metadata.put("reportId", report.id());
        metadata.put("documentJobId", job.id());
        metadata.put("documentJobStatus", job.status().name());
        metadata.put("documentJobProgressStep", job.progressStep().name());
        metadata.put("documentJobProgressPercent", job.progressPercent());
        metadata.put("outputFormat", job.outputFormat().name());
        metadata.put("workerType", job.workerType().name());
        metadata.put("documentTabAvailable", true);
        metadata.put("nextAction", "OPEN_DOCUMENTS");
        completeAssistantMessage(
                session,
                assistantMessageId,
                new FlowReply(
                        "문서 생성을 요청했습니다. 생성 진행률과 결과 파일은 문서 탭에서 같은 이력으로 확인할 수 있습니다.",
                        metadata),
                now,
                ArchDoxWorkerActionType.REQUEST_DOCUMENT_GENERATION);
    }

    private WorkerInteraction resolveInteraction(
            Long officeId,
            Long projectId,
            ArchDoxWorkerChatSession session,
            SendArchDoxWorkerChatMessageRequest request,
            OffsetDateTime now
    ) {
        if (request.createSite() != null) {
            return createSiteInteraction(request);
        }
        if (request.createReport() != null) {
            return createReportInteraction(session, request);
        }
        if (request.updateReportStep() != null) {
            return updateReportStepInteraction(session, request);
        }
        if (request.submitReport() != null) {
            return submitReportInteraction(session, request);
        }
        if (request.runPreflightReview() != null) {
            return runPreflightReviewInteraction(session, request);
        }
        if (request.requestDocumentGeneration() != null) {
            return requestDocumentGenerationInteraction(session, request);
        }
        var selection = applySelection(officeId, projectId, session, request, now);
        if (selection != null) {
            return selection;
        }
        var content = normalizeContent(request.content());
        if (session.reportId() != null) {
            if (isSubmitIntent(content)) {
                return submitReportInteraction(session, request);
            }
            if (isPreflightReviewIntent(content)) {
                return runPreflightReviewInteraction(session, request);
            }
            if (isDocumentGenerationIntent(content)) {
                return requestDocumentGenerationInteraction(session, request);
            }
            return updateReportStepInteraction(
                    session,
                    content,
                    Map.of("workerNote", content, "source", "WORKER_CHAT"));
        }
        return new WorkerInteraction(
                ArchDoxWorkerActionType.WORKER_CHAT_ADVANCE,
                Map.of("plannerEligible", true),
                content,
                Map.of("inputKind", "TEXT"));
    }

    private WorkerInteraction createSiteInteraction(SendArchDoxWorkerChatMessageRequest request) {
        var action = request.createSite();
        var name = requiredText(action.name(), "Site name is required");
        var payload = new LinkedHashMap<String, Object>();
        putIfNotNull(payload, "siteCode", action.siteCode());
        putIfNotNull(payload, "name", name);
        putIfNotNull(payload, "address", action.address());
        putIfNotNull(payload, "siteType", action.siteType());
        putIfNotNull(payload, "startDate", action.startDate());
        putIfNotNull(payload, "endDate", action.endDate());
        return new WorkerInteraction(
                ArchDoxWorkerActionType.CREATE_SITE,
                payload,
                defaultString(request.content(), "현장 생성: " + name),
                Map.of("inputKind", "CREATE_SITE", "siteName", name));
    }

    private WorkerInteraction createReportInteraction(
            ArchDoxWorkerChatSession session,
            SendArchDoxWorkerChatMessageRequest request
    ) {
        var action = request.createReport();
        var siteId = action.siteId() == null ? session.siteId() : action.siteId();
        if (siteId == null) {
            throw new BadRequestException("Site must be selected before creating a report");
        }
        var title = defaultString(action.title(), "감리일지 작성");
        var payload = new LinkedHashMap<String, Object>();
        putIfNotNull(payload, "siteId", siteId);
        putIfNotNull(payload, "reportType", defaultString(action.reportType(), DEFAULT_REPORT_TYPE));
        putIfNotNull(payload, "title", title);
        putIfNotNull(payload, "templateId", action.templateId());
        return new WorkerInteraction(
                ArchDoxWorkerActionType.CREATE_REPORT,
                payload,
                defaultString(request.content(), "리포트 생성: " + title),
                Map.of("inputKind", "CREATE_REPORT", "siteId", siteId, "title", title));
    }

    private WorkerInteraction updateReportStepInteraction(
            ArchDoxWorkerChatSession session,
            SendArchDoxWorkerChatMessageRequest request
    ) {
        var action = request.updateReportStep();
        var payload = mapValue(action.payload());
        if (payload.isEmpty() && trimToNull(request.content()) != null) {
            payload = Map.of("workerNote", request.content().trim(), "source", "WORKER_CHAT");
        }
        return updateReportStepInteraction(session, request.content(), action.reportId(), action.stepCode(), payload);
    }

    private WorkerInteraction updateReportStepInteraction(
            ArchDoxWorkerChatSession session,
            String content,
            Map<String, Object> stepPayload
    ) {
        return updateReportStepInteraction(session, content, null, null, stepPayload);
    }

    private WorkerInteraction updateReportStepInteraction(
            ArchDoxWorkerChatSession session,
            String content,
            Long reportId,
            String stepCode,
            Map<String, Object> stepPayload
    ) {
        var resolvedReportId = reportId == null ? session.reportId() : reportId;
        if (resolvedReportId == null) {
            throw new BadRequestException("Report must be selected before updating a report step");
        }
        if (stepPayload == null || stepPayload.isEmpty()) {
            throw new BadRequestException("Report step payload is required");
        }
        var payload = new LinkedHashMap<String, Object>();
        payload.put("reportId", resolvedReportId);
        if (trimToNull(stepCode) != null) {
            payload.put("stepCode", stepCode.trim().toUpperCase());
        }
        payload.put("payload", stepPayload);
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("inputKind", "UPDATE_REPORT_STEP");
        metadata.put("reportId", resolvedReportId);
        if (trimToNull(stepCode) != null) {
            metadata.put("stepCode", stepCode.trim().toUpperCase());
        }
        return new WorkerInteraction(
                ArchDoxWorkerActionType.UPDATE_REPORT_STEP,
                payload,
                defaultString(content, "리포트 단계 저장"),
                metadata);
    }

    private WorkerInteraction submitReportInteraction(
            ArchDoxWorkerChatSession session,
            SendArchDoxWorkerChatMessageRequest request
    ) {
        var action = request.submitReport();
        var reportId = action == null || action.reportId() == null ? session.reportId() : action.reportId();
        if (reportId == null) {
            throw new BadRequestException("Report must be selected before submitting a report");
        }
        var payload = new LinkedHashMap<String, Object>();
        payload.put("reportId", reportId);
        return new WorkerInteraction(
                ArchDoxWorkerActionType.SUBMIT_REPORT,
                payload,
                defaultString(request.content(), "리포트 제출"),
                Map.of("inputKind", "SUBMIT_REPORT", "reportId", reportId));
    }

    private WorkerInteraction runPreflightReviewInteraction(
            ArchDoxWorkerChatSession session,
            SendArchDoxWorkerChatMessageRequest request
    ) {
        var action = request.runPreflightReview();
        var reportId = action == null || action.reportId() == null ? session.reportId() : action.reportId();
        if (reportId == null) {
            throw new BadRequestException("Report must be selected before running preflight review");
        }
        var payload = new LinkedHashMap<String, Object>();
        payload.put("reportId", reportId);
        return new WorkerInteraction(
                ArchDoxWorkerActionType.RUN_PREFLIGHT_REVIEW,
                payload,
                defaultString(request.content(), "문서 생성 전 검토 실행"),
                Map.of("inputKind", "RUN_PREFLIGHT_REVIEW", "reportId", reportId));
    }

    private WorkerInteraction requestDocumentGenerationInteraction(
            ArchDoxWorkerChatSession session,
            SendArchDoxWorkerChatMessageRequest request
    ) {
        var action = request.requestDocumentGeneration();
        var reportId = action == null || action.reportId() == null ? session.reportId() : action.reportId();
        if (reportId == null) {
            throw new BadRequestException("Report must be selected before requesting document generation");
        }
        var payload = new LinkedHashMap<String, Object>();
        payload.put("reportId", reportId);
        if (action != null) {
            putIfNotNull(payload, "outputFormat", action.outputFormat());
            putIfNotNull(payload, "workerType", action.workerType());
        }
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("inputKind", "REQUEST_DOCUMENT_GENERATION");
        metadata.put("reportId", reportId);
        if (action != null) {
            putIfNotNull(metadata, "outputFormat", action.outputFormat());
            putIfNotNull(metadata, "workerType", action.workerType());
        }
        return new WorkerInteraction(
                ArchDoxWorkerActionType.REQUEST_DOCUMENT_GENERATION,
                payload,
                defaultString(request.content(), "문서 생성 요청"),
                metadata);
    }

    private WorkerInteraction applySelection(
            Long officeId,
            Long projectId,
            ArchDoxWorkerChatSession session,
            SendArchDoxWorkerChatMessageRequest request,
            OffsetDateTime now
    ) {
        if (request.siteId() != null && request.reportId() != null) {
            throw new BadRequestException("Select either a site or a report, not both");
        }
        if (request.siteId() != null) {
            var site = requireSite(officeId, projectId, request.siteId());
            session.selectSite(site.id(), now);
            return new WorkerInteraction(
                    ArchDoxWorkerActionType.WORKER_CHAT_ADVANCE,
                    Map.of(),
                    defaultString(request.content(), "현장 선택: " + site.name()),
                    Map.of(
                            "inputKind", "SITE_SELECTION",
                            "siteId", site.id(),
                            "siteName", site.name()));
        }
        if (request.reportId() != null) {
            var report = requireReport(officeId, projectId, request.reportId());
            if (session.siteId() != null && report.siteId() != null && !Objects.equals(session.siteId(), report.siteId())) {
                throw new BadRequestException("Selected report does not belong to the current worker chat site");
            }
            if (session.siteId() == null && report.siteId() != null) {
                session.selectSite(report.siteId(), now);
            }
            session.selectReport(report.id(), now);
            return new WorkerInteraction(
                    ArchDoxWorkerActionType.WORKER_CHAT_ADVANCE,
                    Map.of(),
                    defaultString(request.content(), "리포트 선택: " + reportTitle(report)),
                    Map.of(
                            "inputKind", "REPORT_SELECTION",
                            "reportId", report.id(),
                            "reportTitle", reportTitle(report)));
        }
        return null;
    }

    private void submitWorkerActionFlow(
            Long projectId,
            Long userId,
            Long officeId,
            Long siteId,
            Long reportId,
            String command,
            UUID requestId,
            Long sessionId,
            Long assistantMessageId,
            ArchDoxWorkerActionType actionType,
            Map<String, Object> payload
    ) {
        var request = new ArchDoxWorkerRequest(
                requestId,
                ArchDoxWorkerRequestSource.UI,
                command,
                new ArchDoxWorkerRequestContext(userId, officeId, projectId, siteId, reportId, null, "ko-KR"),
                java.time.Instant.now());
        var actionPayload = new LinkedHashMap<String, Object>();
        if (payload != null) {
            actionPayload.putAll(payload);
        }
        actionPayload.put("sessionId", sessionId);
        actionPayload.put("assistantMessageId", assistantMessageId);
        var action = new ArchDoxWorkerAction(
                actionType,
                actionPayload,
                "Execute an ArchDox Worker chat action.",
                1.0d,
                ArchDoxWorkerActionOrigin.SYSTEM);
        workerProvider.getObject().submit(flowFactoryProvider.getObject().create(request, action));
    }

    private void submitWorkerActionFlowAfterCommit(
            Long projectId,
            Long userId,
            Long officeId,
            Long siteId,
            Long reportId,
            String command,
            UUID requestId,
            Long sessionId,
            Long assistantMessageId,
            ArchDoxWorkerActionType actionType,
            Map<String, Object> payload
    ) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            submitWorkerActionFlow(
                    projectId,
                    userId,
                    officeId,
                    siteId,
                    reportId,
                    command,
                    requestId,
                    sessionId,
                    assistantMessageId,
                    actionType,
                    payload);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                submitWorkerActionFlow(
                        projectId,
                        userId,
                        officeId,
                        siteId,
                        reportId,
                        command,
                        requestId,
                        sessionId,
                        assistantMessageId,
                        actionType,
                        payload);
            }
        });
    }

    private ArchDoxWorkerChatSession activeSession(Long officeId, Long projectId, Long userId) {
        return sessionRepository.findFirstByOfficeIdAndProjectIdAndUserIdAndStatusOrderByUpdatedAtDesc(
                        officeId,
                        projectId,
                        userId,
                        ArchDoxWorkerChatSessionStatus.ACTIVE)
                .orElseGet(() -> sessionRepository.save(new ArchDoxWorkerChatSession(
                        officeId,
                        projectId,
                        userId,
                        "리포트 작업 세션",
                        OffsetDateTime.now())));
    }

    private void ensureInitialAssistantMessage(ArchDoxWorkerChatSession session) {
        if (messageRepository.existsByOfficeIdAndSessionId(session.officeId(), session.id())) {
            return;
        }
        var now = OffsetDateTime.now();
        var reply = buildFlowReply(session);
        messageRepository.save(new ArchDoxWorkerChatMessage(
                session.officeId(),
                session.id(),
                null,
                ArchDoxWorkerChatMessageRole.ASSISTANT,
                ArchDoxWorkerChatMessageStatus.COMPLETED,
                reply.content(),
                null,
                ArchDoxWorkerActionType.WORKER_CHAT_ADVANCE.name(),
                reply.metadata(),
                now));
        session.touch(now);
    }

    private ArchDoxWorkerChatSession requireSession(Long officeId, Long sessionId) {
        return sessionRepository.findByIdAndOfficeId(sessionId, officeId)
                .orElseThrow(() -> new NotFoundException("ArchDox worker chat session not found"));
    }

    private ArchDoxWorkerChatMessage requireAssistantMessage(Long officeId, Long sessionId, Long assistantMessageId) {
        return messageRepository.findByIdAndOfficeIdAndSessionId(assistantMessageId, officeId, sessionId)
                .orElseThrow(() -> new NotFoundException("ArchDox worker chat message not found"));
    }

    private boolean assistantMessagePending(Long officeId, Long sessionId, Long assistantMessageId) {
        return requireAssistantMessage(officeId, sessionId, assistantMessageId).status()
                == ArchDoxWorkerChatMessageStatus.PENDING;
    }

    private Site requireSite(Long officeId, Long projectId, Long siteId) {
        var site = siteRepository.findByIdAndOfficeId(siteId, officeId)
                .orElseThrow(() -> new NotFoundException("Site not found"));
        if (!Objects.equals(site.projectId(), projectId)) {
            throw new NotFoundException("Site not found");
        }
        return site;
    }

    private InspectionReport requireReport(Long officeId, Long projectId, Long reportId) {
        var report = reportRepository.findByIdAndOfficeId(reportId, officeId)
                .orElseThrow(() -> new NotFoundException("Inspection report not found"));
        if (!Objects.equals(report.projectId(), projectId)) {
            throw new NotFoundException("Inspection report not found");
        }
        return report;
    }

    private void completeAssistantMessage(
            ArchDoxWorkerChatSession session,
            Long assistantMessageId,
            FlowReply reply,
            OffsetDateTime now,
            ArchDoxWorkerActionType actionType
    ) {
        var message = requireAssistantMessage(session.officeId(), session.id(), assistantMessageId);
        if (message.status() != ArchDoxWorkerChatMessageStatus.PENDING) {
            operationEventService.record(
                    session.officeId(),
                    OperationEventSeverity.INFO,
                    "ARCHDOX_WORKER_CHAT_REPLY_IGNORED",
                    "archdox-worker-chat",
                    workflowKey(session.id()),
                    "ARCHDOX_WORKER_CHAT_MESSAGE",
                    assistantMessageId,
                    session.userId(),
                    null,
                    "ArchDox Worker chat reply was ignored because the message was no longer pending.",
                    Map.of(
                            "projectId", session.projectId(),
                            "sessionId", session.id(),
                            "status", message.status().name(),
                            "actionType", actionType.name()));
            return;
        }
        var metadata = new LinkedHashMap<String, Object>(reply.metadata());
        metadata.put("actionType", actionType.name());
        message.complete(reply.content(), metadata, now);
        session.touch(now);
        operationEventService.record(
                session.officeId(),
                OperationEventSeverity.INFO,
                "ARCHDOX_WORKER_CHAT_REPLY_COMPLETED",
                "archdox-worker-chat",
                workflowKey(session.id()),
                "ARCHDOX_WORKER_CHAT_MESSAGE",
                assistantMessageId,
                session.userId(),
                null,
                "ArchDox Worker chat reply completed.",
                Map.of(
                        "projectId", session.projectId(),
                        "sessionId", session.id(),
                        "stage", session.stage().name(),
                        "actionType", actionType.name()));
    }

    private FlowReply buildFlowReply(ArchDoxWorkerChatSession session) {
        return switch (session.stage()) {
            case AWAITING_SITE -> awaitingSiteReply(session);
            case AWAITING_REPORT -> awaitingReportReply(session);
            case REPORT_WORKING -> reportWorkingReply(session);
            case REVIEWING -> simpleReply(session, "검토 단계입니다. 결정적 검증을 먼저 수행하고, 필요한 경우에만 AI 검토를 붙입니다.");
            case SIGNING -> simpleReply(session, "서명 단계입니다. 서명은 선택 사항이며, 문서 템플릿에 서명란이 있을 때만 반영합니다.");
            case GENERATING_DOCUMENT -> simpleReply(session, "문서 생성 단계입니다. Cloud API는 Agent에 명령을 보내고 결과 상태를 추적합니다.");
            case COMPLETED -> simpleReply(session, "이 리포트 작업 세션은 완료되었습니다.");
        };
    }

    private FlowReply awaitingSiteReply(ArchDoxWorkerChatSession session) {
        var sites = siteRepository.findByOfficeIdAndProjectIdOrderByUpdatedAtDesc(session.officeId(), session.projectId());
        if (sites.isEmpty()) {
            return new FlowReply(
                    "이 프로젝트에는 아직 현장이 없습니다. 먼저 현장을 생성해야 리포트 작업을 시작할 수 있습니다.",
                    Map.of(
                            "stage", session.stage().name(),
                            "choiceKind", "SITE",
                            "choices", List.of(),
                            "nextAction", "CREATE_SITE"));
        }
        return new FlowReply(
                "어떤 현장 작업을 진행할까요? 아래 현장 중 하나를 선택하세요.",
                Map.of(
                        "stage", session.stage().name(),
                        "choiceKind", "SITE",
                        "choices", sites.stream().map(this::siteChoice).toList()));
    }

    private FlowReply awaitingReportReply(ArchDoxWorkerChatSession session) {
        var reports = reportRepository.findByOfficeIdAndProjectIdOrderByUpdatedAtDesc(
                        session.officeId(),
                        session.projectId())
                .stream()
                .filter(report -> Objects.equals(report.siteId(), session.siteId()))
                .toList();
        if (reports.isEmpty()) {
            return new FlowReply(
                    "선택한 현장에 리포트가 없습니다. 리포트를 생성하면 이 대화 세션에서 이어서 작업할 수 있습니다.",
                    Map.of(
                            "stage", session.stage().name(),
                            "siteId", session.siteId(),
                            "choiceKind", "REPORT",
                            "choices", List.of(),
                            "nextAction", "CREATE_REPORT"));
        }
        return new FlowReply(
                "이 현장에서 이어서 작업할 리포트를 선택하세요.",
                Map.of(
                        "stage", session.stage().name(),
                        "siteId", session.siteId(),
                        "choiceKind", "REPORT",
                        "choices", reports.stream().map(this::reportChoice).toList()));
    }

    private FlowReply reportWorkingReply(ArchDoxWorkerChatSession session) {
        var report = session.reportId() == null
                ? null
                : reportRepository.findByIdAndOfficeId(session.reportId(), session.officeId()).orElse(null);
        var reportTitle = report == null ? "선택한 리포트" : reportTitle(report);
        var metadata = report == null
                ? Map.<String, Object>of(
                        "stage", session.stage().name(),
                        "siteId", session.siteId(),
                        "reportId", session.reportId(),
                        "nextAction", "UPDATE_REPORT_STEP")
                : reportWorkingMetadata(session, report, Map.of());
        return new FlowReply(
                reportTitle + " 리포트가 선택되었습니다. 작성할 단계를 선택하고 내용을 저장하세요.",
                metadata);
    }

    private Map<String, Object> reportWorkingMetadata(
            ArchDoxWorkerChatSession session,
            InspectionReport report,
            Map<String, Object> extraMetadata
    ) {
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("stage", session.stage().name());
        metadata.put("siteId", session.siteId());
        metadata.put("reportId", report.id());
        metadata.put("nextAction", "UPDATE_REPORT_STEP");
        var steps = workflowSteps(report);
        var nextStep = nextWritableWorkflowStep(report, steps);
        if (nextStep != null) {
            metadata.put("nextStepCode", nextStep.code());
            metadata.put("nextStepTitle", nextStep.title());
        }
        metadata.put("workflowSteps", steps.stream()
                .map(step -> workflowStepChoice(report, step))
                .toList());
        metadata.putAll(extraMetadata);
        return metadata;
    }

    private FlowReply withPlannerProposal(ArchDoxWorkerChatSession session, FlowReply baseReply, String userMessage) {
        try {
            var result = conversationPlannerService.plan(
                    session.officeId(),
                    session.id(),
                    conversationPlannerInput(session, userMessage));
            if (result.isEmpty()) {
                return baseReply;
            }
            return mergePlannerProposal(session, baseReply, result.get());
        } catch (RuntimeException ex) {
            operationEventService.record(
                    session.officeId(),
                    OperationEventSeverity.WARN,
                    "ARCHDOX_WORKER_CHAT_PLANNER_SKIPPED",
                    "archdox-worker-chat",
                    workflowKey(session.id()),
                    "ARCHDOX_WORKER_CHAT_SESSION",
                    session.id(),
                    session.userId(),
                    "WORKER_CHAT_PLANNER_FAILED",
                    "ArchDox Worker chat planner failed; deterministic reply was used.",
                    Map.of("reason", bounded(reasonOf(ex.getMessage()), 400)));
            return baseReply;
        }
    }

    private FlowReply mergePlannerProposal(
            ArchDoxWorkerChatSession session,
            FlowReply baseReply,
            ConversationPlannerResult result
    ) {
        var metadata = new LinkedHashMap<String, Object>(baseReply.metadata());
        var planner = new LinkedHashMap<String, Object>();
        planner.put("used", true);
        planner.put("decision", result.decision().name());
        planner.put("actionType", result.actionType());
        planner.put("requiresConfirmation", result.requiresConfirmation());
        planner.put("confidence", result.confidence());
        planner.put("rationale", result.rationale());
        planner.put("userMessage", result.userMessage());
        planner.put("payload", result.payload());
        metadata.put("plannerProposal", planner);

        var content = result.userMessage().isBlank()
                ? baseReply.content()
                : result.userMessage() + "\n\n" + baseReply.content();
        if (result.decision() == ConversationPlannerDecision.PROPOSE_ACTION
                && plannerActionAllowed(session, result.actionType())) {
            metadata.put("nextAction", result.actionType());
            metadata.put("plannerSuggestedPayload", result.payload());
        }
        return new FlowReply(content, metadata);
    }

    private ConversationPlannerInput conversationPlannerInput(ArchDoxWorkerChatSession session, String userMessage) {
        var reports = session.siteId() == null
                ? List.<ConversationPlannerEntityOption>of()
                : reportRepository.findByOfficeIdAndProjectIdOrderByUpdatedAtDesc(session.officeId(), session.projectId())
                        .stream()
                        .filter(report -> Objects.equals(report.siteId(), session.siteId()))
                        .map(this::plannerReportOption)
                        .toList();
        var report = session.reportId() == null
                ? null
                : reportRepository.findByIdAndOfficeId(session.reportId(), session.officeId()).orElse(null);
        var workflowSteps = report == null
                ? List.<ConversationPlannerWorkflowStepOption>of()
                : workflowSteps(report).stream()
                        .map(step -> plannerWorkflowStepOption(report, step))
                        .toList();
        return new ConversationPlannerInput(
                stringId(session.officeId()),
                stringId(session.projectId()),
                stringId(session.siteId()),
                stringId(session.reportId()),
                session.stage().name(),
                "ko-KR",
                userMessage,
                availablePlannerActions(session),
                siteRepository.findByOfficeIdAndProjectIdOrderByUpdatedAtDesc(session.officeId(), session.projectId())
                        .stream()
                        .map(this::plannerSiteOption)
                        .toList(),
                reports,
                workflowSteps);
    }

    private List<ConversationPlannerActionOption> availablePlannerActions(ArchDoxWorkerChatSession session) {
        return switch (session.stage()) {
            case AWAITING_SITE -> List.of(new ConversationPlannerActionOption(
                    ArchDoxWorkerActionType.CREATE_SITE.name(),
                    "Create site",
                    "Create a site in the selected project.",
                    true));
            case AWAITING_REPORT -> List.of(new ConversationPlannerActionOption(
                    ArchDoxWorkerActionType.CREATE_REPORT.name(),
                    "Create report",
                    "Create a report in the selected site.",
                    true));
            case REPORT_WORKING -> List.of(new ConversationPlannerActionOption(
                    ArchDoxWorkerActionType.UPDATE_REPORT_STEP.name(),
                    "Update report step",
                    "Save content to one step of the selected report workflow.",
                    false),
                    new ConversationPlannerActionOption(
                            ArchDoxWorkerActionType.SUBMIT_REPORT.name(),
                            "Submit report",
                            "Submit the selected report using the normal report validation path.",
                            true));
            case REVIEWING -> List.of(
                    new ConversationPlannerActionOption(
                            ArchDoxWorkerActionType.RUN_PREFLIGHT_REVIEW.name(),
                            "Run preflight review",
                            "Run deterministic and optional AI preflight review for the selected report.",
                            true),
                    new ConversationPlannerActionOption(
                            ArchDoxWorkerActionType.REQUEST_DOCUMENT_GENERATION.name(),
                            "Request document generation",
                            "Request document generation through the normal document job path.",
                            true));
            default -> List.of();
        };
    }

    private boolean plannerActionAllowed(ArchDoxWorkerChatSession session, String actionType) {
        if (actionType == null || actionType.isBlank()) {
            return false;
        }
        return availablePlannerActions(session).stream()
                .anyMatch(option -> option.actionType().equalsIgnoreCase(actionType));
    }

    private ConversationPlannerEntityOption plannerSiteOption(Site site) {
        return new ConversationPlannerEntityOption(
                "SITE",
                stringId(site.id()),
                site.name(),
                site.address());
    }

    private ConversationPlannerEntityOption plannerReportOption(InspectionReport report) {
        return new ConversationPlannerEntityOption(
                "REPORT",
                stringId(report.id()),
                reportTitle(report),
                report.reportType() + " / " + report.status().name());
    }

    private ConversationPlannerWorkflowStepOption plannerWorkflowStepOption(
            InspectionReport report,
            ReportWorkflowStepResponse step
    ) {
        return new ConversationPlannerWorkflowStepOption(
                step.code(),
                step.title(),
                step.description(),
                stepRepository.existsByReportIdAndStepCode(report.id(), step.code()),
                step.fields().stream()
                        .map(field -> new ConversationPlannerFieldOption(
                                field.key(),
                                field.label(),
                                field.type(),
                                field.required()))
                        .toList());
    }

    private ReportWorkflowStepResponse resolveWritableWorkflowStep(InspectionReport report, String requestedStepCode) {
        var steps = workflowSteps(report);
        if (steps.isEmpty()) {
            throw new BadRequestException("Report workflow has no writable steps");
        }
        var normalized = trimToNull(requestedStepCode);
        if (normalized == null) {
            var nextStep = nextWritableWorkflowStep(report, steps);
            if (nextStep != null) {
                return nextStep;
            }
            return steps.get(0);
        }
        var stepCode = normalized.toUpperCase();
        return steps.stream()
                .filter(step -> step.code().equalsIgnoreCase(stepCode))
                .findFirst()
                .orElseThrow(() -> new BadRequestException(
                        "WORKER_CHAT_STEP_NOT_IN_WORKFLOW",
                        "errors.workerChat.stepNotInWorkflow",
                        "Step code is not part of the selected report workflow",
                        Map.of("reportId", report.id(), "stepCode", stepCode)));
    }

    private List<ReportWorkflowStepResponse> workflowSteps(InspectionReport report) {
        return workflowDefinitionService.resolveForReport(report).steps();
    }

    private ReportWorkflowStepResponse nextWritableWorkflowStep(
            InspectionReport report,
            List<ReportWorkflowStepResponse> steps
    ) {
        return steps.stream()
                .filter(step -> !stepRepository.existsByReportIdAndStepCode(report.id(), step.code()))
                .findFirst()
                .orElseGet(() -> steps.isEmpty() ? null : steps.get(0));
    }

    private Map<String, Object> workflowStepChoice(InspectionReport report, ReportWorkflowStepResponse step) {
        var choice = new LinkedHashMap<String, Object>();
        choice.put("code", step.code());
        choice.put("title", step.title());
        choice.put("description", step.description());
        choice.put("stepType", step.stepType());
        choice.put("saved", stepRepository.existsByReportIdAndStepCode(report.id(), step.code()));
        choice.put("fields", step.fields().stream().map(this::workflowFieldChoice).toList());
        return choice;
    }

    private Map<String, Object> workflowFieldChoice(ReportWorkflowFieldResponse field) {
        var choice = new LinkedHashMap<String, Object>();
        choice.put("key", field.key());
        choice.put("label", field.label());
        choice.put("type", field.type());
        choice.put("placeholder", field.placeholder());
        choice.put("required", field.required());
        return choice;
    }

    private FlowReply simpleReply(ArchDoxWorkerChatSession session, String content) {
        return new FlowReply(content, Map.of("stage", session.stage().name()));
    }

    private FlowReply withActionPrefix(FlowReply next, String prefix, Map<String, Object> extraMetadata) {
        var metadata = new LinkedHashMap<String, Object>(next.metadata());
        metadata.putAll(extraMetadata);
        return new FlowReply(prefix + "\n\n" + next.content(), metadata);
    }

    private Map<String, Object> siteChoice(Site site) {
        var choice = new LinkedHashMap<String, Object>();
        choice.put("kind", "SITE");
        choice.put("id", site.id());
        choice.put("label", site.name());
        choice.put("description", site.address() == null || site.address().isBlank() ? "주소 없음" : site.address());
        return choice;
    }

    private Map<String, Object> reportChoice(InspectionReport report) {
        var choice = new LinkedHashMap<String, Object>();
        choice.put("kind", "REPORT");
        choice.put("id", report.id());
        choice.put("label", reportTitle(report));
        choice.put("description", report.reportType() + " / " + report.status().name());
        return choice;
    }

    private void validateProjectAccess(Long officeId, Long projectId, Long userId) {
        permissionService.requireActiveMembership(userId, officeId);
        projectRepository.findByIdAndOfficeId(projectId, officeId)
                .orElseThrow(() -> new NotFoundException("Project not found"));
    }

    private ArchDoxWorkerChatSessionResponse toResponse(ArchDoxWorkerChatSession session) {
        var messages = messageRepository.findByOfficeIdAndSessionIdOrderByCreatedAtAscIdAsc(
                        session.officeId(),
                        session.id())
                .stream()
                .map(this::toResponse)
                .toList();
        return new ArchDoxWorkerChatSessionResponse(
                session.id(),
                session.officeId(),
                session.projectId(),
                session.siteId(),
                session.reportId(),
                session.userId(),
                session.status(),
                session.stage(),
                session.title(),
                session.lastMessageAt(),
                session.completedAt(),
                session.createdAt(),
                session.updatedAt(),
                workflowState(session),
                messages);
    }

    private Map<String, Object> workflowState(ArchDoxWorkerChatSession session) {
        if (session.reportId() == null) {
            return Map.of();
        }
        var report = reportRepository.findByIdAndOfficeId(session.reportId(), session.officeId())
                .orElse(null);
        if (report == null) {
            return Map.of();
        }
        var state = new LinkedHashMap<String, Object>();
        state.put("report", reportState(report));
        var latestPreflight = preflightRunRepository
                .findByOfficeIdAndReportIdOrderByRequestedAtDesc(report.officeId(), report.id())
                .stream()
                .findFirst()
                .orElse(null);
        if (latestPreflight != null) {
            var preflight = new LinkedHashMap<String, Object>();
            preflight.put("id", latestPreflight.id());
            preflight.put("status", latestPreflight.status().name());
            preflight.put("reportRevision", latestPreflight.reportRevision());
            putIfNotNull(preflight, "terminalReason", latestPreflight.terminalReason());
            preflight.put("hasHarness", latestPreflight.hasHarness());
            putIfNotNull(preflight, "harnessStatus", latestPreflight.harnessStatus());
            preflight.put("requestedAt", latestPreflight.requestedAt().toString());
            preflight.put("updatedAt", latestPreflight.updatedAt().toString());
            putIfNotNull(preflight, "completedAt", latestPreflight.completedAt() == null ? null : latestPreflight.completedAt().toString());
            var findings = preflightFindingRepository.findByOfficeIdAndReviewRunIdOrderByIdAsc(report.officeId(), latestPreflight.id());
            preflight.put("findingCount", findings.size());
            preflight.put("openFindingCount", findings.stream()
                    .filter(finding -> finding.resolutionStatus() == ReportPreflightFindingResolutionStatus.OPEN)
                    .count());
            preflight.put("blockingFindingCount", findings.stream()
                    .filter(finding -> finding.resolutionStatus() == ReportPreflightFindingResolutionStatus.OPEN)
                    .filter(finding -> "HIGH".equals(finding.severity()) || "CRITICAL".equals(finding.severity()))
                    .count());
            preflight.put("findings", findings.stream()
                    .limit(3)
                    .map(finding -> {
                        var value = new LinkedHashMap<String, Object>();
                        value.put("id", finding.id());
                        value.put("source", finding.source());
                        value.put("code", finding.code());
                        value.put("severity", finding.severity());
                        putIfNotNull(value, "location", finding.location());
                        value.put("message", finding.message());
                        value.put("resolutionStatus", finding.resolutionStatus().name());
                        return value;
                    })
                    .toList());
            state.put("latestPreflightRun", preflight);
            state.put("preflightActive", latestPreflight.status() == ReportPreflightReviewStatus.REQUESTED
                    || latestPreflight.status() == ReportPreflightReviewStatus.RUNNING);
            state.put("preflightPassedForCurrentRevision", latestPreflight.status() == ReportPreflightReviewStatus.PASSED
                    && latestPreflight.reportRevision() == report.generationRevision());
        } else {
            state.put("preflightActive", false);
            state.put("preflightPassedForCurrentRevision", false);
        }

        var latestJob = documentJobRepository
                .findByOfficeIdAndReportIdOrderByRequestedAtDesc(report.officeId(), report.id())
                .stream()
                .findFirst()
                .orElse(null);
        if (latestJob != null) {
            var job = new LinkedHashMap<String, Object>();
            job.put("id", latestJob.id());
            job.put("status", latestJob.status().name());
            job.put("progressStep", latestJob.progressStep().name());
            job.put("progressPercent", latestJob.progressPercent());
            putIfNotNull(job, "progressMessage", latestJob.progressMessage());
            job.put("reportRevision", latestJob.reportRevision());
            job.put("outputFormat", latestJob.outputFormat().name());
            job.put("workerType", latestJob.workerType().name());
            putIfNotNull(job, "errorCode", latestJob.errorCode());
            putIfNotNull(job, "errorMessage", latestJob.errorMessage());
            job.put("requestedAt", latestJob.requestedAt().toString());
            job.put("updatedAt", latestJob.updatedAt().toString());
            putIfNotNull(job, "completedAt", latestJob.completedAt() == null ? null : latestJob.completedAt().toString());
            state.put("latestDocumentJob", job);
            state.put("documentJobActive", "REQUESTED".equals(latestJob.status().name())
                    || "GENERATING".equals(latestJob.status().name()));
            state.put("documentGenerated", "GENERATED".equals(latestJob.status().name()));
        } else {
            state.put("documentJobActive", false);
            state.put("documentGenerated", false);
        }
        state.put("canRunPreflightReview", report.canRequestGeneration());
        state.put("canRequestDocumentGeneration", Boolean.TRUE.equals(state.get("preflightPassedForCurrentRevision"))
                && report.canRequestGeneration());
        return state;
    }

    private Map<String, Object> reportState(InspectionReport report) {
        var value = new LinkedHashMap<String, Object>();
        value.put("id", report.id());
        value.put("status", report.status().name());
        value.put("contentRevision", report.contentRevision());
        value.put("generationRevision", report.generationRevision());
        putIfNotNull(value, "submittedRevision", report.submittedRevision());
        putIfNotNull(value, "generatedRevision", report.generatedRevision());
        putIfNotNull(value, "lastDocumentJobId", report.lastDocumentJobId());
        return value;
    }

    private ArchDoxWorkerChatMessageResponse toResponse(ArchDoxWorkerChatMessage message) {
        return new ArchDoxWorkerChatMessageResponse(
                message.id(),
                message.sessionId(),
                message.userId(),
                message.role(),
                message.status(),
                message.content(),
                message.workerRequestId(),
                message.workerActionType(),
                message.metadataJson(),
                message.createdAt(),
                message.updatedAt());
    }

    private String normalizeContent(String content) {
        var normalized = trimToNull(content);
        if (normalized == null) {
            throw new BadRequestException("Worker chat message content is required");
        }
        if (normalized.length() > MAX_MESSAGE_LENGTH) {
            throw new BadRequestException(
                    "WORKER_CHAT_MESSAGE_TOO_LONG",
                    "errors.workerChat.messageTooLong",
                    "Worker chat message is too long",
                    Map.of("maxLength", MAX_MESSAGE_LENGTH));
        }
        return normalized;
    }

    private boolean isSubmitIntent(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        var lower = content.toLowerCase(Locale.ROOT);
        return content.contains("제출")
                || content.contains("작성 완료")
                || lower.contains("submit report")
                || lower.equals("submit");
    }

    private boolean isPreflightReviewIntent(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        var lower = content.toLowerCase(Locale.ROOT);
        return content.contains("검토")
                || content.contains("사전검토")
                || lower.contains("preflight")
                || lower.contains("review");
    }

    private boolean isDocumentGenerationIntent(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        var lower = content.toLowerCase(Locale.ROOT);
        return content.contains("문서 생성")
                || content.contains("생성 요청")
                || lower.contains("generate document")
                || lower.contains("document generation");
    }

    private String requiredText(String value, String message) {
        var normalized = trimToNull(value);
        if (normalized == null) {
            throw new BadRequestException(message);
        }
        return normalized;
    }

    private String requiredString(Map<String, Object> payload, String key, String message) {
        return requiredText(stringValue(payload.get(key)), message);
    }

    private void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        return value.toString();
    }

    private String stringId(Object value) {
        return value == null ? "" : value.toString();
    }

    private String defaultString(Object value, String fallback) {
        var normalized = trimToNull(stringValue(value));
        return normalized == null ? fallback : normalized;
    }

    private Long longValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String string && !string.isBlank()) {
            return Long.parseLong(string);
        }
        throw new BadRequestException("Invalid long value: " + value);
    }

    private LocalDate localDateValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        return LocalDate.parse(value.toString());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        if (value == null) {
            return Map.of();
        }
        if (value instanceof Map<?, ?> map) {
            var normalized = new LinkedHashMap<String, Object>();
            map.forEach((key, mapValue) -> {
                if (key != null && mapValue != null) {
                    normalized.put(key.toString(), mapValue);
                }
            });
            return normalized;
        }
        throw new BadRequestException("Invalid map value: " + value);
    }

    private OutputFormat outputFormatValue(Object value) {
        var normalized = defaultString(value, OutputFormat.DOCX.name()).trim().toUpperCase(Locale.ROOT);
        try {
            return OutputFormat.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid output format: " + value);
        }
    }

    private DocumentWorkerType documentWorkerTypeValue(Object value) {
        var normalized = trimToNull(stringValue(value));
        if (normalized == null) {
            return null;
        }
        try {
            return DocumentWorkerType.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid document worker type: " + value);
        }
    }

    private UserPrincipal workerPrincipal(Long userId) {
        return new UserPrincipal(userId, "archdox-worker@local");
    }

    private void registerAfterCommit(Runnable task) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            task.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                task.run();
            }
        });
    }

    private <T> T withOfficeContext(Long officeId, Supplier<T> supplier) {
        var previous = OfficeContext.currentOfficeIdOrNull();
        OfficeContext.set(officeId);
        try {
            return supplier.get();
        } finally {
            if (previous == null) {
                OfficeContext.clear();
            } else {
                OfficeContext.set(previous);
            }
        }
    }

    private String pendingMessage(ArchDoxWorkerActionType actionType) {
        return switch (actionType) {
            case CREATE_SITE -> "현장을 생성하고 있습니다.";
            case CREATE_REPORT -> "리포트를 생성하고 있습니다.";
            case UPDATE_REPORT_STEP -> "리포트 내용을 저장하고 있습니다.";
            case SUBMIT_REPORT -> "리포트를 제출하고 있습니다.";
            case RUN_PREFLIGHT_REVIEW -> "문서 생성 전 검토를 실행하고 있습니다.";
            case REQUEST_DOCUMENT_GENERATION -> "문서 생성을 요청하고 있습니다.";
            default -> "ArchDox Worker가 다음 단계를 확인하고 있습니다.";
        };
    }

    private String reportTitle(InspectionReport report) {
        return report.title() == null || report.title().isBlank() ? report.reportNo() : report.title();
    }

    private String workflowKey(Long sessionId) {
        return "worker-chat-session:" + sessionId;
    }

    private String reasonOf(String reason) {
        return reason == null || reason.isBlank() ? "Unknown worker chat failure" : reason;
    }

    private String bounded(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private record FlowReply(String content, Map<String, Object> metadata) {
    }

    private record WorkerInteraction(
            ArchDoxWorkerActionType actionType,
            Map<String, Object> actionPayload,
            String userMessage,
            Map<String, Object> userMetadata
    ) {
    }
}
