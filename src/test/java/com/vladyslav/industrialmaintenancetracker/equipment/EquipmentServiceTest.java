package com.vladyslav.industrialmaintenancetracker.equipment;

import com.vladyslav.industrialmaintenancetracker.equipment.dto.EquipmentCreateForm;
import com.vladyslav.industrialmaintenancetracker.equipment.dto.EquipmentDetails;
import com.vladyslav.industrialmaintenancetracker.equipment.dto.EquipmentEditForm;
import com.vladyslav.industrialmaintenancetracker.equipment.dto.EquipmentListItem;
import com.vladyslav.industrialmaintenancetracker.exception.DuplicateInventoryNumberException;
import com.vladyslav.industrialmaintenancetracker.exception.EquipmentNotFoundException;
import com.vladyslav.industrialmaintenancetracker.exception.InvalidEquipmentStatusTransitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EquipmentServiceTest {

    @Mock
    private EquipmentRepository equipmentRepository;

    private EquipmentService equipmentService;

    @BeforeEach
    void setUp() {
        equipmentService = new EquipmentService(equipmentRepository);
    }

    @Test
    void shouldCreateEquipmentWithNormalizedData() {
        EquipmentCreateForm form = new EquipmentCreateForm();
        form.setName("  Hydraulic press  ");
        form.setInventoryNumber("  PRESS-001  ");
        form.setDescription("  Main production press  ");
        form.setLocation("  Workshop A  ");

        when(
                equipmentRepository.existsByInventoryNumberIgnoreCase(
                        "PRESS-001"
                )
        ).thenReturn(false);

        when(equipmentRepository.save(any(Equipment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Equipment created = equipmentService.createEquipment(form);

        assertThat(created.getName()).isEqualTo("Hydraulic press");
        assertThat(created.getInventoryNumber()).isEqualTo("PRESS-001");
        assertThat(created.getDescription())
                .isEqualTo("Main production press");
        assertThat(created.getLocation()).isEqualTo("Workshop A");
        assertThat(created.getStatus())
                .isEqualTo(EquipmentStatus.OPERATIONAL);

        verify(equipmentRepository)
                .existsByInventoryNumberIgnoreCase("PRESS-001");
        verify(equipmentRepository).save(created);
    }

    @Test
    void shouldConvertBlankDescriptionToNull() {
        EquipmentCreateForm form = new EquipmentCreateForm();
        form.setName("Compressor");
        form.setInventoryNumber("COMP-002");
        form.setDescription("   ");
        form.setLocation("Utility room");

        when(
                equipmentRepository.existsByInventoryNumberIgnoreCase(
                        "COMP-002"
                )
        ).thenReturn(false);

        when(equipmentRepository.save(any(Equipment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Equipment created = equipmentService.createEquipment(form);

        assertThat(created.getDescription()).isNull();
    }

    @Test
    void shouldRejectDuplicateInventoryNumberWithoutSaving() {
        EquipmentCreateForm form = new EquipmentCreateForm();
        form.setName("Compressor");
        form.setInventoryNumber("  comp-002  ");
        form.setDescription(null);
        form.setLocation("Utility room");

        when(
                equipmentRepository.existsByInventoryNumberIgnoreCase(
                        "comp-002"
                )
        ).thenReturn(true);

        assertThatThrownBy(
                () -> equipmentService.createEquipment(form)
        )
                .isInstanceOf(
                        DuplicateInventoryNumberException.class
                )
                .hasMessageContaining("comp-002");

        verify(equipmentRepository)
                .existsByInventoryNumberIgnoreCase("comp-002");
        verify(equipmentRepository, never())
                .save(any(Equipment.class));
    }

    @Test
    void shouldRejectUnknownEquipment() {
        when(equipmentRepository.findById(99L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                () -> equipmentService.getEquipmentForEditing(99L)
        )
                .isInstanceOf(EquipmentNotFoundException.class)
                .hasMessageContaining("99");

        verify(equipmentRepository).findById(99L);
    }

    @Test
    void shouldReturnEquipmentForEditing() {
        Equipment equipment = new Equipment(
                "Hydraulic press",
                "PRESS-001",
                "Main production press",
                "Workshop A"
        );

        when(equipmentRepository.findById(7L))
                .thenReturn(Optional.of(equipment));

        EquipmentEditForm form =
                equipmentService.getEquipmentForEditing(7L);

        assertThat(form.getName()).isEqualTo("Hydraulic press");
        assertThat(form.getInventoryNumber()).isEqualTo("PRESS-001");
        assertThat(form.getDescription())
                .isEqualTo("Main production press");
        assertThat(form.getLocation()).isEqualTo("Workshop A");

        verify(equipmentRepository).findById(7L);
    }

    @Test
    void shouldUpdateEquipmentWithNormalizedData() {
        Equipment equipment = new Equipment(
                "Old name",
                "PRESS-001",
                "Old description",
                "Old location"
        );

        EquipmentEditForm form = new EquipmentEditForm(
                "  New name  ",
                "  press-001  ",
                "   ",
                "  Workshop B  "
        );

        when(equipmentRepository.findById(7L))
                .thenReturn(Optional.of(equipment));

        equipmentService.updateEquipment(7L, form);

        assertThat(equipment.getName()).isEqualTo("New name");
        assertThat(equipment.getInventoryNumber())
                .isEqualTo("press-001");
        assertThat(equipment.getDescription()).isNull();
        assertThat(equipment.getLocation()).isEqualTo("Workshop B");

        verify(equipmentRepository).findById(7L);
        verify(equipmentRepository, never())
                .existsByInventoryNumberIgnoreCase(anyString());
    }

    @Test
    void shouldRejectDuplicateInventoryNumberWhenUpdating() {
        Equipment equipment = new Equipment(
                "Hydraulic press",
                "PRESS-001",
                null,
                "Workshop A"
        );

        EquipmentEditForm form = new EquipmentEditForm(
                "Hydraulic press",
                "PRESS-999",
                null,
                "Workshop A"
        );

        when(equipmentRepository.findById(7L))
                .thenReturn(Optional.of(equipment));

        when(
                equipmentRepository.existsByInventoryNumberIgnoreCase(
                        "PRESS-999"
                )
        ).thenReturn(true);

        assertThatThrownBy(
                () -> equipmentService.updateEquipment(7L, form)
        )
                .isInstanceOf(
                        DuplicateInventoryNumberException.class
                )
                .hasMessageContaining("PRESS-999");

        assertThat(equipment.getInventoryNumber())
                .isEqualTo("PRESS-001");
    }

    @Test
    void shouldChangeEquipmentStatus() {
        Equipment equipment = new Equipment(
                "Hydraulic press",
                "PRESS-001",
                null,
                "Workshop A"
        );

        when(equipmentRepository.findById(7L))
                .thenReturn(Optional.of(equipment));

        equipmentService.changeEquipmentStatus(
                7L,
                EquipmentStatus.UNDER_REPAIR
        );

        assertThat(equipment.getStatus())
                .isEqualTo(EquipmentStatus.UNDER_REPAIR);

        verify(equipmentRepository).findById(7L);
    }

    @Test
    void shouldRejectStatusChangeAfterDecommissioning() {
        Equipment equipment = new Equipment(
                "Hydraulic press",
                "PRESS-001",
                null,
                "Workshop A"
        );

        equipment.changeStatus(EquipmentStatus.DECOMMISSIONED);

        when(equipmentRepository.findById(7L))
                .thenReturn(Optional.of(equipment));

        assertThatThrownBy(
                () -> equipmentService.changeEquipmentStatus(
                        7L,
                        EquipmentStatus.OPERATIONAL
                )
        )
                .isInstanceOf(
                        InvalidEquipmentStatusTransitionException.class
                )
                .hasMessageContaining("DECOMMISSIONED")
                .hasMessageContaining("OPERATIONAL");

        assertThat(equipment.getStatus())
                .isEqualTo(EquipmentStatus.DECOMMISSIONED);
    }

    @Test
    void shouldReturnEquipmentListUsingNormalizedSearchAndStatus() {
        Equipment first = new Equipment(
                "Hydraulic press",
                "PRESS-001",
                null,
                "Workshop A"
        );

        Equipment second = new Equipment(
                "Hydraulic press",
                "PRESS-002",
                null,
                "Workshop B"
        );

        first.changeStatus(EquipmentStatus.UNDER_REPAIR);
        second.changeStatus(EquipmentStatus.UNDER_REPAIR);

        when(
                equipmentRepository.search(
                        "press",
                        EquipmentStatus.UNDER_REPAIR
                )
        ).thenReturn(List.of(first, second));

        List<EquipmentListItem> items =
                equipmentService.getEquipmentList(
                        "  press  ",
                        EquipmentStatus.UNDER_REPAIR
                );

        assertThat(items)
                .extracting(EquipmentListItem::getInventoryNumber)
                .containsExactly("PRESS-001", "PRESS-002");

        assertThat(items)
                .extracting(EquipmentListItem::getStatus)
                .containsOnly(EquipmentStatus.UNDER_REPAIR);

        verify(equipmentRepository).search(
                "press",
                EquipmentStatus.UNDER_REPAIR
        );
    }

    @Test
    void shouldUseEmptySearchTermWhenSearchIsNull() {
        when(equipmentRepository.search("", null))
                .thenReturn(List.of());

        List<EquipmentListItem> items =
                equipmentService.getEquipmentList(null, null);

        assertThat(items).isEmpty();

        verify(equipmentRepository).search("", null);
    }

    @Test
    void shouldReturnEquipmentDetails() {
        Equipment equipment = mock(Equipment.class);

        Instant createdAt = Instant.parse(
                "2026-09-21T10:00:00Z"
        );
        Instant updatedAt = Instant.parse(
                "2026-09-21T11:00:00Z"
        );

        when(equipment.getId()).thenReturn(7L);
        when(equipment.getName()).thenReturn("Hydraulic press");
        when(equipment.getInventoryNumber()).thenReturn("PRESS-001");
        when(equipment.getDescription())
                .thenReturn("Main production press");
        when(equipment.getLocation()).thenReturn("Workshop A");
        when(equipment.getStatus())
                .thenReturn(EquipmentStatus.OPERATIONAL);
        when(equipment.getCreatedAt()).thenReturn(createdAt);
        when(equipment.getUpdatedAt()).thenReturn(updatedAt);

        when(equipmentRepository.findById(7L))
                .thenReturn(Optional.of(equipment));

        EquipmentDetails details =
                equipmentService.getEquipmentDetails(7L);

        assertThat(details.getId()).isEqualTo(7L);
        assertThat(details.getName()).isEqualTo("Hydraulic press");
        assertThat(details.getInventoryNumber())
                .isEqualTo("PRESS-001");
        assertThat(details.getDescription())
                .isEqualTo("Main production press");
        assertThat(details.getLocation()).isEqualTo("Workshop A");
        assertThat(details.getStatus())
                .isEqualTo(EquipmentStatus.OPERATIONAL);
        assertThat(details.getCreatedAt()).isEqualTo(createdAt);
        assertThat(details.getUpdatedAt()).isEqualTo(updatedAt);

        verify(equipmentRepository).findById(7L);
    }
}