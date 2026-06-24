package com.archdox.cloud.platformops.application;

import com.archdox.cloud.operation.application.OperationEventService;
import com.archdox.cloud.operation.domain.OperationEventSeverity;
import com.archdox.cloud.platformops.domain.PlatformOpsFinding;
import com.archdox.cloud.platformops.domain.PlatformOpsFindingSeverity;
import com.archdox.cloud.platformops.domain.PlatformOpsFindingSource;
import com.archdox.cloud.platformops.domain.PlatformOpsIncident;
import com.archdox.cloud.platformops.domain.PlatformOpsIncidentStatus;
import com.archdox.cloud.platformops.domain.PlatformOpsRun;
import com.archdox.cloud.platformops.domain.PlatformOpsRunTriggerType;
import com.archdox.cloud.platformops.infra.PlatformOpsFindingRepository;
import com.archdox.cloud.platformops.infra.PlatformOpsIncidentRepository;
import com.archdox.cloud.platformops.infra.PlatformOpsRunRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformOpsDetectionService {
    private static final List<PlatformOpsIncidentStatus> ACTIVE_INCIDENT_STATUSES = List.of(
            PlatformOpsIncidentStatus.OPEN,
            PlatformOpsIncidentStatus.ACKNOWLEDGED);

    private final List<PlatformOpsDetector> detectors;
    private final PlatformOpsAutomationSettingsService automationSettingsService;
    private final PlatformOpsRunRepository runRepository;
    private final PlatformOpsIncidentRepository incidentRepository;
    private final PlatformOpsFindingRepository findingRepository;
    private final OperationEventService operationEventService;

    public PlatformOpsDetectionService(
            List<PlatformOpsDetector> detectors,
            PlatformOpsAutomationSettingsService automationSettingsService,
            PlatformOpsRunRepository runRepository,
            PlatformOpsIncidentRepository incidentRepository,
            PlatformOpsFindingRepository findingRepository,
            OperationEventService operationEventService
    ) {
        this.detectors = List.copyOf(detectors);
        this.automationSettingsService = automationSettingsService;
        this.runRepository = runRepository;
        this.incidentRepository = incidentRepository;
        this.findingRepository = findingRepository;
        this.operationEventService = operationEventService;
    }

    @Transactional
    public PlatformOpsDetectionSummary requestStuckDetection(Long startedByUserId) {
        var now = OffsetDateTime.now();
        var run = requestDetectionRun(PlatformOpsRunTriggerType.MANUAL_DETECT_STUCK, startedByUserId, now);
        return new PlatformOpsDetectionSummary(run.id(), 0, 0, 0, 0, 0, 0, now);
    }

    @Transactional
    public PlatformOpsRun requestAutoStuckDetection(OffsetDateTime now) {
        return requestDetectionRun(PlatformOpsRunTriggerType.AUTO_DETECT_STUCK, null, now);
    }

    @Transactional
    public PlatformOpsDetectionSummary executeStuckDetection(Long runId) {
        var run = runRepository.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Platform ops run not found: " + runId));
        return execute(run, OffsetDateTime.now(), PageRequest.of(0, Math.max(1, automationSettingsService.settings().maxDetectedItems())));
    }

    @Transactional
    public PlatformOpsDetectionSummary detectStuck(Long startedByUserId) {
        var now = OffsetDateTime.now();
        var run = requestDetectionRun(PlatformOpsRunTriggerType.MANUAL_DETECT_STUCK, startedByUserId, now);
        return execute(run, now, PageRequest.of(0, Math.max(1, automationSettingsService.settings().maxDetectedItems())));
    }

    @Transactional
    public void markRunFailed(Long runId, String failureCode) {
        runRepository.findById(runId).ifPresent(run -> run.fail(failureCode, OffsetDateTime.now()));
    }

    private PlatformOpsDetectionSummary execute(PlatformOpsRun run, OffsetDateTime now, Pageable page) {
        var context = new PlatformOpsDetectionContext(now, page);

        var detected = new ArrayList<PlatformOpsDetectionFinding>();
        detectors.forEach(detector -> detected.addAll(detector.detect(context)));
        var activeKeys = detected.stream()
                .map(this::incidentKey)
                .toList();

        var incidentCount = 0;
        var findingCount = 0;
        for (var detectedFinding : detected) {
            var incident = upsertIncident(run, detectedFinding, now);
            findingRepository.save(toFinding(run, incident, detectedFinding, now));
            recordOperationEvent(detectedFinding, run.startedByUserId());
            incidentCount += 1;
            findingCount += 1;
        }
        var resolvedCount = resolveStaleIncidents(run, context, activeKeys);

        run.replaceSnapshot(snapshot(detected, incidentCount, findingCount, resolvedCount));
        run.complete(now);
        return new PlatformOpsDetectionSummary(
                run.id(),
                count(detected, "DOCUMENT_JOB_STUCK"),
                count(detected, "AGENT_COMMAND_STUCK"),
                count(detected, "PHOTO_PICKUP_STUCK"),
                count(detected, "DOCUMENT_DELIVERY_STUCK"),
                incidentCount,
                findingCount,
                now);
    }

    private PlatformOpsRun requestDetectionRun(
            PlatformOpsRunTriggerType triggerType,
            Long startedByUserId,
            OffsetDateTime now
    ) {
        var settings = automationSettingsService.settings();
        return runRepository.save(new PlatformOpsRun(
                triggerType,
                startedByUserId,
                Map.of(
                        "state", "REQUESTED",
                        "detectorCount", detectors.size(),
                        "maxDetectedItems", Math.max(1, settings.maxDetectedItems())),
                now));
    }

    private PlatformOpsIncident upsertIncident(
            PlatformOpsRun run,
            PlatformOpsDetectionFinding detectedFinding,
            OffsetDateTime now
    ) {
        var incident = incidentRepository.findFirstByStatusInAndCategoryAndPrimaryResourceTypeAndPrimaryResourceIdOrderByLastSeenAtDesc(
                        ACTIVE_INCIDENT_STATUSES,
                        detectedFinding.category(),
                        detectedFinding.resourceType(),
                        detectedFinding.resourceId())
                .orElseGet(() -> new PlatformOpsIncident(
                        detectedFinding.severity(),
                        detectedFinding.category(),
                        detectedFinding.title(),
                        detectedFinding.message(),
                        detectedFinding.officeId(),
                        detectedFinding.resourceType(),
                        detectedFinding.resourceId(),
                        run.id(),
                        now));
        incident.observe(detectedFinding.severity(), detectedFinding.title(), detectedFinding.message(), run.id(), now);
        var saved = incidentRepository.save(incident);
        run.attachIncident(saved.id());
        return saved;
    }

    private PlatformOpsFinding toFinding(
            PlatformOpsRun run,
            PlatformOpsIncident incident,
            PlatformOpsDetectionFinding detectedFinding,
            OffsetDateTime now
    ) {
        return new PlatformOpsFinding(
                incident.id(),
                run.id(),
                detectedFinding.officeId(),
                detectedFinding.severity(),
                PlatformOpsFindingSource.DETECTOR,
                detectedFinding.code(),
                detectedFinding.category(),
                detectedFinding.title(),
                detectedFinding.message(),
                detectedFinding.resourceType(),
                detectedFinding.resourceId(),
                detectedFinding.workflowType(),
                detectedFinding.workflowKey(),
                detectedFinding.evidenceJson(),
                detectedFinding.recommendation(),
                now);
    }

    private int resolveStaleIncidents(
            PlatformOpsRun run,
            PlatformOpsDetectionContext context,
            List<String> activeKeys
    ) {
        var resolvedCount = 0;
        for (var detector : detectors) {
            if (!detector.supportsAutoResolve()) {
                continue;
            }
            var activeIncidents = incidentRepository.findByStatusInAndCategoryOrderByLastSeenAtDescIdDesc(
                    ACTIVE_INCIDENT_STATUSES,
                    detector.category(),
                    PageRequest.of(0, 500));
            for (var incident : activeIncidents) {
                if (activeKeys.contains(incidentKey(incident))) {
                    continue;
                }
                var resolution = detector.resolve(incident, context);
                if (resolution.isEmpty()) {
                    continue;
                }
                incident.resolve(resolution.get().message(), context.now());
                incidentRepository.save(incident);
                recordResolutionEvent(incident, resolution.get(), run.startedByUserId());
                resolvedCount += 1;
            }
        }
        return resolvedCount;
    }

    private void recordOperationEvent(PlatformOpsDetectionFinding finding, Long actorUserId) {
        operationEventService.record(
                finding.officeId(),
                toOperationSeverity(finding.severity()),
                finding.code(),
                finding.workflowType(),
                finding.workflowKey(),
                finding.resourceType(),
                finding.resourceId(),
                actorUserId,
                null,
                finding.title(),
                finding.evidenceJson());
    }

    private void recordResolutionEvent(
            PlatformOpsIncident incident,
            PlatformOpsIncidentResolution resolution,
            Long actorUserId
    ) {
        operationEventService.record(
                incident.officeId(),
                OperationEventSeverity.INFO,
                resolution.code(),
                incident.category().toLowerCase().replace('_', '-'),
                incident.primaryResourceId(),
                incident.primaryResourceType(),
                parseLong(incident.primaryResourceId()),
                actorUserId,
                null,
                resolution.message(),
                resolution.evidenceJson());
    }

    private OperationEventSeverity toOperationSeverity(PlatformOpsFindingSeverity severity) {
        if (severity == PlatformOpsFindingSeverity.ERROR || severity == PlatformOpsFindingSeverity.CRITICAL) {
            return OperationEventSeverity.ERROR;
        }
        if (severity == PlatformOpsFindingSeverity.WARN) {
            return OperationEventSeverity.WARN;
        }
        return OperationEventSeverity.INFO;
    }

    private Map<String, Object> snapshot(
            List<PlatformOpsDetectionFinding> detected,
            int incidentCount,
            int findingCount,
            int resolvedCount
    ) {
        var byCategory = new LinkedHashMap<String, Object>();
        detected.stream()
                .map(PlatformOpsDetectionFinding::category)
                .distinct()
                .forEach(category -> byCategory.put(category, count(detected, category)));
        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("findingCount", findingCount);
        snapshot.put("incidentCount", incidentCount);
        snapshot.put("resolvedIncidentCount", resolvedCount);
        snapshot.put("byCategory", byCategory);
        return snapshot;
    }

    private String incidentKey(PlatformOpsDetectionFinding finding) {
        return finding.category() + "|" + finding.resourceType() + "|" + finding.resourceId();
    }

    private String incidentKey(PlatformOpsIncident incident) {
        return incident.category() + "|" + incident.primaryResourceType() + "|" + incident.primaryResourceId();
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private int count(List<PlatformOpsDetectionFinding> findings, String category) {
        return (int) findings.stream()
                .filter(finding -> category.equals(finding.category()))
                .count();
    }
}
