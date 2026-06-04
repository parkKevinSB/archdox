package com.archdox.cloud.supervisionledger.infra;

import com.archdox.cloud.supervisionledger.domain.SiteSupervisionEntry;
import com.archdox.cloud.supervisionledger.domain.SiteSupervisionEntryStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SiteSupervisionEntryRepository extends JpaRepository<SiteSupervisionEntry, Long> {
    Optional<SiteSupervisionEntry> findByIdAndOfficeId(Long id, Long officeId);

    List<SiteSupervisionEntry> findByOfficeIdAndProjectIdAndSiteIdOrderByEntryDateDescUpdatedAtDescIdDesc(
            Long officeId,
            Long projectId,
            Long siteId);

    @Modifying
    int deleteByOfficeIdAndSourceReportIdAndSourceReportRevisionAndSourceStepCode(
            Long officeId,
            Long sourceReportId,
            int sourceReportRevision,
            String sourceStepCode);

    @Modifying
    @Query("""
            update SiteSupervisionEntry entry
            set entry.status = :status,
                entry.updatedBy = :updatedBy,
                entry.updatedAt = :updatedAt
            where entry.officeId = :officeId
              and entry.sourceReportId = :sourceReportId
              and entry.sourceReportRevision = :sourceReportRevision
            """)
    int updateStatusForReportRevision(
            Long officeId,
            Long sourceReportId,
            int sourceReportRevision,
            SiteSupervisionEntryStatus status,
            Long updatedBy,
            OffsetDateTime updatedAt);
}
