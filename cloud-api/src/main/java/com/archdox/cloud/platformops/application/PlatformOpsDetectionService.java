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
    private final PlatformOpsDetectionProperties properties;
    private final PlatformOpsRunRepository runRepository;
    private final PlatformOpsIncidentRepository incidentRepository;
    private final PlatformOpsFindingRepository findingRepository;
    private final OperationEventService operationEventService;

    public PlatformOpsDetectionService(
            List<PlatformOpsDetector> detectors,
            PlatformOpsDetectionProperties properties,
            PlatformOpsRunRepository runRepository,
            PlatformOpsIncidentRepository incidentRepository,
            PlatformOpsFindingRepository findingRepository,
            OperationEventService operationEventService
    ) {
        this.detectors = List.copyOf(detectors);
        this.properties = properties;
        this.runRepository = runRepository;
        this.incidentRepository = incidentRepository;
        this.findingRepository = findingRepository;
        this.operationEventService = operationEventService;
    }

    @Transactional
    public PlatformOpsDetectionSummary requestStuckDetection(Long startedByUserId) {
        var now = OffsetDateTime.now();
        var run = runRepository.save(new PlatformOpsRun(
                PlatformOpsRunTriggerType.MANUAL_DETECT_STUCK,
                startedByUserId,
                Map.of(
                        "state", "REQUESTED",
                        "detectorCount", detectors.size(),
                        "maxDetectedItems", Math.max(1, properties.getMaxDetectedItems())),
                now));
        return new PlatformOpsDetectionSummary(run.id(), 0, 0, 0, 0, 0, 0, now);
    }

    @Transactional
    public PlatformOpsDetectionSummary executeStuckDetection(Long runId) {
        var run = runRepository.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Platform ops run not found: " + runId));
        return execute(run, OffsetDateTime.now(), PageRequest.of(0, Math.max(1, properties.getMaxDetectedItems())));
    }

    @Transactional
    public PlatformOpsDetectionSummary detectStuck(Long startedByUserId) {
        var now = OffsetDateTime.now();
        var run = runRepository.save(new PlatformOpsRun(
                PlatformOpsRunTriggerType.MANUAL_DETECT_STUCK,
                startedByUserId,
                Map.of(
                        "detectorCount", detectors.size(),
                        "maxDetectedItems", Math.max(1, properties.getMaxDetectedItems())),
                now));
        return execute(run, now, PageRequest.of(0, Math.max(1, properties.getMaxDetectedItems())));
    }

    @Transactional
    public void markRunFailed(Long runId, String failureCode) {
        runRepository.findById(runId).ifPresent(run -> run.fail(failureCode, OffsetDateTime.now()));
    }

    private PlatformOpsDetectionSummary execute(PlatformOpsRun run, OffsetDateTime now, Pageable page) {
        var context = new PlatformOpsDetectionContext(now, page);

        var detected = new ArrayList<PlatformOpsDetectionFinding>();
        detectors.forEach(detector -> detected.addAll(detector.detect(context)));

        var incidentCount = 0;
        var findingCount = 0;
        for (var detectedFinding : detected) {
            var incident = upsertIncident(run, detectedFinding, now);
            findingRepository.save(toFinding(run, incident, detectedFinding, now));
            recordOperationEvent(detectedFinding, run.startedByUserId());
            incidentCount += 1;
            findingCount += 1;
        }

        run.replaceSnapshot(snapshot(detected, incidentCount, findingCount));
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
            int findingCount
    ) {
        var byCategory = new LinkedHashMap<String, Object>();
        detected.stream()
                .map(PlatformOpsDetectionFinding::category)
                .distinct()
                .forEach(category -> byCategory.put(category, count(detected, category)));
        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("findingCount", findingCount);
        snapshot.put("incidentCount", incidentCount);
        snapshot.put("byCategory", byCategory);
        return snapshot;
    }

    private int count(List<PlatformOpsDetectionFinding> findings, String category) {
        return (int) findings.stream()
                .filter(finding -> category.equals(finding.category()))
                .count();
    }
}
