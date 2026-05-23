package com.archdox.cloud.checklist.application;

import com.archdox.cloud.checklist.domain.ChecklistItem;
import com.archdox.cloud.checklist.domain.ChecklistSchema;
import com.archdox.cloud.checklist.domain.ChecklistSchemaStatus;
import com.archdox.cloud.checklist.domain.InspectionChecklistAnswer;
import com.archdox.cloud.checklist.dto.ChecklistAnswerResponse;
import com.archdox.cloud.checklist.dto.ChecklistItemResponse;
import com.archdox.cloud.checklist.dto.ChecklistSchemaResponse;
import com.archdox.cloud.checklist.dto.ReportChecklistResponse;
import com.archdox.cloud.checklist.dto.SaveChecklistAnswerRequest;
import com.archdox.cloud.checklist.infra.ChecklistItemRepository;
import com.archdox.cloud.checklist.infra.ChecklistSchemaRepository;
import com.archdox.cloud.checklist.infra.InspectionChecklistAnswerRepository;
import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.global.api.NotFoundException;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.inspection.application.InspectionReportService;
import com.archdox.cloud.inspection.domain.InspectionReport;
import com.archdox.cloud.inspectiontarget.application.InspectionTargetService;
import com.archdox.cloud.office.application.OfficeContext;
import com.archdox.cloud.office.application.OfficePermissionService;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChecklistService {
    private final ChecklistSchemaRepository schemaRepository;
    private final ChecklistItemRepository itemRepository;
    private final InspectionChecklistAnswerRepository answerRepository;
    private final InspectionReportService reportService;
    private final InspectionTargetService targetService;
    private final OfficePermissionService permissionService;

    public ChecklistService(
            ChecklistSchemaRepository schemaRepository,
            ChecklistItemRepository itemRepository,
            InspectionChecklistAnswerRepository answerRepository,
            InspectionReportService reportService,
            InspectionTargetService targetService,
            OfficePermissionService permissionService
    ) {
        this.schemaRepository = schemaRepository;
        this.itemRepository = itemRepository;
        this.answerRepository = answerRepository;
        this.reportService = reportService;
        this.targetService = targetService;
        this.permissionService = permissionService;
    }

    @Transactional(readOnly = true)
    public ReportChecklistResponse getReportChecklist(Long reportId) {
        var report = reportService.requireReport(reportId);
        var schema = resolveSchema(report);
        var items = itemRepository.findByChecklistSchemaIdOrderByDisplayOrderAscIdAsc(schema.id());
        var itemById = items.stream().collect(Collectors.toMap(ChecklistItem::id, Function.identity()));
        var answers = answerRepository.findByOfficeIdAndReportIdOrderById(report.officeId(), report.id()).stream()
                .filter(answer -> answer.checklistSchemaId().equals(schema.id()))
                .map(answer -> toAnswerResponse(answer, itemById.get(answer.checklistItemId())))
                .toList();
        return new ReportChecklistResponse(toSchemaResponse(schema, items), answers);
    }

    @Transactional
    public ChecklistAnswerResponse saveAnswer(
            Long reportId,
            String itemCode,
            SaveChecklistAnswerRequest request,
            UserPrincipal principal
    ) {
        var report = reportService.requireReport(reportId);
        permissionService.requireReportWriter(principal.userId(), report.officeId(), report.projectId(), report.id());
        if (!report.canSaveStep()) {
            throw new BadRequestException("Inspection report cannot be edited in status " + report.status());
        }
        var schema = resolveSchema(report);
        var normalizedItemCode = itemCode.trim().toUpperCase();
        var item = itemRepository.findByChecklistSchemaIdAndItemCode(schema.id(), normalizedItemCode)
                .orElseThrow(() -> new NotFoundException("Checklist item not found"));

        if (request.targetId() != null) {
            var target = targetService.requireTarget(request.targetId());
            if (!target.projectId().equals(report.projectId())
                    || (report.siteId() != null && !target.siteId().equals(report.siteId()))) {
                throw new BadRequestException("Checklist answer target does not belong to the report context");
            }
        }

        var now = OffsetDateTime.now();
        var existing = request.targetId() == null
                ? answerRepository.findByOfficeIdAndReportIdAndChecklistItemIdAndTargetIdIsNull(
                        report.officeId(),
                        report.id(),
                        item.id())
                : answerRepository.findByOfficeIdAndReportIdAndChecklistItemIdAndTargetId(
                        report.officeId(),
                        report.id(),
                        item.id(),
                        request.targetId());
        var answer = existing
                .map(current -> {
                    current.update(request.answer(), trimToNull(request.note()), principal.userId(), now);
                    return current;
                })
                .orElseGet(() -> answerRepository.save(new InspectionChecklistAnswer(
                        report.officeId(),
                        report.id(),
                        schema.id(),
                        item.id(),
                        request.targetId(),
                        request.answer(),
                        trimToNull(request.note()),
                        principal.userId(),
                        now)));
        report.markStepSaved("CHECKLIST", now);
        return toAnswerResponse(answer, item);
    }

    public ChecklistSchema resolveSchema(InspectionReport report) {
        var officeId = OfficeContext.requireCurrentOfficeId();
        return schemaRepository.findByReportTypeAndStatusOrderByOfficeIdDescIdAsc(
                        report.reportType(),
                        ChecklistSchemaStatus.ACTIVE).stream()
                .filter(schema -> schema.officeId() == null || Objects.equals(schema.officeId(), officeId))
                .min(Comparator
                        .comparing((ChecklistSchema schema) -> Objects.equals(schema.officeId(), officeId) ? 0 : 1)
                        .thenComparing(schema -> schema.siteType() == null ? 1 : 0)
                        .thenComparing(schema -> schema.targetType() == null ? 1 : 0)
                        .thenComparing(ChecklistSchema::id))
                .orElseThrow(() -> new NotFoundException("Checklist schema not found"));
    }

    public List<Map<String, Object>> answerSnapshot(InspectionReport report) {
        var answers = answerRepository.findByOfficeIdAndReportIdOrderById(report.officeId(), report.id());
        if (answers.isEmpty()) {
            return List.of();
        }
        var itemIds = answers.stream().map(InspectionChecklistAnswer::checklistItemId).toList();
        var items = itemRepository.findAllById(itemIds).stream()
                .collect(Collectors.toMap(ChecklistItem::id, Function.identity()));
        return answers.stream()
                .map(answer -> {
                    var item = items.get(answer.checklistItemId());
                    Map<String, Object> snapshot = new LinkedHashMap<>();
                    snapshot.put("answerId", answer.id());
                    snapshot.put("checklistSchemaId", answer.checklistSchemaId());
                    snapshot.put("checklistItemId", answer.checklistItemId());
                    snapshot.put("itemCode", item == null ? "" : item.itemCode());
                    snapshot.put("label", item == null ? "" : item.label());
                    snapshot.put("answerType", item == null ? "" : item.answerType().name());
                    snapshot.put("targetId", answer.targetId() == null ? "" : answer.targetId());
                    snapshot.put("answer", answer.answerValueJson());
                    snapshot.put("note", answer.note() == null ? "" : answer.note());
                    snapshot.put("clientRevision", answer.clientRevision());
                    snapshot.put("savedAt", answer.savedAt().toString());
                    return snapshot;
                })
                .toList();
    }

    private ChecklistSchemaResponse toSchemaResponse(ChecklistSchema schema, List<ChecklistItem> items) {
        return new ChecklistSchemaResponse(
                schema.id(),
                schema.officeId(),
                schema.reportType(),
                schema.siteType(),
                schema.targetType(),
                schema.code(),
                schema.name(),
                schema.version(),
                schema.schemaJson(),
                items.stream().map(this::toItemResponse).toList());
    }

    private ChecklistItemResponse toItemResponse(ChecklistItem item) {
        return new ChecklistItemResponse(
                item.id(),
                item.itemCode(),
                item.label(),
                item.description(),
                item.answerType(),
                item.required(),
                item.displayOrder(),
                item.optionsJson());
    }

    private ChecklistAnswerResponse toAnswerResponse(InspectionChecklistAnswer answer, ChecklistItem item) {
        return new ChecklistAnswerResponse(
                answer.id(),
                answer.reportId(),
                answer.checklistSchemaId(),
                answer.checklistItemId(),
                item == null ? "" : item.itemCode(),
                answer.targetId(),
                answer.answerValueJson(),
                answer.note(),
                answer.clientRevision(),
                answer.savedAt());
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
