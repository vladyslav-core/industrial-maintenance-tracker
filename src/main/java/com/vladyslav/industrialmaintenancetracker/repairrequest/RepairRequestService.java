package com.vladyslav.industrialmaintenancetracker.repairrequest;

import com.vladyslav.industrialmaintenancetracker.equipment.Equipment;
import com.vladyslav.industrialmaintenancetracker.equipment.EquipmentRepository;
import com.vladyslav.industrialmaintenancetracker.equipment.EquipmentStatus;
import com.vladyslav.industrialmaintenancetracker.exception.EquipmentNotAvailableException;
import com.vladyslav.industrialmaintenancetracker.exception.EquipmentNotFoundException;
import com.vladyslav.industrialmaintenancetracker.exception.UserNotFoundException;
import com.vladyslav.industrialmaintenancetracker.repairrequest.dto.RepairRequestCreateForm;
import com.vladyslav.industrialmaintenancetracker.repairrequest.dto.RepairRequestEquipmentOption;
import com.vladyslav.industrialmaintenancetracker.user.User;
import com.vladyslav.industrialmaintenancetracker.user.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RepairRequestService {

    private final RepairRequestRepository requestRepository;
    private final EquipmentRepository equipmentRepository;
    private final UserRepository userRepository;

    public RepairRequestService(
            RepairRequestRepository requestRepository,
            EquipmentRepository equipmentRepository,
            UserRepository userRepository
    ) {
        this.requestRepository = requestRepository;
        this.equipmentRepository = equipmentRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<RepairRequestEquipmentOption> availableEquipment() {
        return equipmentRepository.search("", null).stream()
                .filter(equipment -> equipment.getStatus() != EquipmentStatus.DECOMMISSIONED)
                .map(equipment -> new RepairRequestEquipmentOption(
                        equipment.getId(), equipment.getName(), equipment.getInventoryNumber()
                ))
                .toList();
    }

    @Transactional
    public RepairRequest create(RepairRequestCreateForm form, Long authorId) {
        Equipment equipment = equipmentRepository.findByIdForUpdate(form.getEquipmentId())
                .orElseThrow(() -> new EquipmentNotFoundException(form.getEquipmentId()));
        if (equipment.getStatus() == EquipmentStatus.DECOMMISSIONED) {
            throw new EquipmentNotAvailableException(equipment.getId());
        }

        User author = userRepository.findById(authorId)
                .orElseThrow(() -> new UserNotFoundException(authorId));
        if (!author.isActive()) {
            throw new AccessDeniedException("Inactive users cannot create repair requests");
        }

        RepairRequest request = new RepairRequest(
                form.getTitle().trim(),
                form.getDescription().trim(),
                equipment,
                author,
                form.getPriority()
        );
        return requestRepository.save(request);
    }
}
