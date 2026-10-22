package com.vladyslav.industrialmaintenancetracker.repairrequest;

import com.vladyslav.industrialmaintenancetracker.equipment.Equipment;
import com.vladyslav.industrialmaintenancetracker.equipment.EquipmentRepository;
import com.vladyslav.industrialmaintenancetracker.equipment.EquipmentService;
import com.vladyslav.industrialmaintenancetracker.equipment.EquipmentStatus;
import com.vladyslav.industrialmaintenancetracker.equipment.dto.EquipmentEditForm;
import com.vladyslav.industrialmaintenancetracker.exception.EquipmentHasOpenRequestsException;
import com.vladyslav.industrialmaintenancetracker.exception.EquipmentNotAvailableException;
import com.vladyslav.industrialmaintenancetracker.exception.InvalidRepairRequestActionException;
import com.vladyslav.industrialmaintenancetracker.exception.TechnicianHasOpenRequestsException;
import com.vladyslav.industrialmaintenancetracker.maintenance.MaintenanceRecordRepository;
import com.vladyslav.industrialmaintenancetracker.repairrequest.dto.RepairRequestCancellationForm;
import com.vladyslav.industrialmaintenancetracker.repairrequest.dto.RepairRequestCreateForm;
import com.vladyslav.industrialmaintenancetracker.user.Role;
import com.vladyslav.industrialmaintenancetracker.user.User;
import com.vladyslav.industrialmaintenancetracker.user.UserRepository;
import com.vladyslav.industrialmaintenancetracker.user.UserService;
import com.vladyslav.industrialmaintenancetracker.user.dto.UserEditForm;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DataJpaTest
@Import({RepairRequestService.class, EquipmentService.class, UserService.class,
        RepairRequestIntegrityTest.PasswordConfiguration.class})
