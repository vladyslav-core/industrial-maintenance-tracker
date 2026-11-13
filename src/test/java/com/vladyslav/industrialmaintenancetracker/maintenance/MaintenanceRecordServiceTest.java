package com.vladyslav.industrialmaintenancetracker.maintenance;

import com.vladyslav.industrialmaintenancetracker.equipment.Equipment;
import com.vladyslav.industrialmaintenancetracker.equipment.EquipmentRepository;
import com.vladyslav.industrialmaintenancetracker.exception.EquipmentNotFoundException;
import com.vladyslav.industrialmaintenancetracker.maintenance.dto.MaintenanceHistoryItem;
import com.vladyslav.industrialmaintenancetracker.repairrequest.RepairRequest;
import com.vladyslav.industrialmaintenancetracker.user.Role;
import com.vladyslav.industrialmaintenancetracker.user.User;
import com.vladyslav.industrialmaintenancetracker.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaintenanceRecordServiceTest {

    private static final Instant CREATED = Instant.parse("2026-11-01T07:00:00Z");
    private static final Instant PERFORMED = CREATED.plusSeconds(3600);
    private static final Instant CLOSED = PERFORMED.plusSeconds(7200);

    @Mock private MaintenanceRecordRepository records;
    @Mock private EquipmentRepository equipment;
    @Mock private UserRepository users;

    private MaintenanceRecordService service;
    private MaintenanceRecord record;

    @BeforeEach
    void setUp() {
        service = new MaintenanceRecordService(records, equipment, users);
        Equipment press = new Equipment("Press", "HIST10-A", null, "Workshop A");
        ReflectionTestUtils.setField(press, "id", 7L);
        User technician = account(12L, Role.TECHNICIAN);
        RepairRequest request = new RepairRequest(
                "Oil leak in press", "Oil is leaking around the seal", press, account(11L, Role.REQUESTER), null
        );
        ReflectionTestUtils.setField(request, "id", 73L);
        ReflectionTestUtils.setField(request, "createdAt", CREATED);
        request.assignTo(technician, CREATED.plusSeconds(60));
        request.startWork(CREATED.plusSeconds(120));
        request.completeWork("Seal replaced.\nPressure test passed.", PERFORMED);
        request.close(CLOSED);
        record = new MaintenanceRecord(request);
        ReflectionTestUtils.setField(record, "id", 5L);
        ReflectionTestUtils.setField(record, "createdAt", CLOSED.plusSeconds(30));
    }

    @ParameterizedTest
    @MethodSource("linkPermissions")
    void equipmentHistoryContainsTheContractFieldsAndOnlyPermittedRequestLinks(
            Role role, Long viewerId, boolean canViewRequest
    ) {
        when(users.findById(viewerId)).thenReturn(Optional.of(account(viewerId, role)));
        when(equipment.existsById(7L)).thenReturn(true);
        when(records.findHistory(7L)).thenReturn(List.of(record));

        assertThat(service.getEquipmentHistory(7L, viewerId)).containsExactly(new MaintenanceHistoryItem(
                5L, 7L, "Press", "HIST10-A", 73L, "Oil leak in press", CREATED,
                "User 11", "Oil is leaking around the seal", "User 12",
                "Seal replaced.\nPressure test passed.", PERFORMED, CLOSED, canViewRequest
        ));
    }

    static Stream<Arguments> linkPermissions() {
        return Stream.of(
                Arguments.of(Role.ADMIN, 99L, true),
                Arguments.of(Role.TECHNICIAN, 12L, true),
                Arguments.of(Role.TECHNICIAN, 11L, true),
                Arguments.of(Role.TECHNICIAN, 99L, false),
                Arguments.of(Role.REQUESTER, 11L, true),
                Arguments.of(Role.REQUESTER, 12L, false),
                Arguments.of(Role.REQUESTER, 99L, false)
        );
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"ADMIN", "TECHNICIAN"})
    void generalHistoryAllowsBothUnfilteredAndFilteredReads(Role role) {
        when(users.findById(99L)).thenReturn(Optional.of(account(99L, role)));
        when(equipment.existsById(7L)).thenReturn(true);
        when(records.findHistory(null)).thenReturn(List.of(record));
        when(records.findHistory(7L)).thenReturn(List.of(record));

        assertThat(service.getHistory(null, 99L)).hasSize(1);
        assertThat(service.getHistory(7L, 99L)).isEqualTo(service.getHistory(null, 99L));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {7L, -1L})
    void requesterCannotUseGeneralHistoryEvenWithAnEquipmentFilter(Long equipmentId) {
        when(users.findById(11L)).thenReturn(Optional.of(account(11L, Role.REQUESTER)));

        assertThatThrownBy(() -> service.getHistory(equipmentId, 11L))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(equipment, records);
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void inactiveViewerCannotReadEitherHistory(Role role) {
        User viewer = account(99L, role);
        viewer.deactivate();
        when(users.findById(99L)).thenReturn(Optional.of(viewer));

        assertThatThrownBy(() -> service.getEquipmentHistory(7L, 99L))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.getHistory(null, 99L))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(equipment, records);
    }

    @Test
    void missingAccountCannotReadEitherHistory() {
        when(users.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getEquipmentHistory(7L, 99L))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.getHistory(null, 99L))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(equipment, records);
    }

    @Test
    void missingIdentityCannotReadEitherHistory() {
        assertThatThrownBy(() -> service.getEquipmentHistory(7L, null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.getHistory(null, null))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(users, equipment, records);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, -1L})
    void equipmentHistoryRequiresAPositiveEquipmentId(Long equipmentId) {
        when(users.findById(99L)).thenReturn(Optional.of(account(99L, Role.ADMIN)));

        assertThatThrownBy(() -> service.getEquipmentHistory(equipmentId, 99L))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(equipment, records);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    void invalidGeneralFilterDoesNotFallBackToAllRecords(Long equipmentId) {
        when(users.findById(99L)).thenReturn(Optional.of(account(99L, Role.ADMIN)));

        assertThatThrownBy(() -> service.getHistory(equipmentId, 99L))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(equipment, records);
    }

    @Test
    void unknownEquipmentIsDifferentFromEquipmentWithoutHistory() {
        when(users.findById(99L)).thenReturn(Optional.of(account(99L, Role.ADMIN)));
        when(equipment.existsById(7L)).thenReturn(false);

        assertThatThrownBy(() -> service.getEquipmentHistory(7L, 99L))
                .isInstanceOf(EquipmentNotFoundException.class);
        assertThatThrownBy(() -> service.getHistory(7L, 99L))
                .isInstanceOf(EquipmentNotFoundException.class);
        verifyNoInteractions(records);
    }

    @Test
    void eachReadUsesTheCurrentAccountRoleAndActivity() {
        User viewer = account(99L, Role.TECHNICIAN);
        when(users.findById(99L)).thenReturn(Optional.of(viewer));
        when(records.findHistory(null)).thenReturn(List.of());
        when(equipment.existsById(7L)).thenReturn(true);
        when(records.findHistory(7L)).thenReturn(List.of(record));
        assertThat(service.getHistory(null, 99L)).isEmpty();

        viewer.updateProfile(viewer.getFullName(), viewer.getEmail(), Role.REQUESTER);
        assertThatThrownBy(() -> service.getHistory(null, 99L))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(service.getEquipmentHistory(7L, 99L)).hasSize(1);

        viewer.deactivate();
        assertThatThrownBy(() -> service.getEquipmentHistory(7L, 99L))
                .isInstanceOf(AccessDeniedException.class);
    }

    private User account(Long id, Role role) {
        User user = new User("User " + id, "user" + id + "@example.com", "hashed-password", role);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
