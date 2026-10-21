package com.vladyslav.industrialmaintenancetracker.repairrequest;

import com.vladyslav.industrialmaintenancetracker.equipment.Equipment;
import com.vladyslav.industrialmaintenancetracker.equipment.EquipmentRepository;
import com.vladyslav.industrialmaintenancetracker.equipment.EquipmentStatus;
import com.vladyslav.industrialmaintenancetracker.exception.InvalidRepairRequestActionException;
import com.vladyslav.industrialmaintenancetracker.exception.RepairRequestEditConflictException;
import com.vladyslav.industrialmaintenancetracker.exception.RepairRequestNotFoundException;
import com.vladyslav.industrialmaintenancetracker.user.Role;
import com.vladyslav.industrialmaintenancetracker.user.User;
import com.vladyslav.industrialmaintenancetracker.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DataJpaTest
@Import(RepairRequestService.class)
class RepairRequestAssignmentTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

    @Autowired private RepairRequestService service;
    @Autowired private RepairRequestRepository requests;
    @Autowired private EquipmentRepository equipmentRepository;
    @Autowired private UserRepository users;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;

    private Long adminId;
    private Long authorId;
    private Long technicianId;
    private Long replacementId;
    private Long requestId;
    private Long equipmentId;

    @BeforeEach
    void seed() {
        adminId = saveUser("Administrator", "admin@example.com", Role.ADMIN).getId();
        User author = saveUser("Author", "author@example.com", Role.REQUESTER);
        authorId = author.getId();
        technicianId = saveUser("Technician", "tech@example.com", Role.TECHNICIAN).getId();
        replacementId = saveUser("Replacement", "replacement@example.com", Role.TECHNICIAN).getId();
        Equipment equipment = equipmentRepository.saveAndFlush(new Equipment("Press", "PRESS-ASSIGN-001", null, "Workshop A"));
        equipmentId = equipment.getId();
        requestId = requests.saveAndFlush(new RepairRequest("Oil leak in press",
                "Hydraulic oil leaking around the seal", equipment, author, RepairRequestPriority.HIGH)).getId();
        jdbc.update("UPDATE repair_requests SET updated_at = TIMESTAMPTZ '2026-01-01 00:00:00+00' WHERE id = ?", requestId);
        entityManager.clear();
    }

    @Test
    void assignsNewRequestWithoutChangingProblemEquipmentOrPriority() {
        var before = service.getDetails(requestId, authorId);
        service.assign(requestId, technicianId, adminId);
        reload();
        RepairRequest request = requests.findById(requestId).orElseThrow();
        assertThat(request.getStatus()).isEqualTo(RepairRequestStatus.ASSIGNED);
        assertThat(request.getAssignedTechnician().getId()).isEqualTo(technicianId);
        assertThat(request.getAssignedAt()).isNotNull();
        assertThat(request.getUpdatedAt()).isAfter(before.updatedAt());
        assertThat(request.getCreatedAt()).isEqualTo(before.createdAt());
        assertThat(request.getTitle()).isEqualTo(before.title());
        assertThat(request.getDescription()).isEqualTo(before.description());
        assertThat(request.getCreatedBy().getId()).isEqualTo(authorId);
        assertThat(request.getEquipment().getId()).isEqualTo(equipmentId);
        assertThat(request.getPriority()).isEqualTo(RepairRequestPriority.HIGH);
        assertThat(request.getStartedAt()).isNull();
        assertThat(request.getResolution()).isNull();
        assertThat(equipmentRepository.findById(equipmentId).orElseThrow().getStatus()).isEqualTo(EquipmentStatus.OPERATIONAL);
        assertThat(service.getDetails(requestId, authorId).canEdit()).isFalse();
    }

    @Test
    void unassignRestoresNewAndAuthorEditing() {
        service.assign(requestId, technicianId, adminId);
        reload();
        service.unassign(requestId, adminId);
        reload();
        RepairRequest request = requests.findById(requestId).orElseThrow();
        assertThat(request.getStatus()).isEqualTo(RepairRequestStatus.NEW);
        assertThat(request.getAssignedTechnician()).isNull();
        assertThat(request.getAssignedAt()).isNull();
        assertThat(request.getStartedAt()).isNull();
        assertThat(service.getDetails(requestId, authorId).canEdit()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = RepairRequestStatus.class, names = {"ASSIGNED", "IN_PROGRESS"})
    void reassignPreservesStatusFirstStartAndDraftAndChangesVisibility(RepairRequestStatus status) {
        setStatus(status);
        Instant originalAssignment = requests.findById(requestId).orElseThrow().getAssignedAt();
        Instant originalStart = requests.findById(requestId).orElseThrow().getStartedAt();
        service.assign(requestId, replacementId, adminId);
        reload();
        RepairRequest request = requests.findById(requestId).orElseThrow();
        assertThat(request.getStatus()).isEqualTo(status);
        assertThat(request.getAssignedTechnician().getId()).isEqualTo(replacementId);
        assertThat(request.getAssignedAt()).isAfter(originalAssignment);
        assertThat(request.getStartedAt()).isEqualTo(originalStart);
        assertThat(request.getResolution()).isEqualTo("Work in progress draft");
        assertThatThrownBy(() -> service.getDetails(requestId, technicianId)).isInstanceOf(RepairRequestNotFoundException.class);
        assertThat(service.getDetails(requestId, replacementId).id()).isEqualTo(requestId);
        assertThat(service.getDetails(requestId, authorId).id()).isEqualTo(requestId);
    }

    @Test
    void duplicateAssignmentDoesNotResetAssignmentTime() {
        setStatus(RepairRequestStatus.ASSIGNED);
        Instant before = requests.findById(requestId).orElseThrow().getAssignedAt();
        assertThatThrownBy(() -> service.assign(requestId, technicianId, adminId))
                .isInstanceOf(InvalidRepairRequestActionException.class);
        assertThat(requests.findById(requestId).orElseThrow().getAssignedAt()).isEqualTo(before);
    }

    @ParameterizedTest
    @EnumSource(value = RepairRequestStatus.class, names = {"COMPLETED", "CLOSED", "CANCELLED"})
    void completedAndTerminalRequestsRejectAssignmentAndPriorityChanges(RepairRequestStatus status) {
        setStatus(status);
        assertThatThrownBy(() -> service.assign(requestId, replacementId, adminId))
                .isInstanceOf(InvalidRepairRequestActionException.class);
        assertThatThrownBy(() -> service.changePriority(requestId, RepairRequestPriority.CRITICAL, adminId))
                .isInstanceOf(InvalidRepairRequestActionException.class);
        assertThat(service.getDetails(requestId, adminId).canManage()).isFalse();
        assertThat(requests.findById(requestId).orElseThrow().getPriority()).isEqualTo(RepairRequestPriority.HIGH);
    }

    @ParameterizedTest
    @EnumSource(value = RepairRequestStatus.class, names = "ASSIGNED", mode = EnumSource.Mode.EXCLUDE)
    void cannotUnassignOutsideAssigned(RepairRequestStatus status) {
        if (status != RepairRequestStatus.NEW) setStatus(status);
        assertThatThrownBy(() -> service.unassign(requestId, adminId))
                .isInstanceOf(InvalidRepairRequestActionException.class);
    }

    @ParameterizedTest
    @EnumSource(value = RepairRequestStatus.class, names = {"NEW", "ASSIGNED", "IN_PROGRESS"})
    void changesOnlyPriorityInAllowedStates(RepairRequestStatus status) {
        if (status != RepairRequestStatus.NEW) setStatus(status);
        var before = service.getDetails(requestId, adminId);
        service.changePriority(requestId, RepairRequestPriority.CRITICAL, adminId);
        reload();
        var after = service.getDetails(requestId, adminId);
        assertThat(after.priority()).isEqualTo(RepairRequestPriority.CRITICAL);
        assertThat(after.status()).isEqualTo(before.status());
        assertThat(after.technicianName()).isEqualTo(before.technicianName());
        assertThat(after.assignedAt()).isEqualTo(before.assignedAt());
        assertThat(after.startedAt()).isEqualTo(before.startedAt());
        assertThat(after.resolution()).isEqualTo(before.resolution());
        assertThat(after.updatedAt()).isAfter(before.updatedAt());
        assertThat(after.canManage()).isTrue();
        assertThat(after.canUnassign()).isEqualTo(status == RepairRequestStatus.ASSIGNED);
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"REQUESTER", "TECHNICIAN"})
    void serviceRejectsNonAdminEvenIfInvokedDirectly(Role role) {
        Long actorId = role == Role.REQUESTER ? authorId : technicianId;
        assertThatThrownBy(() -> service.assign(requestId, replacementId, actorId)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.unassign(requestId, actorId)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.changePriority(requestId, RepairRequestPriority.LOW, actorId)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.availableTechnicians(actorId)).isInstanceOf(AccessDeniedException.class);
        assertThat(service.getDetails(requestId, authorId).canManage()).isFalse();
    }

    @Test
    void serviceRechecksAdminRoleAndActiveStateFromDatabase() {
        jdbc.update("UPDATE users SET active = false WHERE id = ?", adminId);
        entityManager.clear();
        assertThatThrownBy(() -> service.assign(requestId, technicianId, adminId)).isInstanceOf(AccessDeniedException.class);
        jdbc.update("UPDATE users SET active = true, role = 'REQUESTER' WHERE id = ?", adminId);
        entityManager.clear();
        assertThatThrownBy(() -> service.changePriority(requestId, RepairRequestPriority.LOW, adminId)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void rejectsMissingInactiveAndWrongRoleTechniciansAndNullPriority() {
        assertThatThrownBy(() -> service.assign(requestId, Long.MAX_VALUE, adminId)).isInstanceOf(InvalidRepairRequestActionException.class);
        assertThatThrownBy(() -> service.assign(requestId, authorId, adminId)).isInstanceOf(InvalidRepairRequestActionException.class);
        assertThatThrownBy(() -> service.assign(requestId, adminId, adminId)).isInstanceOf(InvalidRepairRequestActionException.class);
        jdbc.update("UPDATE users SET active = false WHERE id = ?", technicianId);
        entityManager.clear();
        assertThatThrownBy(() -> service.assign(requestId, technicianId, adminId)).isInstanceOf(InvalidRepairRequestActionException.class);
        assertThatThrownBy(() -> service.changePriority(requestId, null, adminId)).isInstanceOf(InvalidRepairRequestActionException.class);
        assertThat(requests.findById(requestId).orElseThrow().getStatus()).isEqualTo(RepairRequestStatus.NEW);
    }

    @Test
    void technicianChoicesContainOnlyActiveTechniciansInStableOrder() {
        jdbc.update("UPDATE users SET active = false WHERE id = ?", technicianId);
        entityManager.clear();
        Long second = saveUser("Replacement", "replacement-second@example.com", Role.TECHNICIAN).getId();
        Long first = saveUser("Alpha", "alpha@example.com", Role.TECHNICIAN).getId();
        assertThat(service.availableTechnicians(adminId)).extracting(option -> option.id())
                .containsExactly(first, replacementId, second);
    }

    @Test
    void unknownRequestIs404ForAdmin() {
        assertThatThrownBy(() -> service.assign(Long.MAX_VALUE, technicianId, adminId)).isInstanceOf(RepairRequestNotFoundException.class);
        assertThatThrownBy(() -> service.unassign(Long.MAX_VALUE, adminId)).isInstanceOf(RepairRequestNotFoundException.class);
        assertThatThrownBy(() -> service.changePriority(Long.MAX_VALUE, RepairRequestPriority.LOW, adminId)).isInstanceOf(RepairRequestNotFoundException.class);
    }

    @Test
    void assignmentAndPriorityInvalidatePreviouslyOpenedEditForms() {
        var form = service.getEditForm(requestId, authorId);
        service.changePriority(requestId, RepairRequestPriority.CRITICAL, adminId);
        reload();
        assertThatThrownBy(() -> service.updateDetails(requestId, form, authorId)).isInstanceOf(RepairRequestEditConflictException.class);
        var currentForm = service.getEditForm(requestId, authorId);
        service.assign(requestId, technicianId, adminId);
        reload();
        assertThatThrownBy(() -> service.updateDetails(requestId, currentForm, authorId)).isInstanceOf(RepairRequestEditConflictException.class);
    }

    private User saveUser(String name, String email, Role role) {
        return users.saveAndFlush(new User(name, email, "hashed-password", role));
    }

    private void reload() {
        entityManager.flush();
        entityManager.clear();
    }

    // Technician work and final transitions are introduced in later steps.
    private void setStatus(RepairRequestStatus status) {
        String value = status.name();
        jdbc.update("""
                UPDATE repair_requests SET status = ?, assigned_technician_id = ?,
                    assigned_at = TIMESTAMPTZ '2026-01-02 00:00:00+00',
                    started_at = CASE WHEN ? = 'ASSIGNED' THEN NULL ELSE TIMESTAMPTZ '2026-01-03 00:00:00+00' END,
                    completed_at = CASE WHEN ? IN ('COMPLETED', 'CLOSED') THEN CURRENT_TIMESTAMP ELSE NULL END,
                    closed_at = CASE WHEN ? IN ('CLOSED', 'CANCELLED') THEN CURRENT_TIMESTAMP ELSE NULL END,
                    resolution = 'Work in progress draft',
                    cancellation_reason = CASE WHEN ? = 'CANCELLED' THEN 'Duplicate problem report' ELSE NULL END
                WHERE id = ?
                """, value, technicianId, value, value, value, value, requestId);
        entityManager.clear();
    }
}
