package com.archdox.cloud.office.infra;

import com.archdox.cloud.office.domain.Office;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OfficeRepository extends JpaRepository<Office, Long> {
    Optional<Office> findByOfficeCodeIgnoreCase(String officeCode);
}
