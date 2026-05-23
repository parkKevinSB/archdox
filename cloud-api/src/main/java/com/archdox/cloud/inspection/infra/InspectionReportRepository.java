package com.archdox.cloud.inspection.infra;

import com.archdox.cloud.inspection.domain.InspectionReport;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InspectionReportRepository extends JpaRepository<InspectionReport, Long> {
    List<InspectionReport> findByOfficeIdOrderByUpdatedAtDesc(Long officeId);

    List<InspectionReport> findByOfficeIdAndProjectIdOrderByUpdatedAtDesc(Long officeId, Long projectId);

    Optional<InspectionReport> findByIdAndOfficeId(Long id, Long officeId);
}
