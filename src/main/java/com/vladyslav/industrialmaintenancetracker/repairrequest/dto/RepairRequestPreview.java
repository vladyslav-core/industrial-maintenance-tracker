package com.vladyslav.industrialmaintenancetracker.repairrequest.dto;

import com.vladyslav.industrialmaintenancetracker.repairrequest.RepairRequestPriority;
import com.vladyslav.industrialmaintenancetracker.repairrequest.RepairRequestStatus;

import java.time.Instant;

public record RepairRequestPreview(
        Long id,
        String title,
        Long equipmentId,
        String equipmentName,
        RepairRequestStatus status,
        RepairRequestPriority priority,
        Instant createdAt
) {
}
