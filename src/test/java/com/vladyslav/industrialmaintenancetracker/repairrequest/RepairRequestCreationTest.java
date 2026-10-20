package com.vladyslav.industrialmaintenancetracker.repairrequest;

import com.vladyslav.industrialmaintenancetracker.equipment.Equipment;
import com.vladyslav.industrialmaintenancetracker.equipment.EquipmentRepository;
import com.vladyslav.industrialmaintenancetracker.equipment.EquipmentStatus;
import com.vladyslav.industrialmaintenancetracker.exception.EquipmentNotAvailableException;
import com.vladyslav.industrialmaintenancetracker.exception.EquipmentNotFoundException;
import com.vladyslav.industrialmaintenancetracker.repairrequest.dto.RepairRequestCreateForm;
import com.vladyslav.industrialmaintenancetracker.user.Role;
import com.vladyslav.industrialmaintenancetracker.user.User;
import com.vladyslav.industrialmaintenancetracker.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DataJpaTest
@Import(RepairRequestService.class)
class RepairRequestCreationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

    @Autowired
    private RepairRequestService requestService;

    @Autowired
    private RepairRequestRepository requestRepository;

    @Autowired
    private EquipmentRepository equipmentRepository;

    @Autowired
    private UserRepository userRepository;

    @ParameterizedTest
    @EnumSource(Role.class)
    void everyRoleCanCreateOwnNewRequestWithDefaultPriority(Role role) {
        Equipment equipment = saveEquipment();
        User author = saveUser(role);
        RepairRequestCreateForm form = form(equipment.getId());
        form.setTitle("  Oil leak in press  ");
        form.setDescription("  Hydraulic oil leaking around the seal  ");

        RepairRequest saved = requestService.create(form, author.getId());
        requestRepository.flush();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedBy().getId()).isEqualTo(author.getId());
        assertThat(saved.getEquipment().getId()).isEqualTo(equipment.getId());
        assertThat(saved.getTitle()).isEqualTo("Oil leak in press");
        assertThat(saved.getDescription()).isEqualTo("Hydraulic oil leaking around the seal");
        assertThat(saved.getPriority()).isEqualTo(RepairRequestPriority.MEDIUM);
        assertThat(saved.getStatus()).isEqualTo(RepairRequestStatus.NEW);
        assertThat(saved.getAssignedTechnician()).isNull();
    }

    @Test
    void preservesSelectedCriticalPriority() {
        Equipment equipment = saveEquipment();
        User author = saveUser(Role.REQUESTER);
        RepairRequestCreateForm form = form(equipment.getId());
        form.setPriority(RepairRequestPriority.CRITICAL);

        RepairRequest saved = requestService.create(form, author.getId());
        requestRepository.flush();

        assertThat(saved.getPriority()).isEqualTo(RepairRequestPriority.CRITICAL);
    }

    @Test
    void excludesDecommissionedEquipmentFromChoicesAndRejectsCreation() {
        Equipment equipment = saveEquipment();
        equipment.changeStatus(EquipmentStatus.DECOMMISSIONED);
        equipmentRepository.flush();
        User author = saveUser(Role.REQUESTER);

        assertThat(requestService.availableEquipment())
                .noneMatch(option -> option.id().equals(equipment.getId()));
        assertThatThrownBy(() -> requestService.create(form(equipment.getId()), author.getId()))
                .isInstanceOf(EquipmentNotAvailableException.class);
        assertThat(requestRepository.count()).isZero();
    }

    @Test
    void rejectsNonexistentEquipmentWithoutSaving() {
        User author = saveUser(Role.REQUESTER);

        assertThatThrownBy(() -> requestService.create(form(99999L), author.getId()))
                .isInstanceOf(EquipmentNotFoundException.class);
        assertThat(requestRepository.count()).isZero();
    }

    @Test
    void rejectsUserDeactivatedAfterSigningIn() {
        Equipment equipment = saveEquipment();
        User author = saveUser(Role.REQUESTER);
        author.deactivate();
        userRepository.flush();

        assertThatThrownBy(() -> requestService.create(form(equipment.getId()), author.getId()))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(requestRepository.count()).isZero();
    }

    private RepairRequestCreateForm form(Long equipmentId) {
        RepairRequestCreateForm form = new RepairRequestCreateForm();
        form.setEquipmentId(equipmentId);
        form.setTitle("Oil leak in press");
        form.setDescription("Hydraulic oil leaking around the seal");
        return form;
    }

    private Equipment saveEquipment() {
        return equipmentRepository.saveAndFlush(new Equipment(
                "Hydraulic press", "PRESS-08-001", null, "Workshop A"
        ));
    }

    private User saveUser(Role role) {
        return userRepository.saveAndFlush(new User(
                "Test User", "test-" + role.name().toLowerCase(Locale.ROOT) + "@example.com",
                "hashed-password", role
        ));
    }
}
