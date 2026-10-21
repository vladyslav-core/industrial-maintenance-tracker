package com.vladyslav.industrialmaintenancetracker.repairrequest.dto;

import com.vladyslav.industrialmaintenancetracker.repairrequest.RepairRequestPriority;
import com.vladyslav.industrialmaintenancetracker.repairrequest.RepairRequestStatus;

import java.time.Instant;

public record RepairRequestListItem(
        Long id,
        String title,
        Long equipmentId,
        String equipmentName,
        String authorName,
        String technicianName,
        RepairRequestPriority priority,
        RepairRequestStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
