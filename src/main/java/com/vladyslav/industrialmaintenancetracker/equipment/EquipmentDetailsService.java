package com.vladyslav.industrialmaintenancetracker.equipment;

import com.vladyslav.industrialmaintenancetracker.equipment.dto.EquipmentDetailsPage;
import com.vladyslav.industrialmaintenancetracker.maintenance.MaintenanceRecordService;
import com.vladyslav.industrialmaintenancetracker.repairrequest.RepairRequestService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EquipmentDetailsService {

    private final EquipmentService equipmentService;
    private final MaintenanceRecordService maintenanceRecordService;
    private final RepairRequestService repairRequestService;

    public EquipmentDetailsService(
            EquipmentService equipmentService,
            MaintenanceRecordService maintenanceRecordService,
            RepairRequestService repairRequestService
    ) {
        this.equipmentService = equipmentService;
        this.maintenanceRecordService = maintenanceRecordService;
        this.repairRequestService = repairRequestService;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public EquipmentDetailsPage getDetailsPage(Long equipmentId, Long viewerId) {
        var equipment = equipmentService.getEquipmentDetails(equipmentId);
        var history = maintenanceRecordService.getEquipmentHistory(equipmentId, viewerId);
        var openRequests = repairRequestService.getEquipmentOpenRequests(equipmentId, viewerId);
        return new EquipmentDetailsPage(equipment, history, openRequests);
    }
}
