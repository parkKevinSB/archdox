package com.archdox.cloud.reportai.infra;

import com.archdox.cloud.reportai.domain.ReportPreflightFindingResolutionStatus;
import com.archdox.cloud.reportai.domain.ReportPreflightReviewFinding;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportPreflightReviewFindingRepository extends JpaRepository<ReportPreflightReviewFinding, Long> {
    List<ReportPreflightReviewFinding> findByOfficeIdAndReviewRunIdOrderByIdAsc(Long officeId, Long reviewRunId);

    Optional<ReportPreflightReviewFinding> findByIdAndOfficeIdAndReviewRunIdAndReportId(
            Long id,
            Long officeId,
            Long reviewRunId,
            Long reportId);

    @Query("""
            select finding
            from ReportPreflightReviewFinding finding
            where (:officeId is null or finding.officeId = :officeId)
              and (:severity is null or finding.severity = :severity)
              and (:resolutionStatus is null or finding.resolutionStatus = :resolutionStatus)
              and (:source is null or finding.source = :source)
            order by finding.createdAt desc, finding.id desc
            """)
    List<ReportPreflightReviewFinding> searchPlatformFindings(
            @Param("officeId") Long officeId,
            @Param("severity") String severity,
            @Param("resolutionStatus") ReportPreflightFindingResolutionStatus resolutionStatus,
            @Param("source") String source,
            Pageable pageable);

    long countByOfficeIdAndReviewRunId(Long officeId, Long reviewRunId);

    void deleteByReviewRunId(Long reviewRunId);

    void deleteByReviewRunIdAndSource(Long reviewRunId, String source);
}
