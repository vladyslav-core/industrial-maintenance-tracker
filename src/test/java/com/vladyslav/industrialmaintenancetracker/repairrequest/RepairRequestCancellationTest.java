package com.vladyslav.industrialmaintenancetracker.repairrequest;

import com.vladyslav.industrialmaintenancetracker.equipment.Equipment;
import com.vladyslav.industrialmaintenancetracker.equipment.EquipmentRepository;
import com.vladyslav.industrialmaintenancetracker.equipment.EquipmentStatus;
import com.vladyslav.industrialmaintenancetracker.exception.InvalidRepairRequestActionException;
import com.vladyslav.industrialmaintenancetracker.exception.RepairRequestEditConflictException;
import com.vladyslav.industrialmaintenancetracker.exception.RepairRequestNotFoundException;
import com.vladyslav.industrialmaintenancetracker.exception.RepairRequestWorkConflictException;
import com.vladyslav.industrialmaintenancetracker.maintenance.MaintenanceRecordRepository;
import com.vladyslav.industrialmaintenancetracker.repairrequest.dto.RepairRequestCancellationForm;
import com.vladyslav.industrialmaintenancetracker.repairrequest.dto.RepairRequestDetails;
import com.vladyslav.industrialmaintenancetracker.repairrequest.dto.RepairRequestEditForm;
import com.vladyslav.industrialmaintenancetracker.user.Role;
import com.vladyslav.industrialmaintenancetracker.user.User;
import com.vladyslav.industrialmaintenancetracker.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DataJpaTest
@Import(RepairRequestService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RepairRequestCancellationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

    @Autowired private RepairRequestService service;
    @Autowired private RepairRequestRepository requests;
    @Autowired private EquipmentRepository equipmentRepository;
    @Autowired private UserRepository users;
    @Autowired private MaintenanceRecordRepository maintenance;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;

    private TransactionTemplate transaction;
    private Long adminId;
    private Long authorId;
    private Long technicianId;
    private Long otherTechnicianId;
    private Long otherRequesterId;
    private Long requestId;
    private Long equipmentId;

    @BeforeEach
    void commitNewRequest() {
        transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            adminId = saveUser("Administrator", "admin@example.com", Role.ADMIN).getId();
            User author = saveUser("Author", "author@example.com", Role.REQUESTER);
            authorId = author.getId();
            technicianId = saveUser("Technician", "tech@example.com", Role.TECHNICIAN).getId();
            otherTechnicianId = saveUser("Other Technician", "other-tech@example.com", Role.TECHNICIAN).getId();
            otherRequesterId = saveUser("Other Requester", "other-author@example.com", Role.REQUESTER).getId();
            Equipment equipment = equipmentRepository.saveAndFlush(new Equipment("Press", "PRESS-CANCEL-001", null, "Workshop A"));
            equipmentId = equipment.getId();
            requestId = requests.saveAndFlush(new RepairRequest("Oil leak in press", "Hydraulic oil leaking around the seal",
                    equipment, author, RepairRequestPriority.HIGH)).getId();
        });
    }

    @AfterEach
    void cleanIsolatedContainerDatabase() {
        jdbc.update("DELETE FROM maintenance_records");
        jdbc.update("DELETE FROM repair_requests");
        jdbc.update("DELETE FROM equipment");
        jdbc.update("DELETE FROM users");
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void authorOfAnyRoleCanCancelOwnUnassignedNewRequest(Role role) {
        jdbc.update("UPDATE users SET role = ? WHERE id = ?", role.name(), authorId);
        assertThat(service.getDetails(requestId, authorId).canCancel()).isTrue();
        var form = service.getCancellationForm(requestId, authorId);
        assertThat(form.getCancellationReason()).isNull();
        form.setCancellationReason("  Duplicate request for the same fault.  ");
        service.cancelRequest(requestId, form, authorId);
        var after = service.getDetails(requestId, authorId);
        assertThat(after.status()).isEqualTo(RepairRequestStatus.CANCELLED);
        assertThat(after.cancellationReason()).isEqualTo("Duplicate request for the same fault.");
        assertThat(after.closedAt()).isNotNull();
        assertThat(after.assignedAt()).isNull();
        assertThat(after.technicianName()).isNull();
        assertThat(after.startedAt()).isNull();
        assertThat(after.completedAt()).isNull();
        assertThat(after.canCancel()).isFalse();
        assertThat(after.canEdit()).isFalse();
        assertNoHistoryOrEquipmentChange();
    }

    @ParameterizedTest
    @EnumSource(value = RepairRequestStatus.class, names = {"NEW", "ASSIGNED", "IN_PROGRESS"})
    void adminCancelsAnyOpenRequestWithoutLosingWorkOrCreatingHistory(RepairRequestStatus state) {
        advanceTo(state);
        var before = service.getDetails(requestId, adminId);
        assertThat(before.canCancel()).isTrue();
        Instant earliestClosure = Instant.now();
        service.cancelRequest(requestId, cancellationForm(adminId), adminId);
        var after = service.getDetails(requestId, authorId);
        assertThat(after.status()).isEqualTo(RepairRequestStatus.CANCELLED);
        assertThat(after.closedAt()).isAfterOrEqualTo(earliestClosure.minusMillis(1));
        assertThat(after.updatedAt()).isAfter(before.updatedAt());
        assertThat(after.completedAt()).isNull();
        assertThat(after.cancellationReason()).isEqualTo("Duplicate request for this fault.");
        assertRepairDataPreserved(before, after);
        assertThat(after.canCancel()).isFalse();
        var adminCard = service.getDetails(requestId, adminId);
        assertThat(adminCard.canManage()).isFalse();
        assertThat(adminCard.canClose()).isFalse();
        assertThat(adminCard.canReturn()).isFalse();
        assertNoHistoryOrEquipmentChange();
        if (state != RepairRequestStatus.NEW) {
            assertThat(service.getDetails(requestId, technicianId).status()).isEqualTo(RepairRequestStatus.CANCELLED);
        }
    }

    @ParameterizedTest
    @EnumSource(value = RepairRequestStatus.class, names = {"ASSIGNED", "IN_PROGRESS"})
    void authorCannotCancelAfterAssignmentEvenWithCurrentVersion(RepairRequestStatus state) {
        advanceTo(state);
        var form = cancellationForm(authorId);
        assertThat(service.getDetails(requestId, authorId).canCancel()).isFalse();
        assertThatThrownBy(() -> service.getCancellationForm(requestId, authorId))
                .isInstanceOf(InvalidRepairRequestActionException.class);
        assertThatThrownBy(() -> service.cancelRequest(requestId, form, authorId))
                .isInstanceOf(InvalidRepairRequestActionException.class);
        assertThat(service.getDetails(requestId, adminId).status()).isEqualTo(state);
        assertNoHistoryOrEquipmentChange();
    }

    @ParameterizedTest
    @EnumSource(value = RepairRequestStatus.class, names = {"ASSIGNED", "IN_PROGRESS"})
    void assignedTechnicianCannotCancelSomeoneElsesRequest(RepairRequestStatus state) {
        advanceTo(state);
        var form = cancellationForm(technicianId);
        assertThat(service.getDetails(requestId, technicianId).canCancel()).isFalse();
        assertThatThrownBy(() -> service.cancelRequest(requestId, form, technicianId)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.getCancellationForm(requestId, technicianId)).isInstanceOf(AccessDeniedException.class);
        assertThat(service.getDetails(requestId, adminId).status()).isEqualTo(state);
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"TECHNICIAN", "REQUESTER"})
    void unrelatedUserCannotDiscoverOrCancelAnotherRequest(Role role) {
        Long actor = role == Role.TECHNICIAN ? otherTechnicianId : otherRequesterId;
        var form = cancellationForm(adminId);
        assertThatThrownBy(() -> service.cancelRequest(requestId, form, actor)).isInstanceOf(RepairRequestNotFoundException.class);
        assertThatThrownBy(() -> service.getCancellationForm(requestId, actor)).isInstanceOf(RepairRequestNotFoundException.class);
        assertThat(service.getDetails(requestId, adminId).status()).isEqualTo(RepairRequestStatus.NEW);
    }

    @ParameterizedTest
    @EnumSource(value = RepairRequestStatus.class, names = {"COMPLETED", "CLOSED", "CANCELLED"})
    void adminCannotCancelCompletedOrFinalStatesOrChangeExistingHistory(RepairRequestStatus state) {
        advanceTo(state);
        var before = service.getDetails(requestId, adminId);
        long historyCount = maintenance.count();
        assertThat(before.canCancel()).isFalse();
        assertThatThrownBy(() -> service.cancelRequest(requestId, cancellationForm(adminId), adminId))
                .isInstanceOf(InvalidRepairRequestActionException.class);
        assertThatThrownBy(() -> service.getCancellationForm(requestId, adminId))
                .isInstanceOf(InvalidRepairRequestActionException.class);
        assertThat(service.getDetails(requestId, adminId)).isEqualTo(before);
        assertThat(maintenance.count()).isEqualTo(historyCount);
    }

    @Test
    void authorCannotCancelFromStaleCardAfterEditingTheRequest() {
        var stale = cancellationForm(authorId);
        var edit = service.getEditForm(requestId, authorId);
        edit.setDescription("Updated fault description after another inspection");
        service.updateDetails(requestId, edit, authorId);
        var before = service.getDetails(requestId, adminId);
        assertThatThrownBy(() -> service.cancelRequest(requestId, stale, authorId))
                .isInstanceOf(InvalidRepairRequestActionException.class).hasMessageContaining("Reload");
        assertThat(service.getDetails(requestId, adminId)).isEqualTo(before);
    }

    @Test
    void adminCannotCancelUsingVersionFromBeforeAssignmentAndDraft() {
        var stale = cancellationForm(adminId);
        advanceTo(RepairRequestStatus.IN_PROGRESS);
        var before = service.getDetails(requestId, adminId);
        assertThatThrownBy(() -> service.cancelRequest(requestId, stale, adminId))
                .isInstanceOf(InvalidRepairRequestActionException.class).hasMessageContaining("Reload");
        assertThat(service.getDetails(requestId, adminId)).isEqualTo(before);
        assertNoHistoryOrEquipmentChange();
    }

    @Test
    void returnedNewRequestNeedsFreshVersionBeforeAuthorCanCancel() {
        var stale = cancellationForm(authorId);
        service.assign(requestId, technicianId, adminId);
        service.unassign(requestId, adminId);
        assertThat(service.getDetails(requestId, authorId).canCancel()).isTrue();
        assertThatThrownBy(() -> service.cancelRequest(requestId, stale, authorId))
                .isInstanceOf(InvalidRepairRequestActionException.class).hasMessageContaining("Reload");
        var fresh = service.getCancellationForm(requestId, authorId);
        fresh.setCancellationReason("Duplicate request after removing assignment");
        service.cancelRequest(requestId, fresh, authorId);
        assertThat(service.getDetails(requestId, authorId).status()).isEqualTo(RepairRequestStatus.CANCELLED);
        assertNoHistoryOrEquipmentChange();
    }

    @Test
    void demotedTechnicianCannotUseOldAssignmentToDiscoverRequest() {
        advanceTo(RepairRequestStatus.ASSIGNED);
        var form = cancellationForm(technicianId);
        jdbc.update("UPDATE users SET role = 'REQUESTER' WHERE id = ?", technicianId);
        assertThatThrownBy(() -> service.cancelRequest(requestId, form, technicianId))
                .isInstanceOf(RepairRequestNotFoundException.class);
        assertThatThrownBy(() -> service.getCancellationForm(requestId, technicianId))
                .isInstanceOf(RepairRequestNotFoundException.class);
        assertThat(service.getDetails(requestId, adminId).status()).isEqualTo(RepairRequestStatus.ASSIGNED);
    }

    @Test
    void unknownRequestIsUnavailableToAdministratorToo() {
        var form = cancellationForm(adminId);
        assertThatThrownBy(() -> service.cancelRequest(Long.MAX_VALUE, form, adminId))
                .isInstanceOf(RepairRequestNotFoundException.class);
        assertThatThrownBy(() -> service.getCancellationForm(Long.MAX_VALUE, adminId))
                .isInstanceOf(RepairRequestNotFoundException.class);
    }

    @Test
    void serviceRejectsInvalidReasonAndMissingVersionWithoutRelyingOnMvcValidation() {
        var before = service.getDetails(requestId, adminId);
        for (String reason : Arrays.asList(null, "", "    ", "four", "x".repeat(501))) {
            var invalid = new RepairRequestCancellationForm(reason, before.updatedAt());
            assertThatThrownBy(() -> service.cancelRequest(requestId, invalid, adminId))
                    .isInstanceOf(InvalidRepairRequestActionException.class);
        }
        var noVersion = new RepairRequestCancellationForm("Duplicate request", null);
        assertThatThrownBy(() -> service.cancelRequest(requestId, noVersion, adminId))
                .isInstanceOf(InvalidRepairRequestActionException.class);
        assertThat(service.getDetails(requestId, adminId)).isEqualTo(before);
        assertNoHistoryOrEquipmentChange();
    }

    @Test
    void checksCurrentDatabaseRoleAndActivityInsteadOfTrustingAnOldAdminSession() {
        var form = cancellationForm(adminId);
        jdbc.update("UPDATE users SET active = false WHERE id = ?", adminId);
        assertThatThrownBy(() -> service.cancelRequest(requestId, form, adminId)).isInstanceOf(AccessDeniedException.class);
        jdbc.update("UPDATE users SET active = true, role = 'REQUESTER' WHERE id = ?", adminId);
        assertThatThrownBy(() -> service.cancelRequest(requestId, form, adminId)).isInstanceOf(RepairRequestNotFoundException.class);
        assertThat(service.getDetails(requestId, authorId).status()).isEqualTo(RepairRequestStatus.NEW);
    }

    @Test
    void repeatCancellationCannotOverwriteOriginalReasonOrTime() {
        var form = cancellationForm(authorId);
        service.cancelRequest(requestId, form, authorId);
        var cancelled = service.getDetails(requestId, authorId);
        var retry = new RepairRequestCancellationForm("A different reason", cancelled.updatedAt());
        assertThatThrownBy(() -> service.cancelRequest(requestId, retry, adminId))
                .isInstanceOf(InvalidRepairRequestActionException.class);
        assertThat(service.getDetails(requestId, authorId)).isEqualTo(cancelled);
        assertNoHistoryOrEquipmentChange();
    }

    @Test
    void cancellationPreventsLaterEditsWorkCompletionReviewAndManagement() {
        advanceTo(RepairRequestStatus.IN_PROGRESS);
        var staleWork = service.getResolutionForm(requestId, technicianId);
        service.cancelRequest(requestId, cancellationForm(adminId), adminId);
        var cancelled = service.getDetails(requestId, adminId);
        assertThatThrownBy(() -> service.updateDetails(requestId,
                new RepairRequestEditForm("Changed title", "Changed description", cancelled.updatedAt()), authorId))
                .isInstanceOf(RepairRequestEditConflictException.class);
        assertThatThrownBy(() -> service.saveResolution(requestId, staleWork, technicianId))
                .isInstanceOf(RepairRequestWorkConflictException.class);
        assertThatThrownBy(() -> service.completeWork(requestId, staleWork, technicianId))
                .isInstanceOf(RepairRequestWorkConflictException.class);
        assertThatThrownBy(() -> service.assign(requestId, otherTechnicianId, adminId))
                .isInstanceOf(InvalidRepairRequestActionException.class);
        assertThatThrownBy(() -> service.changePriority(requestId, RepairRequestPriority.CRITICAL, adminId))
                .isInstanceOf(InvalidRepairRequestActionException.class);
        assertThatThrownBy(() -> service.closeRequest(requestId, cancelled.updatedAt(), adminId))
                .isInstanceOf(InvalidRepairRequestActionException.class);
        assertThatThrownBy(() -> service.returnForRework(requestId, cancelled.updatedAt(), adminId))
                .isInstanceOf(InvalidRepairRequestActionException.class);
        assertThat(service.getDetails(requestId, adminId)).isEqualTo(cancelled);
        assertNoHistoryOrEquipmentChange();
    }

    @Test
    void concurrentCompletionAndCancellationCannotBothSucceed() throws Exception {
        advanceTo(RepairRequestStatus.IN_PROGRESS);
        var cancelForm = cancellationForm(adminId);
        var workForm = service.getResolutionForm(requestId, technicianId);
        workForm.setResolution("Seal replaced and tested");
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var cancellation = pool.submit(() -> concurrentAction(ready, start,
                    () -> service.cancelRequest(requestId, cancelForm, adminId)));
            var completion = pool.submit(() -> concurrentAction(ready, start,
                    () -> service.completeWork(requestId, workForm, technicianId)));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var outcomes = Arrays.asList(cancellation.get(15, TimeUnit.SECONDS), completion.get(15, TimeUnit.SECONDS));
            assertThat(outcomes.stream().filter(Objects::isNull).count()).isEqualTo(1);
            assertThat(outcomes.stream().filter(Objects::nonNull).findFirst().orElseThrow())
                    .isInstanceOfAny(InvalidRepairRequestActionException.class, RepairRequestWorkConflictException.class);
            var after = service.getDetails(requestId, adminId);
            if (after.status() == RepairRequestStatus.CANCELLED) {
                assertThat(after.closedAt()).isNotNull();
                assertThat(after.completedAt()).isNull();
                assertThat(after.cancellationReason()).isEqualTo(cancelForm.getCancellationReason());
                assertThat(after.resolution()).isEqualTo("Seal inspection draft");
            } else {
                assertThat(after.status()).isEqualTo(RepairRequestStatus.COMPLETED);
                assertThat(after.closedAt()).isNull();
                assertThat(after.completedAt()).isNotNull();
                assertThat(after.cancellationReason()).isNull();
                assertThat(after.resolution()).isEqualTo(workForm.getResolution());
            }
            assertNoHistoryOrEquipmentChange();
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private RuntimeException concurrentAction(CountDownLatch ready, CountDownLatch start, Runnable action) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Concurrent test did not start");
        try {
            transaction.executeWithoutResult(status -> {
                jdbc.execute("SET LOCAL lock_timeout = '5s'");
                action.run();
            });
            return null;
        } catch (RuntimeException exception) {
            return exception;
        }
    }

    private void advanceTo(RepairRequestStatus state) {
        if (state == RepairRequestStatus.NEW) return;
        service.assign(requestId, technicianId, adminId);
        if (state == RepairRequestStatus.ASSIGNED) return;
        service.startWork(requestId, technicianId);
        var form = service.getResolutionForm(requestId, technicianId);
        form.setResolution("Seal inspection draft");
        service.saveResolution(requestId, form, technicianId);
        if (state == RepairRequestStatus.IN_PROGRESS) return;
        if (state == RepairRequestStatus.CANCELLED) {
            service.cancelRequest(requestId, cancellationForm(adminId), adminId);
            return;
        }
        form = service.getResolutionForm(requestId, technicianId);
        form.setResolution("Seal replaced and tested");
        service.completeWork(requestId, form, technicianId);
        if (state == RepairRequestStatus.CLOSED) {
            service.closeRequest(requestId, service.getDetails(requestId, adminId).updatedAt(), adminId);
        }
    }

    private RepairRequestCancellationForm cancellationForm(Long viewerId) {
        return new RepairRequestCancellationForm("Duplicate request for this fault.", service.getDetails(requestId, viewerId).updatedAt());
    }

    private void assertNoHistoryOrEquipmentChange() {
        assertThat(maintenance.count()).isZero();
        assertThat(equipmentRepository.findById(equipmentId).orElseThrow().getStatus()).isEqualTo(EquipmentStatus.OPERATIONAL);
    }

    private void assertRepairDataPreserved(RepairRequestDetails before, RepairRequestDetails after) {
        assertThat(after.title()).isEqualTo(before.title());
        assertThat(after.description()).isEqualTo(before.description());
        assertThat(after.equipmentId()).isEqualTo(before.equipmentId());
        assertThat(after.authorName()).isEqualTo(before.authorName());
        assertThat(after.technicianName()).isEqualTo(before.technicianName());
        assertThat(after.assignedAt()).isEqualTo(before.assignedAt());
        assertThat(after.startedAt()).isEqualTo(before.startedAt());
        assertThat(after.resolution()).isEqualTo(before.resolution());
        assertThat(after.priority()).isEqualTo(before.priority());
        assertThat(after.createdAt()).isEqualTo(before.createdAt());
    }

    private User saveUser(String name, String email, Role role) {
        return users.saveAndFlush(new User(name, email, "hashed-password", role));
    }
}
