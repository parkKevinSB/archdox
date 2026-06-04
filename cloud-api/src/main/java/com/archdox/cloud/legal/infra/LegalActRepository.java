package com.archdox.cloud.legal.infra;

import com.archdox.cloud.legal.domain.LegalAct;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LegalActRepository extends JpaRepository<LegalAct, Long> {
    Optional<LegalAct> findBySourceIdAndActCode(Long sourceId, String actCode);
}
