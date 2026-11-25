package com.vladyslav.industrialmaintenancetracker.dashboard.dto;

import com.vladyslav.industrialmaintenancetracker.repairrequest.dto.RepairRequestPreview;

import java.util.List;

public record TechnicianDashboard(
        long assignedCount,
        long inProgressCount,
        List<RepairRequestPreview> assignedRequests,
        List<RepairRequestPreview> inProgressRequests
) {
    public TechnicianDashboard {
        assignedRequests = List.copyOf(assignedRequests);
        inProgressRequests = List.copyOf(inProgressRequests);
    }
}
