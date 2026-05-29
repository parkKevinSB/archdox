package com.archdox.cloud.documentai.infra;

import com.archdox.cloud.documentai.domain.DocumentAiReviewRun;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentAiReviewRunRepository extends JpaRepository<DocumentAiReviewRun, Long> {
    Optional<DocumentAiReviewRun> findByHarnessRunId(String harnessRunId);

    Optional<DocumentAiReviewRun> findByIdAndOfficeId(Long id, Long officeId);

    Optional<DocumentAiReviewRun> findByIdAndOfficeIdAndDocumentJobId(Long id, Long officeId, Long documentJobId);

    List<DocumentAiReviewRun> findByOfficeIdAndDocumentJobIdOrderByRequestedAtDesc(Long officeId, Long documentJobId);
}
