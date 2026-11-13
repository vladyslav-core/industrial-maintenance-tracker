package com.vladyslav.industrialmaintenancetracker.maintenance;

import com.vladyslav.industrialmaintenancetracker.equipment.Equipment;
import com.vladyslav.industrialmaintenancetracker.equipment.EquipmentRepository;
import com.vladyslav.industrialmaintenancetracker.equipment.EquipmentStatus;
import com.vladyslav.industrialmaintenancetracker.maintenance.dto.MaintenanceHistoryItem;
import com.vladyslav.industrialmaintenancetracker.repairrequest.RepairRequest;
import com.vladyslav.industrialmaintenancetracker.repairrequest.RepairRequestRepository;
import com.vladyslav.industrialmaintenancetracker.repairrequest.RepairRequestStatus;
import com.vladyslav.industrialmaintenancetracker.user.Role;
import com.vladyslav.industrialmaintenancetracker.user.User;
import com.vladyslav.industrialmaintenancetracker.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataJpaTest
@Import(MaintenanceRecordService.class)
class MaintenanceHistoryReadTest {

    private static final Instant CREATED = Instant.parse("2026-11-01T07:00:00Z");
    private static final Instant PERFORMED = CREATED.plusSeconds(3600);
    private static final String RESULT = "Seal replaced.\nPressure test passed.";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

    @Autowired private MaintenanceRecordRepository records;
    @Autowired private RepairRequestRepository requests;
    @Autowired private EquipmentRepository equipment;
    @Autowired private UserRepository users;
    @Autowired private MaintenanceRecordService service;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;

    private User admin;
    private User author;
    private User technician;
    private Equipment press;
    private Equipment pump;
    private Equipment emptyEquipment;
    private MaintenanceRecord first;
    private MaintenanceRecord newer;
    private MaintenanceRecord tied;

    @BeforeEach
    void createHistory() {
        admin = saveUser("Administrator", Role.ADMIN);
        author = saveUser("Author", Role.REQUESTER);
        technician = saveUser("Technician", Role.TECHNICIAN);
        press = equipment.saveAndFlush(new Equipment("Press", "HIST10-A", null, "Workshop A"));
        pump = equipment.saveAndFlush(new Equipment("Pump", "HIST10-B", null, "Workshop B"));
        emptyEquipment = equipment.saveAndFlush(new Equipment("Spare", "HIST10-C", null, "Storage"));

        // Insertion order, completion time and closure time deliberately give different orders.
        first = closeRequest(press, PERFORMED, PERFORMED.plusSeconds(10800));
        newer = closeRequest(pump, PERFORMED.plusSeconds(3600), PERFORMED.plusSeconds(3660));
        tied = closeRequest(press, PERFORMED, PERFORMED.plusSeconds(60));
        entityManager.clear();
    }

    @Test
    void repositoryOrdersByCompletionAndRecordIdAndFiltersEquipment() {
        assertThat(records.findHistory(null)).extracting(MaintenanceRecord::getId)
                .containsExactly(newer.getId(), tied.getId(), first.getId());
        assertThat(records.findHistory(press.getId())).extracting(MaintenanceRecord::getId)
                .containsExactly(tied.getId(), first.getId());
        assertThat(records.findHistory(pump.getId())).extracting(MaintenanceRecord::getId)
                .containsExactly(newer.getId());
    }

    @Test
    void serviceReturnsTheThreeDistinctContractDatesAndDetachedValues() {
        List<MaintenanceHistoryItem> result = service.getEquipmentHistory(press.getId(), admin.getId());
        entityManager.clear();

        MaintenanceHistoryItem expected = new MaintenanceHistoryItem(
                first.getId(), press.getId(), "Press", "HIST10-A",
                first.getRepairRequest().getId(), "Oil leak in Press", CREATED, "Author",
                "Oil is leaking around the seal", "Technician", RESULT,
                PERFORMED, PERFORMED.plusSeconds(10800), true
        );
        assertThat(result).containsExactly(
                new MaintenanceHistoryItem(
                        tied.getId(), press.getId(), "Press", "HIST10-A",
                        tied.getRepairRequest().getId(), "Oil leak in Press", CREATED, "Author",
                        "Oil is leaking around the seal", "Technician", RESULT,
                        PERFORMED, PERFORMED.plusSeconds(60), true
                ),
                expected
        );
        assertThat(records.findById(first.getId()).orElseThrow().getCreatedAt())
                .isEqualTo(expected.closedAt().plusSeconds(30));
    }

