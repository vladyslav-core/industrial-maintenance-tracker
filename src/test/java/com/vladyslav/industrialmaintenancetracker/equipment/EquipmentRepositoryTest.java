package com.vladyslav.industrialmaintenancetracker.equipment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DataJpaTest
class EquipmentRepositoryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:18");

    @Autowired
    private EquipmentRepository equipmentRepository;

    @Test
    void shouldSaveEquipmentWithDefaultStatusAndTimestamps() {
        Equipment equipment = new Equipment(
                "CNC lathe",
                "INV-001",
                "Main production lathe",
                "Workshop A"
        );

        Equipment saved = equipmentRepository.saveAndFlush(equipment);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(EquipmentStatus.OPERATIONAL);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void shouldFindAndDetectEquipmentByInventoryNumberIgnoringCase() {
        Equipment saved = equipmentRepository.saveAndFlush(
                new Equipment(
                        "Hydraulic press",
                        "PRESS-002",
                        null,
                        "Workshop B"
                )
        );

        assertThat(
                equipmentRepository.findByInventoryNumberIgnoreCase("press-002")
        ).isPresent()
                .get()
                .extracting(Equipment::getId)
                .isEqualTo(saved.getId());

        assertThat(
                equipmentRepository.existsByInventoryNumberIgnoreCase("PrEsS-002")
        ).isTrue();
    }

    @Test
    void shouldRejectDuplicateInventoryNumberIgnoringCase() {
        equipmentRepository.saveAndFlush(
                new Equipment(
                        "Compressor 1",
                        "COMP-003",
                        null,
                        "Utility room"
                )
        );

        Equipment duplicate = new Equipment(
                "Compressor 2",
                "comp-003",
                null,
                "Workshop C"
        );

        assertThatThrownBy(
                () -> equipmentRepository.saveAndFlush(duplicate)
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldSearchByNameOrInventoryNumberIgnoringCase() {
        Equipment compressor = new Equipment(
                "Air compressor",
                "COMP-001",
                null,
                "Utility room"
        );

        Equipment press = new Equipment(
                "Hydraulic press",
                "PRESS-001",
                null,
                "Workshop A"
        );

        equipmentRepository.saveAllAndFlush(
                List.of(compressor, press)
        );

        assertThat(equipmentRepository.search("HYDRAULIC", null))
                .extracting(Equipment::getInventoryNumber)
                .containsExactly("PRESS-001");

        assertThat(equipmentRepository.search("comp-001", null))
                .extracting(Equipment::getName)
                .containsExactly("Air compressor");
    }

    @Test
    void shouldFilterEquipmentByStatus() {
        Equipment operational = new Equipment(
                "Air compressor",
                "COMP-001",
                null,
                "Utility room"
        );

        Equipment underRepair = new Equipment(
                "Hydraulic press",
                "PRESS-001",
                null,
                "Workshop A"
        );

        underRepair.changeStatus(EquipmentStatus.UNDER_REPAIR);

        equipmentRepository.saveAllAndFlush(
                List.of(operational, underRepair)
        );

        assertThat(
                equipmentRepository.search(
                        "",
                        EquipmentStatus.UNDER_REPAIR
                )
        )
                .extracting(Equipment::getInventoryNumber)
                .containsExactly("PRESS-001");
    }

    @Test
    void shouldCombineSearchAndStatusAndSortResults() {
        Equipment secondPress = new Equipment(
                "Hydraulic press",
                "PRESS-002",
                null,
                "Workshop B"
        );

        Equipment firstPress = new Equipment(
                "Hydraulic press",
                "PRESS-001",
                null,
                "Workshop A"
        );

        Equipment decommissionedPress = new Equipment(
                "Old hydraulic press",
                "PRESS-003",
                null,
                "Storage"
        );

        decommissionedPress.changeStatus(
                EquipmentStatus.DECOMMISSIONED
        );

        equipmentRepository.saveAllAndFlush(
                List.of(
                        secondPress,
                        firstPress,
                        decommissionedPress
                )
        );

        assertThat(
                equipmentRepository.search(
                        "press",
                        EquipmentStatus.OPERATIONAL
                )
        )
                .extracting(Equipment::getInventoryNumber)
                .containsExactly("PRESS-001", "PRESS-002");
    }
}