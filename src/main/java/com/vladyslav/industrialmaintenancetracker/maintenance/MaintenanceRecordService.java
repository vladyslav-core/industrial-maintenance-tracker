package com.vladyslav.industrialmaintenancetracker.maintenance;

import com.vladyslav.industrialmaintenancetracker.equipment.EquipmentRepository;
import com.vladyslav.industrialmaintenancetracker.exception.EquipmentNotFoundException;
import com.vladyslav.industrialmaintenancetracker.maintenance.dto.MaintenanceHistoryItem;
import com.vladyslav.industrialmaintenancetracker.repairrequest.RepairRequest;
import com.vladyslav.industrialmaintenancetracker.user.Role;
import com.vladyslav.industrialmaintenancetracker.user.User;
import com.vladyslav.industrialmaintenancetracker.user.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class MaintenanceRecordService {

    private final MaintenanceRecordRepository recordRepository;
    private final EquipmentRepository equipmentRepository;
    private final UserRepository userRepository;

    public MaintenanceRecordService(
            MaintenanceRecordRepository recordRepository,
            EquipmentRepository equipmentRepository,
            UserRepository userRepository
    ) {
        this.recordRepository = recordRepository;
        this.equipmentRepository = equipmentRepository;
        this.userRepository = userRepository;
    }

    public List<MaintenanceHistoryItem> getEquipmentHistory(Long equipmentId, Long viewerId) {
        User viewer = activeViewer(viewerId);
        requireEquipment(equipmentId);
        return history(equipmentId, viewer);
    }

    public List<MaintenanceHistoryItem> getHistory(Long equipmentId, Long viewerId) {
        User viewer = activeViewer(viewerId);
        if (viewer.getRole() != Role.ADMIN && viewer.getRole() != Role.TECHNICIAN) {
            throw new AccessDeniedException("Only administrators and technicians can view the general history");
        }
        if (equipmentId != null) {
            requireEquipment(equipmentId);
        }
        return history(equipmentId, viewer);
    }

    private User activeViewer(Long viewerId) {
        if (viewerId == null) {
            throw new AccessDeniedException("Account is unavailable");
        }
        User viewer = userRepository.findById(viewerId)
                .orElseThrow(() -> new AccessDeniedException("Account is unavailable"));
        if (!viewer.isActive()) {
            throw new AccessDeniedException("Account is inactive");
        }
        return viewer;
    }

    private void requireEquipment(Long equipmentId) {
        if (equipmentId == null || equipmentId <= 0) {
            throw new IllegalArgumentException("Equipment id must be a positive number");
        }
        if (!equipmentRepository.existsById(equipmentId)) {
            throw new EquipmentNotFoundException(equipmentId);
        }
    }

    private List<MaintenanceHistoryItem> history(Long equipmentId, User viewer) {
        return recordRepository.findHistory(equipmentId).stream()
                .map(record -> historyItem(record, viewer))
                .toList();
    }

    private MaintenanceHistoryItem historyItem(MaintenanceRecord record, User viewer) {
        RepairRequest request = record.getRepairRequest();
        boolean canViewRequest = viewer.getRole() == Role.ADMIN
                || request.getCreatedBy().getId().equals(viewer.getId())
                || (viewer.getRole() == Role.TECHNICIAN
                    && request.getAssignedTechnician() != null
                    && request.getAssignedTechnician().getId().equals(viewer.getId()));

        return new MaintenanceHistoryItem(
                record.getId(),
                record.getEquipment().getId(),
                record.getEquipment().getName(),
                record.getEquipment().getInventoryNumber(),
                request.getId(),
                request.getTitle(),
                request.getCreatedAt(),
                request.getCreatedBy().getFullName(),
                request.getDescription(),
                record.getPerformedBy().getFullName(),
                record.getWorkDescription(),
                record.getPerformedAt(),
                request.getClosedAt(),
                canViewRequest
        );
    }
}
