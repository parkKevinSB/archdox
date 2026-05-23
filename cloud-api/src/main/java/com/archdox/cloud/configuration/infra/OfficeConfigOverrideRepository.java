package com.archdox.cloud.configuration.infra;

import com.archdox.cloud.configuration.domain.OfficeConfigOverride;
import com.archdox.cloud.configuration.domain.OfficeConfigOverrideStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OfficeConfigOverrideRepository extends JpaRepository<OfficeConfigOverride, Long> {
    @EntityGraph(attributePaths = {
            "templateRevision.template",
            "workflowRevision.definition",
            "ruleSetRevision.ruleSet",
            "outputLayoutRevision.config"
    })
    Optional<OfficeConfigOverride> findByOfficeIdAndReportTypeAndStatus(
            Long officeId,
            String reportType,
            OfficeConfigOverrideStatus status);

    @EntityGraph(attributePaths = {
            "templateRevision.template",
            "workflowRevision.definition",
            "ruleSetRevision.ruleSet",
            "outputLayoutRevision.config"
    })
    List<OfficeConfigOverride> findByOfficeIdOrderByUpdatedAtDesc(Long officeId);
}
