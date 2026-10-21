package com.vladyslav.industrialmaintenancetracker.repairrequest;

import com.vladyslav.industrialmaintenancetracker.equipment.Equipment;
import com.vladyslav.industrialmaintenancetracker.equipment.EquipmentRepository;
import com.vladyslav.industrialmaintenancetracker.equipment.EquipmentStatus;
import com.vladyslav.industrialmaintenancetracker.exception.EquipmentNotAvailableException;
import com.vladyslav.industrialmaintenancetracker.exception.EquipmentNotFoundException;
import com.vladyslav.industrialmaintenancetracker.exception.RepairRequestNotFoundException;
import com.vladyslav.industrialmaintenancetracker.exception.UserNotFoundException;
import com.vladyslav.industrialmaintenancetracker.repairrequest.dto.RepairRequestCreateForm;
import com.vladyslav.industrialmaintenancetracker.repairrequest.dto.RepairRequestDetails;
import com.vladyslav.industrialmaintenancetracker.repairrequest.dto.RepairRequestEquipmentOption;
import com.vladyslav.industrialmaintenancetracker.repairrequest.dto.RepairRequestListItem;
import com.vladyslav.industrialmaintenancetracker.user.Role;
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

    @Transactional(readOnly = true)
    public List<RepairRequestListItem> getList(Long viewerId) {
        User viewer = activeViewer(viewerId);
        return requestRepository.findVisible(
                        viewer.getId(),
                        viewer.getRole() == Role.ADMIN,
                        viewer.getRole() == Role.TECHNICIAN
                ).stream()
                .map(request -> new RepairRequestListItem(
                        request.getId(), request.getTitle(),
                        request.getEquipment().getId(), request.getEquipment().getName(),
                        request.getCreatedBy().getFullName(), technicianName(request),
                        request.getPriority(), request.getStatus(),
                        request.getCreatedAt(), request.getUpdatedAt()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public RepairRequestDetails getDetails(Long requestId, Long viewerId) {
        User viewer = activeViewer(viewerId);
        RepairRequest request = requestRepository.findVisibleById(
                requestId, viewer.getId(),
                viewer.getRole() == Role.ADMIN,
                viewer.getRole() == Role.TECHNICIAN
        ).orElseThrow(() -> new RepairRequestNotFoundException(requestId));

        return new RepairRequestDetails(
                request.getId(), request.getTitle(), request.getDescription(),
                request.getEquipment().getId(), request.getEquipment().getName(),
                request.getEquipment().getInventoryNumber(), request.getEquipment().getLocation(),
                request.getCreatedBy().getFullName(), technicianName(request),
                request.getPriority(), request.getStatus(),
                request.getResolution(), request.getCancellationReason(),
                request.getCreatedAt(), request.getUpdatedAt(), request.getAssignedAt(),
                request.getStartedAt(), request.getCompletedAt(), request.getClosedAt()
        );
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

    private User activeViewer(Long viewerId) {
        User viewer = userRepository.findById(viewerId)
                .orElseThrow(() -> new AccessDeniedException("Account is unavailable"));
        if (!viewer.isActive()) {
            throw new AccessDeniedException("Account is inactive");
        }
        return viewer;
    }

    private String technicianName(RepairRequest request) {
        return request.getAssignedTechnician() == null
                ? null : request.getAssignedTechnician().getFullName();
    }
}
