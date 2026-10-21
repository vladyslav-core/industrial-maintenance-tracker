package com.vladyslav.industrialmaintenancetracker.repairrequest;

import com.vladyslav.industrialmaintenancetracker.equipment.Equipment;
import com.vladyslav.industrialmaintenancetracker.equipment.EquipmentRepository;
import com.vladyslav.industrialmaintenancetracker.equipment.EquipmentStatus;
import com.vladyslav.industrialmaintenancetracker.exception.InvalidRepairRequestActionException;
import com.vladyslav.industrialmaintenancetracker.exception.RepairRequestNotFoundException;
import com.vladyslav.industrialmaintenancetracker.exception.RepairRequestWorkConflictException;
import com.vladyslav.industrialmaintenancetracker.maintenance.MaintenanceRecordRepository;
import com.vladyslav.industrialmaintenancetracker.repairrequest.dto.RepairRequestResolutionForm;
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
class RepairRequestReviewTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

    @Autowired private RepairRequestService service;
    @Autowired private RepairRequestRepository requests;
    @Autowired private EquipmentRepository equipmentRepository;
    @Autowired private UserRepository users;
    @Autowired private MaintenanceRecordRepository maintenance;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;

    private Long adminId;
    private Long authorId;
    private Long technicianId;
    private Long replacementId;
    private Long requestId;
    private Long equipmentId;
    private RepairRequestResolutionForm originalWorkForm;

    @BeforeEach
    void completeRepair() {
        adminId = saveUser("Administrator", "admin@example.com", Role.ADMIN).getId();
        User author = saveUser("Author", "author@example.com", Role.REQUESTER);
        authorId = author.getId();
        technicianId = saveUser("Technician", "tech@example.com", Role.TECHNICIAN).getId();
        replacementId = saveUser("Replacement", "replacement@example.com", Role.TECHNICIAN).getId();
        Equipment equipment = equipmentRepository.saveAndFlush(new Equipment("Press", "PRESS-REVIEW-001", null, "Workshop A"));
        equipmentId = equipment.getId();
        requestId = requests.saveAndFlush(new RepairRequest("Oil leak in press", "Hydraulic oil leaking around the seal",
                equipment, author, RepairRequestPriority.HIGH)).getId();
        entityManager.clear();
        service.assign(requestId, technicianId, adminId);
        reload();
        service.startWork(requestId, technicianId);
        reload();
        originalWorkForm = service.getResolutionForm(requestId, technicianId);
        originalWorkForm.setResolution("Seal replaced. Pressure test passed.");
        service.completeWork(requestId, originalWorkForm, technicianId);
        reload();
    }

    @Test
    void returnPreservesRepairDataAndClearsOnlyCompletion() {
        var before = service.getDetails(requestId, adminId);
        returnRepair();
        var after = service.getDetails(requestId, adminId);
        assertThat(after.status()).isEqualTo(RepairRequestStatus.IN_PROGRESS);
        assertThat(after.completedAt()).isNull();
        assertThat(after.closedAt()).isNull();
        assertThat(after.resolution()).isEqualTo(before.resolution());
        assertThat(after.startedAt()).isEqualTo(before.startedAt());
        assertThat(after.assignedAt()).isEqualTo(before.assignedAt());
        assertThat(after.technicianName()).isEqualTo(before.technicianName());
        assertThat(after.authorName()).isEqualTo(before.authorName());
        assertThat(after.createdAt()).isEqualTo(before.createdAt());
        assertThat(after.updatedAt()).isAfter(before.updatedAt());
        assertThat(after.title()).isEqualTo(before.title());
        assertThat(after.description()).isEqualTo(before.description());
        assertThat(after.equipmentId()).isEqualTo(equipmentId);
        assertThat(after.priority()).isEqualTo(RepairRequestPriority.HIGH);
        assertThat(after.canReturn()).isFalse();
        assertThat(after.canManage()).isTrue();
        assertThat(service.getDetails(requestId, technicianId).canWork()).isTrue();
        assertThat(service.getDetails(requestId, technicianId).canStart()).isFalse();
        assertThat(service.getResolutionForm(requestId, technicianId).getResolution()).isEqualTo(before.resolution());
        assertThat(requests.findById(requestId).orElseThrow().getAssignedTechnician().getId()).isEqualTo(technicianId);
        assertThat(equipmentRepository.findById(equipmentId).orElseThrow().getStatus()).isEqualTo(EquipmentStatus.OPERATIONAL);
        assertThat(maintenance.count()).isZero();
    }

    @Test
    void technicianCanCorrectAndCompleteAgainWithoutRestartOrHistoryEntry() {
        var first = service.getDetails(requestId, adminId);
        returnRepair();
        finishAgain("Seal checked again. Extended pressure test passed.", technicianId);
        var second = service.getDetails(requestId, authorId);
        assertThat(second.status()).isEqualTo(RepairRequestStatus.COMPLETED);
        assertThat(second.completedAt()).isAfter(first.completedAt());
        assertThat(second.startedAt()).isEqualTo(first.startedAt());
        assertThat(second.assignedAt()).isEqualTo(first.assignedAt());
        assertThat(second.resolution()).isEqualTo("Seal checked again. Extended pressure test passed.");
        assertThat(second.closedAt()).isNull();
        assertThat(requests.count()).isEqualTo(1);
        assertThat(maintenance.count()).isZero();
        assertThat(service.getDetails(requestId, adminId).canReturn()).isTrue();
    }

    @Test
    void oldReviewCannotReturnNewlyCompletedCorrection() {
        Instant oldReview = currentVersion();
        returnRepair();
        finishAgain("Corrected result after extended testing", technicianId);
        var corrected = service.getDetails(requestId, adminId);
        assertThatThrownBy(() -> service.returnForRework(requestId, oldReview, adminId))
                .isInstanceOf(InvalidRepairRequestActionException.class);
        var after = service.getDetails(requestId, adminId);
        assertThat(after.status()).isEqualTo(RepairRequestStatus.COMPLETED);
        assertThat(after.resolution()).isEqualTo(corrected.resolution());
        assertThat(after.completedAt()).isEqualTo(corrected.completedAt());
        assertThat(after.updatedAt()).isEqualTo(corrected.updatedAt());
    }

    @Test
    void oldTechnicianFormFromPreviousWorkCycleStaysStaleAfterReturn() {
        returnRepair();
        originalWorkForm.setResolution("Stale text from the previous work cycle");
        assertThatThrownBy(() -> service.saveResolution(requestId, originalWorkForm, technicianId))
                .isInstanceOf(RepairRequestWorkConflictException.class);
        assertThatThrownBy(() -> service.completeWork(requestId, originalWorkForm, technicianId))
                .isInstanceOf(RepairRequestWorkConflictException.class);
        assertThat(service.getResolutionForm(requestId, technicianId).getResolution())
                .isEqualTo("Seal replaced. Pressure test passed.");
    }

    @Test
    void adminCanReassignReturnedRepairWhileKeepingFirstStartAndResult() {
        var before = service.getDetails(requestId, adminId);
        returnRepair();
        service.assign(requestId, replacementId, adminId);
        reload();
        assertThatThrownBy(() -> service.getResolutionForm(requestId, technicianId)).isInstanceOf(RepairRequestNotFoundException.class);
        assertThat(service.getResolutionForm(requestId, replacementId).getResolution()).isEqualTo(before.resolution());
        finishAgain("Replacement technician verified the repair", replacementId);
        var after = service.getDetails(requestId, adminId);
        assertThat(after.startedAt()).isEqualTo(before.startedAt());
        assertThat(after.technicianName()).isEqualTo("Replacement");
        assertThat(after.status()).isEqualTo(RepairRequestStatus.COMPLETED);
        assertThat(maintenance.count()).isZero();
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"TECHNICIAN", "REQUESTER"})
    void serviceRejectsOtherRolesEvenForTheirVisibleRequests(Role role) {
        Long actorId = role == Role.TECHNICIAN ? technicianId : authorId;
        Instant version = currentVersion();
        assertThatThrownBy(() -> service.returnForRework(requestId, version, actorId)).isInstanceOf(AccessDeniedException.class);
        assertThat(service.getDetails(requestId, adminId).status()).isEqualTo(RepairRequestStatus.COMPLETED);
    }

    @ParameterizedTest
    @EnumSource(value = RepairRequestStatus.class, names = "COMPLETED", mode = EnumSource.Mode.EXCLUDE)
    void onlyCompletedRequestsCanBeReturned(RepairRequestStatus state) {
        setState(state);
        Instant version = currentVersion();
        assertThatThrownBy(() -> service.returnForRework(requestId, version, adminId))
                .isInstanceOf(InvalidRepairRequestActionException.class);
        assertThat(service.getDetails(requestId, adminId).status()).isEqualTo(state);
        assertThat(service.getDetails(requestId, adminId).canReturn()).isFalse();
    }

    @Test
    void missingOrDifferentReviewVersionCannotChangeRequest() {
        assertThatThrownBy(() -> service.returnForRework(requestId, null, adminId)).isInstanceOf(InvalidRepairRequestActionException.class);
        assertThatThrownBy(() -> service.returnForRework(requestId, Instant.EPOCH, adminId)).isInstanceOf(InvalidRepairRequestActionException.class);
        assertThat(service.getDetails(requestId, adminId).status()).isEqualTo(RepairRequestStatus.COMPLETED);
    }

    @Test
    void rechecksAdministratorActivityAndRoleFromDatabase() {
        Instant version = currentVersion();
        jdbc.update("UPDATE users SET active = false WHERE id = ?", adminId);
        entityManager.clear();
        assertThatThrownBy(() -> service.returnForRework(requestId, version, adminId)).isInstanceOf(AccessDeniedException.class);
        jdbc.update("UPDATE users SET active = true, role = 'REQUESTER' WHERE id = ?", adminId);
        entityManager.clear();
        assertThatThrownBy(() -> service.returnForRework(requestId, version, adminId)).isInstanceOf(AccessDeniedException.class);
        assertThat(requests.findById(requestId).orElseThrow().getStatus()).isEqualTo(RepairRequestStatus.COMPLETED);
    }

    @Test
    void missingRequestIsUnavailable() {
        assertThatThrownBy(() -> service.returnForRework(Long.MAX_VALUE, Instant.EPOCH, adminId))
                .isInstanceOf(RepairRequestNotFoundException.class);
    }

    @Test
    void duplicateReturnDoesNotChangeFirstStartOrCreateHistory() {
        Instant version = currentVersion();
        returnRepair();
        var before = service.getDetails(requestId, adminId);
        assertThatThrownBy(() -> service.returnForRework(requestId, version, adminId))
                .isInstanceOf(InvalidRepairRequestActionException.class);
        var after = service.getDetails(requestId, adminId);
        assertThat(after.status()).isEqualTo(RepairRequestStatus.IN_PROGRESS);
        assertThat(after.startedAt()).isEqualTo(before.startedAt());
        assertThat(after.updatedAt()).isEqualTo(before.updatedAt());
        assertThat(after.completedAt()).isNull();
        assertThat(maintenance.count()).isZero();
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void returnActionIsAvailableOnlyToAdminInCompletedState(Role role) {
        Long viewer = switch (role) {
            case ADMIN -> adminId;
            case TECHNICIAN -> technicianId;
            case REQUESTER -> authorId;
        };
        assertThat(service.getDetails(requestId, viewer).canReturn()).isEqualTo(role == Role.ADMIN);
        returnRepair();
        assertThat(service.getDetails(requestId, viewer).canReturn()).isFalse();
    }

    private Instant currentVersion() {
        return service.getDetails(requestId, adminId).updatedAt();
    }

    private void returnRepair() {
        service.returnForRework(requestId, currentVersion(), adminId);
        reload();
    }

    private void finishAgain(String result, Long actorId) {
        var form = service.getResolutionForm(requestId, actorId);
        form.setResolution(result);
        service.completeWork(requestId, form, actorId);
        reload();
    }

    private User saveUser(String name, String email, Role role) {
        return users.saveAndFlush(new User(name, email, "hashed-password", role));
    }

    private void reload() {
        entityManager.flush();
        entityManager.clear();
    }

    // Terminal transitions are introduced in later steps; these fixtures test rejection by status.
    private void setState(RepairRequestStatus state) {
        String value = state.name();
        jdbc.update("""
                UPDATE repair_requests SET status = ?,
                    assigned_technician_id = CASE WHEN ? = 'NEW' THEN NULL ELSE assigned_technician_id END,
                    assigned_at = CASE WHEN ? = 'NEW' THEN NULL ELSE assigned_at END,
                    started_at = CASE WHEN ? IN ('NEW', 'ASSIGNED') THEN NULL ELSE started_at END,
                    completed_at = CASE WHEN ? = 'CLOSED' THEN completed_at ELSE NULL END,
                    closed_at = CASE WHEN ? IN ('CLOSED', 'CANCELLED') THEN CURRENT_TIMESTAMP ELSE NULL END,
                    cancellation_reason = CASE WHEN ? = 'CANCELLED' THEN 'Duplicate problem report' ELSE NULL END
                WHERE id = ?
                """, value, value, value, value, value, value, value, requestId);
        entityManager.clear();
    }
}
