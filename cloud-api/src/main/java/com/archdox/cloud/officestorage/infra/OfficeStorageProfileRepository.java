package com.archdox.cloud.officestorage.infra;

import com.archdox.cloud.officestorage.domain.OfficeStorageProfile;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OfficeStorageProfileRepository extends JpaRepository<OfficeStorageProfile, Long> {
    List<OfficeStorageProfile> findByOfficeIdOrderByCreatedAtDesc(Long officeId);

    Optional<OfficeStorageProfile> findByOfficeIdAndId(Long officeId, Long id);

    Optional<OfficeStorageProfile> findByOfficeIdAndProfileCode(Long officeId, String profileCode);
}