// Independent service transactions must commit so a waiting operation sees their actual result.
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RepairRequestIntegrityTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

    @TestConfiguration(proxyBeanMethods = false)
    static class PasswordConfiguration {
        @Bean
        PasswordEncoder passwordEncoder() {
            return PasswordEncoderFactories.createDelegatingPasswordEncoder();
        }
    }

    @Autowired private RepairRequestService repairs;
    @Autowired private EquipmentService equipmentService;
    @Autowired private UserService userService;
    @Autowired private RepairRequestRepository requests;
    @Autowired private EquipmentRepository equipment;
    @Autowired private UserRepository users;
    @Autowired private MaintenanceRecordRepository maintenance;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;

    private TransactionTemplate transaction;
    private Long adminId;
    private Long authorId;
    private Long technicianId;
    private Long otherTechnicianId;
    private Long equipmentId;

    @BeforeEach
    void commitAvailableEquipmentAndAccounts() {
        transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            adminId = saveUser("Administrator", "admin@example.com", Role.ADMIN).getId();
            authorId = saveUser("Author", "author@example.com", Role.REQUESTER).getId();
            technicianId = saveUser("Technician", "tech@example.com", Role.TECHNICIAN).getId();
            otherTechnicianId = saveUser("Other Technician", "other-tech@example.com", Role.TECHNICIAN).getId();
            equipmentId = equipment.saveAndFlush(new Equipment("Press", "PRESS-INTEGRITY-001", null, "Workshop A")).getId();
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
    @EnumSource(RepairRequestStatus.class)
    void equipmentCanBeDecommissionedOnlyAfterEveryRequestIsFinal(RepairRequestStatus state) {
        Long requestId = createRequest();
        advanceTo(requestId, state);
        var before = repairs.getDetails(requestId, adminId);
        long historyCount = maintenance.count();
        if (state == RepairRequestStatus.CLOSED || state == RepairRequestStatus.CANCELLED) {
            equipmentService.changeEquipmentStatus(equipmentId, EquipmentStatus.DECOMMISSIONED);
            assertThat(equipment.findById(equipmentId).orElseThrow().getStatus()).isEqualTo(EquipmentStatus.DECOMMISSIONED);
        } else {
            assertThatThrownBy(() -> equipmentService.changeEquipmentStatus(equipmentId, EquipmentStatus.DECOMMISSIONED))
                    .isInstanceOf(EquipmentHasOpenRequestsException.class);
            assertThat(equipment.findById(equipmentId).orElseThrow().getStatus()).isEqualTo(EquipmentStatus.OPERATIONAL);
        }
        assertThat(repairs.getDetails(requestId, adminId)).isEqualTo(before);
        assertThat(maintenance.count()).isEqualTo(historyCount);
    }

    @ParameterizedTest
    @EnumSource(value = RepairRequestStatus.class, names = {"ASSIGNED", "IN_PROGRESS", "COMPLETED"})
    void technicianCannotBeDisabledOrChangeRoleWithOpenAssignments(RepairRequestStatus state) {
        Long requestId = createRequest();
        advanceTo(requestId, state);
        var before = repairs.getDetails(requestId, adminId);
        assertThatThrownBy(() -> userService.deactivateUser(technicianId)).isInstanceOf(TechnicianHasOpenRequestsException.class);
        for (Role role : new Role[]{Role.ADMIN, Role.REQUESTER}) {
            assertThatThrownBy(() -> userService.updateUser(technicianId,
                    new UserEditForm("Changed name", "changed@example.com", role)))
                    .isInstanceOf(TechnicianHasOpenRequestsException.class);
        }
        User technician = users.findById(technicianId).orElseThrow();
        assertThat(technician.isActive()).isTrue();
        assertThat(technician.getRole()).isEqualTo(Role.TECHNICIAN);
        assertThat(technician.getFullName()).isEqualTo("Technician");
        assertThat(technician.getEmail()).isEqualTo("tech@example.com");
        assertThat(repairs.getDetails(requestId, adminId)).isEqualTo(before);
        assertThat(maintenance.count()).isZero();
    }

    @ParameterizedTest
    @EnumSource(value = RepairRequestStatus.class, names = {"CLOSED", "CANCELLED"})
    void finalRequestsReleaseTechnicianWithoutChangingHistory(RepairRequestStatus state) {
        Long requestId = createRequest();
        advanceTo(requestId, state);
        var historyBefore = maintenance.findByRepairRequestId(requestId);
        userService.deactivateUser(technicianId);
        assertThat(users.findById(technicianId).orElseThrow().isActive()).isFalse();
        userService.activateUser(technicianId);
        userService.updateUser(technicianId, new UserEditForm("Technician", "tech@example.com", Role.REQUESTER));
        User changed = users.findById(technicianId).orElseThrow();
        assertThat(changed.isActive()).isTrue();
        assertThat(changed.getRole()).isEqualTo(Role.REQUESTER);
        assertThat(repairs.getDetails(requestId, adminId).status()).isEqualTo(state);
        var historyAfter = maintenance.findByRepairRequestId(requestId);
        assertThat(historyAfter.isPresent()).isEqualTo(historyBefore.isPresent());
        if (historyBefore.isPresent()) {
            var original = historyBefore.orElseThrow();
            var preserved = historyAfter.orElseThrow();
            assertThat(preserved.getId()).isEqualTo(original.getId());
            assertThat(preserved.getWorkDescription()).isEqualTo(original.getWorkDescription());
            assertThat(preserved.getPerformedAt()).isEqualTo(original.getPerformedAt());
            assertThat(jdbc.queryForObject("SELECT performed_by_id FROM maintenance_records WHERE repair_request_id = ?",
                    Long.class, requestId)).isEqualTo(technicianId);
        }
    }

    @Test
    void authorshipOfUnassignedNewRequestDoesNotPreventAccountChanges() {
        Long requestId = repairs.create(createForm(equipmentId), technicianId).getId();
        userService.deactivateUser(technicianId);
        userService.updateUser(technicianId, new UserEditForm("Technician", "tech@example.com", Role.REQUESTER));
        assertThat(users.findById(technicianId).orElseThrow().isActive()).isFalse();
        assertThat(users.findById(technicianId).orElseThrow().getRole()).isEqualTo(Role.REQUESTER);
        assertThat(repairs.getDetails(requestId, adminId).status()).isEqualTo(RepairRequestStatus.NEW);
        assertThatThrownBy(() -> equipmentService.changeEquipmentStatus(equipmentId, EquipmentStatus.DECOMMISSIONED))
                .isInstanceOf(EquipmentHasOpenRequestsException.class);
    }

    @Test
    void removingAssignmentReleasesTechnicianButNotEquipment() {
        Long requestId = createRequest();
        repairs.assign(requestId, technicianId, adminId);
        repairs.unassign(requestId, adminId);
        userService.deactivateUser(technicianId);
        assertThat(users.findById(technicianId).orElseThrow().isActive()).isFalse();
        assertThat(repairs.getDetails(requestId, adminId).status()).isEqualTo(RepairRequestStatus.NEW);
        assertThatThrownBy(() -> equipmentService.changeEquipmentStatus(equipmentId, EquipmentStatus.DECOMMISSIONED))
                .isInstanceOf(EquipmentHasOpenRequestsException.class);
    }

    @ParameterizedTest
    @EnumSource(value = RepairRequestStatus.class, names = {"ASSIGNED", "IN_PROGRESS"})
    void reassignmentReleasesOldTechnicianAndProtectsCurrentOne(RepairRequestStatus state) {
        Long requestId = createRequest();
        advanceTo(requestId, state);
        repairs.assign(requestId, otherTechnicianId, adminId);
        userService.deactivateUser(technicianId);
        userService.updateUser(technicianId, new UserEditForm("Technician", "tech@example.com", Role.REQUESTER));
        assertThatThrownBy(() -> userService.deactivateUser(otherTechnicianId))
                .isInstanceOf(TechnicianHasOpenRequestsException.class);
        assertThat(users.findById(technicianId).orElseThrow().isActive()).isFalse();
        assertThat(users.findById(otherTechnicianId).orElseThrow().isActive()).isTrue();
        assertThat(repairs.getDetails(requestId, adminId).technicianName()).isEqualTo("Other Technician");
    }

    @Test
    void cancellingOneRequestDoesNotReleaseEquipmentOrTechnicianWhileAnotherIsOpen() {
        Long first = createRequest();
        Long second = createRequest();
        repairs.assign(first, technicianId, adminId);
        repairs.assign(second, technicianId, adminId);
        cancel(second);
        assertThatThrownBy(() -> equipmentService.changeEquipmentStatus(equipmentId, EquipmentStatus.DECOMMISSIONED))
                .isInstanceOf(EquipmentHasOpenRequestsException.class);
        assertThatThrownBy(() -> userService.deactivateUser(technicianId)).isInstanceOf(TechnicianHasOpenRequestsException.class);
        cancel(first);
        equipmentService.changeEquipmentStatus(equipmentId, EquipmentStatus.DECOMMISSIONED);
        userService.deactivateUser(technicianId);
        assertThat(equipment.findById(equipmentId).orElseThrow().getStatus()).isEqualTo(EquipmentStatus.DECOMMISSIONED);
        assertThat(users.findById(technicianId).orElseThrow().isActive()).isFalse();
    }

    @Test
    void requestsForOtherEquipmentAndOtherTechnicianDoNotBlockFreeResources() {
        Long otherEquipment = equipment.saveAndFlush(new Equipment("Compressor", "COMP-INTEGRITY-002", null, "Workshop B")).getId();
        Long requestId = repairs.create(createForm(otherEquipment), authorId).getId();
        repairs.assign(requestId, otherTechnicianId, adminId);
        equipmentService.changeEquipmentStatus(equipmentId, EquipmentStatus.DECOMMISSIONED);
        userService.deactivateUser(technicianId);
        assertThat(equipment.findById(equipmentId).orElseThrow().getStatus()).isEqualTo(EquipmentStatus.DECOMMISSIONED);
        assertThat(users.findById(technicianId).orElseThrow().isActive()).isFalse();
        assertThat(repairs.getDetails(requestId, adminId).status()).isEqualTo(RepairRequestStatus.ASSIGNED);
    }

    @ParameterizedTest
    @EnumSource(value = EquipmentStatus.class, names = "DECOMMISSIONED", mode = EnumSource.Mode.EXCLUDE)
    void openRequestsDoNotBlockOtherEquipmentStatusChanges(EquipmentStatus state) {
        createRequest();
        equipmentService.changeEquipmentStatus(equipmentId, state);
        assertThat(equipment.findById(equipmentId).orElseThrow().getStatus()).isEqualTo(state);
    }

    @Test
    void ordinaryProfileAndEquipmentDetailsRemainEditableWithOpenRequest() {
        Long requestId = createRequest();
        advanceTo(requestId, RepairRequestStatus.IN_PROGRESS);
        userService.updateUser(technicianId, new UserEditForm("Updated Technician", "updated@example.com", Role.TECHNICIAN));
        equipmentService.updateEquipment(equipmentId, equipmentForm());
        assertThat(users.findById(technicianId).orElseThrow().getFullName()).isEqualTo("Updated Technician");
        assertThat(equipment.findById(equipmentId).orElseThrow().getLocation()).isEqualTo("Workshop B");
        assertThat(repairs.getDetails(requestId, adminId).status()).isEqualTo(RepairRequestStatus.IN_PROGRESS);
    }

    @Test
    void creationCommitsBeforeWaitingDecommissioningChecksRequests() throws Exception {
        var result = runWithFirstTransactionHoldingLock(this::createRequest,
                () -> equipmentService.changeEquipmentStatus(equipmentId, EquipmentStatus.DECOMMISSIONED));
        assertThat(result.first()).isNull();
        assertThat(result.second()).isInstanceOf(EquipmentHasOpenRequestsException.class);
        assertThat(requests.count()).isEqualTo(1);
        assertThat(equipment.findById(equipmentId).orElseThrow().getStatus()).isEqualTo(EquipmentStatus.OPERATIONAL);
    }

    @Test
    void decommissioningCommitsBeforeWaitingCreationChecksEquipmentStatus() throws Exception {
        var result = runWithFirstTransactionHoldingLock(
                () -> equipmentService.changeEquipmentStatus(equipmentId, EquipmentStatus.DECOMMISSIONED), this::createRequest);
        assertThat(result.first()).isNull();
        assertThat(result.second()).isInstanceOf(EquipmentNotAvailableException.class);
        assertThat(requests.count()).isZero();
        assertThat(equipment.findById(equipmentId).orElseThrow().getStatus()).isEqualTo(EquipmentStatus.DECOMMISSIONED);
    }

    @ParameterizedTest
    @EnumSource(AccountChange.class)
    void assignmentCommitsBeforeWaitingAccountChangeChecksOpenRequests(AccountChange change) throws Exception {
        Long requestId = createRequest();
        var result = runWithFirstTransactionHoldingLock(() -> repairs.assign(requestId, technicianId, adminId),
                () -> changeAccount(change));
        assertThat(result.first()).isNull();
        assertThat(result.second()).isInstanceOf(TechnicianHasOpenRequestsException.class);
        assertTechnicianAvailable();
        assertThat(repairs.getDetails(requestId, adminId).status()).isEqualTo(RepairRequestStatus.ASSIGNED);
    }

    @ParameterizedTest
    @EnumSource(AccountChange.class)
    void accountChangeCommitsBeforeWaitingAssignmentChecksRoleAndActivity(AccountChange change) throws Exception {
        Long requestId = createRequest();
        var result = runWithFirstTransactionHoldingLock(() -> changeAccount(change),
                () -> repairs.assign(requestId, technicianId, adminId));
        assertThat(result.first()).isNull();
        assertThat(result.second()).isInstanceOf(InvalidRepairRequestActionException.class);
        assertThat(repairs.getDetails(requestId, adminId).status()).isEqualTo(RepairRequestStatus.NEW);
        User changed = users.findById(technicianId).orElseThrow();
        assertThat(changed.isActive()).isEqualTo(change != AccountChange.DEACTIVATE);
        assertThat(changed.getRole()).isEqualTo(change == AccountChange.CHANGE_ROLE ? Role.REQUESTER : Role.TECHNICIAN);
    }

    @Test
    void detailsEditCannotRestoreOldEquipmentStatusAfterConcurrentDecommissioning() throws Exception {
        var result = runWithFirstTransactionHoldingLock(
                () -> equipmentService.changeEquipmentStatus(equipmentId, EquipmentStatus.DECOMMISSIONED),
                () -> equipmentService.updateEquipment(equipmentId, equipmentForm()));
        assertThat(result.first()).isNull();
        assertThat(result.second()).isNull();
        Equipment changed = equipment.findById(equipmentId).orElseThrow();
        assertThat(changed.getStatus()).isEqualTo(EquipmentStatus.DECOMMISSIONED);
        assertThat(changed.getLocation()).isEqualTo("Workshop B");
    }

    @Test
    void profileEditCannotReactivateConcurrentlyDisabledTechnician() throws Exception {
        var result = runWithFirstTransactionHoldingLock(() -> userService.deactivateUser(technicianId),
                () -> userService.updateUser(technicianId,
                        new UserEditForm("Updated Technician", "updated@example.com", Role.TECHNICIAN)));
        assertThat(result.first()).isNull();
        assertThat(result.second()).isNull();
        User changed = users.findById(technicianId).orElseThrow();
        assertThat(changed.isActive()).isFalse();
        assertThat(changed.getFullName()).isEqualTo("Updated Technician");
    }

    @Test
    void activationCannotRestoreRoleFromBeforeConcurrentAccountChange() throws Exception {
        userService.deactivateUser(technicianId);
        var result = runWithFirstTransactionHoldingLock(
                () -> userService.updateUser(technicianId, new UserEditForm("Technician", "tech@example.com", Role.REQUESTER)),
                () -> userService.activateUser(technicianId));
        assertThat(result.first()).isNull();
        assertThat(result.second()).isNull();
        User changed = users.findById(technicianId).orElseThrow();
        assertThat(changed.isActive()).isTrue();
        assertThat(changed.getRole()).isEqualTo(Role.REQUESTER);
    }

    private enum AccountChange { DEACTIVATE, CHANGE_ROLE }

    private void changeAccount(AccountChange change) {
        if (change == AccountChange.DEACTIVATE) userService.deactivateUser(technicianId);
        else userService.updateUser(technicianId, new UserEditForm("Technician", "tech@example.com", Role.REQUESTER));
    }

    private record PairResult(RuntimeException first, RuntimeException second) { }

    private PairResult runWithFirstTransactionHoldingLock(Runnable firstAction, Runnable secondAction) throws Exception {
        var firstReady = new CountDownLatch(1);
        var secondReady = new CountDownLatch(1);
        var releaseFirst = new CountDownLatch(1);
        var firstBackend = new AtomicInteger();
        var secondBackend = new AtomicInteger();
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> inIndependentTransaction(() -> {
                firstBackend.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                firstAction.run();
                firstReady.countDown();
                await(releaseFirst);
            }));
            assertThat(firstReady.await(10, TimeUnit.SECONDS)).as("first operation holds its transaction open").isTrue();
            var second = pool.submit(() -> inIndependentTransaction(() -> {
                secondBackend.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                secondReady.countDown();
                secondAction.run();
            }));
            assertThat(secondReady.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(waitForDatabaseBlock(firstBackend.get(), secondBackend.get(), second))
                    .as("PostgreSQL confirms the second operation waits for the first transaction").isTrue();
            releaseFirst.countDown();
            return new PairResult(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
        } finally {
            releaseFirst.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(15, TimeUnit.SECONDS)).isTrue();
        }
    }

    private RuntimeException inIndependentTransaction(Runnable action) {
        try {
            transaction.executeWithoutResult(status -> {
                jdbc.execute("SET LOCAL lock_timeout = '10s'");
                action.run();
            });
            return null;
        } catch (RuntimeException exception) {
            return exception;
        }
    }

    private boolean waitForDatabaseBlock(int firstBackend, int secondBackend, Future<?> waiting) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline && !waiting.isDone()) {
            if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT ? = ANY(pg_blocking_pids(?))",
                    Boolean.class, firstBackend, secondBackend))) return true;
            Thread.sleep(20);
        }
        return false;
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Concurrent test was not released");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrent test interrupted", exception);
        }
    }

    private void assertTechnicianAvailable() {
        User technician = users.findById(technicianId).orElseThrow();
        assertThat(technician.isActive()).isTrue();
        assertThat(technician.getRole()).isEqualTo(Role.TECHNICIAN);
    }

    private Long createRequest() {
        return repairs.create(createForm(equipmentId), authorId).getId();
    }

    private RepairRequestCreateForm createForm(Long selectedEquipmentId) {
        var form = new RepairRequestCreateForm();
        form.setEquipmentId(selectedEquipmentId);
        form.setTitle("Oil leak in press");
        form.setDescription("Hydraulic oil leaking around the seal");
        form.setPriority(RepairRequestPriority.HIGH);
        return form;
    }

    private EquipmentEditForm equipmentForm() {
        return new EquipmentEditForm("Updated Press", "PRESS-INTEGRITY-001", "Updated description", "Workshop B");
    }

    private void advanceTo(Long requestId, RepairRequestStatus state) {
        if (state == RepairRequestStatus.NEW) return;
        repairs.assign(requestId, technicianId, adminId);
        if (state == RepairRequestStatus.ASSIGNED) return;
        repairs.startWork(requestId, technicianId);
        if (state == RepairRequestStatus.IN_PROGRESS) return;
        if (state == RepairRequestStatus.CANCELLED) {
            cancel(requestId);
            return;
        }
        var form = repairs.getResolutionForm(requestId, technicianId);
        form.setResolution("Seal replaced and tested");
        repairs.completeWork(requestId, form, technicianId);
        if (state == RepairRequestStatus.CLOSED) {
            repairs.closeRequest(requestId, repairs.getDetails(requestId, adminId).updatedAt(), adminId);
        }
    }

    private void cancel(Long requestId) {
        repairs.cancelRequest(requestId, new RepairRequestCancellationForm("Duplicate request for this fault",
                repairs.getDetails(requestId, adminId).updatedAt()), adminId);
    }

    private User saveUser(String name, String email, Role role) {
        return users.saveAndFlush(new User(name, email, "hashed-password", role));
    }
}
