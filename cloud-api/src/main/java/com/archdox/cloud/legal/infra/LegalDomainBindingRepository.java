package com.archdox.cloud.legal.infra;

import com.archdox.cloud.legal.domain.LegalDomainBinding;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LegalDomainBindingRepository extends JpaRepository<LegalDomainBinding, Long> {
    List<LegalDomainBinding> findByStatusAndCatalogCodeAndCatalogVersionAndChecklistItemCodeOrderByIdAsc(
            String status,
            String catalogCode,
            Integer catalogVersion,
            String checklistItemCode);

    List<LegalDomainBinding> findByStatusAndReportTypeOrderByIdAsc(String status, String reportType);
}
