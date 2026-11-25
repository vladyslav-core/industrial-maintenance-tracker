package com.vladyslav.industrialmaintenancetracker.dashboard.dto;

public record AdminDashboard(
        long newCount,
        long assignedCount,
        long inProgressCount,
        long awaitingReviewCount,
        long criticalOpenCount
) {
}
