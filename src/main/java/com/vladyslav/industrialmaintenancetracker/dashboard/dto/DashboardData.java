package com.vladyslav.industrialmaintenancetracker.dashboard.dto;

import com.vladyslav.industrialmaintenancetracker.repairrequest.dto.RepairRequestPreview;
import com.vladyslav.industrialmaintenancetracker.user.dto.CurrentUserSummary;

import java.util.List;

public record DashboardData(
        CurrentUserSummary viewer,
        AdminDashboard administrator,
        TechnicianDashboard technician,
        List<RepairRequestPreview> latestRequests
) {
    public DashboardData {
        latestRequests = List.copyOf(latestRequests);
    }
}
