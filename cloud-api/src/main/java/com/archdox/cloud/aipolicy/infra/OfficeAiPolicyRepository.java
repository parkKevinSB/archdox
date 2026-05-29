package com.archdox.cloud.aipolicy.infra;

import com.archdox.cloud.aipolicy.domain.OfficeAiPolicy;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OfficeAiPolicyRepository extends JpaRepository<OfficeAiPolicy, Long> {
    Optional<OfficeAiPolicy> findByOfficeId(Long officeId);

    List<OfficeAiPolicy> findByPreferredProviderCredentialIdAndAiEnabledTrue(Long preferredProviderCredentialId);
}
