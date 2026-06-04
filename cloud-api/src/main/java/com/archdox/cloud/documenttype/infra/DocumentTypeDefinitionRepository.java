package com.archdox.cloud.documenttype.infra;

import com.archdox.cloud.documenttype.domain.DocumentTypeDefinition;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentTypeDefinitionRepository extends JpaRepository<DocumentTypeDefinition, Long> {
    @Query("""
            select definition from DocumentTypeDefinition definition
            where definition.active = true
              and (definition.officeId is null or definition.officeId = :officeId)
              and not (
                    definition.officeId is null
                and exists (
                    select 1 from DocumentTypeDefinition officeDefinition
                    where officeDefinition.active = true
                      and officeDefinition.officeId = :officeId
                      and upper(officeDefinition.code) = upper(definition.code)
                )
              )
            order by
              case when definition.officeId = :officeId then 0 else 1 end,
              definition.displayOrder asc,
              definition.id asc
            """)
    List<DocumentTypeDefinition> findVisible(@Param("officeId") Long officeId);

    @Query("""
            select definition from DocumentTypeDefinition definition
            where definition.active = true
              and definition.officeId is null
            order by definition.displayOrder asc, definition.id asc
            """)
    List<DocumentTypeDefinition> findSystemVisible();

    @Query("""
            select definition from DocumentTypeDefinition definition
            where definition.active = true
              and (definition.officeId is null or definition.officeId = :officeId)
              and (
                    upper(definition.code) = :normalizedCode
                 or upper(definition.reportType) = :normalizedCode
              )
            order by
              case when definition.officeId = :officeId then 0 else 1 end,
              definition.displayOrder asc,
              definition.id asc
            """)
    List<DocumentTypeDefinition> findResolutionCandidates(
            @Param("officeId") Long officeId,
            @Param("normalizedCode") String normalizedCode
    );

    @Query("""
            select definition from DocumentTypeDefinition definition
            where definition.active = true
              and definition.officeId is null
              and (
                    upper(definition.code) = :normalizedCode
                 or upper(definition.reportType) = :normalizedCode
              )
            order by definition.displayOrder asc, definition.id asc
            """)
    List<DocumentTypeDefinition> findSystemResolutionCandidates(
            @Param("normalizedCode") String normalizedCode
    );

    default Optional<DocumentTypeDefinition> resolve(Long officeId, String normalizedCode) {
        return findResolutionCandidates(officeId, normalizedCode).stream().findFirst();
    }
}
