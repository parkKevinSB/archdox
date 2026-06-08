package com.archdox.cloud.aipolicy.infra;

import com.archdox.cloud.aipolicy.domain.AiHarnessPolicy;
import com.archdox.cloud.aipolicy.domain.AiHarnessPolicyKey;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiHarnessPolicyRepository extends JpaRepository<AiHarnessPolicy, Long> {
    Optional<AiHarnessPolicy> findByPolicyKey(AiHarnessPolicyKey policyKey);

    List<AiHarnessPolicy> findAllByOrderByPolicyKeyAsc();
}