    @Test
    void existingEquipmentWithoutHistoryAndAnEmptyDatabaseReturnEmptyLists() {
        assertThat(service.getEquipmentHistory(emptyEquipment.getId(), author.getId())).isEmpty();
        assertThat(service.getHistory(emptyEquipment.getId(), admin.getId())).isEmpty();

        records.deleteAllInBatch();
        entityManager.clear();

        assertThat(service.getHistory(null, admin.getId())).isEmpty();
        assertThat(service.getEquipmentHistory(press.getId(), author.getId())).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = RepairRequestStatus.class, names = {"COMPLETED", "CANCELLED"})
    void unconfirmedRequestsDoNotProduceHistory(RepairRequestStatus status) {
        RepairRequest request = new RepairRequest(
                "New oil leak in press", "Oil is leaking again after restart", press, author, null
        );
        if (status == RepairRequestStatus.COMPLETED) {
            request.assignTo(technician, CREATED.plusSeconds(60));
            request.startWork(CREATED.plusSeconds(120));
            request.completeWork("Replaced another seal and checked pressure", PERFORMED);
        } else {
            request.cancel("Duplicate problem report", PERFORMED);
        }
        requests.saveAndFlush(request);
        entityManager.clear();

        assertThat(service.getEquipmentHistory(press.getId(), author.getId()))
                .extracting(MaintenanceHistoryItem::requestId)
                .containsExactly(tied.getRepairRequest().getId(), first.getRepairRequest().getId());
        assertThat(records.findByRepairRequestId(request.getId())).isEmpty();
    }

    @Test
    void historySurvivesAccountChangesAndDecommissioningWithCurrentDisplayNames() {
        User otherTechnician = saveUser("Other Technician", Role.TECHNICIAN);
        User otherRequester = saveUser("Other Requester", Role.REQUESTER);
        User performer = users.findById(technician.getId()).orElseThrow();
        performer.deactivate();
        performer.updateProfile("Former Technician", performer.getEmail(), Role.REQUESTER);
        users.findById(author.getId()).orElseThrow().deactivate();
        Equipment archived = equipment.findById(press.getId()).orElseThrow();
        archived.updateDetails("Archived press", "HIST10-A", null, "Storage");
        archived.changeStatus(EquipmentStatus.DECOMMISSIONED);
        entityManager.flush();
        entityManager.clear();

        assertThat(service.getHistory(press.getId(), admin.getId())).hasSize(2)
                .allSatisfy(item -> {
                    assertThat(item.technicianName()).isEqualTo("Former Technician");
                    assertThat(item.equipmentName()).isEqualTo("Archived press");
                    assertThat(item.workDescription()).isEqualTo(RESULT);
                    assertThat(item.performedAt()).isEqualTo(PERFORMED);
                });
        assertThat(service.getHistory(press.getId(), otherTechnician.getId())).hasSize(2)
                .allSatisfy(item -> assertThat(item.canViewRequest()).isFalse());
        assertThat(service.getEquipmentHistory(press.getId(), otherRequester.getId())).hasSize(2)
                .allSatisfy(item -> assertThat(item.canViewRequest()).isFalse());
    }

    @Test
    void queryLoadsEveryRequiredRelationshipInOneSelectWithoutLazyReads() {
        var statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        try {
            List<MaintenanceRecord> result = records.findHistory(null);
            entityManager.clear();
            assertThat(result).hasSize(3).allSatisfy(record -> {
                assertThat(record.getEquipment().getName()).isNotBlank();
                assertThat(record.getRepairRequest().getCreatedBy().getFullName()).isEqualTo("Author");
                assertThat(record.getRepairRequest().getAssignedTechnician().getFullName()).isEqualTo("Technician");
                assertThat(record.getPerformedBy().getFullName()).isEqualTo("Technician");
                assertThat(record.getWorkDescription()).isEqualTo(RESULT);
            });
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        } finally {
            statistics.setStatisticsEnabled(false);
        }
    }

    @Test
    void repeatedReadsKeepTheSameRecordsAndContents() {
        List<MaintenanceHistoryItem> before = service.getHistory(null, admin.getId());
        service.getEquipmentHistory(press.getId(), author.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(service.getHistory(null, admin.getId())).isEqualTo(before);
        assertThat(records.count()).isEqualTo(3);
    }

    private User saveUser(String name, Role role) {
        String email = name.toLowerCase().replace(" ", ".") + "@example.com";
        return users.saveAndFlush(new User(name, email, "hashed-password", role));
    }

    private MaintenanceRecord closeRequest(Equipment item, Instant performedAt, Instant closedAt) {
        RepairRequest request = new RepairRequest(
                "Oil leak in " + item.getName(), "Oil is leaking around the seal", item, author, null
        );
        request.assignTo(technician, CREATED.plusSeconds(60));
        request.startWork(CREATED.plusSeconds(120));
        request.completeWork(RESULT, performedAt);
        request.close(closedAt);
        requests.saveAndFlush(request);
        MaintenanceRecord record = records.saveAndFlush(new MaintenanceRecord(request));

        jdbc.update("UPDATE repair_requests SET created_at = ? WHERE id = ?",
                java.sql.Timestamp.from(CREATED), request.getId());
        jdbc.update("UPDATE maintenance_records SET created_at = ? WHERE id = ?",
                java.sql.Timestamp.from(closedAt.plusSeconds(30)), record.getId());
        return record;
    }
}
