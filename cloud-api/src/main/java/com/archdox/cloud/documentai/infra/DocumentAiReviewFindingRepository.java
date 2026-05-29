package com.archdox.cloud.documentai.infra;

import com.archdox.cloud.documentai.domain.DocumentAiReviewFinding;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentAiReviewFindingRepository extends JpaRepository<DocumentAiReviewFinding, Long> {
    List<DocumentAiReviewFinding> findByOfficeIdAndReviewRunIdOrderByIdAsc(Long officeId, Long reviewRunId);

    long countByOfficeIdAndReviewRunId(Long officeId, Long reviewRunId);

    void deleteByReviewRunId(Long reviewRunId);
}
