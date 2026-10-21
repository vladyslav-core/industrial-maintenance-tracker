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
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DataJpaTest
@Import(RepairRequestService.class)
class RepairRequestWorkTest {

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

    @BeforeEach
    void seedAssignedRequest() {
        adminId = saveUser("Administrator", "admin@example.com", Role.ADMIN).getId();
        User author = saveUser("Author", "author@example.com", Role.REQUESTER);
        authorId = author.getId();
        technicianId = saveUser("Technician", "tech@example.com", Role.TECHNICIAN).getId();
        replacementId = saveUser("Replacement", "replacement@example.com", Role.TECHNICIAN).getId();
        Equipment equipment = equipmentRepository.saveAndFlush(new Equipment("Press", "PRESS-WORK-001", null, "Workshop A"));
        equipmentId = equipment.getId();
        requestId = requests.saveAndFlush(new RepairRequest("Oil leak in press", "Hydraulic oil leaking around the seal",
                equipment, author, RepairRequestPriority.HIGH)).getId();
        entityManager.clear();
        service.assign(requestId, technicianId, adminId);
        reload();
        jdbc.update("UPDATE repair_requests SET updated_at = TIMESTAMPTZ '2026-01-01 00:00:00+00' WHERE id = ?", requestId);
        entityManager.clear();
    }

    @Test
    void startsAssignedRepairWithoutChangingAssignmentProblemOrEquipment() {
        var before = service.getDetails(requestId, technicianId);
        assertThat(before.canStart()).isTrue();
        assertThat(before.canWork()).isFalse();
        service.startWork(requestId, technicianId);
        reload();
        var after = service.getDetails(requestId, technicianId);
        assertThat(after.status()).isEqualTo(RepairRequestStatus.IN_PROGRESS);
        assertThat(after.startedAt()).isAfterOrEqualTo(before.assignedAt());
        assertThat(after.assignedAt()).isEqualTo(before.assignedAt());
        assertThat(after.technicianName()).isEqualTo(before.technicianName());
        assertThat(after.title()).isEqualTo(before.title());
        assertThat(after.description()).isEqualTo(before.description());
        assertThat(after.priority()).isEqualTo(before.priority());
        assertThat(after.createdAt()).isEqualTo(before.createdAt());
        assertThat(after.updatedAt()).isAfter(before.updatedAt());
        assertThat(after.completedAt()).isNull();
        assertThat(after.closedAt()).isNull();
        assertThat(after.canStart()).isFalse();
        assertThat(after.canWork()).isTrue();
        assertThat(maintenance.count()).isZero();
        assertThat(equipmentRepository.findById(equipmentId).orElseThrow().getStatus()).isEqualTo(EquipmentStatus.OPERATIONAL);
    }

    @Test
    void repeatedStartDoesNotOverwriteFirstStart() {
        start();
        var firstStart = service.getDetails(requestId, technicianId).startedAt();
        assertThatThrownBy(() -> service.startWork(requestId, technicianId)).isInstanceOf(InvalidRepairRequestActionException.class);
        assertThat(service.getDetails(requestId, technicianId).startedAt()).isEqualTo(firstStart);
    }

    @ParameterizedTest
    @EnumSource(value = RepairRequestStatus.class, names = {"IN_PROGRESS", "COMPLETED", "CLOSED", "CANCELLED"})
    void cannotStartOutsideAssigned(RepairRequestStatus state) {
        setState(state);
        assertThatThrownBy(() -> service.startWork(requestId, technicianId)).isInstanceOf(InvalidRepairRequestActionException.class);
        assertThat(service.getDetails(requestId, technicianId).status()).isEqualTo(state);
    }

