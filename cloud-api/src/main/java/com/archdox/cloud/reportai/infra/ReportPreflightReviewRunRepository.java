package com.archdox.cloud.reportai.infra;

import com.archdox.cloud.reportai.domain.ReportPreflightReviewRun;
import com.archdox.cloud.reportai.domain.ReportPreflightReviewStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportPreflightReviewRunRepository extends JpaRepository<ReportPreflightReviewRun, Long> {
    List<ReportPreflightReviewRun> findByOfficeIdAndReportIdOrderByRequestedAtDesc(Long officeId, Long reportId);

    Optional<ReportPreflightReviewRun> findByIdAndOfficeIdAndReportId(Long id, Long officeId, Long reportId);

    Optional<ReportPreflightReviewRun> findByHarnessRunId(String harnessRunId);

    boolean existsByOfficeIdAndReportIdAndReportRevisionAndStatus(
            Long officeId,
            Long reportId,
            int reportRevision,
            ReportPreflightReviewStatus status);

    Optional<ReportPreflightReviewRun> findFirstByOfficeIdAndReportIdAndStatusOrderByReportRevisionDescRequestedAtDesc(
            Long officeId,
            Long reportId,
            ReportPreflightReviewStatus status);
}
