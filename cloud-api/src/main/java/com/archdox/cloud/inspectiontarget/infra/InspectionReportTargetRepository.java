package com.archdox.cloud.inspectiontarget.infra;

import com.archdox.cloud.inspectiontarget.domain.InspectionReportTarget;
import com.archdox.cloud.inspectiontarget.domain.InspectionReportTargetRole;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InspectionReportTargetRepository extends JpaRepository<InspectionReportTarget, Long> {
    List<InspectionReportTarget> findByOfficeIdAndReportIdOrderByRoleAscIdAsc(Long officeId, Long reportId);

    Optional<InspectionReportTarget> findByOfficeIdAndReportIdAndTargetIdAndRole(
            Long officeId,
            Long reportId,
            Long targetId,
            InspectionReportTargetRole role
    );
}
