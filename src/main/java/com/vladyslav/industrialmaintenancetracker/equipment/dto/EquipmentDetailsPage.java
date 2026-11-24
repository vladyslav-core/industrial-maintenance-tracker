package com.vladyslav.industrialmaintenancetracker.equipment.dto;

import com.vladyslav.industrialmaintenancetracker.maintenance.dto.MaintenanceHistoryItem;
import com.vladyslav.industrialmaintenancetracker.repairrequest.dto.RepairRequestListItem;

import java.util.List;

public record EquipmentDetailsPage(
        EquipmentDetails equipment,
        List<MaintenanceHistoryItem> maintenanceHistory,
        List<RepairRequestListItem> openRepairRequests
) {
}
