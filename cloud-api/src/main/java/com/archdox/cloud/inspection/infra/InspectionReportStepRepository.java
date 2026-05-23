package com.archdox.cloud.inspection.infra;

import com.archdox.cloud.inspection.domain.InspectionReportStep;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InspectionReportStepRepository extends JpaRepository<InspectionReportStep, Long> {
    Optional<InspectionReportStep> findByReportIdAndStepCode(Long reportId, String stepCode);

    List<InspectionReportStep> findByReportIdOrderById(Long reportId);

    boolean existsByReportIdAndStepCode(Long reportId, String stepCode);
}
