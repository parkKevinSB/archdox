package com.archdox.cloud.office.infra;

import com.archdox.cloud.office.domain.OfficeInvitation;
import com.archdox.cloud.office.domain.OfficeInvitationStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OfficeInvitationRepository extends JpaRepository<OfficeInvitation, Long> {
    @EntityGraph(attributePaths = {"office", "invitedBy", "acceptedBy"})
    List<OfficeInvitation> findByOfficeIdOrderByCreatedAtDesc(Long officeId);

    @EntityGraph(attributePaths = {"office", "invitedBy", "acceptedBy"})
    Optional<OfficeInvitation> findByIdAndOfficeId(Long id, Long officeId);

    @EntityGraph(attributePaths = {"office", "invitedBy", "acceptedBy"})
    Optional<OfficeInvitation> findByTokenHash(String tokenHash);

    @EntityGraph(attributePaths = {"office", "invitedBy", "acceptedBy"})
    Optional<OfficeInvitation> findFirstByOfficeIdAndEmailIgnoreCaseAndStatusOrderByCreatedAtDesc(
            Long officeId,
            String email,
            OfficeInvitationStatus status);
}
