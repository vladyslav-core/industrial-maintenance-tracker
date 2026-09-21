package com.vladyslav.industrialmaintenancetracker.equipment;

import com.vladyslav.industrialmaintenancetracker.equipment.dto.EquipmentCreateForm;
import com.vladyslav.industrialmaintenancetracker.equipment.dto.EquipmentDetails;
import com.vladyslav.industrialmaintenancetracker.equipment.dto.EquipmentEditForm;
import com.vladyslav.industrialmaintenancetracker.equipment.dto.EquipmentListItem;
import com.vladyslav.industrialmaintenancetracker.exception.DuplicateInventoryNumberException;
import com.vladyslav.industrialmaintenancetracker.exception.EquipmentNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EquipmentService {

    private final EquipmentRepository equipmentRepository;

    public EquipmentService(EquipmentRepository equipmentRepository) {
        this.equipmentRepository = equipmentRepository;
    }

    @Transactional
    public Equipment createEquipment(EquipmentCreateForm form) {
        String name = form.getName().trim();
        String inventoryNumber = form.getInventoryNumber().trim();
        String description = normalizeOptional(form.getDescription());
        String location = form.getLocation().trim();

        if (
                equipmentRepository.existsByInventoryNumberIgnoreCase(
                        inventoryNumber
                )
        ) {
            throw new DuplicateInventoryNumberException(
                    inventoryNumber
            );
        }

        Equipment equipment = new Equipment(
                name,
                inventoryNumber,
                description,
                location
        );

        return equipmentRepository.save(equipment);
    }

    @Transactional(readOnly = true)
    public List<EquipmentListItem> getEquipmentList(
            String searchTerm,
            EquipmentStatus status
    ) {
        String normalizedSearchTerm =
                normalizeSearchTerm(searchTerm);

        return equipmentRepository.search(
                        normalizedSearchTerm,
                        status
                )
                .stream()
                .map(equipment -> new EquipmentListItem(
                        equipment.getId(),
                        equipment.getName(),
                        equipment.getInventoryNumber(),
                        equipment.getLocation(),
                        equipment.getStatus()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public EquipmentDetails getEquipmentDetails(Long equipmentId) {
        Equipment equipment = findEquipment(equipmentId);

        return new EquipmentDetails(
                equipment.getId(),
                equipment.getName(),
                equipment.getInventoryNumber(),
                equipment.getDescription(),
                equipment.getLocation(),
                equipment.getStatus(),
                equipment.getCreatedAt(),
                equipment.getUpdatedAt()
        );
    }

    @Transactional(readOnly = true)
    public EquipmentEditForm getEquipmentForEditing(Long equipmentId) {
        Equipment equipment = findEquipment(equipmentId);

        return new EquipmentEditForm(
                equipment.getName(),
                equipment.getInventoryNumber(),
                equipment.getDescription(),
                equipment.getLocation()
        );
    }

    @Transactional
    public void updateEquipment(
            Long equipmentId,
            EquipmentEditForm form
    ) {
        Equipment equipment = findEquipment(equipmentId);

        String name = form.getName().trim();
        String inventoryNumber = form.getInventoryNumber().trim();
        String description = normalizeOptional(form.getDescription());
        String location = form.getLocation().trim();

        boolean inventoryNumberChanged =
                !equipment.getInventoryNumber()
                        .equalsIgnoreCase(inventoryNumber);

        if (
                inventoryNumberChanged
                        && equipmentRepository
                        .existsByInventoryNumberIgnoreCase(
                                inventoryNumber
                        )
        ) {
            throw new DuplicateInventoryNumberException(
                    inventoryNumber
            );
        }

        equipment.updateDetails(
                name,
                inventoryNumber,
                description,
                location
        );
    }

    @Transactional
    public void changeEquipmentStatus(
            Long equipmentId,
            EquipmentStatus targetStatus
    ) {
        Equipment equipment = findEquipment(equipmentId);

        equipment.changeStatus(targetStatus);
    }

    private Equipment findEquipment(Long equipmentId) {
        return equipmentRepository.findById(equipmentId)
                .orElseThrow(
                        () -> new EquipmentNotFoundException(
                                equipmentId
                        )
                );
    }

    private String normalizeSearchTerm(String searchTerm) {
        if (searchTerm == null) {
            return "";
        }

        return searchTerm.trim();
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();

        return normalized.isEmpty() ? null : normalized;
    }
}