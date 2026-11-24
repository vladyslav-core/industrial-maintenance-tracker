package com.vladyslav.industrialmaintenancetracker.equipment;

import com.vladyslav.industrialmaintenancetracker.equipment.dto.EquipmentDetailsPage;
import com.vladyslav.industrialmaintenancetracker.maintenance.MaintenanceRecordService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EquipmentDetailsService {

    private final EquipmentService equipmentService;
    private final MaintenanceRecordService maintenanceRecordService;

    public EquipmentDetailsService(
            EquipmentService equipmentService,
            MaintenanceRecordService maintenanceRecordService
    ) {
        this.equipmentService = equipmentService;
        this.maintenanceRecordService = maintenanceRecordService;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public EquipmentDetailsPage getDetailsPage(Long equipmentId, Long viewerId) {
        var history = maintenanceRecordService.getEquipmentHistory(equipmentId, viewerId);
        return new EquipmentDetailsPage(equipmentService.getEquipmentDetails(equipmentId), history);
    }
}
