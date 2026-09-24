package com.vladyslav.industrialmaintenancetracker.maintenance;

import com.vladyslav.industrialmaintenancetracker.equipment.Equipment;
import com.vladyslav.industrialmaintenancetracker.equipment.EquipmentRepository;
import com.vladyslav.industrialmaintenancetracker.repairrequest.RepairRequest;
import com.vladyslav.industrialmaintenancetracker.repairrequest.RepairRequestRepository;
import com.vladyslav.industrialmaintenancetracker.user.Role;
import com.vladyslav.industrialmaintenancetracker.user.User;
import com.vladyslav.industrialmaintenancetracker.user.UserRepository;
import jakarta.persistence.EntityManager;
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
class MaintenanceRecordRepositoryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

    @Autowired
    private MaintenanceRecordRepository recordRepository;

    @Autowired
    private RepairRequestRepository requestRepository;

    @Autowired
    private EquipmentRepository equipmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    void savesSnapshotOfClosedRequest() {
        RepairRequest request = closedRequest();

        MaintenanceRecord saved = recordRepository.saveAndFlush(new MaintenanceRecord(request));
        Long id = saved.getId();
        entityManager.clear();

        MaintenanceRecord loaded = recordRepository.findByRepairRequestId(request.getId()).orElseThrow();
        assertThat(loaded.getId()).isEqualTo(id);
        assertThat(loaded.getEquipment().getId()).isEqualTo(request.getEquipment().getId());
        assertThat(loaded.getRepairRequest().getId()).isEqualTo(request.getId());
        assertThat(loaded.getPerformedBy().getId()).isEqualTo(request.getAssignedTechnician().getId());
        assertThat(loaded.getWorkDescription()).isEqualTo(request.getResolution());
        assertThat(loaded.getPerformedAt()).isEqualTo(request.getCompletedAt());
        assertThat(loaded.getCreatedAt()).isNotNull();
    }

    @Test
    void databaseRejectsSecondRecordForSameRequest() {
        RepairRequest request = closedRequest();
        recordRepository.saveAndFlush(new MaintenanceRecord(request));

        assertThatThrownBy(() -> recordRepository.saveAndFlush(new MaintenanceRecord(request)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databasePreservesLinkedRequest() {
        RepairRequest request = closedRequest();
        recordRepository.saveAndFlush(new MaintenanceRecord(request));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "DELETE FROM repair_requests WHERE id = ?", request.getId()
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void cannotBuildRecordBeforeClosure() {
        Equipment equipment = equipmentRepository.saveAndFlush(new Equipment(
                "Hydraulic press", "PRESS-08-001", null, "Workshop A"
        ));
        User author = userRepository.saveAndFlush(new User(
                "Test Requester", "requester@example.com", "hashed-password", Role.REQUESTER
        ));
        RepairRequest request = requestRepository.saveAndFlush(new RepairRequest(
                "Machine is leaking", "Oil is leaking from the hydraulic unit", equipment, author, null
        ));

        assertThatThrownBy(() -> new MaintenanceRecord(request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // The public workflow for completing and closing requests is added later in stage 08.
    // Set up a valid CLOSED row here to test V5 and the snapshot independently.
    private RepairRequest closedRequest() {
        Equipment equipment = equipmentRepository.saveAndFlush(new Equipment(
                "Hydraulic press", "PRESS-08-001", null, "Workshop A"
        ));
        User author = userRepository.saveAndFlush(new User(
                "Test Requester", "requester@example.com", "hashed-password", Role.REQUESTER
        ));
        User technician = userRepository.saveAndFlush(new User(
                "Test Technician", "technician@example.com", "hashed-password", Role.TECHNICIAN
        ));
        RepairRequest request = requestRepository.saveAndFlush(new RepairRequest(
                "Machine is leaking", "Oil is leaking from the hydraulic unit", equipment, author, null
        ));

        jdbcTemplate.update("""
                UPDATE repair_requests
                   SET assigned_technician_id = ?, assigned_at = CURRENT_TIMESTAMP,
                       status = 'CLOSED', resolution = 'Replaced the seal; pressure test passed',
                       started_at = CURRENT_TIMESTAMP, completed_at = CURRENT_TIMESTAMP,
                       closed_at = CURRENT_TIMESTAMP
                 WHERE id = ?
                """, technician.getId(), request.getId());

        entityManager.clear();
        return requestRepository.findById(request.getId()).orElseThrow();
    }
}