    @Test
    void draftChangesOnlyResultAndUpdateTime() {
        start();
        var before = service.getDetails(requestId, technicianId);
        var form = service.getResolutionForm(requestId, technicianId);
        form.setResolution("  Seal removed.\nChecking the cylinder.  ");
        service.saveResolution(requestId, form, technicianId);
        reload();
        var after = service.getDetails(requestId, technicianId);
        assertThat(after.resolution()).isEqualTo("Seal removed.\nChecking the cylinder.");
        assertThat(after.updatedAt()).isAfter(before.updatedAt());
        assertThat(after.startedAt()).isEqualTo(before.startedAt());
        assertThat(after.assignedAt()).isEqualTo(before.assignedAt());
        assertThat(after.status()).isEqualTo(RepairRequestStatus.IN_PROGRESS);
        assertThat(after.completedAt()).isNull();
        assertThat(after.closedAt()).isNull();
        assertThat(maintenance.count()).isZero();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\n\t"})
    void emptyDraftClearsTextWithoutCompleting(String text) {
        setState(RepairRequestStatus.IN_PROGRESS);
        var form = service.getResolutionForm(requestId, technicianId);
        form.setResolution(text);
        service.saveResolution(requestId, form, technicianId);
        reload();
        var after = service.getDetails(requestId, technicianId);
        assertThat(after.resolution()).isNull();
        assertThat(after.status()).isEqualTo(RepairRequestStatus.IN_PROGRESS);
        assertThat(after.completedAt()).isNull();
    }

    @Test
    void completionStoresSubmittedResultAndWaitsForReviewWithoutMaintenanceRecord() {
        start();
        var before = service.getDetails(requestId, technicianId);
        var form = service.getResolutionForm(requestId, technicianId);
        form.setResolution("  Seal replaced. Pressure test passed.  ");
        service.completeWork(requestId, form, technicianId);
        reload();
        var after = service.getDetails(requestId, authorId);
        assertThat(after.resolution()).isEqualTo("Seal replaced. Pressure test passed.");
        assertThat(after.status()).isEqualTo(RepairRequestStatus.COMPLETED);
        assertThat(after.completedAt()).isAfterOrEqualTo(before.startedAt());
        assertThat(after.startedAt()).isEqualTo(before.startedAt());
        assertThat(after.assignedAt()).isEqualTo(before.assignedAt());
        assertThat(after.closedAt()).isNull();
        assertThat(after.priority()).isEqualTo(before.priority());
        assertThat(service.getDetails(requestId, technicianId).canWork()).isFalse();
        assertThat(service.getDetails(requestId, adminId).resolution()).isEqualTo(after.resolution());
        assertThat(maintenance.count()).isZero();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\n\t"})
    void blankCompletionDoesNotUseOldDraftOrPartlyChangeRequest(String text) {
        setState(RepairRequestStatus.IN_PROGRESS);
        var form = service.getResolutionForm(requestId, technicianId);
        form.setResolution(text);
        assertThatThrownBy(() -> service.completeWork(requestId, form, technicianId)).isInstanceOf(InvalidRepairRequestActionException.class);
        assertThat(service.getDetails(requestId, technicianId).resolution()).isEqualTo("Existing draft");
        assertThat(service.getDetails(requestId, technicianId).status()).isEqualTo(RepairRequestStatus.IN_PROGRESS);
        assertThat(service.getDetails(requestId, technicianId).completedAt()).isNull();
        assertThat(maintenance.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void staleDraftOrCompletionCannotOverwriteNewerSavedResult(boolean complete) {
        start();
        var first = service.getResolutionForm(requestId, technicianId);
        var stale = service.getResolutionForm(requestId, technicianId);
        first.setResolution("First saved result");
        service.saveResolution(requestId, first, technicianId);
        reload();
        stale.setResolution("Stale submitted result");
        assertThatThrownBy(() -> submit(stale, complete)).isInstanceOf(RepairRequestWorkConflictException.class);
        assertThat(service.getDetails(requestId, technicianId).resolution()).isEqualTo("First saved result");
        assertThat(service.getDetails(requestId, technicianId).status()).isEqualTo(RepairRequestStatus.IN_PROGRESS);
    }

    @ParameterizedTest
    @EnumSource(value = RepairRequestStatus.class, names = {"ASSIGNED", "COMPLETED", "CLOSED", "CANCELLED"})
    void onlyInProgressAllowsResultFormAndChanges(RepairRequestStatus state) {
        setState(state);
        var form = new RepairRequestResolutionForm("Attempted result", service.getDetails(requestId, technicianId).updatedAt());
        assertThatThrownBy(() -> service.getResolutionForm(requestId, technicianId)).isInstanceOf(RepairRequestWorkConflictException.class);
        assertThatThrownBy(() -> service.saveResolution(requestId, form, technicianId)).isInstanceOf(RepairRequestWorkConflictException.class);
        assertThatThrownBy(() -> service.completeWork(requestId, form, technicianId)).isInstanceOf(RepairRequestWorkConflictException.class);
        assertThat(service.getDetails(requestId, technicianId).resolution()).isEqualTo("Existing draft");
    }

    @Test
    void priorityChangeInvalidatesOpenResultForm() {
        start();
        var form = service.getResolutionForm(requestId, technicianId);
        form.setResolution("Stale submitted result");
        service.changePriority(requestId, RepairRequestPriority.CRITICAL, adminId);
        reload();
        assertThatThrownBy(() -> service.saveResolution(requestId, form, technicianId)).isInstanceOf(RepairRequestWorkConflictException.class);
        assertThat(service.getDetails(requestId, technicianId).resolution()).isNull();
    }

    @Test
    void reassignmentRevokesOldTechniciansWriteAccessAndKeepsDraftForNewTechnician() {
        setState(RepairRequestStatus.IN_PROGRESS);
        var form = service.getResolutionForm(requestId, technicianId);
        service.assign(requestId, replacementId, adminId);
        reload();
        assertThatThrownBy(() -> service.saveResolution(requestId, form, technicianId)).isInstanceOf(RepairRequestNotFoundException.class);
        assertThatThrownBy(() -> service.completeWork(requestId, form, technicianId)).isInstanceOf(RepairRequestNotFoundException.class);
        assertThat(service.getResolutionForm(requestId, replacementId).getResolution()).isEqualTo("Existing draft");
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"ADMIN", "REQUESTER"})
    void serviceRejectsNonTechniciansEvenWhenCalledDirectly(Role role) {
        setState(RepairRequestStatus.IN_PROGRESS);
        Long actorId = role == Role.ADMIN ? adminId : authorId;
        var form = service.getResolutionForm(requestId, technicianId);
        assertThatThrownBy(() -> service.startWork(requestId, actorId)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.getResolutionForm(requestId, actorId)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.saveResolution(requestId, form, actorId)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.completeWork(requestId, form, actorId)).isInstanceOf(AccessDeniedException.class);
        assertThat(service.getDetails(requestId, actorId).canWork()).isFalse();
        assertThat(service.getDetails(requestId, actorId).canStart()).isFalse();
    }

    @Test
    void unrelatedTechnicianGets404ForReadAndWrite() {
        setState(RepairRequestStatus.IN_PROGRESS);
        var form = service.getResolutionForm(requestId, technicianId);
        assertThatThrownBy(() -> service.startWork(requestId, replacementId)).isInstanceOf(RepairRequestNotFoundException.class);
        assertThatThrownBy(() -> service.getResolutionForm(requestId, replacementId)).isInstanceOf(RepairRequestNotFoundException.class);
        assertThatThrownBy(() -> service.saveResolution(requestId, form, replacementId)).isInstanceOf(RepairRequestNotFoundException.class);
        assertThatThrownBy(() -> service.completeWork(requestId, form, replacementId)).isInstanceOf(RepairRequestNotFoundException.class);
    }

    @Test
    void technicianAuthorCannotWorkOnOwnUnassignedRequest() {
        Long own = requests.saveAndFlush(new RepairRequest("Own problem title", "Own problem description",
                equipmentRepository.findById(equipmentId).orElseThrow(), users.findById(technicianId).orElseThrow(), null)).getId();
        entityManager.clear();
        var form = new RepairRequestResolutionForm("Attempted result", service.getDetails(own, technicianId).updatedAt());
        assertThatThrownBy(() -> service.startWork(own, technicianId)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.getResolutionForm(own, technicianId)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.saveResolution(own, form, technicianId)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.completeWork(own, form, technicianId)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void rechecksAccountActivityAndRoleFromDatabase() {
        start();
        var form = service.getResolutionForm(requestId, technicianId);
        jdbc.update("UPDATE users SET active = false WHERE id = ?", technicianId);
        entityManager.clear();
        assertThatThrownBy(() -> service.saveResolution(requestId, form, technicianId)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.completeWork(requestId, form, technicianId)).isInstanceOf(AccessDeniedException.class);
        jdbc.update("UPDATE users SET active = true, role = 'REQUESTER' WHERE id = ?", technicianId);
        entityManager.clear();
        assertThatThrownBy(() -> service.startWork(requestId, technicianId)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.getResolutionForm(requestId, technicianId)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void missingVersionOrRequestCannotBeSubmitted() {
        start();
        var form = new RepairRequestResolutionForm("Attempted result", null);
        assertThatThrownBy(() -> service.saveResolution(requestId, form, technicianId)).isInstanceOf(RepairRequestWorkConflictException.class);
        assertThatThrownBy(() -> service.completeWork(requestId, form, technicianId)).isInstanceOf(RepairRequestWorkConflictException.class);
        assertThatThrownBy(() -> service.startWork(Long.MAX_VALUE, technicianId)).isInstanceOf(RepairRequestNotFoundException.class);
        assertThatThrownBy(() -> service.getResolutionForm(Long.MAX_VALUE, technicianId)).isInstanceOf(RepairRequestNotFoundException.class);
        assertThatThrownBy(() -> service.saveResolution(Long.MAX_VALUE, form, technicianId)).isInstanceOf(RepairRequestNotFoundException.class);
    }

    @Test
    void repeatedCompletionCannotChangeResultOrTime() {
        start();
        var form = service.getResolutionForm(requestId, technicianId);
        form.setResolution("Seal replaced and tested");
        service.completeWork(requestId, form, technicianId);
        reload();
        var before = service.getDetails(requestId, technicianId);
        form.setResolution("Duplicate completion attempt");
        assertThatThrownBy(() -> service.completeWork(requestId, form, technicianId)).isInstanceOf(RepairRequestWorkConflictException.class);
        assertThat(service.getDetails(requestId, technicianId).completedAt()).isEqualTo(before.completedAt());
        assertThat(service.getDetails(requestId, technicianId).resolution()).isEqualTo(before.resolution());
        assertThat(maintenance.count()).isZero();
    }

    private void submit(RepairRequestResolutionForm form, boolean complete) {
        if (complete) service.completeWork(requestId, form, technicianId);
        else service.saveResolution(requestId, form, technicianId);
    }

    private void start() {
        service.startWork(requestId, technicianId);
        reload();
    }

    private User saveUser(String name, String email, Role role) {
        return users.saveAndFlush(new User(name, email, "hashed-password", role));
    }

    private void reload() {
        entityManager.flush();
        entityManager.clear();
    }

    // Later steps add review, closing and cancellation actions.
    private void setState(RepairRequestStatus state) {
        String value = state.name();
        jdbc.update("""
                UPDATE repair_requests SET status = ?,
                    started_at = CASE WHEN ? = 'ASSIGNED' THEN NULL ELSE TIMESTAMPTZ '2026-01-02 00:00:00+00' END,
                    completed_at = CASE WHEN ? IN ('COMPLETED', 'CLOSED') THEN CURRENT_TIMESTAMP ELSE NULL END,
                    closed_at = CASE WHEN ? IN ('CLOSED', 'CANCELLED') THEN CURRENT_TIMESTAMP ELSE NULL END,
                    resolution = 'Existing draft',
                    cancellation_reason = CASE WHEN ? = 'CANCELLED' THEN 'Duplicate problem report' ELSE NULL END
                WHERE id = ?
                """, value, value, value, value, value, requestId);
        entityManager.clear();
    }
}
