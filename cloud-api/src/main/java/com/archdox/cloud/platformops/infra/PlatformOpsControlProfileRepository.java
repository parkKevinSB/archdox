package com.archdox.cloud.platformops.infra;

import com.archdox.cloud.platformops.domain.PlatformOpsControlProfile;
import com.archdox.cloud.platformops.domain.PlatformOpsControlProfileScope;
import com.archdox.cloud.platformops.domain.PlatformOpsControlProfileStatus;
import com.archdox.cloud.platformops.domain.PlatformOpsControlSignalKind;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlatformOpsControlProfileRepository extends JpaRepository<PlatformOpsControlProfile, Long> {
    @Query("""
            select profile
            from PlatformOpsControlProfile profile
            where (:status is not null and profile.status = :status)
               or (:status is null and profile.status <> :hiddenStatus)
            order by profile.lastObservedAt desc, profile.id desc
            """)
    List<PlatformOpsControlProfile> search(
            @Param("status") PlatformOpsControlProfileStatus status,
            @Param("hiddenStatus") PlatformOpsControlProfileStatus hiddenStatus,
            Pageable pageable);

    @Query("""
            select profile
            from PlatformOpsControlProfile profile
            where profile.signalKind = :signalKind
              and profile.scopeType = :scopeType
              and coalesce(profile.modelId, '') = coalesce(:modelId, '')
              and profile.signalKey = :signalKey
            order by profile.id desc
            """)
    Optional<PlatformOpsControlProfile> findSignal(
            @Param("signalKind") PlatformOpsControlSignalKind signalKind,
            @Param("scopeType") PlatformOpsControlProfileScope scopeType,
            @Param("modelId") String modelId,
            @Param("signalKey") String signalKey);
}
