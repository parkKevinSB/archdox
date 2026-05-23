package com.archdox.cloud.office.infra;

import com.archdox.cloud.office.domain.OfficeMembership;
import com.archdox.shared.MembershipRole;
import com.archdox.shared.MembershipStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OfficeMembershipRepository extends JpaRepository<OfficeMembership, Long> {
    @EntityGraph(attributePaths = "office")
    List<OfficeMembership> findByUserId(Long userId);

    @EntityGraph(attributePaths = "office")
    List<OfficeMembership> findByUserIdAndStatus(Long userId, MembershipStatus status);

    @EntityGraph(attributePaths = "user")
    List<OfficeMembership> findByOfficeId(Long officeId);

    boolean existsByUserIdAndOfficeId(Long userId, Long officeId);

    boolean existsByUserIdAndOfficeIdAndStatus(Long userId, Long officeId, MembershipStatus status);

    long countByOfficeIdAndRoleAndStatus(Long officeId, MembershipRole role, MembershipStatus status);

    Optional<OfficeMembership> findByUserIdAndOfficeId(Long userId, Long officeId);

    Optional<OfficeMembership> findByUserIdAndOfficeIdAndStatus(
            Long userId,
            Long officeId,
            MembershipStatus status);
}
