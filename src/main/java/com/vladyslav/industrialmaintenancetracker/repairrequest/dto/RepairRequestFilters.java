package com.vladyslav.industrialmaintenancetracker.repairrequest.dto;

import com.vladyslav.industrialmaintenancetracker.repairrequest.RepairRequestPriority;
import com.vladyslav.industrialmaintenancetracker.repairrequest.RepairRequestScope;
import com.vladyslav.industrialmaintenancetracker.repairrequest.RepairRequestStatus;

public class RepairRequestFilters {

    private String search;
    private RepairRequestStatus status;
    private RepairRequestPriority priority;
    private Long equipmentId;
    private Long authorId;
    private Long technicianId;
    private RepairRequestScope scope = RepairRequestScope.ALL;

    public String getSearch() { return search; }
    public void setSearch(String search) { this.search = search; }
    public RepairRequestStatus getStatus() { return status; }
    public void setStatus(RepairRequestStatus status) { this.status = status; }
    public RepairRequestPriority getPriority() { return priority; }
    public void setPriority(RepairRequestPriority priority) { this.priority = priority; }
    public Long getEquipmentId() { return equipmentId; }
    public void setEquipmentId(Long equipmentId) { this.equipmentId = equipmentId; }
    public Long getAuthorId() { return authorId; }
    public void setAuthorId(Long authorId) { this.authorId = authorId; }
    public Long getTechnicianId() { return technicianId; }
    public void setTechnicianId(Long technicianId) { this.technicianId = technicianId; }
    public RepairRequestScope getScope() { return scope; }
    public void setScope(RepairRequestScope scope) {
        this.scope = scope == null ? RepairRequestScope.ALL : scope;
    }
}
