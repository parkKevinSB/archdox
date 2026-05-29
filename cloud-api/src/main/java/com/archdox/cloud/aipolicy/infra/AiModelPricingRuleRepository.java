package com.archdox.cloud.aipolicy.infra;

import com.archdox.cloud.aipolicy.domain.AiModelPricingRule;
import com.archdox.cloud.aipolicy.domain.AiModelPricingRuleStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiModelPricingRuleRepository extends JpaRepository<AiModelPricingRule, Long> {
    List<AiModelPricingRule> findAllByOrderByProviderCodeAscModelNameAscCreatedAtDesc(Pageable pageable);

    List<AiModelPricingRule> findByStatusOrderByProviderCodeAscModelNameAscCreatedAtDesc(
            AiModelPricingRuleStatus status,
            Pageable pageable);

    Optional<AiModelPricingRule> findFirstByProviderCodeAndModelNameAndStatusOrderByCreatedAtDesc(
            String providerCode,
            String modelName,
            AiModelPricingRuleStatus status);

    boolean existsByProviderCodeAndModelNameAndStatus(
            String providerCode,
            String modelName,
            AiModelPricingRuleStatus status);
}
