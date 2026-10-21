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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DataJpaTest
@Import(RepairRequestService.class)
// Service calls must really commit or roll back; no surrounding test transaction may hide the outcome.
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RepairRequestClosureTest {

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
    @Autowired private PlatformTransactionManager transactionManager;

    private TransactionTemplate transaction;
    private Long adminId;
    private Long authorId;
    private Long technicianId;
    private Long replacementId;
    private Long requestId;
    private Long equipmentId;

    @BeforeEach
    void commitCompletedRepair() {
        transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            adminId = saveUser("Administrator", "admin@example.com", Role.ADMIN).getId();
            User author = saveUser("Author", "author@example.com", Role.REQUESTER);
            authorId = author.getId();
            technicianId = saveUser("Technician", "tech@example.com", Role.TECHNICIAN).getId();
            replacementId = saveUser("Replacement", "replacement@example.com", Role.TECHNICIAN).getId();
            Equipment equipment = equipmentRepository.saveAndFlush(new Equipment("Press", "PRESS-CLOSE-001", null, "Workshop A"));
            equipmentId = equipment.getId();
            requestId = requests.saveAndFlush(new RepairRequest("Oil leak in press", "Hydraulic oil leaking around the seal",
                    equipment, author, RepairRequestPriority.HIGH)).getId();
            entityManager.clear();
            service.assign(requestId, technicianId, adminId);
            flushAndClear();
            service.startWork(requestId, technicianId);
            flushAndClear();
            var form = service.getResolutionForm(requestId, technicianId);
            form.setResolution("Seal replaced. Pressure test passed.");
            service.completeWork(requestId, form, technicianId);
        });
    }

    @AfterEach
    void cleanIsolatedContainerDatabase() {
        dropFailureTrigger();
        jdbc.update("DELETE FROM maintenance_records");
        jdbc.update("DELETE FROM repair_requests");
        jdbc.update("DELETE FROM equipment");
        jdbc.update("DELETE FROM users");
    }

    @Test
    void closesRepairAndCommitsExactHistorySnapshot() {
        var before = service.getDetails(requestId, adminId);
        assertThat(maintenance.count()).isZero();
        service.closeRequest(requestId, before.updatedAt(), adminId);
        var after = service.getDetails(requestId, authorId);
        assertThat(after.status()).isEqualTo(RepairRequestStatus.CLOSED);
        assertThat(after.closedAt()).isAfterOrEqualTo(before.completedAt());
        assertThat(after.updatedAt()).isAfter(before.updatedAt());
        assertThat(after.completedAt()).isEqualTo(before.completedAt());
        assertThat(after.startedAt()).isEqualTo(before.startedAt());
        assertThat(after.assignedAt()).isEqualTo(before.assignedAt());
        assertThat(after.resolution()).isEqualTo(before.resolution());
        assertThat(after.title()).isEqualTo(before.title());
        assertThat(after.description()).isEqualTo(before.description());
        assertThat(after.priority()).isEqualTo(before.priority());
        assertThat(after.authorName()).isEqualTo(before.authorName());
        assertThat(after.createdAt()).isEqualTo(before.createdAt());
        assertThat(maintenance.count()).isEqualTo(1);
        transaction.executeWithoutResult(status -> {
            var record = maintenance.findByRepairRequestId(requestId).orElseThrow();
            assertThat(record.getRepairRequest().getId()).isEqualTo(requestId);
            assertThat(record.getEquipment().getId()).isEqualTo(equipmentId);
            assertThat(record.getPerformedBy().getId()).isEqualTo(technicianId);
            assertThat(record.getWorkDescription()).isEqualTo(before.resolution());
            assertThat(record.getPerformedAt()).isEqualTo(before.completedAt());
            assertThat(record.getCreatedAt()).isAfterOrEqualTo(after.closedAt());
        });
        assertThat(equipmentRepository.findById(equipmentId).orElseThrow().getStatus()).isEqualTo(EquipmentStatus.OPERATIONAL);
        assertThat(service.getDetails(requestId, adminId).canClose()).isFalse();
        assertThat(service.getDetails(requestId, adminId).canReturn()).isFalse();
    }

    @Test
    void repeatedCloseKeepsOneRecordAndOriginalClosureTime() {
        Instant version = currentVersion();
        service.closeRequest(requestId, version, adminId);
        var first = service.getDetails(requestId, adminId);
        Long historyId = maintenance.findByRepairRequestId(requestId).orElseThrow().getId();
        service.closeRequest(requestId, version, adminId);
        var second = service.getDetails(requestId, adminId);
        assertThat(second.closedAt()).isEqualTo(first.closedAt());
        assertThat(second.updatedAt()).isEqualTo(first.updatedAt());
        assertThat(maintenance.count()).isEqualTo(1);
        assertThat(maintenance.findByRepairRequestId(requestId).orElseThrow().getId()).isEqualTo(historyId);
    }

    @Test
    void concurrentClosesBothFinishWithExactlyOneRecord() throws Exception {
        Instant version = currentVersion();
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            Runnable close = () -> {
                ready.countDown();
                try {
                    if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Start barrier timed out");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
                transaction.executeWithoutResult(status -> {
                    jdbc.execute("SET LOCAL lock_timeout = '5s'");
                    service.closeRequest(requestId, version, adminId);
                });
            };
            var first = pool.submit(close);
            var second = pool.submit(close);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(service.getDetails(requestId, adminId).status()).isEqualTo(RepairRequestStatus.CLOSED);
        assertThat(maintenance.count()).isEqualTo(1);
    }

    @Test
    void historyInsertFailureReallyRollsBackAlreadyFlushedClosureAndAllowsRetry() {
        var before = service.getDetails(requestId, adminId);
        jdbc.execute("""
                CREATE FUNCTION fail_test_history_insert() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    IF (SELECT status FROM repair_requests WHERE id = NEW.repair_request_id)
                            IS DISTINCT FROM 'CLOSED' THEN
                        RAISE EXCEPTION 'closure_was_not_flushed';
                    END IF;
                    RAISE EXCEPTION 'simulated_history_write_failure' USING ERRCODE = '23514';
                    RETURN NEW;
                END;
                $$
                """);
        jdbc.execute("""
                CREATE TRIGGER fail_test_history_insert BEFORE INSERT ON maintenance_records
                FOR EACH ROW EXECUTE FUNCTION fail_test_history_insert()
                """);
        assertThatThrownBy(() -> service.closeRequest(requestId, before.updatedAt(), adminId))
                .isInstanceOf(DataAccessException.class)
                .hasStackTraceContaining("simulated_history_write_failure");
        // This read starts a new transaction after the failed service transaction has rolled back.
        var after = service.getDetails(requestId, adminId);
        assertThat(after.status()).isEqualTo(RepairRequestStatus.COMPLETED);
        assertThat(after.closedAt()).isNull();
        assertThat(after.updatedAt()).isEqualTo(before.updatedAt());
        assertThat(after.completedAt()).isEqualTo(before.completedAt());
        assertThat(after.resolution()).isEqualTo(before.resolution());
        assertThat(maintenance.count()).isZero();
        dropFailureTrigger();
        service.closeRequest(requestId, before.updatedAt(), adminId);
        assertThat(service.getDetails(requestId, adminId).status()).isEqualTo(RepairRequestStatus.CLOSED);
        assertThat(maintenance.count()).isEqualTo(1);
    }

    @Test
    void snapshotUsesLastCompletionAndCurrentTechnicianAfterRework() {
        var first = service.getDetails(requestId, adminId);
        service.returnForRework(requestId, first.updatedAt(), adminId);
        service.assign(requestId, replacementId, adminId);
        var form = service.getResolutionForm(requestId, replacementId);
        form.setResolution("Replacement technician corrected and tested the repair");
        service.completeWork(requestId, form, replacementId);
        var corrected = service.getDetails(requestId, adminId);
        service.closeRequest(requestId, corrected.updatedAt(), adminId);
        transaction.executeWithoutResult(status -> {
            var record = maintenance.findByRepairRequestId(requestId).orElseThrow();
            assertThat(record.getPerformedBy().getId()).isEqualTo(replacementId);
            assertThat(record.getPerformedAt()).isEqualTo(corrected.completedAt());
            assertThat(record.getWorkDescription()).isEqualTo(corrected.resolution());
        });
        assertThat(service.getDetails(requestId, adminId).startedAt()).isEqualTo(first.startedAt());
    }

    @Test
    void staleAcceptanceCannotCloseUnseenCorrectedResult() {
        Instant oldReview = currentVersion();
        service.returnForRework(requestId, oldReview, adminId);
        var form = service.getResolutionForm(requestId, technicianId);
        form.setResolution("Corrected result after extended testing");
        service.completeWork(requestId, form, technicianId);
        assertThatThrownBy(() -> service.closeRequest(requestId, oldReview, adminId))
                .isInstanceOf(InvalidRepairRequestActionException.class);
        assertThat(service.getDetails(requestId, adminId).status()).isEqualTo(RepairRequestStatus.COMPLETED);
        assertThat(maintenance.count()).isZero();
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"TECHNICIAN", "REQUESTER"})
    void onlyAdminCanCloseIncludingDirectServiceCalls(Role role) {
        Instant version = currentVersion();
        Long actorId = role == Role.TECHNICIAN ? technicianId : authorId;
        assertThatThrownBy(() -> service.closeRequest(requestId, version, actorId)).isInstanceOf(AccessDeniedException.class);
        assertThat(service.getDetails(requestId, adminId).status()).isEqualTo(RepairRequestStatus.COMPLETED);
        assertThat(maintenance.count()).isZero();
        service.closeRequest(requestId, version, adminId);
        assertThatThrownBy(() -> service.closeRequest(requestId, version, actorId)).isInstanceOf(AccessDeniedException.class);
    }

    @ParameterizedTest
    @EnumSource(value = RepairRequestStatus.class, names = {"NEW", "ASSIGNED", "IN_PROGRESS", "CANCELLED"})
    void rejectsClosureOutsideCompleted(RepairRequestStatus state) {
        setState(state);
        Instant version = currentVersion();
        assertThatThrownBy(() -> service.closeRequest(requestId, version, adminId)).isInstanceOf(InvalidRepairRequestActionException.class);
        assertThat(service.getDetails(requestId, adminId).status()).isEqualTo(state);
        assertThat(maintenance.count()).isZero();
    }

    @Test
    void missingVersionAndMissingRequestAreRejected() {
        assertThatThrownBy(() -> service.closeRequest(requestId, null, adminId)).isInstanceOf(InvalidRepairRequestActionException.class);
        assertThatThrownBy(() -> service.closeRequest(Long.MAX_VALUE, Instant.EPOCH, adminId)).isInstanceOf(RepairRequestNotFoundException.class);
        assertThat(maintenance.count()).isZero();
    }

    @Test
    void databaseAccountChangesOverrideOldAdminSession() {
        Instant version = currentVersion();
        jdbc.update("UPDATE users SET active = false WHERE id = ?", adminId);
        assertThatThrownBy(() -> service.closeRequest(requestId, version, adminId)).isInstanceOf(AccessDeniedException.class);
        jdbc.update("UPDATE users SET active = true, role = 'REQUESTER' WHERE id = ?", adminId);
        assertThatThrownBy(() -> service.closeRequest(requestId, version, adminId)).isInstanceOf(AccessDeniedException.class);
        assertThat(maintenance.count()).isZero();
    }

    @Test
    void closedResultAndSnapshotCannotBeChangedThroughWorkflow() {
        Instant version = currentVersion();
        service.closeRequest(requestId, version, adminId);
        var form = new RepairRequestResolutionForm("Attempted edit", version);
        assertThatThrownBy(() -> service.saveResolution(requestId, form, technicianId)).isInstanceOf(RepairRequestWorkConflictException.class);
        assertThatThrownBy(() -> service.returnForRework(requestId, currentVersion(), adminId)).isInstanceOf(InvalidRepairRequestActionException.class);
        assertThatThrownBy(() -> service.changePriority(requestId, RepairRequestPriority.LOW, adminId)).isInstanceOf(InvalidRepairRequestActionException.class);
        assertThatThrownBy(() -> service.assign(requestId, replacementId, adminId)).isInstanceOf(InvalidRepairRequestActionException.class);
        assertThat(maintenance.findByRepairRequestId(requestId).orElseThrow().getWorkDescription())
                .isEqualTo("Seal replaced. Pressure test passed.");
        assertThat(maintenance.count()).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void closeActionIsAvailableOnlyToAdminBeforeClosure(Role role) {
        Long viewer = switch (role) {
            case ADMIN -> adminId;
            case TECHNICIAN -> technicianId;
            case REQUESTER -> authorId;
        };
        assertThat(service.getDetails(requestId, viewer).canClose()).isEqualTo(role == Role.ADMIN);
        service.closeRequest(requestId, currentVersion(), adminId);
        var closed = service.getDetails(requestId, viewer);
        assertThat(closed.canClose()).isFalse();
        assertThat(closed.canReturn()).isFalse();
        assertThat(closed.canWork()).isFalse();
    }

    private Instant currentVersion() {
        return service.getDetails(requestId, adminId).updatedAt();
    }

    private User saveUser(String name, String email, Role role) {
        return users.saveAndFlush(new User(name, email, "hashed-password", role));
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private void dropFailureTrigger() {
        jdbc.execute("DROP TRIGGER IF EXISTS fail_test_history_insert ON maintenance_records");
        jdbc.execute("DROP FUNCTION IF EXISTS fail_test_history_insert()");
    }

    private void setState(RepairRequestStatus state) {
        String value = state.name();
        jdbc.update("""
                UPDATE repair_requests SET status = ?,
                    assigned_technician_id = CASE WHEN ? = 'NEW' THEN NULL ELSE assigned_technician_id END,
                    assigned_at = CASE WHEN ? = 'NEW' THEN NULL ELSE assigned_at END,
                    started_at = CASE WHEN ? IN ('NEW', 'ASSIGNED') THEN NULL ELSE started_at END,
                    completed_at = NULL,
                    closed_at = CASE WHEN ? = 'CANCELLED' THEN clock_timestamp() ELSE NULL END,
                    cancellation_reason = CASE WHEN ? = 'CANCELLED' THEN 'Duplicate problem report' ELSE NULL END
                WHERE id = ?
                """, value, value, value, value, value, value, requestId);
    }
}
