package com.archdox.cloud.inspectiontarget.infra;

import com.archdox.cloud.inspectiontarget.domain.InspectionTarget;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InspectionTargetRepository extends JpaRepository<InspectionTarget, Long> {
    List<InspectionTarget> findByOfficeIdAndSiteIdOrderById(Long officeId, Long siteId);

    Optional<InspectionTarget> findByIdAndOfficeId(Long id, Long officeId);
}
