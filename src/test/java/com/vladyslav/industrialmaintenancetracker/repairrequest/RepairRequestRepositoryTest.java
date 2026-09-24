package com.vladyslav.industrialmaintenancetracker.repairrequest;

import com.vladyslav.industrialmaintenancetracker.equipment.Equipment;
import com.vladyslav.industrialmaintenancetracker.equipment.EquipmentRepository;
import com.vladyslav.industrialmaintenancetracker.user.Role;
import com.vladyslav.industrialmaintenancetracker.user.User;
import com.vladyslav.industrialmaintenancetracker.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DataJpaTest
class RepairRequestRepositoryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:18");

    @Autowired
    private RepairRequestRepository requestRepository;

    @Autowired
    private EquipmentRepository equipmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void savesRequestWithDefaultsAndRequiredRelations() {
        Equipment equipment = saveEquipment();
        User author = saveAuthor();

        RepairRequest saved = requestRepository.saveAndFlush(new RepairRequest(
                "Machine is leaking",
                "Oil is leaking from the hydraulic unit",
                equipment,
                author,
                null
        ));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getEquipment().getId()).isEqualTo(equipment.getId());
        assertThat(saved.getCreatedBy().getId()).isEqualTo(author.getId());
        assertThat(saved.getPriority()).isEqualTo(RepairRequestPriority.MEDIUM);
        assertThat(saved.getStatus()).isEqualTo(RepairRequestStatus.NEW);
        assertThat(saved.getAssignedTechnician()).isNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getAssignedAt()).isNull();
        assertThat(saved.getStartedAt()).isNull();
        assertThat(saved.getCompletedAt()).isNull();
        assertThat(saved.getClosedAt()).isNull();
    }

    @Test
    void persistsCriticalPriorityAsString() {
        RepairRequest saved = requestRepository.saveAndFlush(new RepairRequest(
                "Machine is leaking",
                "Oil is leaking from the hydraulic unit",
                saveEquipment(),
                saveAuthor(),
                RepairRequestPriority.CRITICAL
        ));

        String priority = jdbcTemplate.queryForObject(
                "SELECT priority FROM repair_requests WHERE id = ?",
                String.class,
                saved.getId()
        );

        assertThat(priority).isEqualTo("CRITICAL");
    }

    @Test
    void databaseRejectsTooShortTitle() {
        RepairRequest invalid = new RepairRequest(
                "Bad",
                "Oil is leaking from the hydraulic unit",
                saveEquipment(),
                saveAuthor(),
                RepairRequestPriority.HIGH
        );

        assertThatThrownBy(() -> requestRepository.saveAndFlush(invalid))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsUnknownPriority() {
        RepairRequest saved = requestRepository.saveAndFlush(new RepairRequest(
                "Machine is leaking",
                "Oil is leaking from the hydraulic unit",
                saveEquipment(),
                saveAuthor(),
                RepairRequestPriority.HIGH
        ));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE repair_requests SET priority = 'URGENT' WHERE id = ?",
                saved.getId()
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    private Equipment saveEquipment() {
        return equipmentRepository.saveAndFlush(new Equipment(
                "Hydraulic press",
                "PRESS-08-001",
                null,
                "Workshop A"
        ));
    }

    private User saveAuthor() {
        return userRepository.saveAndFlush(new User(
                "Test Requester",
                "requester@example.com",
                "hashed-password",
                Role.REQUESTER
        ));
    }
}
