package com.archdox.cloud.checklist.infra;

import com.archdox.cloud.checklist.domain.InspectionChecklistAnswer;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InspectionChecklistAnswerRepository extends JpaRepository<InspectionChecklistAnswer, Long> {
    List<InspectionChecklistAnswer> findByOfficeIdAndReportIdOrderById(Long officeId, Long reportId);

    Optional<InspectionChecklistAnswer> findByOfficeIdAndReportIdAndChecklistItemIdAndTargetId(
            Long officeId,
            Long reportId,
            Long checklistItemId,
            Long targetId
    );

    Optional<InspectionChecklistAnswer> findByOfficeIdAndReportIdAndChecklistItemIdAndTargetIdIsNull(
            Long officeId,
            Long reportId,
            Long checklistItemId
    );

    boolean existsByOfficeIdAndReportId(Long officeId, Long reportId);
}
