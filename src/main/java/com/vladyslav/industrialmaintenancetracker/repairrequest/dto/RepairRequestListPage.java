package com.vladyslav.industrialmaintenancetracker.repairrequest.dto;

import java.util.List;

public record RepairRequestListPage(
        List<RepairRequestListItem> requests,
        List<RepairRequestFilterOption> equipment,
        List<RepairRequestFilterOption> authors,
        List<RepairRequestFilterOption> technicians,
        boolean administrator,
        boolean technician
) {
}
