package com.archdox.cloud.worker.governance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.operation.application.OperationEventService;
import com.archdox.cloud.operation.domain.OperationEvent;
import com.archdox.cloud.operation.infra.OperationEventRepository;
import com.archdox.cloud.platformadmin.application.PlatformAdminService;
import com.archdox.cloud.platformadmin.domain.PlatformAdmin;
import com.archdox.worker.application.ArchDoxWorkerActionRegistry;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

class WorkerGovernanceReadServiceTest {
    private final PlatformAdminService platformAdminService = mock(PlatformAdminService.class);
    private final OperationEventRepository eventRepository = mock(OperationEventRepository.class);
    private final OperationEventService operationEventService = mock(OperationEventService.class);
    private final WorkerGovernanceReadService service = new WorkerGovernanceReadService(
            platformAdminService,
            eventRepository,
            operationEventService,
            new ArchDoxWorkerActionRegistry(List.of()));
    private final UserPrincipal principal = new UserPrincipal(7L, "platform@test.co.kr");

    @Test
    void summarizesWorkerGovernanceFromExistingOperationEvents() {
        when(platformAdminService.requirePlatformAdmin(principal)).thenReturn(mock(PlatformAdmin.class));
        when(eventRepository.summarizeWorkerEventTypes(eq(3L), any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(List.of(
                        eventType("ARCHDOX_WORKER_REQUEST_RECEIVED", 10L),
                        eventType("ARCHDOX_WORKER_POLICY_ALLOWED", 7L),
                        eventType("ARCHDOX_WORKER_POLICY_DENIED", 2L),
                        eventType("ARCHDOX_WORKER_APPROVAL_REQUIRED", 1L),
                        eventType("ARCHDOX_WORKER_ACTION_SUCCEEDED", 6L),
                        eventType("ARCHDOX_WORKER_ACTION_CANCELLED", 3L),
                        eventType("ARCHDOX_WORKER_ACTION_FAILED", 1L)));
        when(eventRepository.summarizeWorkerActionEvents(eq(3L), any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(List.of(actionEvent("CREATE_REPORT", "ARCHDOX_WORKER_POLICY_DENIED", 2L)));
        when(eventRepository.summarizeWorkerReasons(eq(3L), any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(List.of(reason("ARCHDOX_WORKER_POLICY_DENIED", "ACTION_REQUIRES_APPROVAL", 2L)));
        when(eventRepository.searchPlatformEvents(
                eq(3L),
                eq(null),
                eq("archdox-worker"),
                eq(null),
                eq(null),
                eq(null),
                any(Pageable.class)))
                .thenReturn(List.of(mock(OperationEvent.class)));

        var summary = service.summary(principal, 3L, 7, 20);

        assertThat(summary.requestReceived()).isEqualTo(10);
        assertThat(summary.policyAllowed()).isEqualTo(7);
        assertThat(summary.policyDenied()).isEqualTo(2);
        assertThat(summary.approvalRequired()).isEqualTo(1);
        assertThat(summary.actionSucceeded()).isEqualTo(6);
        assertThat(summary.actionFailed()).isEqualTo(1);
        assertThat(summary.actionCancelled()).isEqualTo(3);
        assertThat(summary.catchRate()).isEqualTo(20.0);
        assertThat(summary.approvalRequiredRate()).isEqualTo(10.0);
        assertThat(summary.failureRate()).isEqualTo(14.29);
        assertThat(summary.actionEvents()).singleElement()
                .satisfies(group -> {
                    assertThat(group.actionType()).isEqualTo("CREATE_REPORT");
                    assertThat(group.eventType()).isEqualTo("POLICY_DENIED");
                    assertThat(group.count()).isEqualTo(2);
                });
        assertThat(summary.reasons()).singleElement()
                .satisfies(group -> assertThat(group.reasonCode()).isEqualTo("ACTION_REQUIRES_APPROVAL"));
        verify(platformAdminService).requirePlatformAdmin(principal);
    }

    @Test
    void rejectsNonPositiveDays() {
        when(platformAdminService.requirePlatformAdmin(principal)).thenReturn(mock(PlatformAdmin.class));

        assertThatThrownBy(() -> service.summary(principal, null, 0, 10))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("days must be greater than zero");
    }

    private OperationEventRepository.WorkerEventTypeCountProjection eventType(String eventType, Long count) {
        return new OperationEventRepository.WorkerEventTypeCountProjection() {
            @Override
            public String getEventType() {
                return eventType;
            }

            @Override
            public Long getEventCount() {
                return count;
            }
        };
    }

    private OperationEventRepository.WorkerActionEventCountProjection actionEvent(
            String actionType,
            String eventType,
            Long count
    ) {
        return new OperationEventRepository.WorkerActionEventCountProjection() {
            @Override
            public String getActionType() {
                return actionType;
            }

            @Override
            public String getEventType() {
                return eventType;
            }

            @Override
            public Long getEventCount() {
                return count;
            }
        };
    }

    private OperationEventRepository.WorkerReasonCountProjection reason(
            String eventType,
            String reasonCode,
            Long count
    ) {
        return new OperationEventRepository.WorkerReasonCountProjection() {
            @Override
            public String getEventType() {
                return eventType;
            }

            @Override
            public String getReasonCode() {
                return reasonCode;
            }

            @Override
            public Long getEventCount() {
                return count;
            }
        };
    }
}
