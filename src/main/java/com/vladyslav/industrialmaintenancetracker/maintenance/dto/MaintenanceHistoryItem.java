package com.vladyslav.industrialmaintenancetracker.maintenance.dto;

import java.time.Instant;

public record MaintenanceHistoryItem(
        Long id,
        Long equipmentId,
        String equipmentName,
        String inventoryNumber,
        Long requestId,
        String requestTitle,
        Instant requestCreatedAt,
        String authorName,
        String requestDescription,
        String technicianName,
        String workDescription,
        Instant performedAt,
        Instant closedAt,
        boolean canViewRequest
) {
}
