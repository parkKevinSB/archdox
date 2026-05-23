package com.archdox.cloud.site.infra;

import com.archdox.cloud.site.domain.Site;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SiteRepository extends JpaRepository<Site, Long> {
    List<Site> findByOfficeIdAndProjectIdOrderByUpdatedAtDesc(Long officeId, Long projectId);

    Optional<Site> findByIdAndOfficeId(Long id, Long officeId);
}
