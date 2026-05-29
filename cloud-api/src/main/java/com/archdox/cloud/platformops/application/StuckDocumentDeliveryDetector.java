package com.archdox.cloud.platformops.application;

import com.archdox.cloud.document.domain.DocumentDeliveryStatus;
import com.archdox.cloud.document.infra.DocumentDeliveryRequestRepository;
import com.archdox.cloud.platformops.domain.PlatformOpsFindingSeverity;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class StuckDocumentDeliveryDetector implements PlatformOpsDetector {
    private final DocumentDeliveryRequestRepository repository;
    private final PlatformOpsDetectionProperties properties;

    public StuckDocumentDeliveryDetector(DocumentDeliveryRequestRepository repository, PlatformOpsDetectionProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Override
    public String category() {
        return "DOCUMENT_DELIVERY_STUCK";
    }

    @Override
    public List<PlatformOpsDetectionFinding> detect(PlatformOpsDetectionContext context) {
        var cutoff = context.now().minusMinutes(properties.getDeliveryStuckMinutes());
        return repository.findByStatusInAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                        List.of(DocumentDeliveryStatus.REQUESTED, DocumentDeliveryStatus.SENDING),
                        cutoff,
                        context.page())
                .stream()
                .map(delivery -> {
                    var evidence = new LinkedHashMap<String, Object>();
                    evidence.put("documentJobId", delivery.documentJobId());
                    evidence.put("status", delivery.status().name());
                    evidence.put("agentCommandId", delivery.agentCommandId() == null ? "null" : delivery.agentCommandId());
                    evidence.put("updatedAt", delivery.updatedAt().toString());
                    evidence.put("thresholdMinutes", properties.getDeliveryStuckMinutes());
                    return new PlatformOpsDetectionFinding(
                            delivery.officeId(),
                            PlatformOpsFindingSeverity.WARN,
                            category(),
                            "DOCUMENT_DELIVERY_STUCK_DETECTED",
                            "Document delivery appears stuck",
                            "A document delivery request has not completed within the expected time window.",
                            "DOCUMENT_DELIVERY_REQUEST",
                            String.valueOf(delivery.id()),
                            "document-delivery",
                            String.valueOf(delivery.id()),
                            evidence,
                            "Check artifact availability, Agent delivery command state, and delivery retry policy.");
                })
                .toList();
    }
}
