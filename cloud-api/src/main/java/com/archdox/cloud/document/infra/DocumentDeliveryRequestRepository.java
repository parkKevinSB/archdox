package com.archdox.cloud.document.infra;

import com.archdox.cloud.document.domain.DocumentDeliveryRequest;
import com.archdox.cloud.document.domain.DocumentDeliveryStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DocumentDeliveryRequestRepository extends JpaRepository<DocumentDeliveryRequest, Long> {
    List<DocumentDeliveryRequest> findByOfficeIdAndDocumentJobIdOrderByRequestedAtDesc(Long officeId, Long documentJobId);

    List<DocumentDeliveryRequest> findByStatusOrderByRequestedAtAsc(DocumentDeliveryStatus status);

    Optional<DocumentDeliveryRequest> findByIdAndOfficeId(Long id, Long officeId);

    long countByOfficeId(Long officeId);

    long countByOfficeIdAndStatus(Long officeId, DocumentDeliveryStatus status);

    @Query("""
            select delivery
            from DocumentDeliveryRequest delivery
            where delivery.officeId = :officeId
              and (:status is null or delivery.status = :status)
            order by delivery.requestedAt desc, delivery.id desc
            """)
    List<DocumentDeliveryRequest> searchOfficeDeliveries(
            Long officeId,
            DocumentDeliveryStatus status,
            Pageable pageable);
}
