package com.vladyslav.industrialmaintenancetracker.equipment;

import com.vladyslav.industrialmaintenancetracker.equipment.dto.EquipmentCreateForm;
import com.vladyslav.industrialmaintenancetracker.equipment.dto.EquipmentDetails;
import com.vladyslav.industrialmaintenancetracker.equipment.dto.EquipmentEditForm;
import com.vladyslav.industrialmaintenancetracker.equipment.dto.EquipmentListItem;
import com.vladyslav.industrialmaintenancetracker.exception.DuplicateInventoryNumberException;
import com.vladyslav.industrialmaintenancetracker.exception.EquipmentNotFoundException;
import com.vladyslav.industrialmaintenancetracker.exception.EquipmentHasOpenRequestsException;
import com.vladyslav.industrialmaintenancetracker.repairrequest.RepairRequestRepository;
import com.vladyslav.industrialmaintenancetracker.repairrequest.RepairRequestStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EquipmentService {

    private final EquipmentRepository equipmentRepository;
    private final RepairRequestRepository requestRepository;

    public EquipmentService(EquipmentRepository equipmentRepository, RepairRequestRepository requestRepository) {
        this.equipmentRepository = equipmentRepository;
        this.requestRepository = requestRepository;
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
        Equipment equipment = lockedEquipment(equipmentId);

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
        // Request creation takes the same equipment lock before checking its status.
        Equipment equipment = lockedEquipment(equipmentId);
        if (targetStatus == EquipmentStatus.DECOMMISSIONED
                && equipment.getStatus() != EquipmentStatus.DECOMMISSIONED
                && requestRepository.existsByEquipment_IdAndStatusIn(equipmentId, List.of(
                        RepairRequestStatus.NEW, RepairRequestStatus.ASSIGNED,
                        RepairRequestStatus.IN_PROGRESS, RepairRequestStatus.COMPLETED
                ))) {
            throw new EquipmentHasOpenRequestsException();
        }

        equipment.changeStatus(targetStatus);
    }

    private Equipment lockedEquipment(Long equipmentId) {
        return equipmentRepository.findByIdForUpdate(equipmentId)
                .orElseThrow(() -> new EquipmentNotFoundException(equipmentId));
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
