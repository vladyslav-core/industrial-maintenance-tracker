package com.vladyslav.industrialmaintenancetracker.dashboard;

import com.vladyslav.industrialmaintenancetracker.repairrequest.RepairRequestPriority;
import com.vladyslav.industrialmaintenancetracker.repairrequest.RepairRequestRepository;
import com.vladyslav.industrialmaintenancetracker.repairrequest.RepairRequestStatus;
import com.vladyslav.industrialmaintenancetracker.user.Role;
import com.vladyslav.industrialmaintenancetracker.user.UserService;
import com.vladyslav.industrialmaintenancetracker.user.dto.CurrentUserSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock private UserService users;
    @Mock private RepairRequestRepository requests;
    private DashboardService service;

    @BeforeEach
    void setUp() {
        service = new DashboardService(users, requests);
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void eachRoleReceivesOnlyItsOwnDashboardPayloadAndFixedPreview(Role role) {
        CurrentUserSummary current = new CurrentUserSummary(42L, "Current database name", role);
        when(users.findActiveCurrentUserSummary(42L)).thenReturn(Optional.of(current));

        var data = service.getDashboard(42L, role);

        assertThat(data.viewer()).isEqualTo(current);
        if (role == Role.ADMIN) {
            assertThat(data.administrator()).isNotNull();
            assertThat(data.technician()).isNull();
            verify(requests).findLatestDashboardRequests(PageRequest.of(0, 5));
        } else {
            assertThat(data.administrator()).isNull();
            verify(requests, never()).countByStatus(any());
            verify(requests, never()).countByPriorityAndStatusIn(any(RepairRequestPriority.class), anyCollection());
            verify(requests, never()).findLatestDashboardRequests(any(Pageable.class));
        }
        if (role == Role.TECHNICIAN) {
            assertThat(data.technician()).isNotNull();
            assertThat(data.latestRequests()).isEmpty();
            verify(requests).findLatestDashboardAssignments(42L, RepairRequestStatus.ASSIGNED, PageRequest.of(0, 5));
            verify(requests).findLatestDashboardAssignments(42L, RepairRequestStatus.IN_PROGRESS, PageRequest.of(0, 5));
            verify(requests, never()).findLatestDashboardRequestsByAuthor(anyLong(), any(Pageable.class));
        } else {
            assertThat(data.technician()).isNull();
            verify(requests, never()).countByAssignedTechnician_IdAndStatus(anyLong(), any());
            verify(requests, never()).findLatestDashboardAssignments(anyLong(), any(), any(Pageable.class));
        }
        if (role == Role.REQUESTER) {
            verify(requests).findLatestDashboardRequestsByAuthor(42L, PageRequest.of(0, 5));
        }
    }

    @ParameterizedTest
    @MethodSource("missingIdentity")
    void missingIdentityIsRejectedBeforeAnyReads(Long id, Role role) {
        assertThatThrownBy(() -> service.getDashboard(id, role)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(users, requests);
    }

    static Stream<Arguments> missingIdentity() {
        return Stream.of(Arguments.of(null, Role.ADMIN), Arguments.of(42L, null), Arguments.of(null, null));
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void unavailableAccountCannotReadAnyCountsOrPreview(Role authenticatedRole) {
        when(users.findActiveCurrentUserSummary(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDashboard(42L, authenticatedRole))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(requests);
    }

    @ParameterizedTest
    @MethodSource("changedRoles")
    void cachedRoleNeverSelectsAFormerOrNewlyGrantedDashboard(Role authenticatedRole, Role databaseRole) {
        when(users.findActiveCurrentUserSummary(42L))
                .thenReturn(Optional.of(new CurrentUserSummary(42L, "Changed account", databaseRole)));

        assertThatThrownBy(() -> service.getDashboard(42L, authenticatedRole))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(requests);
    }

    static Stream<Arguments> changedRoles() {
        return Arrays.stream(Role.values()).flatMap(oldRole -> Arrays.stream(Role.values())
                .filter(currentRole -> currentRole != oldRole).map(currentRole -> Arguments.of(oldRole, currentRole)));
    }

    @Test
    void countsRetainLongValuesBeyondTheIntegerRange() {
        when(users.findActiveCurrentUserSummary(42L))
                .thenReturn(Optional.of(new CurrentUserSummary(42L, "Administrator", Role.ADMIN)));
        long total = (long) Integer.MAX_VALUE + 25;
        when(requests.countByStatus(RepairRequestStatus.NEW)).thenReturn(total);

        assertThat(service.getDashboard(42L, Role.ADMIN).administrator().newCount()).isEqualTo(total);
    }
}
