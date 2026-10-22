package com.vladyslav.industrialmaintenancetracker.repairrequest.dto;

import com.vladyslav.industrialmaintenancetracker.repairrequest.RepairRequestPriority;
import com.vladyslav.industrialmaintenancetracker.repairrequest.RepairRequestStatus;

import java.time.Instant;

public record RepairRequestDetails(
        Long id,
        String title,
        String description,
        Long equipmentId,
        String equipmentName,
        String inventoryNumber,
        String location,
        String authorName,
        String technicianName,
        RepairRequestPriority priority,
        RepairRequestStatus status,
        String resolution,
        String cancellationReason,
        Instant createdAt,
        Instant updatedAt,
        Instant assignedAt,
        Instant startedAt,
        Instant completedAt,
        Instant closedAt,
        boolean canEdit,
        boolean canManage,
        boolean canUnassign,
        boolean canStart,
        boolean canWork,
        boolean canReturn,
        boolean canClose,
        boolean canCancel
) {
}
