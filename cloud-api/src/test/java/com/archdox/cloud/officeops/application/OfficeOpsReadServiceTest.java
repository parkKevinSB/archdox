package com.archdox.cloud.officeops.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.archdox.cloud.agent.domain.ArchDoxAgentStatus;
import com.archdox.cloud.agent.infra.ArchDoxAgentCommandRepository;
import com.archdox.cloud.agent.infra.ArchDoxAgentRepository;
import com.archdox.cloud.agent.infra.ArchDoxAgentSessionRepository;
import com.archdox.cloud.document.domain.DocumentJobStatus;
import com.archdox.cloud.document.infra.DocumentArtifactRepository;
import com.archdox.cloud.document.infra.DocumentDeliveryRequestRepository;
import com.archdox.cloud.document.infra.DocumentJobRepository;
import com.archdox.cloud.global.api.ForbiddenException;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.office.application.OfficeContext;
import com.archdox.cloud.office.domain.OfficeMembership;
import com.archdox.cloud.office.infra.OfficeMembershipRepository;
import com.archdox.cloud.platformadmin.application.PlatformAdminService;
import com.archdox.cloud.photo.infra.PhotoAssetRepository;
import com.archdox.cloud.photo.infra.PhotoRepository;
import com.archdox.shared.MembershipRole;
import com.archdox.shared.MembershipStatus;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OfficeOpsReadServiceTest {
    private final OfficeMembershipRepository membershipRepository = mock(OfficeMembershipRepository.class);
    private final PlatformAdminService platformAdminService = mock(PlatformAdminService.class);
    private final ArchDoxAgentRepository agentRepository = mock(ArchDoxAgentRepository.class);
    private final ArchDoxAgentSessionRepository sessionRepository = mock(ArchDoxAgentSessionRepository.class);
    private final ArchDoxAgentCommandRepository commandRepository = mock(ArchDoxAgentCommandRepository.class);
    private final DocumentJobRepository documentJobRepository = mock(DocumentJobRepository.class);
    private final DocumentArtifactRepository artifactRepository = mock(DocumentArtifactRepository.class);
    private final PhotoRepository photoRepository = mock(PhotoRepository.class);
    private final PhotoAssetRepository photoAssetRepository = mock(PhotoAssetRepository.class);
    private final DocumentDeliveryRequestRepository deliveryRepository = mock(DocumentDeliveryRequestRepository.class);
    private final OfficeAdminAccessService officeAdminAccessService =
            new OfficeAdminAccessService(membershipRepository, platformAdminService);

    private final OfficeOpsReadService service = new OfficeOpsReadService(
            officeAdminAccessService,
            agentRepository,
            sessionRepository,
            commandRepository,
            documentJobRepository,
            artifactRepository,
            photoRepository,
            photoAssetRepository,
            deliveryRepository);

    @AfterEach
    void clearOfficeContext() {
        OfficeContext.clear();
    }

    @Test
    void ownerCanReadSummary() {
        OfficeContext.set(10L);
        var membership = mock(OfficeMembership.class);
        when(membership.role()).thenReturn(MembershipRole.OWNER);
        when(membershipRepository.findByUserIdAndOfficeIdAndStatus(1L, 10L, MembershipStatus.ACTIVE))
                .thenReturn(Optional.of(membership));
        when(agentRepository.countByOfficeId(10L)).thenReturn(2L);
        when(agentRepository.countByOfficeIdAndStatus(10L, ArchDoxAgentStatus.ONLINE)).thenReturn(1L);
        when(documentJobRepository.countByOfficeIdAndStatus(10L, DocumentJobStatus.GENERATING)).thenReturn(3L);

        var response = service.getSummary(new UserPrincipal(1L, "owner@example.com"));

        assertEquals(10L, response.officeId());
        assertEquals(2L, response.agents().total());
        assertEquals(1L, response.agents().byStatus().get("ONLINE"));
        assertEquals(3L, response.documentJobs().byStatus().get("GENERATING"));
    }

    @Test
    void memberCannotReadOpsData() {
        OfficeContext.set(10L);
        var membership = mock(OfficeMembership.class);
        when(membership.role()).thenReturn(MembershipRole.MEMBER);
        when(membershipRepository.findByUserIdAndOfficeIdAndStatus(1L, 10L, MembershipStatus.ACTIVE))
                .thenReturn(Optional.of(membership));

        assertThrows(
                ForbiddenException.class,
                () -> service.getSummary(new UserPrincipal(1L, "member@example.com")));
    }

    @Test
    void platformAdminCanReadSummaryWithoutOfficeMembership() {
        OfficeContext.set(10L);
        var principal = new UserPrincipal(1L, "platform@example.com");
        when(platformAdminService.isPlatformAdmin(principal)).thenReturn(true);
        when(agentRepository.countByOfficeId(10L)).thenReturn(2L);
        when(agentRepository.countByOfficeIdAndStatus(10L, ArchDoxAgentStatus.ONLINE)).thenReturn(1L);
        when(documentJobRepository.countByOfficeIdAndStatus(10L, DocumentJobStatus.GENERATING)).thenReturn(3L);

        var response = service.getSummary(principal);

        assertEquals(10L, response.officeId());
        assertEquals(2L, response.agents().total());
        assertEquals(1L, response.agents().byStatus().get("ONLINE"));
        assertEquals(3L, response.documentJobs().byStatus().get("GENERATING"));
    }
}
