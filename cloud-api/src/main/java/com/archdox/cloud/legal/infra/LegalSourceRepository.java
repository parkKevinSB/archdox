package com.archdox.cloud.legal.infra;

import com.archdox.cloud.legal.domain.LegalSource;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LegalSourceRepository extends JpaRepository<LegalSource, Long> {
    Optional<LegalSource> findByCode(String code);
}
