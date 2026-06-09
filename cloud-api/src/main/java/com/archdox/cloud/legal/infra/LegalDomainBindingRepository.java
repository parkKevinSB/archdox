package com.archdox.cloud.legal.infra;

import com.archdox.cloud.legal.domain.LegalDomainBinding;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LegalDomainBindingRepository extends JpaRepository<LegalDomainBinding, Long> {
    List<LegalDomainBinding> findByStatusAndCatalogCodeAndCatalogVersionAndChecklistItemCodeOrderByIdAsc(
            String status,
            String catalogCode,
            Integer catalogVersion,
            String checklistItemCode);

    List<LegalDomainBinding> findByStatusAndReportTypeOrderByIdAsc(String status, String reportType);

    @Query("""
            select binding
            from LegalDomainBinding binding
            where (:status is null or binding.status = :status)
              and (:bindingScope is null or binding.bindingScope = :bindingScope)
              and (:bindingKey is null or binding.bindingKey = :bindingKey)
              and (:reportType is null or binding.reportType = :reportType)
              and (:catalogCode is null or binding.catalogCode = :catalogCode)
              and (:catalogVersion is null or binding.catalogVersion = :catalogVersion)
              and (:checklistItemCode is null or binding.checklistItemCode = :checklistItemCode)
            order by binding.updatedAt desc, binding.id desc
            """)
    List<LegalDomainBinding> search(
            @Param("status") String status,
            @Param("bindingScope") String bindingScope,
            @Param("bindingKey") String bindingKey,
            @Param("reportType") String reportType,
            @Param("catalogCode") String catalogCode,
            @Param("catalogVersion") Integer catalogVersion,
            @Param("checklistItemCode") String checklistItemCode,
            Pageable pageable);
}
