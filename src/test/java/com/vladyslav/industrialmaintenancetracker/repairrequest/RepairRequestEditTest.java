package com.vladyslav.industrialmaintenancetracker.repairrequest;

import com.vladyslav.industrialmaintenancetracker.equipment.Equipment;
import com.vladyslav.industrialmaintenancetracker.equipment.EquipmentRepository;
import com.vladyslav.industrialmaintenancetracker.exception.RepairRequestEditConflictException;
import com.vladyslav.industrialmaintenancetracker.exception.RepairRequestNotFoundException;
import com.vladyslav.industrialmaintenancetracker.repairrequest.dto.RepairRequestEditForm;
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

import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DataJpaTest
@Import(RepairRequestService.class)
class RepairRequestEditTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

    @Autowired private RepairRequestService service;
    @Autowired private RepairRequestRepository requests;
    @Autowired private EquipmentRepository equipmentRepository;
    @Autowired private UserRepository users;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;

    private Long authorId;
    private Long adminId;
    private Long technicianId;
    private Long equipmentId;
    private Long requestId;

    @BeforeEach
    void seedNewRequest() {
        User author = saveUser("Author", "author@example.com", Role.REQUESTER);
        authorId = author.getId();
        adminId = saveUser("Admin", "admin@example.com", Role.ADMIN).getId();
        technicianId = saveUser("Technician", "tech@example.com", Role.TECHNICIAN).getId();
        Equipment equipment = equipmentRepository.saveAndFlush(new Equipment(
                "Hydraulic press", "PRESS-EDIT-001", null, "Workshop A"
        ));
        equipmentId = equipment.getId();
        requestId = requests.saveAndFlush(new RepairRequest(
                "Oil leak in press", "Hydraulic oil leaking around the seal",
                equipment, author, RepairRequestPriority.HIGH
        )).getId();
        // Reload database timestamps at PostgreSQL's microsecond precision.
        entityManager.clear();
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void everyRoleMayEditOnlyOwnNewRequestDetails(Role role) {
        jdbc.update("UPDATE users SET role = ? WHERE id = ?", role.name(), authorId);
        entityManager.clear();
        var before = service.getDetails(requestId, authorId);
        RepairRequestEditForm form = service.getEditForm(requestId, authorId);
        form.setTitle("  Updated problem title  ");
        form.setDescription("  Updated problem description  ");
        service.updateDetails(requestId, form, authorId);
        var after = service.getDetails(requestId, authorId);
        assertThat(after.title()).isEqualTo("Updated problem title");
        assertThat(after.description()).isEqualTo("Updated problem description");
        assertThat(after.updatedAt()).isAfter(before.updatedAt());
        assertThat(after.equipmentId()).isEqualTo(equipmentId);
        assertThat(after.authorName()).isEqualTo(before.authorName());
        assertThat(after.createdAt()).isEqualTo(before.createdAt());
        assertThat(after.priority()).isEqualTo(RepairRequestPriority.HIGH);
        assertThat(after.status()).isEqualTo(RepairRequestStatus.NEW);
        assertThat(after.technicianName()).isNull();
        assertThat(after.canEdit()).isTrue();
    }

    @Test
    void adminCannotEditAnotherAuthorsRequest() {
        RepairRequestEditForm form = service.getEditForm(requestId, authorId);
        assertThatThrownBy(() -> service.getEditForm(requestId, adminId)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.updateDetails(requestId, form, adminId)).isInstanceOf(AccessDeniedException.class);
        assertThat(service.getDetails(requestId, adminId).canEdit()).isFalse();
    }

    @Test
    void unrelatedRequesterCannotReadOrSubmitEdit() {
        Long outsider = saveUser("Outsider", "outsider@example.com", Role.REQUESTER).getId();
        RepairRequestEditForm form = service.getEditForm(requestId, authorId);
        assertThatThrownBy(() -> service.getEditForm(requestId, outsider)).isInstanceOf(RepairRequestNotFoundException.class);
        assertThatThrownBy(() -> service.updateDetails(requestId, form, outsider)).isInstanceOf(RepairRequestNotFoundException.class);
    }

    @ParameterizedTest
    @EnumSource(value = RepairRequestStatus.class, names = "NEW", mode = EnumSource.Mode.EXCLUDE)
    void changedStatusPreventsSavingPreviouslyOpenedForm(RepairRequestStatus status) {
        RepairRequestEditForm form = service.getEditForm(requestId, authorId);
        form.setTitle("Attempted late change");
        changeStatus(status);
        assertThatThrownBy(() -> service.updateDetails(requestId, form, authorId))
                .isInstanceOf(RepairRequestEditConflictException.class);
        assertThat(service.getDetails(requestId, authorId).title()).isEqualTo("Oil leak in press");
        assertThat(service.getDetails(requestId, authorId).canEdit()).isFalse();
    }

    @Test
    void assignedTechnicianCannotEditAuthorsProblemDetails() {
        changeStatus(RepairRequestStatus.ASSIGNED);
        assertThatThrownBy(() -> service.getEditForm(requestId, technicianId)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void firstSaveMakesSecondOpenFormStale() {
        RepairRequestEditForm first = service.getEditForm(requestId, authorId);
        RepairRequestEditForm second = service.getEditForm(requestId, authorId);
        first.setTitle("First saved problem title");
        second.setTitle("Second stale problem title");
        service.updateDetails(requestId, first, authorId);
        assertThatThrownBy(() -> service.updateDetails(requestId, second, authorId))
                .isInstanceOf(RepairRequestEditConflictException.class);
        assertThat(service.getDetails(requestId, authorId).title()).isEqualTo("First saved problem title");
    }

    @Test
    void conditionalUpdateRejectsAssignmentAfterFormWasRead() {
        RepairRequestEditForm form = service.getEditForm(requestId, authorId);
        changeStatus(RepairRequestStatus.ASSIGNED);
        int changed = requests.updateOwnNewDetails(requestId, authorId, RepairRequestStatus.NEW,
                "Attempted racing change", form.getDescription(), form.getUpdatedAt(),
                form.getUpdatedAt().plus(1, ChronoUnit.MICROS));
        assertThat(changed).isZero();
        assertThat(service.getDetails(requestId, authorId).title()).isEqualTo("Oil leak in press");
    }

    private User saveUser(String name, String email, Role role) {
        return users.saveAndFlush(new User(name, email, "hashed-password", role));
    }

    // Later steps add workflow actions. These fixtures exercise editing independently.
    private void changeStatus(RepairRequestStatus status) {
        String value = status.name();
        jdbc.update("""
                UPDATE repair_requests SET status = ?, assigned_technician_id = ?,
                    assigned_at = CURRENT_TIMESTAMP,
                    started_at = CASE WHEN ? = 'ASSIGNED' THEN NULL ELSE CURRENT_TIMESTAMP END,
                    completed_at = CASE WHEN ? IN ('COMPLETED', 'CLOSED') THEN CURRENT_TIMESTAMP ELSE NULL END,
                    closed_at = CASE WHEN ? IN ('CLOSED', 'CANCELLED') THEN CURRENT_TIMESTAMP ELSE NULL END,
                    resolution = CASE WHEN ? IN ('COMPLETED', 'CLOSED') THEN 'Replaced the seal' ELSE NULL END,
                    cancellation_reason = CASE WHEN ? = 'CANCELLED' THEN 'Duplicate problem report' ELSE NULL END
                WHERE id = ?
                """, value, technicianId, value, value, value, value, value, requestId);
        entityManager.clear();
    }
}
