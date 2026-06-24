package com.archdox.cloud.inspection.infra;

import com.archdox.cloud.inspection.domain.InspectionReport;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InspectionReportRepository extends JpaRepository<InspectionReport, Long> {
    List<InspectionReport> findByOfficeIdOrderByUpdatedAtDesc(Long officeId);

    List<InspectionReport> findByOfficeIdAndProjectIdOrderByUpdatedAtDesc(Long officeId, Long projectId);

    Optional<InspectionReport> findByIdAndOfficeId(Long id, Long officeId);

    @Query("""
            select report.reportNo
            from InspectionReport report
            where report.officeId = :officeId
              and report.projectId = :projectId
              and report.reportNo like concat(:prefix, '%')
            order by report.reportNo desc
            """)
    List<String> findReportNosByPrefix(
            @Param("officeId") Long officeId,
            @Param("projectId") Long projectId,
            @Param("prefix") String prefix);
}
