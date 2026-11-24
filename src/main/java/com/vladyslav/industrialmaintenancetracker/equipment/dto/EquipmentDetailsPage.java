package com.vladyslav.industrialmaintenancetracker.equipment.dto;

import com.vladyslav.industrialmaintenancetracker.maintenance.dto.MaintenanceHistoryItem;

import java.util.List;

public record EquipmentDetailsPage(
        EquipmentDetails equipment,
        List<MaintenanceHistoryItem> maintenanceHistory
) {
}
