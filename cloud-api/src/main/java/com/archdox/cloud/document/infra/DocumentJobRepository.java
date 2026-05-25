package com.archdox.cloud.document.infra;

import com.archdox.cloud.document.domain.DocumentJob;
import com.archdox.cloud.document.domain.DocumentJobStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.time.OffsetDateTime;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DocumentJobRepository extends JpaRepository<DocumentJob, Long> {
    List<DocumentJob> findByOfficeIdAndReportIdOrderByRequestedAtDesc(Long officeId, Long reportId);

    List<DocumentJob> findByStatusInOrderByRequestedAtAsc(Collection<DocumentJobStatus> statuses);

    List<DocumentJob> findByStatusInAndUpdatedAtBeforeOrderByUpdatedAtAsc(
            Collection<DocumentJobStatus> statuses,
            OffsetDateTime updatedBefore,
            Pageable pageable);

    Optional<DocumentJob> findByIdAndOfficeId(Long id, Long officeId);

    long countByOfficeId(Long officeId);

    long countByOfficeIdAndStatus(Long officeId, DocumentJobStatus status);

    long countByStatus(DocumentJobStatus status);

    @Query("""
            select job
            from DocumentJob job
            where job.officeId = :officeId
              and (:status is null or job.status = :status)
            order by job.requestedAt desc, job.id desc
            """)
    List<DocumentJob> searchOfficeJobs(Long officeId, DocumentJobStatus status, Pageable pageable);

    @Query("""
            select job
            from DocumentJob job
            where (:officeId is null or job.officeId = :officeId)
              and (:status is null or job.status = :status)
            order by job.requestedAt desc, job.id desc
            """)
    List<DocumentJob> searchPlatformJobs(Long officeId, DocumentJobStatus status, Pageable pageable);
}
