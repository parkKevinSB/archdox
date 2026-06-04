package com.archdox.cloud.legal.infra;

import com.archdox.cloud.legal.domain.LegalVersion;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LegalVersionRepository extends JpaRepository<LegalVersion, Long> {
    Optional<LegalVersion> findByActIdAndSourceVersionKey(Long actId, String sourceVersionKey);

    Optional<LegalVersion> findFirstByActIdAndIdNotOrderByCapturedAtDescIdDesc(Long actId, Long id);

    Optional<LegalVersion> findFirstByActIdOrderByCapturedAtDescIdDesc(Long actId);
}
