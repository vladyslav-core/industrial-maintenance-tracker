package com.vladyslav.industrialmaintenancetracker.maintenance.dto;

import com.vladyslav.industrialmaintenancetracker.equipment.dto.EquipmentListItem;

import java.util.List;

public record MaintenanceHistoryPage(
        List<MaintenanceHistoryItem> history,
        List<EquipmentListItem> equipment
) {
}
