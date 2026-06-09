package com.archdox.cloud.aipolicy.infra;

import com.archdox.cloud.aipolicy.domain.AiUserBudgetOverride;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiUserBudgetOverrideRepository extends JpaRepository<AiUserBudgetOverride, Long> {
    @Query("""
            select item
            from AiUserBudgetOverride item
            where item.officeId = :officeId
              and item.userId = :userId
              and item.disabledAt is null
              and (item.expiresAt is null or item.expiresAt > :now)
            order by item.createdAt desc
            """)
    List<AiUserBudgetOverride> findActiveByOfficeIdAndUserId(
            @Param("officeId") Long officeId,
            @Param("userId") Long userId,
            @Param("now") OffsetDateTime now);

    @Query("""
            select item
            from AiUserBudgetOverride item
            where item.disabledAt is null
              and (item.expiresAt is null or item.expiresAt > :now)
            order by item.createdAt desc
            """)
    List<AiUserBudgetOverride> findActiveAt(@Param("now") OffsetDateTime now, Pageable pageable);

    List<AiUserBudgetOverride> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
