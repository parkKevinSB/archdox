package com.archdox.cloud.document.infra;

import com.archdox.cloud.document.domain.DocumentArtifact;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentArtifactRepository extends JpaRepository<DocumentArtifact, Long> {
    List<DocumentArtifact> findByDocumentJobIdOrderById(Long documentJobId);

    List<DocumentArtifact> findByDocumentJobIdInOrderByDocumentJobIdAscIdAsc(List<Long> documentJobIds);

    Optional<DocumentArtifact> findByIdAndOfficeId(Long id, Long officeId);

    void deleteByDocumentJobId(Long documentJobId);
}
